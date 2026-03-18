package com.novel.writing.assistant.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novel.writing.assistant.ui.components.LoadingScreen
import com.novel.writing.assistant.ui.components.ErrorScreen
import com.novel.writing.assistant.service.GenerationForegroundService
import com.novel.writing.assistant.service.GenerationTaskState
import com.novel.writing.assistant.service.GenerationTaskStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InitialGenerationScreen(
    projectId: String,
    onBackClick: () -> Unit,
    onGenerationComplete: (String) -> Unit,
    uiOnly: Boolean = false
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val taskState by GenerationTaskStore.state.collectAsState()
    
    var selectedGenre by remember { mutableStateOf("小说") }
    var writingDirection by remember { mutableStateOf("") }
    var referenceDocText by remember { mutableStateOf("") }
    var selectedDocumentId by remember { mutableStateOf<String?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var isGenreExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loadingMessage by remember { mutableStateOf("正在生成内容...") }
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
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedFileName = uri.lastPathSegment ?: "selected-file"
        scope.launch {
            isUploading = true
            errorMessage = null
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("无法读取文件内容")
                referenceDocText = bytes.toString(Charsets.UTF_8).trim()
                selectedDocumentId = null
            } catch (e: Exception) {
                errorMessage = "文件读取失败：${e.message}"
            } finally {
                isUploading = false
            }
        }
    }
    
    val genres = listOf(
        "小说", "散文", "诗歌", "剧本", "报告", "论文", "简历", 
        "合同", "广告文案", "产品描述", "社交媒体内容", "自定义"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding()
            .verticalScroll(scrollState)
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
                text = "初始生成",
                fontSize = 20.sp,
                fontWeight = MaterialTheme.typography.headlineSmall.fontWeight
            )
            Box(modifier = Modifier.width(64.dp)) {}
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Reference Document Upload
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "参考文档",
                    fontSize = 16.sp,
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "上传参考文档（支持md/txt格式，大小不超过20MB）",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (uiOnly) {
                            selectedDocumentId = "mock-document-id"
                            selectedFileName = "demo.md"
                            return@Button
                        }
                        filePickerLauncher.launch(arrayOf("text/plain", "text/markdown"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading
                ) {
                    Text(if (isUploading) "上传中..." else "选择文件")
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (selectedDocumentId != null && selectedFileName != null) {
                    Text(
                        text = "已选择文件：$selectedFileName",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = referenceDocText,
                    onValueChange = { referenceDocText = it },
                    label = { Text("参考内容（可选）") },
                    placeholder = { Text("粘贴参考内容，或仅上传文件") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Genre Selection
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "题材类型",
                    fontSize = 16.sp,
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                )
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = isGenreExpanded,
                    onExpandedChange = { isGenreExpanded = it }
                ) {
                    TextField(
                        value = selectedGenre,
                        onValueChange = {},
                        label = { Text("选择题材") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isGenreExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = isGenreExpanded,
                        onDismissRequest = { isGenreExpanded = false }
                    ) {
                        genres.forEach { genre ->
                            DropdownMenuItem(
                                text = { Text(genre) },
                                onClick = {
                                    selectedGenre = genre
                                    isGenreExpanded = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Writing Direction
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "写作内容走向",
                    fontSize = 16.sp,
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = writingDirection,
                    onValueChange = { writingDirection = it },
                    placeholder = { Text("描述你希望的写作内容走向") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Generate Button
        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    errorMessage = null
                    loadingMessage = "正在连接流式生成..."
                    focusManager.clearFocus()
                    
                    try {
                        if (uiOnly) {
                            onGenerationComplete("（示例）初始生成结果：夜色落在屋檐上，风从巷口穿过，故事从一声叹息开始。")
                            return@launch
                        }
                        val requestId = GenerationForegroundService.start(
                            context = context,
                            projectId = projectId,
                            isContinueWriting = false,
                            referenceDoc = referenceDocText.takeIf { it.isNotBlank() },
                            genreType = selectedGenre,
                            writingDirection = writingDirection,
                            sessionId = null
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
            enabled = selectedGenre.isNotEmpty() && writingDirection.isNotEmpty()
        ) {
            Text(
                text = "生成内容",
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
    
    // Uploading State
    if (isUploading) {
        LoadingScreen(message = "文件上传中...")
    }
}
