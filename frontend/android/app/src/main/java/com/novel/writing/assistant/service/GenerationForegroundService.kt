package com.novel.writing.assistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.novel.writing.assistant.MainActivity
import com.novel.writing.assistant.network.ApiService
import com.novel.writing.assistant.network.GenerationResponse
import com.novel.writing.assistant.utils.SessionIdStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed class GenerationTaskState {
    data object Idle : GenerationTaskState()
    data class Running(val requestId: String, val statusMessage: String) : GenerationTaskState()
    data class Success(val requestId: String, val response: GenerationResponse) : GenerationTaskState()
    data class Failed(val requestId: String, val message: String) : GenerationTaskState()
}

object GenerationTaskStore {
    private val stateFlow = MutableStateFlow<GenerationTaskState>(GenerationTaskState.Idle)
    val state: StateFlow<GenerationTaskState> = stateFlow

    fun update(state: GenerationTaskState) {
        stateFlow.value = state
    }

    fun reset() {
        stateFlow.value = GenerationTaskState.Idle
    }
}

class GenerationForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val apiService = ApiService()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                GenerationTaskStore.reset()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
                val projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
                val isContinueWriting = intent.getBooleanExtra(EXTRA_IS_CONTINUE_WRITING, false)
                if (requestId.isBlank() || projectId.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, createNotification("正在准备生成任务..."))
                serviceScope.launch {
                    runGeneration(
                        requestId = requestId,
                        projectId = projectId,
                        isContinueWriting = isContinueWriting,
                        referenceDoc = intent.getStringExtra(EXTRA_REFERENCE_DOC),
                        genreType = intent.getStringExtra(EXTRA_GENRE_TYPE),
                        writingDirection = intent.getStringExtra(EXTRA_WRITING_DIRECTION),
                        sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                    )
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runGeneration(
        requestId: String,
        projectId: String,
        isContinueWriting: Boolean,
        referenceDoc: String?,
        genreType: String?,
        writingDirection: String?,
        sessionId: String?
    ) {
        try {
            GenerationTaskStore.update(GenerationTaskState.Running(requestId, "正在连接流式服务..."))
            updateNotification("正在连接流式服务...")
            val result = apiService.generateContentStream(
                projectId = projectId,
                isContinueWriting = isContinueWriting,
                referenceFileId = null,
                referenceDoc = referenceDoc,
                genreType = genreType,
                writingDirection = writingDirection,
                sessionId = sessionId
            ) { event ->
                when (event.event) {
                    "error" -> {
                        val message = "流式生成异常：${event.errorMessage ?: "未知错误"}"
                        GenerationTaskStore.update(GenerationTaskState.Running(requestId, message))
                        updateNotification(message)
                    }

                    "done" -> {
                        GenerationTaskStore.update(GenerationTaskState.Running(requestId, "生成完成，正在整理结果..."))
                        updateNotification("生成完成，正在整理结果...")
                    }

                    else -> {
                        val status = event.partialOutput?.takeIf { it.isNotBlank() }?.let {
                            "正在生成中（已接收 ${it.length} 字）..."
                        } ?: "正在生成中..."
                        GenerationTaskStore.update(GenerationTaskState.Running(requestId, status))
                        updateNotification(status)
                    }
                }
            }
            SessionIdStore(this).saveSessionId(projectId, result.sessionId)
            GenerationTaskStore.update(GenerationTaskState.Success(requestId, result))
            updateNotification("任务已完成")
        } catch (e: Exception) {
            val message = "生成失败：${e.message}"
            GenerationTaskStore.update(GenerationTaskState.Failed(requestId, message))
            updateNotification(message)
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotification(content: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle("小说写作助手")
        .setContentText(content)
        .setContentIntent(createContentIntent())
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }

    companion object {
        private const val CHANNEL_ID = "generation_foreground_channel"
        private const val CHANNEL_NAME = "生成任务"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_START = "com.novel.writing.assistant.action.START_GENERATION"
        private const val ACTION_CANCEL = "com.novel.writing.assistant.action.CANCEL_GENERATION"

        private const val EXTRA_REQUEST_ID = "extra_request_id"
        private const val EXTRA_PROJECT_ID = "extra_project_id"
        private const val EXTRA_IS_CONTINUE_WRITING = "extra_is_continue_writing"
        private const val EXTRA_REFERENCE_DOC = "extra_reference_doc"
        private const val EXTRA_GENRE_TYPE = "extra_genre_type"
        private const val EXTRA_WRITING_DIRECTION = "extra_writing_direction"
        private const val EXTRA_SESSION_ID = "extra_session_id"

        fun start(
            context: Context,
            projectId: String,
            isContinueWriting: Boolean,
            referenceDoc: String?,
            genreType: String?,
            writingDirection: String?,
            sessionId: String?
        ): String {
            createChannel(context)
            val requestId = UUID.randomUUID().toString()
            val intent = Intent(context, GenerationForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_REQUEST_ID, requestId)
                putExtra(EXTRA_PROJECT_ID, projectId)
                putExtra(EXTRA_IS_CONTINUE_WRITING, isContinueWriting)
                putExtra(EXTRA_REFERENCE_DOC, referenceDoc)
                putExtra(EXTRA_GENRE_TYPE, genreType)
                putExtra(EXTRA_WRITING_DIRECTION, writingDirection)
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            return requestId
        }

        fun cancel(context: Context) {
            val intent = Intent(context, GenerationForegroundService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) {
                return
            }
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
    }
}
