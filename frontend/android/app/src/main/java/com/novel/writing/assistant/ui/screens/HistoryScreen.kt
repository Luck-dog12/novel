package com.novel.writing.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novel.writing.assistant.network.ApiService
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    projectId: String,
    onBackClick: () -> Unit,
    onHistoryItemClick: (String) -> Unit,
    uiOnly: Boolean = false
) {
    val apiService = ApiService()
    val scope = rememberCoroutineScope()
    
    var historyItems by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var deletingHistoryId by remember { mutableStateOf<String?>(null) }
    var isDeletingAll by remember { mutableStateOf(false) }
    var confirmDeleteItem by remember { mutableStateOf<HistoryItem?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    
    // Load history when screen is composited
    LaunchedEffect(projectId, uiOnly) {
        if (uiOnly) {
            historyItems = listOf(
                HistoryItem(
                    id = "1",
                    generationType = "initial",
                    inputParams = "{}",
                    outputContent = "（示例）初始生成内容：远处钟声回荡，故事从一封未寄出的信开始……",
                    timestamp = "2026-02-05T12:00:00Z"
                ),
                HistoryItem(
                    id = "2",
                    generationType = "continuation",
                    inputParams = "{}",
                    outputContent = "（示例）续写内容：他终于读懂了信里的名字，却已来不及回头。",
                    timestamp = "2026-02-05T12:05:00Z"
                )
            )
            isLoading = false
            errorMessage = null
            return@LaunchedEffect
        }
        scope.launch {
            try {
                val items = apiService.getHistoryByProjectId(projectId)
                historyItems = items.map {
                    HistoryItem(
                        id = it.id,
                        generationType = it.generationType,
                        inputParams = it.inputParams,
                        outputContent = it.outputContent,
                        timestamp = it.generationDate
                    )
                }
            } catch (e: Exception) {
                errorMessage = "加载历史记录失败：${e.message}"
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
                text = "生成历史",
                fontSize = 20.sp,
                fontWeight = MaterialTheme.typography.headlineSmall.fontWeight
            )
            TextButton(
                onClick = { showClearAllConfirm = true },
                enabled = historyItems.isNotEmpty() && !isLoading && !isDeletingAll && deletingHistoryId == null
            ) {
                Text(if (isDeletingAll) "清空中..." else "清空")
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // History List
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
            ) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
            ) {
                Text(
                    text = errorMessage ?: "加载失败",
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else if (historyItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
            ) {
                Text(text = "暂无生成历史")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(historyItems) {
                    HistoryItemCard(
                        item = it,
                        isDeleting = deletingHistoryId == it.id,
                        onClick = { onHistoryItemClick(it.outputContent) },
                        onDeleteClick = { confirmDeleteItem = it }
                    )
                }
            }
        }
    }

    confirmDeleteItem?.let { target ->
        AlertDialog(
            onDismissRequest = { if (deletingHistoryId == null) confirmDeleteItem = null },
            confirmButton = {
                TextButton(
                    enabled = deletingHistoryId == null && !isDeletingAll,
                    onClick = {
                        scope.launch {
                            deletingHistoryId = target.id
                            errorMessage = null
                            try {
                                val success = if (uiOnly) {
                                    true
                                } else {
                                    apiService.deleteHistoryById(target.id)
                                }
                                if (success) {
                                    historyItems = historyItems.filterNot { it.id == target.id }
                                } else {
                                    errorMessage = "删除失败：未找到该记录"
                                }
                            } catch (e: Exception) {
                                errorMessage = "删除失败：${e.message}"
                            } finally {
                                deletingHistoryId = null
                                confirmDeleteItem = null
                            }
                        }
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = deletingHistoryId == null && !isDeletingAll,
                    onClick = { confirmDeleteItem = null }
                ) {
                    Text("取消")
                }
            },
            title = { Text("删除历史记录") },
            text = { Text("删除后不可恢复，是否继续？") }
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAll) showClearAllConfirm = false },
            confirmButton = {
                TextButton(
                    enabled = !isDeletingAll && deletingHistoryId == null,
                    onClick = {
                        scope.launch {
                            isDeletingAll = true
                            errorMessage = null
                            try {
                                val success = if (uiOnly) {
                                    true
                                } else {
                                    apiService.deleteHistoryByProjectId(projectId)
                                }
                                if (success) {
                                    historyItems = emptyList()
                                } else {
                                    errorMessage = "清空失败：当前项目没有可删除记录"
                                }
                            } catch (e: Exception) {
                                errorMessage = "清空失败：${e.message}"
                            } finally {
                                isDeletingAll = false
                                showClearAllConfirm = false
                            }
                        }
                    }
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeletingAll && deletingHistoryId == null,
                    onClick = { showClearAllConfirm = false }
                ) {
                    Text("取消")
                }
            },
            title = { Text("清空历史记录") },
            text = { Text("将删除当前项目全部历史记录，且不可恢复，是否继续？") }
        )
    }
}

data class HistoryItem(
    val id: String,
    val generationType: String,
    val inputParams: String,
    val outputContent: String,
    val timestamp: String
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HistoryItemCard(
    item: HistoryItem,
    isDeleting: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (item.generationType == "initial") "初始生成" else "续写",
                    fontSize = 14.sp,
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.timestamp,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = onDeleteClick,
                        enabled = !isDeleting
                    ) {
                        Text(if (isDeleting) "删除中..." else "删除")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.outputContent.take(100) + if (item.outputContent.length > 100) "..." else "",
                fontSize = 14.sp,
                maxLines = 3
            )
        }
    }
}
