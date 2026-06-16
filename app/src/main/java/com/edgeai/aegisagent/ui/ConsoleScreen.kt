package com.edgeai.aegisagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgeai.aegisagent.ui.theme.*

@Composable
fun ConsoleScreen(
    viewModel: AegisViewModel,
    modifier: Modifier = Modifier
) {
    val logs by viewModel.consoleLogs.collectAsState()
    val listState = rememberLazyListState()

    // Auto scroll to bottom when new log entries arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "📁 COGNITIVE TRACE LOGS",
            style = MaterialTheme.typography.titleLarge
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs) { log ->
                    val color = when {
                        log.startsWith("> User:") -> CyberCyan
                        log.startsWith("< Assistant:") -> ActiveGreen
                        log.startsWith("[SYSTEM]") -> ElectricPurple
                        log.startsWith("[PARSER]") -> CyberCyan.copy(alpha = 0.8f)
                        log.startsWith("[EXEC]") -> WarningAmber
                        log.startsWith("[RESULT]") -> ActiveGreen.copy(alpha = 0.8f)
                        log.startsWith("[ERROR]") -> Color(0xFFEF4444)
                        else -> TextPrimary
                    }
                    Text(
                        text = log,
                        fontFamily = MonospaceFamily,
                        fontSize = 11.sp,
                        color = color,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
