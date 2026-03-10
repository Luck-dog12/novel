package com.novel.writing.assistant.network

import com.novel.writing.assistant.utils.CacheManager
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

class ApiService {
    suspend fun generateContent(projectId: String, isContinueWriting: Boolean, referenceFileId: String?, 
                              referenceDoc: String?, genreType: String?, writingDirection: String?, sessionId: String? = null): GenerationResponse {
        return withContext(Dispatchers.IO) {
            val response = ApiClient.client.post("${ApiClient.baseUrl}/v1/generation") {
                contentType(ContentType.Application.Json)
                setBody(
                    GenerationRequest(
                        projectId = projectId,
                        isContinueWriting = isContinueWriting,
                        sessionId = sessionId,
                        referenceFileId = referenceFileId,
                        referenceDoc = referenceDoc,
                        genreType = genreType,
                        writingDirection = writingDirection
                    )
                )
            }
            response.body<GenerationResponse>()
        }
    }
    
    suspend fun getProjects(): List<Project> {
        val cacheKey = "projects"
        
        // 尝试从缓存获取
        CacheManager.instance.get<List<Project>>(cacheKey)?.let {
            return it
        }
        
        return withContext(Dispatchers.IO) {
            val response = ApiClient.client.get("${ApiClient.baseUrl}/v1/projects")
            val projects = response.body<List<Project>>()
            
            // 缓存结果
            CacheManager.instance.put(cacheKey, projects)
            
            projects
        }
    }
    
    suspend fun createProject(title: String, description: String, genreType: String): Project {
        return withContext(Dispatchers.IO) {
            val response = ApiClient.client.post("${ApiClient.baseUrl}/v1/projects") {
                contentType(ContentType.Application.Json)
                setBody(ProjectCreateRequest(title = title, description = description, genreType = genreType))
            }
            val project = response.body<Project>()
            
            // 清除项目列表缓存
            CacheManager.instance.remove("projects")
            
            project
        }
    }
    
    suspend fun uploadDocument(projectId: String, fileBytes: ByteArray, fileName: String): Document {
        return withContext(Dispatchers.IO) {
            val response = ApiClient.client.post("${ApiClient.baseUrl}/v1/documents") {
                contentType(ContentType.MultiPart.FormData)
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("projectId", projectId)
                            append(
                                "file",
                                fileBytes,
                                Headers.build {
                                    append(HttpHeaders.ContentDisposition, "filename=$fileName")
                                    append(HttpHeaders.ContentType, "text/plain")
                                }
                            )
                        }
                    )
                )
            }
            response.body()
        }
    }
    
    // Overload for File parameter
    suspend fun uploadDocument(file: java.io.File): String {
        return withContext(Dispatchers.IO) {
            // In production, we would read the file and upload it
            // For now, return a mock document ID
            "mock-document-id-${System.currentTimeMillis()}"
        }
    }
    
    suspend fun saveConfig(config: ConfigRequest): ConfigResponse {
        return withContext(Dispatchers.IO) {
            val response = ApiClient.client.post("${ApiClient.baseUrl}/v1/config") {
                contentType(ContentType.Application.Json)
                setBody(config)
            }
            response.body()
        }
    }
    
    suspend fun getConfig(projectId: String): ConfigResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val response = ApiClient.client.get("${ApiClient.baseUrl}/v1/config/$projectId")
                response.body()
            } catch (e: Exception) {
                null
            }
        }
    }
    
    suspend fun getHistoryByProjectId(projectId: String): List<GenerationHistory> {
        return withContext(Dispatchers.IO) {
            val response = ApiClient.client.get("${ApiClient.baseUrl}/v1/history") {
                parameter("projectId", projectId)
            }
            response.body()
        }
    }

    suspend fun getContextByProjectId(projectId: String, sessionId: String? = null): List<ContextInfo> {
        return withContext(Dispatchers.IO) {
            val response = ApiClient.client.get("${ApiClient.baseUrl}/v1/context") {
                parameter("projectId", projectId)
                if (!sessionId.isNullOrBlank()) {
                    parameter("sessionId", sessionId)
                }
            }
            response.body()
        }
    }

    suspend fun getLatestSessionId(projectId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val response = ApiClient.client.get("${ApiClient.baseUrl}/v1/sessions/latest") {
                    parameter("projectId", projectId)
                }
                response.body<SessionInfo>().sessionId
            } catch (_: Exception) {
                null
            }
        }
    }
}

// Data classes for API responses
@Serializable
data class ProjectCreateRequest(
    val title: String,
    val description: String,
    val genreType: String
)

@Serializable
data class GenerationRequest(
    val projectId: String,
    val isContinueWriting: Boolean,
    val sessionId: String? = null,
    val referenceFileId: String? = null,
    val referenceDoc: String? = null,
    val genreType: String? = null,
    val writingDirection: String? = null
)

@Serializable
data class GenerationResponse(
    val id: String,
    val projectId: String,
    val sessionId: String,
    val generationType: String,
    val outputContent: String,
    val generationDate: String,
    val duration: Long
)

@Serializable
data class ConfigRequest(
    val projectId: String,
    val genreType: String,
    val writingDirection: String,
    val maxLength: Int,
    val creativityLevel: String
)

@Serializable
data class ConfigResponse(
    val id: String,
    val projectId: String,
    val genreType: String,
    val writingDirection: String,
    val maxLength: Int,
    val creativityLevel: String,
    val timestamp: String
)

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

@Serializable
data class ContextInfo(
    val id: String,
    val projectId: String,
    val sessionId: String,
    val contextType: String,
    val contextContent: String,
    val lastUpdated: String
)

@Serializable
data class SessionInfo(
    val projectId: String,
    val sessionId: String
)

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
