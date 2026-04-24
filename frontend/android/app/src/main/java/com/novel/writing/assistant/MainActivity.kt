package com.novel.writing.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.novel.writing.assistant.network.ApiClient
import com.novel.writing.assistant.service.GenerationResultRecoveryStore
import com.novel.writing.assistant.service.GenerationResultStore
import com.novel.writing.assistant.service.GenerationTaskState
import com.novel.writing.assistant.service.GenerationTaskStore
import com.novel.writing.assistant.theme.NovelWritingAssistantTheme
import com.novel.writing.assistant.ui.screens.ConfigScreen
import com.novel.writing.assistant.ui.screens.ContinueWritingScreen
import com.novel.writing.assistant.ui.screens.HistoryScreen
import com.novel.writing.assistant.ui.screens.InitialGenerationScreen
import com.novel.writing.assistant.ui.screens.ResultScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.initialize(applicationContext)
        setContent {
            NovelWritingAssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UiSandbox(uiOnly = false)
                }
            }
        }
    }
}

@Composable
fun UiSandbox(uiOnly: Boolean) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val taskState by GenerationTaskStore.state.collectAsState()
    val projectId = "demo-project"
    var screen by remember { mutableStateOf("home") }
    var resultText by remember { mutableStateOf("生成结果将在这里显示") }

    fun showResult(content: String) {
        resultText = content
        screen = "result"
    }

    suspend fun restorePendingResultIfNeeded() {
        if (uiOnly) return
        val generationId = GenerationResultRecoveryStore.consume(context) ?: return
        val response = withContext(Dispatchers.IO) {
            GenerationResultStore.load(context, generationId)
        } ?: return
        showResult(response.outputContent)
    }

    LaunchedEffect(uiOnly) {
        restorePendingResultIfNeeded()
    }

    LaunchedEffect(taskState) {
        when (val state = taskState) {
            is GenerationTaskState.Success -> {
                GenerationResultRecoveryStore.clear(context)
                showResult(state.response.outputContent)
                GenerationTaskStore.reset()
            }

            else -> Unit
        }
    }

    DisposableEffect(lifecycleOwner, uiOnly) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !uiOnly) {
                scope.launch {
                    restorePendingResultIfNeeded()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    when (screen) {
        "home" -> {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(text = "界面测试入口", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { screen = "initial" }, modifier = Modifier.fillMaxWidth()) { Text("初始生成") }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { screen = "continue" }, modifier = Modifier.fillMaxWidth()) { Text("续写模式") }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { screen = "config" }, modifier = Modifier.fillMaxWidth()) { Text("参数配置") }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { screen = "history" }, modifier = Modifier.fillMaxWidth()) { Text("生成历史") }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { screen = "result" }, modifier = Modifier.fillMaxWidth()) { Text("结果页") }
            }
        }

        "initial" -> InitialGenerationScreen(
            projectId = projectId,
            onBackClick = { screen = "home" },
            onGenerationComplete = { showResult(it) },
            uiOnly = uiOnly
        )

        "continue" -> ContinueWritingScreen(
            projectId = projectId,
            onBackClick = { screen = "home" },
            onGenerationComplete = { showResult(it) },
            uiOnly = uiOnly
        )

        "config" -> ConfigScreen(
            projectId = projectId,
            onBackClick = { screen = "home" },
            onConfigSaved = { screen = "home" },
            uiOnly = uiOnly
        )

        "history" -> HistoryScreen(
            projectId = projectId,
            onBackClick = { screen = "home" },
            onHistoryItemClick = { showResult(it) },
            uiOnly = uiOnly
        )

        "result" -> ResultScreen(
            generatedContent = resultText,
            onBackClick = { screen = "home" },
            onRegenerateClick = { screen = "home" }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NovelWritingAssistantTheme {
        UiSandbox(uiOnly = true)
    }
}
