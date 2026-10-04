@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.limits.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.limits.data.UsageHistorySample
import app.limits.domain.ProviderId
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    samples: List<UsageHistorySample>,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    if (samples.isNotEmpty()) {
                        TextButton(onClick = onClear) { Text("Clear") }
                    }
                },
            )
        },
    ) { padding ->
        if (samples.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
            ) {
                Text(
                    "No history yet",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Quota samples are recorded whenever Open Limits successfully refreshes a provider.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            ProviderId.entries.forEachIndexed { providerIndex, provider ->
                val providerSamples = samples.filter { it.provider == provider }
                if (providerSamples.isEmpty()) return@forEachIndexed

                Text(
                    provider.displayName,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(10.dp))

                providerSamples
                    .groupBy { it.windowId }
                    .values
                    .sortedBy { windowRank(it.first().windowId) }
                    .forEach { windowSamples ->
                        HistoryWindow(windowSamples)
                        Spacer(Modifier.height(18.dp))
                    }

                if (providerIndex != ProviderId.entries.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun HistoryWindow(samples: List<UsageHistorySample>) {
    val sorted = samples.sortedBy { it.capturedAtEpochMillis }
    val latest = sorted.last()
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(latest.windowLabel, style = MaterialTheme.typography.titleSmall)
        Text(
            "${(100.0 - latest.usedPercent).coerceIn(0.0, 100.0).roundToInt()}% left",
            style = MaterialTheme.typography.labelLarge,
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "${sorted.size} samples · ${historySpan(sorted)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        drawLine(
            color = track,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )

        if (sorted.size == 1) {
            val remaining = (100.0 - sorted.first().usedPercent).coerceIn(0.0, 100.0)
            val y = size.height * (1f - (remaining / 100.0).toFloat())
            drawCircle(color = color, radius = 3.dp.toPx(), center = Offset(size.width, y))
            return@Canvas
        }

        val minTime = sorted.first().capturedAtEpochMillis
        val maxTime = max(minTime + 1, sorted.last().capturedAtEpochMillis)
        val path = Path()

        sorted.forEachIndexed { index, sample ->
            val x = ((sample.capturedAtEpochMillis - minTime).toFloat() /
                (maxTime - minTime).toFloat()) * size.width
            val remaining = (100.0 - sample.usedPercent).coerceIn(0.0, 100.0)
            val y = size.height * (1f - (remaining / 100.0).toFloat())
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

private fun historySpan(samples: List<UsageHistorySample>): String {
    if (samples.size < 2) return "first sample"
    val duration = Duration.between(
        Instant.ofEpochMilli(samples.first().capturedAtEpochMillis),
        Instant.ofEpochMilli(samples.last().capturedAtEpochMillis),
    )
    val hours = duration.toHours()
    return when {
        hours >= 48 -> "last ${hours / 24}d"
        hours >= 1 -> "last ${hours}h"
        else -> "last ${duration.toMinutes().coerceAtLeast(1)}m"
    }
}

private fun windowRank(id: String): Int = when (id) {
    "five_hour", "rolling", "primary" -> 0
    "seven_day", "weekly", "secondary" -> 1
    "seven_day_opus", "seven_day_sonnet" -> 2
    "monthly" -> 3
    else -> 9
}
