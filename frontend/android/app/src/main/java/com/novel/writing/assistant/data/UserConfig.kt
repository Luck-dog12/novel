package com.novel.writing.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_config")
data class UserConfig(
    @PrimaryKey val id: String = "default",
    val theme: String = "light",
    val fontSize: Int = 16,
    val fontFamily: String = "sans-serif",
    val autoSave: Boolean = true,
    val notificationEnabled: Boolean = true,
    val lastUpdated: String = System.currentTimeMillis().toString()
)

@androidx.room.Dao
interface UserConfigDao {
    @androidx.room.Query("SELECT * FROM user_config WHERE id = 'default'")
    suspend fun getDefaultConfig(): UserConfig?
    
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: UserConfig)
}
