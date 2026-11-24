package io.github.emilmelgaard.pathwake.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute, id")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms ORDER BY hour, minute, id")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Long): AlarmEntity?

    /** Inserts a new alarm (id 0) or replaces one with the same id; returns the id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [AlarmEntity::class], version = 1, exportSchema = true)
abstract class PathwakeDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        /**
         * The database lives in device-protected storage so alarms can be scheduled and rung after a reboot,
         * before the user unlocks the device (direct boot). It only holds alarm settings.
         */
        fun create(context: Context): PathwakeDatabase {
            val storage = context.createDeviceProtectedStorageContext()
            return Room.databaseBuilder(storage, PathwakeDatabase::class.java, "pathwake.db").build()
        }
    }
}
