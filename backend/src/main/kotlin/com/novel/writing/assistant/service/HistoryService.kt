package com.novel.writing.assistant.service

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class GenerationHistory(
    val id: String,
    val projectId: String,
    val generationType: String,
    val inputParams: String,
    val outputContent: String,
    val generationDate: String,
    val duration: Long
)

object HistoryService {
    fun getAllHistory(): List<GenerationHistory> {
        return DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id, project_id, generation_type, input_params, output_content, generation_date, duration
                FROM generation_history
                """.trimIndent()
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    val results = mutableListOf<GenerationHistory>()
                    while (rs.next()) {
                        results.add(
                            GenerationHistory(
                                id = rs.getString("id"),
                                projectId = rs.getString("project_id"),
                                generationType = rs.getString("generation_type"),
                                inputParams = rs.getString("input_params"),
                                outputContent = rs.getString("output_content"),
                                generationDate = rs.getString("generation_date"),
                                duration = rs.getLong("duration")
                            )
                        )
                    }
                    results
                }
            }
        }
    }
    
    fun getHistoryByProjectId(projectId: String): List<GenerationHistory> {
        return DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id, project_id, generation_type, input_params, output_content, generation_date, duration
                FROM generation_history
                WHERE project_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, projectId)
                statement.executeQuery().use { rs ->
                    val results = mutableListOf<GenerationHistory>()
                    while (rs.next()) {
                        results.add(
                            GenerationHistory(
                                id = rs.getString("id"),
                                projectId = rs.getString("project_id"),
                                generationType = rs.getString("generation_type"),
                                inputParams = rs.getString("input_params"),
                                outputContent = rs.getString("output_content"),
                                generationDate = rs.getString("generation_date"),
                                duration = rs.getLong("duration")
                            )
                        )
                    }
                    results
                }
            }
        }
    }
    
    fun getHistoryById(id: String): GenerationHistory? {
        return DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id, project_id, generation_type, input_params, output_content, generation_date, duration
                FROM generation_history
                WHERE id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { rs ->
                    if (!rs.next()) return@withConnection null
                    GenerationHistory(
                        id = rs.getString("id"),
                        projectId = rs.getString("project_id"),
                        generationType = rs.getString("generation_type"),
                        inputParams = rs.getString("input_params"),
                        outputContent = rs.getString("output_content"),
                        generationDate = rs.getString("generation_date"),
                        duration = rs.getLong("duration")
                    )
                }
            }
        }
    }
    
    fun saveHistory(
        projectId: String,
        generationType: String,
        inputParams: String,
        outputContent: String,
        duration: Long
    ): GenerationHistory {
        val historyItem = GenerationHistory(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            generationType = generationType,
            inputParams = inputParams,
            outputContent = outputContent,
            generationDate = DatabaseService.nowString(),
            duration = duration
        )
        DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO generation_history (id, project_id, generation_type, input_params, output_content, generation_date, duration)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, historyItem.id)
                statement.setString(2, historyItem.projectId)
                statement.setString(3, historyItem.generationType)
                statement.setString(4, historyItem.inputParams)
                statement.setString(5, historyItem.outputContent)
                statement.setString(6, historyItem.generationDate)
                statement.setLong(7, historyItem.duration)
                statement.executeUpdate()
            }
        }
        return historyItem
    }
    
    fun deleteHistoryById(id: String): Boolean {
        return DatabaseService.withConnection { connection ->
            connection.prepareStatement("DELETE FROM generation_history WHERE id = ?").use { statement ->
                statement.setString(1, id)
                statement.executeUpdate() > 0
            }
        }
    }
    
    fun deleteHistoryByProjectId(projectId: String): Boolean {
        return DatabaseService.withConnection { connection ->
            connection.prepareStatement("DELETE FROM generation_history WHERE project_id = ?").use { statement ->
                statement.setString(1, projectId)
                statement.executeUpdate() > 0
            }
        }
    }
}
