package com.novel.writing.assistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novel.writing.assistant.theme.NovelWritingAssistantTheme

@Composable
fun ErrorScreen(
    message: String = "An error occurred",
    onRetry: (() -> Unit)? = null
) {
    NovelWritingAssistantTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(16.dp)
            )
            if (onRetry != null) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Retry")
                }
            }
        }
    }
}
