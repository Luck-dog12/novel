package com.novel.writing.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.novel.writing.assistant.network.ApiClient
import com.novel.writing.assistant.theme.NovelWritingAssistantTheme
import com.novel.writing.assistant.ui.screens.ConfigScreen
import com.novel.writing.assistant.ui.screens.ContinueWritingScreen
import com.novel.writing.assistant.ui.screens.HistoryScreen
import com.novel.writing.assistant.ui.screens.InitialGenerationScreen
import com.novel.writing.assistant.ui.screens.ResultScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.initialize(applicationContext)
        setContent {
            NovelWritingAssistantTheme {
                // A surface container using the 'background' color from the theme
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
    val projectId = "demo-project"
    var screen by remember { mutableStateOf("home") }
    var resultText by remember { mutableStateOf("（示例）生成结果将在这里显示") }

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
            onGenerationComplete = {
                resultText = it
                screen = "result"
            },
            uiOnly = uiOnly
        )
        "continue" -> ContinueWritingScreen(
            projectId = projectId,
            onBackClick = { screen = "home" },
            onGenerationComplete = {
                resultText = it
                screen = "result"
            },
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
            onHistoryItemClick = {
                resultText = it
                screen = "result"
            },
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
