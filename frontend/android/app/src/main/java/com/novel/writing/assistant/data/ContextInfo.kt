package com.novel.writing.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "context_info")
data class ContextInfo(
    @PrimaryKey val id: String,
    val projectId: String,
    val contextType: String,
    val contextContent: String,
    val lastUpdated: String
)

@androidx.room.Dao
interface ContextInfoDao {
    @androidx.room.Query("SELECT * FROM context_info WHERE projectId = :projectId ORDER BY lastUpdated DESC")
    suspend fun getContextByProjectId(projectId: String): List<ContextInfo>
    
    @androidx.room.Query("SELECT * FROM context_info WHERE id = :id")
    suspend fun getContextById(id: String): ContextInfo?
    
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(context: ContextInfo)
    
    @androidx.room.Delete
    suspend fun delete(context: ContextInfo)
    
    @androidx.room.Query("DELETE FROM context_info WHERE projectId = :projectId")
    suspend fun deleteByProjectId(projectId: String)
}
