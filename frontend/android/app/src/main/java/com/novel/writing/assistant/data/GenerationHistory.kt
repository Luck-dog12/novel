package com.novel.writing.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "generation_history")
data class GenerationHistory(
    @PrimaryKey val id: String,
    val projectId: String,
    val generationType: String,
    val inputParams: String,
    val outputContent: String,
    val generationDate: String,
    val duration: Long
)

@androidx.room.Dao
interface GenerationHistoryDao {
    @androidx.room.Query("SELECT * FROM generation_history WHERE projectId = :projectId ORDER BY generationDate DESC")
    suspend fun getHistoryByProjectId(projectId: String): List<GenerationHistory>
    
    @androidx.room.Query("SELECT * FROM generation_history WHERE id = :id")
    suspend fun getHistoryById(id: String): GenerationHistory?
    
    @androidx.room.Insert
    suspend fun insert(history: GenerationHistory)
    
    @androidx.room.Delete
    suspend fun delete(history: GenerationHistory)
    
    @androidx.room.Query("DELETE FROM generation_history WHERE projectId = :projectId")
    suspend fun deleteByProjectId(projectId: String)
}
