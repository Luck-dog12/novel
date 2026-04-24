package com.novel.writing.assistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.novel.writing.assistant.MainActivity
import com.novel.writing.assistant.network.ApiService
import com.novel.writing.assistant.network.GenerationReceiptRequest
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
                if (requestId.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, createNotification("正在准备生成任务..."))
                serviceScope.launch {
                    try {
                        val request = GenerationRequestStore.load(this@GenerationForegroundService, requestId)
                        if (request == null) {
                            val message = "未找到生成请求，请重新提交"
                            GenerationTaskStore.update(GenerationTaskState.Failed(requestId, message))
                            updateNotification(message)
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                            return@launch
                        }
                        runGeneration(requestId = requestId, request = request)
                    } finally {
                        GenerationRequestStore.delete(this@GenerationForegroundService, requestId)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runGeneration(
        requestId: String,
        request: PendingGenerationRequest
    ) {
        try {
            flushPendingReceipts()
            GenerationTaskStore.update(GenerationTaskState.Running(requestId, "正在连接流式服务..."))
            updateNotification("正在连接流式服务...")
            val result = apiService.generateContentStream(
                projectId = request.projectId,
                isContinueWriting = request.isContinueWriting,
                referenceFileId = null,
                referenceDoc = request.referenceDoc,
                genreType = request.genreType,
                writingDirection = request.writingDirection,
                sessionId = request.sessionId
            ) { event ->
                when (event.event) {
                    "error" -> {
                        val message = "流式生成异常：${event.errorMessage ?: "未知错误"}"
                        GenerationTaskStore.update(GenerationTaskState.Running(requestId, message))
                        updateNotification(message)
                    }

                    "done", "result_meta" -> {
                        val message = "生成完成，正在保存结果..."
                        GenerationTaskStore.update(GenerationTaskState.Running(requestId, message))
                        updateNotification(message)
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
            val storageRef = GenerationResultStore.save(this, result)
            GenerationResultRecoveryStore.stage(this, result.id)
            GenerationTaskStore.update(GenerationTaskState.Success(requestId, result))
            runCatching {
                SessionIdStore(this).saveSessionId(request.projectId, result.sessionId)
            }.onFailure { error ->
                Log.w(TAG, "Failed to save sessionId for ${request.projectId}: ${error.message}")
            }
            runCatching {
                stageAndFlushReceipt(result, storageRef)
            }.onFailure { error ->
                Log.w(TAG, "Failed to stage generation receipt ${result.id}: ${error.message}")
            }
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

    private suspend fun stageAndFlushReceipt(result: GenerationResponse, storageRef: String) {
        GenerationReceiptStore.stage(
            context = this,
            pendingReceipt = PendingGenerationReceipt(
                generationId = result.id,
                receipt = GenerationReceiptRequest(
                    projectId = result.projectId,
                    sessionId = result.sessionId,
                    contentLength = result.outputContent.length,
                    storageRef = storageRef
                )
            )
        )
        flushPendingReceipts()
    }

    private suspend fun flushPendingReceipts() {
        GenerationReceiptStore.loadAll(this).forEach { pendingReceipt ->
            runCatching {
                apiService.acknowledgeGenerationReceived(
                    generationId = pendingReceipt.generationId,
                    receipt = pendingReceipt.receipt
                )
            }.onSuccess {
                GenerationReceiptStore.delete(this, pendingReceipt.generationId)
            }.onFailure { error ->
                Log.w(TAG, "Failed to flush generation receipt ${pendingReceipt.generationId}: ${error.message}")
            }
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
        private const val TAG = "GenerationService"
        private const val CHANNEL_ID = "generation_foreground_channel"
        private const val CHANNEL_NAME = "生成任务"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_START = "com.novel.writing.assistant.action.START_GENERATION"
        private const val ACTION_CANCEL = "com.novel.writing.assistant.action.CANCEL_GENERATION"

        private const val EXTRA_REQUEST_ID = "extra_request_id"

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
            GenerationRequestStore.stage(
                context = context,
                requestId = requestId,
                request = PendingGenerationRequest(
                    projectId = projectId,
                    isContinueWriting = isContinueWriting,
                    referenceDoc = referenceDoc,
                    genreType = genreType,
                    writingDirection = writingDirection,
                    sessionId = sessionId
                )
            )
            val intent = Intent(context, GenerationForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_REQUEST_ID, requestId)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                GenerationRequestStore.delete(context, requestId)
                throw e
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
