package com.novel.writing.assistant.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novel.writing.assistant.network.ApiService
import com.novel.writing.assistant.service.GenerationForegroundService
import com.novel.writing.assistant.service.GenerationTaskState
import com.novel.writing.assistant.service.GenerationTaskStore
import com.novel.writing.assistant.ui.components.LoadingScreen
import com.novel.writing.assistant.ui.components.ErrorScreen
import com.novel.writing.assistant.utils.SessionIdStore
import kotlinx.coroutines.launch

@Composable
fun ContinueWritingScreen(
    projectId: String,
    onBackClick: () -> Unit,
    onGenerationComplete: (String) -> Unit,
    uiOnly: Boolean = false
) {
    val context = LocalContext.current
    val apiService = ApiService()
    val scope = rememberCoroutineScope()
    val sessionIdStore = remember(context) { SessionIdStore(context) }
    val contextScrollState = rememberScrollState()
    val taskState by GenerationTaskStore.state.collectAsState()
    
    var lastChapterContent by remember { mutableStateOf<String?>(null) }
    var isContextAvailable by remember { mutableStateOf(false) }
    var sessionId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loadingMessage by remember { mutableStateOf("正在生成续写...") }
    var activeRequestId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(taskState, activeRequestId) {
        val requestId = activeRequestId ?: return@LaunchedEffect
        when (val state = taskState) {
            is GenerationTaskState.Running -> {
                if (state.requestId == requestId) {
                    isLoading = true
                    loadingMessage = state.statusMessage
                }
            }

            is GenerationTaskState.Success -> {
                if (state.requestId == requestId) {
                    isLoading = false
                    GenerationTaskStore.reset()
                    activeRequestId = null
                    onGenerationComplete(state.response.outputContent)
                }
            }

            is GenerationTaskState.Failed -> {
                if (state.requestId == requestId) {
                    isLoading = false
                    errorMessage = state.message
                    GenerationTaskStore.reset()
                    activeRequestId = null
                }
            }

            GenerationTaskState.Idle -> {}
        }
    }

    LaunchedEffect(projectId, uiOnly) {
        if (uiOnly) {
            sessionId = "demo-session"
            lastChapterContent = "（示例）上一章节内容预览：主角在雨夜推开旧书店的门，尘封的气味与微光交织。"
            isContextAvailable = true
            return@LaunchedEffect
        }
        scope.launch {
            try {
                val storedSessionId = sessionIdStore.getSessionId(projectId)
                val recoveredSessionId = storedSessionId ?: apiService.getLatestSessionId(projectId)?.also {
                    sessionIdStore.saveSessionId(projectId, it)
                }
                sessionId = recoveredSessionId
                if (recoveredSessionId.isNullOrBlank()) {
                    isContextAvailable = false
                    return@launch
                }

                val contexts = apiService.getContextByProjectId(projectId, recoveredSessionId)
                val latestChapter = contexts.firstOrNull { it.contextType == "chapter" }?.contextContent
                lastChapterContent = latestChapter
                isContextAvailable = !latestChapter.isNullOrBlank() || contexts.isNotEmpty()
            } catch (_: Exception) {
                isContextAvailable = false
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBackClick) {
                Text("返回")
            }
            Text(
                text = "续写模式",
                fontSize = 20.sp,
                fontWeight = MaterialTheme.typography.headlineSmall.fontWeight
            )
            Box(modifier = Modifier.width(64.dp)) {}
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "续写上下文",
                    fontSize = 16.sp,
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isContextAvailable) "已加载上次生成的章节内容，点击下方按钮开始续写" else "未找到可用于续写的上下文，请先完成一次初始生成",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                SelectionContainer {
                    Text(
                        text = lastChapterContent ?: "",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 260.dp)
                            .verticalScroll(contextScrollState)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Generate Button
        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    errorMessage = null
                    loadingMessage = "正在连接流式续写..."
                    
                    try {
                        if (uiOnly) {
                            val demo = buildString {
                                appendLine("（示例）续写结果：")
                                appendLine()
                                appendLine("雨声渐密，旧书店的木门在他身后轻轻合拢。")
                                appendLine("他循着微光走进更深处，尘埃在光束里旋转，像一场迟到的雪。")
                                appendLine("柜台上那本无名旧册忽然翻页，纸张发出低低的呢喃——仿佛在等他续写未尽的故事。")
                            }
                            onGenerationComplete(demo.trimEnd())
                            return@launch
                        }
                        if (sessionId.isNullOrBlank()) {
                            errorMessage = "未找到可用的会话信息，请先完成一次初始生成"
                            return@launch
                        }
                        val requestId = GenerationForegroundService.start(
                            context = context,
                            projectId = projectId,
                            isContinueWriting = true,
                            referenceDoc = null,
                            genreType = null,
                            writingDirection = null,
                            sessionId = sessionId
                        )
                        activeRequestId = requestId
                        loadingMessage = "正在连接流式服务..."
                    } catch (e: Exception) {
                        errorMessage = "生成失败：${e.message}"
                        isLoading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = isContextAvailable && !isLoading
        ) {
            Text(
                text = "生成续写",
                fontSize = 16.sp
            )
        }
    }
    
    // Loading State
    if (isLoading) {
        LoadingScreen(message = loadingMessage)
    }
    
    // Error State
    errorMessage?.let {
        ErrorScreen(
            message = it,
            onRetry = { errorMessage = null }
        )
    }
}
