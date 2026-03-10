package com.novel.writing.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reference_document")
data class ReferenceDocument(
    @PrimaryKey val id: String,
    val projectId: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val uploadDate: String,
    val filePath: String,
    val contentHash: String
)

@androidx.room.Dao
interface ReferenceDocumentDao {
    @androidx.room.Query("SELECT * FROM reference_document WHERE projectId = :projectId ORDER BY uploadDate DESC")
    suspend fun getDocumentsByProjectId(projectId: String): List<ReferenceDocument>
    
    @androidx.room.Query("SELECT * FROM reference_document WHERE id = :id")
    suspend fun getDocumentById(id: String): ReferenceDocument?
    
    @androidx.room.Insert
    suspend fun insert(document: ReferenceDocument)
    
    @androidx.room.Delete
    suspend fun delete(document: ReferenceDocument)
    
    @androidx.room.Query("DELETE FROM reference_document WHERE projectId = :projectId")
    suspend fun deleteByProjectId(projectId: String)
}
