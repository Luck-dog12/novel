package com.novel.writing.assistant.service

import com.novel.writing.assistant.model.ProjectCreateRequest
import com.novel.writing.assistant.model.ProjectUpdateRequest
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class Project(
    val id: String,
    val title: String,
    val description: String,
    val genreType: String,
    val creationDate: String,
    val lastModifiedDate: String,
    val status: String,
    val wordCount: Int
)

object ProjectService {
    fun getAllProjects(): List<Project> {
        val cacheKey = "projects:all"
        
        // Try to get from cache
        CacheService.instance.get<List<Project>>(cacheKey)?.let {
            return it
        }

        val result = DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id, title, description, genre_type, creation_date, last_modified_date, status, word_count
                FROM writing_projects
                """.trimIndent()
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    val results = mutableListOf<Project>()
                    while (rs.next()) {
                        results.add(
                            Project(
                                id = rs.getString("id"),
                                title = rs.getString("title"),
                                description = rs.getString("description"),
                                genreType = rs.getString("genre_type"),
                                creationDate = rs.getString("creation_date"),
                                lastModifiedDate = rs.getString("last_modified_date"),
                                status = rs.getString("status"),
                                wordCount = rs.getInt("word_count")
                            )
                        )
                    }
                    results
                }
            }
        }

        // Cache the result
        CacheService.instance.put(cacheKey, result)
        return result
    }
    
    fun getProjectById(id: String): Project? {
        val cacheKey = "projects:$id"
        
        // Try to get from cache
        CacheService.instance.get<Project>(cacheKey)?.let {
            return it
        }

        val result = DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id, title, description, genre_type, creation_date, last_modified_date, status, word_count
                FROM writing_projects
                WHERE id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { rs ->
                    if (!rs.next()) return@withConnection null
                    Project(
                        id = rs.getString("id"),
                        title = rs.getString("title"),
                        description = rs.getString("description"),
                        genreType = rs.getString("genre_type"),
                        creationDate = rs.getString("creation_date"),
                        lastModifiedDate = rs.getString("last_modified_date"),
                        status = rs.getString("status"),
                        wordCount = rs.getInt("word_count")
                    )
                }
            }
        }

        // Cache the result if found
        result?.let {
            CacheService.instance.put(cacheKey, it)
        }
        return result
    }
    
    fun createProject(request: ProjectCreateRequest): Project {
        val now = DatabaseService.nowString()
        val project = Project(
            id = UUID.randomUUID().toString(),
            title = request.title,
            description = request.description,
            genreType = request.genreType,
            creationDate = now,
            lastModifiedDate = now,
            status = "in_progress",
            wordCount = 0
        )
        DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO writing_projects (id, title, description, genre_type, creation_date, last_modified_date, status, word_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, project.id)
                statement.setString(2, project.title)
                statement.setString(3, project.description)
                statement.setString(4, project.genreType)
                statement.setString(5, project.creationDate)
                statement.setString(6, project.lastModifiedDate)
                statement.setString(7, project.status)
                statement.setInt(8, project.wordCount)
                statement.executeUpdate()
            }
        }
        
        // Clear relevant caches
        CacheService.instance.remove("projects:all")
        return project
    }
    
    fun updateProject(id: String, request: ProjectUpdateRequest): Project? {
        val project = getProjectById(id) ?: return null
        val updatedProject = project.copy(
            title = request.title ?: project.title,
            description = request.description ?: project.description,
            genreType = request.genreType ?: project.genreType,
            status = request.status ?: project.status,
            lastModifiedDate = DatabaseService.nowString()
        )
        DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE writing_projects
                SET title = ?, description = ?, genre_type = ?, last_modified_date = ?, status = ?, word_count = ?
                WHERE id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, updatedProject.title)
                statement.setString(2, updatedProject.description)
                statement.setString(3, updatedProject.genreType)
                statement.setString(4, updatedProject.lastModifiedDate)
                statement.setString(5, updatedProject.status)
                statement.setInt(6, updatedProject.wordCount)
                statement.setString(7, updatedProject.id)
                statement.executeUpdate()
            }
        }
        
        // Clear relevant caches
        CacheService.instance.remove("projects:all")
        CacheService.instance.remove("projects:$id")
        return updatedProject
    }
    
    fun deleteProject(id: String): Boolean {
        val result = DatabaseService.withConnection { connection ->
            connection.prepareStatement("DELETE FROM writing_projects WHERE id = ?").use { statement ->
                statement.setString(1, id)
                statement.executeUpdate() > 0
            }
        }
        
        // Clear relevant caches
        if (result) {
            CacheService.instance.remove("projects:all")
            CacheService.instance.remove("projects:$id")
        }
        return result
    }
}
