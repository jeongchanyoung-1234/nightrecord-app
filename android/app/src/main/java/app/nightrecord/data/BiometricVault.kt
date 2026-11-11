package app.nightrecord.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * Seals data with the public half of a Keystore RSA key (no authentication needed),
 * so rotated tokens can be re-sealed at any time. Opening requires a cipher that was
 * unlocked by a strong biometric through BiometricPrompt.
 */
class BiometricVault {
    sealed interface CipherResult {
        data class Ready(val cipher: Cipher) : CipherResult
        data object Invalidated : CipherResult
        data object Missing : CipherResult
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun hasKey(): Boolean = runCatching { keyStore.containsAlias(ALIAS) }.getOrDefault(false)

    fun createKey() {
        deleteKey()
        val builder = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE).apply {
            initialize(builder.build())
            generateKeyPair()
        }
    }

    fun deleteKey() {
        runCatching { if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS) }
    }

    fun seal(plain: ByteArray): String {
        val stored = keyStore.getCertificate(ALIAS)?.publicKey
            ?: throw IllegalStateException("biometric key missing")
        val publicKey = KeyFactory.getInstance(stored.algorithm)
            .generatePublic(X509EncodedKeySpec(stored.encoded))
        val dataKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val aes = Cipher.getInstance(AES_MODE).apply { init(Cipher.ENCRYPT_MODE, dataKey) }
        val body = aes.doFinal(plain)
        val rsa = Cipher.getInstance(RSA_MODE).apply { init(Cipher.ENCRYPT_MODE, publicKey, OAEP) }
        val wrapped = rsa.doFinal(dataKey.encoded)
        return listOf(wrapped, aes.iv, body).joinToString(".") { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    fun decryptCipher(): CipherResult {
        return try {
            val key = keyStore.getKey(ALIAS, null) as? PrivateKey ?: return CipherResult.Missing
            CipherResult.Ready(Cipher.getInstance(RSA_MODE).apply { init(Cipher.DECRYPT_MODE, key, OAEP) })
        } catch (_: KeyPermanentlyInvalidatedException) {
            CipherResult.Invalidated
        } catch (_: Exception) {
            CipherResult.Missing
        }
    }

    fun open(authenticated: Cipher, sealed: String): ByteArray {
        val parts = sealed.split(".")
        require(parts.size == 3) { "invalid sealed payload" }
        val (wrapped, iv, body) = parts.map { Base64.decode(it, Base64.NO_WRAP) }
        val keyBytes = authenticated.doFinal(wrapped)
        val aes = Cipher.getInstance(AES_MODE).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        }
        keyBytes.fill(0)
        return aes.doFinal(body)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "nightrecord.session.v1"
        const val RSA_MODE = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        const val AES_MODE = "AES/GCM/NoPadding"
        val OAEP = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    }
}
