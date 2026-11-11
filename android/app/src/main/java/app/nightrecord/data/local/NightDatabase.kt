package app.nightrecord.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(tableName = "expeditions")
data class ExpeditionEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val timestamp: Long,
    val payload: String,
)

@Entity(tableName = "cache_entries")
data class CacheEntry(
    @PrimaryKey val key: String,
    val json: String,
    val updatedAt: Long,
)

@Dao
abstract class CacheDao {
    @Query("SELECT payload FROM expeditions ORDER BY timestamp ASC, rowId ASC")
    abstract suspend fun runPayloads(): List<String>

    @Query("DELETE FROM expeditions")
    abstract suspend fun clearRuns()

    @Insert
    abstract suspend fun insertRuns(rows: List<ExpeditionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun put(entry: CacheEntry)

    @Query("SELECT * FROM cache_entries WHERE `key` = :key LIMIT 1")
    abstract suspend fun get(key: String): CacheEntry?

    @Query("DELETE FROM cache_entries")
    abstract suspend fun clearEntries()

    @Transaction
    open suspend fun replaceSnapshot(runs: List<ExpeditionEntity>, entries: List<CacheEntry>) {
        clearRuns()
        insertRuns(runs)
        entries.forEach { put(it) }
    }

    @Transaction
    open suspend fun clearAll() {
        clearRuns()
        clearEntries()
    }
}

@Database(entities = [ExpeditionEntity::class, CacheEntry::class], version = 1, exportSchema = false)
abstract class NightDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    companion object {
        @Volatile private var instance: NightDatabase? = null

        fun get(context: Context): NightDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, NightDatabase::class.java, "nightrecord.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
