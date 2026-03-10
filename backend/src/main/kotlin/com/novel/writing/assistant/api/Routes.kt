package com.novel.writing.assistant.api

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.*
import com.novel.writing.assistant.model.ContextInfoCreate
import com.novel.writing.assistant.model.ConfigRequest
import com.novel.writing.assistant.model.GenerationRequest
import com.novel.writing.assistant.model.ProjectCreateRequest
import com.novel.writing.assistant.model.ProjectUpdateRequest
import com.novel.writing.assistant.model.SessionInfo
import com.novel.writing.assistant.service.ContextService
import com.novel.writing.assistant.service.ConfigService
import com.novel.writing.assistant.service.DocumentService
import com.novel.writing.assistant.service.GenerationService
import com.novel.writing.assistant.service.HistoryService
import com.novel.writing.assistant.service.MissingSessionIdException
import com.novel.writing.assistant.service.NoContextException
import com.novel.writing.assistant.service.ProjectService

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Novel Writing Assistant Backend API", ContentType.Text.Plain)
        }
        get("/health") {
            call.respondText("OK", ContentType.Text.Plain)
        }
        
        // API routes
        route("/api") {
            route("/v1") {
                // Project routes
                route("/projects") {
                    get {
                        call.respond(ProjectService.getAllProjects())
                    }
                    post {
                        val project = call.receive<ProjectCreateRequest>()
                        call.respond(HttpStatusCode.Created, ProjectService.createProject(project))
                    }
                    get("/{id}") {
                        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing project ID")
                        val project = ProjectService.getProjectById(id)
                        if (project != null) {
                            call.respond(project)
                        } else {
                            call.respond(HttpStatusCode.NotFound, "Project not found")
                        }
                    }
                    put("/{id}") {
                        val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing project ID")
                        val project = call.receive<ProjectUpdateRequest>()
                        val updatedProject = ProjectService.updateProject(id, project)
                        if (updatedProject != null) {
                            call.respond(updatedProject)
                        } else {
                            call.respond(HttpStatusCode.NotFound, "Project not found")
                        }
                    }
                    delete("/{id}") {
                        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing project ID")
                        if (ProjectService.deleteProject(id)) {
                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            call.respond(HttpStatusCode.NotFound, "Project not found")
                        }
                    }
                }

                // Document routes
                route("/documents") {
                    post {
                        val multipart = call.receiveMultipart()
                        val document = DocumentService.uploadDocument(multipart)
                        call.respond(HttpStatusCode.Created, document)
                    }
                    get("/{id}") {
                        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing document ID")
                        val document = DocumentService.getDocumentById(id)
                        if (document != null) {
                            call.respond(document)
                        } else {
                            call.respond(HttpStatusCode.NotFound, "Document not found")
                        }
                    }
                    delete("/{id}") {
                        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing document ID")
                        if (DocumentService.deleteDocument(id)) {
                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            call.respond(HttpStatusCode.NotFound, "Document not found")
                        }
                    }
                }

                // Generation routes
                route("/generation") {
                    post {
                        try {
                            val request = call.receive<GenerationRequest>()
                            val response = GenerationService.generateContent(request)
                            call.respond(response)
                        } catch (e: MissingSessionIdException) {
                            call.respond(HttpStatusCode.BadRequest, e.message ?: "Missing sessionId")
                        } catch (e: NoContextException) {
                            call.respond(HttpStatusCode.Conflict, e.message ?: "No context")
                        }
                    }
                }

                // History routes
                route("/history") {
                    get {
                        val projectId = call.request.queryParameters["projectId"]
                        call.respond(
                            if (projectId != null) HistoryService.getHistoryByProjectId(projectId) else HistoryService.getAllHistory()
                        )
                    }
                }

                route("/context") {
                    post {
                        val context = call.receive<ContextInfoCreate>()
                        val saved = ContextService.saveContext(
                            projectId = context.projectId,
                            sessionId = context.sessionId,
                            contextType = context.contextType,
                            contextContent = context.contextContent
                        )
                        call.respond(HttpStatusCode.Created, saved)
                    }
                    get {
                        val projectId = call.request.queryParameters["projectId"]
                            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing project ID")
                        val sessionId = call.request.queryParameters["sessionId"]
                        call.respond(ContextService.getContextByProjectId(projectId, sessionId))
                    }
                }

                route("/sessions") {
                    get("/latest") {
                        val projectId = call.request.queryParameters["projectId"]
                            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing project ID")
                        val latestSessionId = ContextService.getLatestSessionId(projectId)
                            ?: return@get call.respond(HttpStatusCode.NotFound, "Session not found")
                        call.respond(SessionInfo(projectId = projectId, sessionId = latestSessionId))
                    }
                }

                // Config routes
                route("/config") {
                    post {
                        val config = call.receive<ConfigRequest>()
                        val response = ConfigService.saveConfig(config)
                        call.respond(response)
                    }
                    get("/{projectId}") {
                        val projectId = call.parameters["projectId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing project ID")
                        val config = ConfigService.getConfigByProjectId(projectId)
                        if (config != null) {
                            call.respond(config)
                        } else {
                            call.respond(HttpStatusCode.NotFound, "Config not found")
                        }
                    }
                }
            }
        }
    }
}
