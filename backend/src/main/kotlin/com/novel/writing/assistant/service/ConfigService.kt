package com.novel.writing.assistant.service

import com.novel.writing.assistant.model.ConfigRequest
import com.novel.writing.assistant.model.ConfigResponse
import java.util.*

object ConfigService {
    suspend fun saveConfig(request: ConfigRequest): ConfigResponse {
        // Validate config
        validateConfig(request)
        
        // Create response
        val response = ConfigResponse(
            id = UUID.randomUUID().toString(),
            projectId = request.projectId,
            genreType = request.genreType,
            writingDirection = request.writingDirection,
            maxLength = request.maxLength,
            creativityLevel = request.creativityLevel,
            timestamp = DatabaseService.nowString()
        )
        DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO project_configs (id, project_id, genre_type, writing_direction, max_length, creativity_level, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    genre_type = VALUES(genre_type),
                    writing_direction = VALUES(writing_direction),
                    max_length = VALUES(max_length),
                    creativity_level = VALUES(creativity_level),
                    timestamp = VALUES(timestamp)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, response.id)
                statement.setString(2, response.projectId)
                statement.setString(3, response.genreType)
                statement.setString(4, response.writingDirection)
                statement.setInt(5, response.maxLength)
                statement.setString(6, response.creativityLevel)
                statement.setString(7, response.timestamp)
                statement.executeUpdate()
            }
        }
        
        return response
    }
    
    fun getConfigByProjectId(projectId: String): ConfigResponse? {
        return DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id, project_id, genre_type, writing_direction, max_length, creativity_level, timestamp
                FROM project_configs
                WHERE project_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, projectId)
                statement.executeQuery().use { rs ->
                    if (!rs.next()) return@withConnection null
                    ConfigResponse(
                        id = rs.getString("id"),
                        projectId = rs.getString("project_id"),
                        genreType = rs.getString("genre_type"),
                        writingDirection = rs.getString("writing_direction"),
                        maxLength = rs.getInt("max_length"),
                        creativityLevel = rs.getString("creativity_level"),
                        timestamp = rs.getString("timestamp")
                    )
                }
            }
        }
    }
    
    private fun validateConfig(request: ConfigRequest) {
        // Validate max length
        if (request.maxLength <= 0 || request.maxLength > 5000) {
            throw IllegalArgumentException("Max length must be between 1 and 5000")
        }
        
        // Validate creativity level
        val validCreativityLevels = listOf("low", "medium", "high")
        if (!validCreativityLevels.contains(request.creativityLevel)) {
            throw IllegalArgumentException("Invalid creativity level. Must be one of: low, medium, high")
        }
        
        // Validate genre type
        if (request.genreType.isBlank()) {
            throw IllegalArgumentException("Genre type cannot be blank")
        }
        
        // Validate project ID
        if (request.projectId.isBlank()) {
            throw IllegalArgumentException("Project ID cannot be blank")
        }
    }
    
    // Get config with defaults if not exists
    fun getConfigWithDefaults(projectId: String): ConfigResponse {
        val existing = getConfigByProjectId(projectId)
        if (existing != null) return existing
        val defaults = ConfigRequest(
            projectId = projectId,
            genreType = "小说",
            writingDirection = "",
            maxLength = 500,
            creativityLevel = "medium"
        )
        return ConfigResponse(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            genreType = defaults.genreType,
            writingDirection = defaults.writingDirection,
            maxLength = defaults.maxLength,
            creativityLevel = defaults.creativityLevel,
            timestamp = DatabaseService.nowString()
        )
    }
}
