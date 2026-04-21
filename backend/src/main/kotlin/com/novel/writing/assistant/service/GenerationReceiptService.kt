package com.novel.writing.assistant.service

import com.novel.writing.assistant.model.GenerationReceiptRequest
import com.novel.writing.assistant.model.GenerationReceiptResponse
import com.novel.writing.assistant.model.GenerationRequest
import com.novel.writing.assistant.model.GenerationResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class PendingGenerationRecord(
    val generationId: String,
    val projectId: String,
    val sessionId: String,
    val generationType: String,
    val requestPayload: String,
    val outputContent: String,
    val generationDate: String,
    val duration: Long,
    val createdAt: String
)

object GenerationReceiptService {
    private val json = Json { ignoreUnknownKeys = true }

    fun stagePending(
        request: GenerationRequest,
        response: GenerationResponse
    ) {
        val record = PendingGenerationRecord(
            generationId = response.id,
            projectId = response.projectId,
            sessionId = response.sessionId,
            generationType = response.generationType,
            requestPayload = json.encodeToString(request),
            outputContent = response.outputContent,
            generationDate = response.generationDate,
            duration = response.duration,
            createdAt = DatabaseService.nowString()
        )

        DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE pending_generations
                SET project_id = ?, session_id = ?, generation_type = ?, request_payload = ?,
                    output_content = ?, generation_date = ?, duration = ?, created_at = ?
                WHERE generation_id = ?
                """.trimIndent()
            ).use { update ->
                update.setString(1, record.projectId)
                update.setString(2, record.sessionId)
                update.setString(3, record.generationType)
                update.setString(4, record.requestPayload)
                update.setString(5, record.outputContent)
                update.setString(6, record.generationDate)
                update.setLong(7, record.duration)
                update.setString(8, record.createdAt)
                update.setString(9, record.generationId)
                if (update.executeUpdate() > 0) {
                    return@withConnection
                }
            }

            connection.prepareStatement(
                """
                INSERT INTO pending_generations (
                    generation_id,
                    project_id,
                    session_id,
                    generation_type,
                    request_payload,
                    output_content,
                    generation_date,
                    duration,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { insert ->
                insert.setString(1, record.generationId)
                insert.setString(2, record.projectId)
                insert.setString(3, record.sessionId)
                insert.setString(4, record.generationType)
                insert.setString(5, record.requestPayload)
                insert.setString(6, record.outputContent)
                insert.setString(7, record.generationDate)
                insert.setLong(8, record.duration)
                insert.setString(9, record.createdAt)
                insert.executeUpdate()
            }
        }
    }

    fun acknowledge(
        generationId: String,
        request: GenerationReceiptRequest
    ): GenerationReceiptResponse? {
        getReceipt(generationId)?.let { return it }

        val pending = loadPending(generationId) ?: return null
        validateReceiptRequest(generationId, request, pending)

        val decodedRequest = json.decodeFromString<GenerationRequest>(pending.requestPayload)
        GenerationService.persistGenerationData(
            request = decodedRequest,
            sessionId = pending.sessionId,
            generationType = pending.generationType,
            outputContent = pending.outputContent,
            duration = pending.duration
        )

        val receipt = upsertReceipt(
            generationId = generationId,
            projectId = pending.projectId,
            sessionId = pending.sessionId,
            contentLength = request.contentLength,
            storageRef = request.storageRef
        )

        deletePending(generationId)
        return receipt
    }

    private fun validateReceiptRequest(
        generationId: String,
        request: GenerationReceiptRequest,
        pending: PendingGenerationRecord
    ) {
        require(request.projectId == pending.projectId) {
            "Generation $generationId projectId mismatch"
        }
        require(request.sessionId == pending.sessionId) {
            "Generation $generationId sessionId mismatch"
        }
        require(request.contentLength == pending.outputContent.length) {
            "Generation $generationId contentLength mismatch"
        }
    }

    private fun loadPending(generationId: String): PendingGenerationRecord? {
        return DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT generation_id, project_id, session_id, generation_type, request_payload,
                       output_content, generation_date, duration, created_at
                FROM pending_generations
                WHERE generation_id = ?
                LIMIT 1
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, generationId)
                statement.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return@withConnection null
                    }
                    PendingGenerationRecord(
                        generationId = rs.getString("generation_id"),
                        projectId = rs.getString("project_id"),
                        sessionId = rs.getString("session_id"),
                        generationType = rs.getString("generation_type"),
                        requestPayload = rs.getString("request_payload"),
                        outputContent = rs.getString("output_content"),
                        generationDate = rs.getString("generation_date"),
                        duration = rs.getLong("duration"),
                        createdAt = rs.getString("created_at")
                    )
                }
            }
        }
    }

    private fun deletePending(generationId: String) {
        DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                "DELETE FROM pending_generations WHERE generation_id = ?"
            ).use { statement ->
                statement.setString(1, generationId)
                statement.executeUpdate()
            }
        }
    }

    private fun getReceipt(generationId: String): GenerationReceiptResponse? {
        return DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT generation_id, project_id, session_id, content_length, storage_ref, client_received_at
                FROM generation_receipts
                WHERE generation_id = ?
                LIMIT 1
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, generationId)
                statement.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return@withConnection null
                    }
                    GenerationReceiptResponse(
                        generationId = rs.getString("generation_id"),
                        projectId = rs.getString("project_id"),
                        sessionId = rs.getString("session_id"),
                        acknowledged = true,
                        clientReceivedAt = rs.getString("client_received_at"),
                        contentLength = rs.getInt("content_length"),
                        storageRef = rs.getString("storage_ref")
                    )
                }
            }
        }
    }

    private fun upsertReceipt(
        generationId: String,
        projectId: String,
        sessionId: String,
        contentLength: Int,
        storageRef: String?
    ): GenerationReceiptResponse {
        val clientReceivedAt = DatabaseService.nowString()
        DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE generation_receipts
                SET project_id = ?, session_id = ?, content_length = ?, storage_ref = ?, client_received_at = ?
                WHERE generation_id = ?
                """.trimIndent()
            ).use { update ->
                update.setString(1, projectId)
                update.setString(2, sessionId)
                update.setInt(3, contentLength)
                update.setString(4, storageRef)
                update.setString(5, clientReceivedAt)
                update.setString(6, generationId)
                if (update.executeUpdate() > 0) {
                    return@withConnection
                }
            }

            connection.prepareStatement(
                """
                INSERT INTO generation_receipts (
                    generation_id,
                    project_id,
                    session_id,
                    content_length,
                    storage_ref,
                    client_received_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { insert ->
                insert.setString(1, generationId)
                insert.setString(2, projectId)
                insert.setString(3, sessionId)
                insert.setInt(4, contentLength)
                insert.setString(5, storageRef)
                insert.setString(6, clientReceivedAt)
                insert.executeUpdate()
            }
        }

        return GenerationReceiptResponse(
            generationId = generationId,
            projectId = projectId,
            sessionId = sessionId,
            acknowledged = true,
            clientReceivedAt = clientReceivedAt,
            contentLength = contentLength,
            storageRef = storageRef
        )
    }
}
