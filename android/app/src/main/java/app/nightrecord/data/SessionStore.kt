package app.nightrecord.data

import android.content.Context
import org.json.JSONObject
import javax.crypto.Cipher

/**
 * Plain mode keeps tokens in private prefs. Biometric mode keeps them only in memory
 * after unlock and persists a vault-sealed copy, so a locked app holds no usable token.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("nightrecord.session", Context.MODE_PRIVATE)
    private val vault = BiometricVault()

    @Volatile private var memAccess: String? = null
    @Volatile private var memRefresh: String? = null
    private var pendingSeal: String? = null

    val biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIO, false)

    val isLocked: Boolean
        get() = biometricEnabled && memAccess == null && memRefresh == null

    var accessToken: String?
        get() = if (biometricEnabled) memAccess else prefs.getString(KEY_ACCESS, null)
        set(value) {
            if (biometricEnabled) {
                memAccess = value
                persistSealed()
            } else {
                prefs.edit().putString(KEY_ACCESS, value).apply()
            }
        }

    var refreshToken: String?
        get() = if (biometricEnabled) memRefresh else prefs.getString(KEY_REFRESH, null)
        set(value) {
            if (biometricEnabled) {
                memRefresh = value
                persistSealed()
            } else {
                prefs.edit().putString(KEY_REFRESH, value).apply()
            }
        }

    var username: String?
        get() = prefs.getString(KEY_USER, null)
        set(value) = prefs.edit().putString(KEY_USER, value).apply()

    fun hasSession(): Boolean =
        if (biometricEnabled) !prefs.getString(KEY_SEALED, null).isNullOrBlank()
        else !prefs.getString(KEY_ACCESS, null).isNullOrBlank()

    fun unlockCipher(): BiometricVault.CipherResult = vault.decryptCipher()

    fun unlock(authenticated: Cipher): Boolean {
        val sealed = prefs.getString(KEY_SEALED, null) ?: return false
        return runCatching {
            val json = JSONObject(String(vault.open(authenticated, sealed), Charsets.UTF_8))
            memAccess = json.optString("a").ifBlank { null }
            memRefresh = json.optString("r").ifBlank { null }
            memAccess != null || memRefresh != null
        }.getOrDefault(false)
    }

    fun lock() {
        if (!biometricEnabled) return
        memAccess = null
        memRefresh = null
    }

    /** Creates the key and seals the current tokens; returns a cipher to confirm via BiometricPrompt. */
    fun prepareBiometric(): Cipher? {
        if (biometricEnabled) return null
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null)
        return runCatching {
            vault.createKey()
            pendingSeal = vault.seal(tokenJson(access, refresh))
            (vault.decryptCipher() as? BiometricVault.CipherResult.Ready)?.cipher
        }.getOrNull().also { if (it == null) cancelBiometric() }
    }

    fun commitBiometric(authenticated: Cipher): Boolean {
        val sealed = pendingSeal ?: return false
        val ok = runCatching {
            val json = JSONObject(String(vault.open(authenticated, sealed), Charsets.UTF_8))
            json.optString("a") == prefs.getString(KEY_ACCESS, null)
        }.getOrDefault(false)
        if (!ok) {
            cancelBiometric()
            return false
        }
        memAccess = prefs.getString(KEY_ACCESS, null)
        memRefresh = prefs.getString(KEY_REFRESH, null)
        prefs.edit()
            .putString(KEY_SEALED, sealed)
            .putBoolean(KEY_BIO, true)
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .commit()
        pendingSeal = null
        return true
    }

    fun cancelBiometric() {
        pendingSeal = null
        if (!biometricEnabled) vault.deleteKey()
    }

    fun disableBiometric() {
        if (!biometricEnabled || isLocked) return
        prefs.edit()
            .putString(KEY_ACCESS, memAccess)
            .putString(KEY_REFRESH, memRefresh)
            .remove(KEY_SEALED)
            .putBoolean(KEY_BIO, false)
            .commit()
        memAccess = null
        memRefresh = null
        vault.deleteKey()
    }

    fun clear() {
        memAccess = null
        memRefresh = null
        pendingSeal = null
        prefs.edit().clear().commit()
        vault.deleteKey()
    }

    private fun persistSealed() {
        val access = memAccess
        val refresh = memRefresh
        if (access == null && refresh == null) return
        runCatching { vault.seal(tokenJson(access, refresh)) }
            .onSuccess { prefs.edit().putString(KEY_SEALED, it).apply() }
    }

    private fun tokenJson(access: String?, refresh: String?): ByteArray =
        JSONObject().put("a", access ?: "").put("r", refresh ?: "").toString().toByteArray(Charsets.UTF_8)

    companion object {
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_USER = "username"
        private const val KEY_BIO = "biometric_enabled"
        private const val KEY_SEALED = "sealed_session"
    }
}
