package com.novel.writing.assistant.model

import kotlinx.serialization.Serializable

@Serializable
data class ProjectCreateRequest(
    val title: String,
    val description: String,
    val genreType: String
)

@Serializable
data class ProjectUpdateRequest(
    val title: String? = null,
    val description: String? = null,
    val genreType: String? = null,
    val status: String? = null
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
data class ContextInfo(
    val id: String,
    val projectId: String,
    val sessionId: String,
    val contextType: String,
    val contextContent: String,
    val lastUpdated: String
)

@Serializable
data class ContextInfoCreate(
    val projectId: String,
    val sessionId: String,
    val contextType: String,
    val contextContent: String
)

@Serializable
data class SessionInfo(
    val projectId: String,
    val sessionId: String
)
