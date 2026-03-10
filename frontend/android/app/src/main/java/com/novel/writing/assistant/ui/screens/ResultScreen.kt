package com.novel.writing.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ResultScreen(
    generatedContent: String,
    onBackClick: () -> Unit,
    onRegenerateClick: () -> Unit
) {
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
                text = "生成结果",
                fontSize = 20.sp,
                fontWeight = MaterialTheme.typography.headlineSmall.fontWeight
            )
            Box(modifier = Modifier.width(64.dp)) {}
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Result Content
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "生成的内容",
                    fontSize = 16.sp,
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = generatedContent,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onBackClick,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text("保存并返回")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = onRegenerateClick,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text("重新生成")
            }
        }
    }
}