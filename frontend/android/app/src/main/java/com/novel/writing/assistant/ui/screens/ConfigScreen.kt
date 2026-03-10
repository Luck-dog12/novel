package com.novel.writing.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novel.writing.assistant.data.UserConfig
import com.novel.writing.assistant.network.ConfigRequest
import com.novel.writing.assistant.network.ApiService
import com.novel.writing.assistant.ui.components.LoadingScreen
import com.novel.writing.assistant.ui.components.ErrorScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    projectId: String,
    onBackClick: () -> Unit,
    onConfigSaved: () -> Unit,
    uiOnly: Boolean = false
) {
    val context = LocalContext.current
    val apiService = ApiService()
    val scope = rememberCoroutineScope()
    
    var selectedGenre by remember { mutableStateOf("小说") }
    var writingDirection by remember { mutableStateOf("") }
    var maxLength by remember { mutableStateOf("500") }
    var creativityLevel by remember { mutableStateOf("medium") }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isGenreExpanded by remember { mutableStateOf(false) }
    var isCreativityExpanded by remember { mutableStateOf(false) }
    
    val genres = listOf(
        "小说", "散文", "诗歌", "剧本", "报告", "论文", "简历", 
        "合同", "广告文案", "产品描述", "社交媒体内容", "自定义"
    )
    
    val creativityLevels = listOf(
        "low" to "低创意",
        "medium" to "中等创意",
        "high" to "高创意"
    )
    
    // Load existing config when screen is composited
    LaunchedEffect(projectId, uiOnly) {
        if (uiOnly) {
            selectedGenre = "小说"
            writingDirection = "（示例）以悬疑为主线，节奏紧凑，章节结尾留悬念"
            maxLength = "800"
            creativityLevel = "medium"
            isLoading = false
            errorMessage = null
            return@LaunchedEffect
        }
        scope.launch {
            try {
                isLoading = true
                val config = apiService.getConfig(projectId)
                if (config != null) {
                    selectedGenre = config.genreType
                    writingDirection = config.writingDirection
                    maxLength = config.maxLength.toString()
                    creativityLevel = config.creativityLevel
                }
            } catch (e: Exception) {
                errorMessage = "加载配置失败：${e.message}"
            } finally {
                isLoading = false
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
                text = "参数配置",
                fontSize = 20.sp,
                fontWeight = MaterialTheme.typography.headlineSmall.fontWeight
            )
            Box(modifier = Modifier.width(64.dp)) {}
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
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
                    text = "写作方向",
                    fontSize = 16.sp,
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = writingDirection,
                    onValueChange = { writingDirection = it },
                    placeholder = { Text("描述你希望的写作内容走向") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Max Length
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "最大长度",
                    fontSize = 16.sp,
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = maxLength,
                    onValueChange = { maxLength = it },
                    placeholder = { Text("输入最大生成长度") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Creativity Level
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "创意程度",
                    fontSize = 16.sp,
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                )
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = isCreativityExpanded,
                    onExpandedChange = { isCreativityExpanded = it }
                ) {
                    TextField(
                        value = creativityLevels.find { it.first == creativityLevel }?.second ?: "中等创意",
                        onValueChange = {},
                        label = { Text("选择创意程度") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCreativityExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = isCreativityExpanded,
                        onDismissRequest = { isCreativityExpanded = false }
                    ) {
                        creativityLevels.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    creativityLevel = value
                                    isCreativityExpanded = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    // Reset to default values
                    selectedGenre = "小说"
                    writingDirection = ""
                    maxLength = "500"
                    creativityLevel = "medium"
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text("重置")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        if (uiOnly) {
                            onConfigSaved()
                            return@launch
                        }
                        isSaving = true
                        errorMessage = null
                        
                        try {
                            // Validate input
                            val validationError = validateInput(
                                maxLength = maxLength
                            )
                            
                            if (validationError != null) {
                                errorMessage = validationError
                                return@launch
                            }
                            
                            // Create config object
                            val config = ConfigRequest(
                                projectId = projectId,
                                genreType = selectedGenre,
                                writingDirection = writingDirection,
                                maxLength = maxLength.toInt(),
                                creativityLevel = creativityLevel
                            )
                            
                            // Save config
                            apiService.saveConfig(config)
                            onConfigSaved()
                        } catch (e: Exception) {
                            errorMessage = "保存失败：${e.message}"
                        } finally {
                            isSaving = false
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                enabled = !isSaving
            ) {
                Text(if (isSaving) "保存中..." else "保存")
            }
        }
    }
    
    // Loading State
    if (isLoading) {
        LoadingScreen()
    }
    
    // Saving State
    if (isSaving) {
        LoadingScreen(message = "保存配置中...")
    }
    
    // Error State
    errorMessage?.let {
        ErrorScreen(
            message = it,
            onRetry = { errorMessage = null }
        )
    }
}

private fun validateInput(
    maxLength: String
): String? {
    // Validate max length
    val maxLengthInt = maxLength.toIntOrNull()
    if (maxLengthInt == null || maxLengthInt <= 0 || maxLengthInt > 5000) {
        return "最大长度必须在 1-5000 之间"
    }
    
    return null
}
