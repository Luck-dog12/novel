package com.novel.writing.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "writing_project")
data class WritingProject(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val genreType: String,
    val creationDate: String,
    val lastModifiedDate: String,
    val status: String,
    val wordCount: Int
)

@androidx.room.Dao
interface WritingProjectDao {
    @androidx.room.Query("SELECT * FROM writing_project ORDER BY lastModifiedDate DESC")
    suspend fun getAllProjects(): List<WritingProject>
    
    @androidx.room.Query("SELECT * FROM writing_project WHERE id = :id")
    suspend fun getProjectById(id: String): WritingProject?
    
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(project: WritingProject)
    
    @androidx.room.Delete
    suspend fun delete(project: WritingProject)
}
