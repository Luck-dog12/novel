package com.novel.writing.assistant.service

import com.novel.writing.assistant.model.ContextInfo
import java.util.*

object ContextService {
    fun saveContext(projectId: String, sessionId: String, contextType: String, contextContent: String): ContextInfo {
        val context = ContextInfo(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            sessionId = sessionId,
            contextType = contextType,
            contextContent = contextContent,
            lastUpdated = DatabaseService.nowString()
        )
        DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO context_entries (id, project_id, session_id, context_type, context_content, last_updated)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, context.id)
                statement.setString(2, context.projectId)
                statement.setString(3, context.sessionId)
                statement.setString(4, context.contextType)
                statement.setString(5, context.contextContent)
                statement.setString(6, context.lastUpdated)
                statement.executeUpdate()
            }
        }
        return context
    }

    fun getContextByProjectId(projectId: String, sessionId: String? = null): List<ContextInfo> {
        return DatabaseService.withConnection { connection ->
            val sql = if (sessionId.isNullOrBlank()) {
                """
                SELECT id, project_id, session_id, context_type, context_content, last_updated
                FROM context_entries
                WHERE project_id = ?
                ORDER BY last_updated DESC
                """.trimIndent()
            } else {
                """
                SELECT id, project_id, session_id, context_type, context_content, last_updated
                FROM context_entries
                WHERE project_id = ? AND session_id = ?
                ORDER BY last_updated DESC
                """.trimIndent()
            }

            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, projectId)
                if (!sessionId.isNullOrBlank()) {
                    statement.setString(2, sessionId)
                }
                statement.executeQuery().use { rs ->
                    val results = mutableListOf<ContextInfo>()
                    while (rs.next()) {
                        results.add(
                            ContextInfo(
                                id = rs.getString("id"),
                                projectId = rs.getString("project_id"),
                                sessionId = rs.getString("session_id"),
                                contextType = rs.getString("context_type"),
                                contextContent = rs.getString("context_content"),
                                lastUpdated = rs.getString("last_updated")
                            )
                        )
                    }
                    results
                }
            }
        }
    }

    fun getLatestContextContent(projectId: String, sessionId: String, contextType: String? = null): String? {
        return DatabaseService.withConnection { connection ->
            val sql = if (contextType.isNullOrBlank()) {
                """
                SELECT context_content
                FROM context_entries
                WHERE project_id = ? AND session_id = ?
                ORDER BY last_updated DESC
                LIMIT 1
                """.trimIndent()
            } else {
                """
                SELECT context_content
                FROM context_entries
                WHERE project_id = ? AND session_id = ? AND context_type = ?
                ORDER BY last_updated DESC
                LIMIT 1
                """.trimIndent()
            }

            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, projectId)
                statement.setString(2, sessionId)
                if (!contextType.isNullOrBlank()) {
                    statement.setString(3, contextType)
                }
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.getString("context_content") else null
                }
            }
        }
    }

    fun getNextChapterNumber(projectId: String, sessionId: String): Int {
        return DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT COUNT(*) AS cnt
                FROM context_entries
                WHERE project_id = ? AND session_id = ? AND context_type = 'chapter'
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, projectId)
                statement.setString(2, sessionId)
                statement.executeQuery().use { rs ->
                    val existing = if (rs.next()) rs.getInt("cnt") else 0
                    existing + 1
                }
            }
        }
    }

    fun getLatestSessionId(projectId: String): String? {
        return DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT session_id
                FROM context_entries
                WHERE project_id = ?
                ORDER BY last_updated DESC
                LIMIT 1
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, projectId)
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.getString("session_id") else null
                }
            }
        }
    }
}
