package com.novel.writing.assistant.service

import io.ktor.http.content.*
import kotlinx.serialization.Serializable
import java.io.File
import java.util.*

@Serializable
data class Document(
    val id: String,
    val projectId: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val uploadDate: String,
    val filePath: String,
    val contentHash: String
)

object DocumentService {
    private val uploadDir = File(System.getenv("UPLOAD_DIR")?.trim().orEmpty().ifBlank { "uploads" })
    
    init {
        uploadDir.mkdirs()
    }
    
    suspend fun uploadDocument(multipart: MultiPartData): Document {
        var projectId: String? = null
        var fileBytes: ByteArray? = null
        var fileName: String? = null
        
        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    if (part.name == "projectId") {
                        projectId = part.value
                    }
                }
                is PartData.FileItem -> {
                    fileName = part.originalFileName
                    fileBytes = part.streamProvider().readBytes()
                }
                else -> {}
            }
            part.dispose()
        }
        
        if (projectId == null || fileBytes == null || fileName == null) {
            throw IllegalArgumentException("Missing required fields")
        }
        
        val id = UUID.randomUUID().toString()
        val fileType = fileName!!.split(".").lastOrNull() ?: "txt"
        val fileSize = fileBytes!!.size.toLong()
        val uploadDate = Date().toString()
        val contentHash = calculateHash(fileBytes!!)
        val filePath = "${uploadDir.absolutePath}/${id}.$fileType"
        
        // Save file
        File(filePath).writeBytes(fileBytes!!)
        
        val document = Document(
            id = id,
            projectId = projectId!!,
            fileName = fileName!!,
            fileType = fileType,
            fileSize = fileSize,
            uploadDate = uploadDate,
            filePath = filePath,
            contentHash = contentHash
        )

        DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO documents (id, project_id, file_name, file_type, file_size, upload_date, file_path, content_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, document.id)
                statement.setString(2, document.projectId)
                statement.setString(3, document.fileName)
                statement.setString(4, document.fileType)
                statement.setLong(5, document.fileSize)
                statement.setString(6, document.uploadDate)
                statement.setString(7, document.filePath)
                statement.setString(8, document.contentHash)
                statement.executeUpdate()
            }
        }
        return document
    }
    
    fun getDocumentById(id: String): Document? {
        return DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id, project_id, file_name, file_type, file_size, upload_date, file_path, content_hash
                FROM documents
                WHERE id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { rs ->
                    if (!rs.next()) return@withConnection null
                    Document(
                        id = rs.getString("id"),
                        projectId = rs.getString("project_id"),
                        fileName = rs.getString("file_name"),
                        fileType = rs.getString("file_type"),
                        fileSize = rs.getLong("file_size"),
                        uploadDate = rs.getString("upload_date"),
                        filePath = rs.getString("file_path"),
                        contentHash = rs.getString("content_hash")
                    )
                }
            }
        }
    }
    
    fun getDocumentsByProjectId(projectId: String): List<Document> {
        return DatabaseService.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id, project_id, file_name, file_type, file_size, upload_date, file_path, content_hash
                FROM documents
                WHERE project_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, projectId)
                statement.executeQuery().use { rs ->
                    val results = mutableListOf<Document>()
                    while (rs.next()) {
                        results.add(
                            Document(
                                id = rs.getString("id"),
                                projectId = rs.getString("project_id"),
                                fileName = rs.getString("file_name"),
                                fileType = rs.getString("file_type"),
                                fileSize = rs.getLong("file_size"),
                                uploadDate = rs.getString("upload_date"),
                                filePath = rs.getString("file_path"),
                                contentHash = rs.getString("content_hash")
                            )
                        )
                    }
                    results
                }
            }
        }
    }
    
    fun deleteDocument(id: String): Boolean {
        val document = getDocumentById(id) ?: return false
        File(document.filePath).delete()
        return DatabaseService.withConnection { connection ->
            connection.prepareStatement("DELETE FROM documents WHERE id = ?").use { statement ->
                statement.setString(1, id)
                statement.executeUpdate() > 0
            }
        }
    }
    
    private fun calculateHash(bytes: ByteArray): String {
        return UUID.nameUUIDFromBytes(bytes).toString()
    }
}
