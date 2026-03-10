package com.novel.writing.assistant.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class BatchRequest(
    val requests: List<SingleRequest>
)

@Serializable
data class SingleRequest(
    val id: String,
    val method: String,
    val url: String,
    val body: JsonElement? = null
)

@Serializable
data class BatchResponse(
    val responses: List<SingleResponse>
)

@Serializable
data class SingleResponse(
    val id: String,
    val status: Int,
    val body: JsonElement? = null,
    val error: String? = null
)

object BatchRequestService {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun processBatchRequest(batchRequest: BatchRequest): BatchResponse {
        val responses = batchRequest.requests.map { request ->
            processSingleRequest(request)
        }
        
        return BatchResponse(responses = responses)
    }
    
    private suspend fun processSingleRequest(request: SingleRequest): SingleResponse {
        try {
            // Parse URL to determine which service to call
            val path = request.url.removePrefix("/api/")
            
            when {
                path == "projects" && request.method == "GET" -> {
                    val projects = ProjectService.getAllProjects()
                    return SingleResponse(
                        id = request.id,
                        status = 200,
                        body = json.encodeToJsonElement(projects)
                    )
                }
                path.startsWith("projects/") && request.method == "GET" -> {
                    val id = path.removePrefix("projects/")
                    val project = ProjectService.getProjectById(id)
                    return if (project != null) {
                        SingleResponse(
                            id = request.id,
                            status = 200,
                            body = json.encodeToJsonElement(project)
                        )
                    } else {
                        SingleResponse(
                            id = request.id,
                            status = 404,
                            error = "Project not found"
                        )
                    }
                }
                path == "config" && request.method == "GET" -> {
                    val projectId = request.body
                        ?.jsonObject
                        ?.get("projectId")
                        ?.jsonPrimitive
                        ?.content
                    if (projectId != null) {
                        val config = ConfigService.getConfigByProjectId(projectId)
                        return if (config != null) {
                            SingleResponse(
                                id = request.id,
                                status = 200,
                                body = json.encodeToJsonElement(config)
                            )
                        } else {
                            SingleResponse(
                                id = request.id,
                                status = 404,
                                error = "Config not found"
                            )
                        }
                    } else {
                        return SingleResponse(
                            id = request.id,
                            status = 400,
                            error = "Missing projectId"
                        )
                    }
                }
                else -> {
                    return SingleResponse(
                        id = request.id,
                        status = 404,
                        error = "Endpoint not found"
                    )
                }
            }
        } catch (e: Exception) {
            return SingleResponse(
                id = request.id,
                status = 500,
                error = e.message
            )
        }
    }
}
