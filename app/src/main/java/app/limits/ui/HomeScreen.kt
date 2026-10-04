@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.limits.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.limits.domain.ProviderId
import app.limits.domain.ProviderUsage
import app.limits.domain.QuotaWindow
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    snapshots: Map<ProviderId, ProviderUsage>,
    connected: Set<ProviderId>,
    errors: Map<ProviderId, String>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onHistory: () -> Unit,
    onConnect: (ProviderId) -> Unit,
    onDisconnect: (ProviderId) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Limits") },
                actions = {
                    TextButton(onClick = onHistory) {
                        Text("History")
                    }
                    TextButton(onClick = onRefresh, enabled = !refreshing) {
                        Text(if (refreshing) "Refreshing…" else "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            ProviderId.entries.forEachIndexed { index, provider ->
                ProviderSection(
                    provider = provider,
                    usage = snapshots[provider],
                    connected = provider in connected,
                    error = errors[provider],
                    onConnect = { onConnect(provider) },
                    onDisconnect = { onDisconnect(provider) },
                )
                if (index != ProviderId.entries.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 22.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Credentials stay encrypted on this device. Claude and Codex use provider account auth; OpenCode Go uses your Go API key.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProviderSection(
    provider: ProviderId,
    usage: ProviderUsage?,
    connected: Boolean,
    error: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            provider.displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        if (connected) {
            TextButton(onClick = onDisconnect) { Text("Disconnect") }
        } else {
            OutlinedButton(onClick = onConnect) { Text("Connect") }
        }
    }
    Spacer(Modifier.height(8.dp))

    if (!connected) {
        Text("Not connected", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    if (error != null) {
        Text(
            if (usage != null) "Update failed · showing last known data" else error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
    }

    when {
        usage == null -> Text(
            "Waiting for first refresh…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        usage.windows.isEmpty() -> Text(
            "No quota windows returned",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> usage.windows.forEach { window ->
            QuotaRow(window)
            Spacer(Modifier.height(14.dp))
        }
    }

    usage?.let {
        val stale = it.isStale()
        Text(
            buildString {
                append("Updated ")
                append(formatAge(it.fetchedAtEpochMillis))
                if (stale) append(" · stale")
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (stale) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuotaRow(window: QuotaWindow) {
    val remaining = window.remainingPercent
    val pace = window.paceRatio()

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            window.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(110.dp),
        )
        LinearProgressIndicator(
            progress = { (remaining / 100.0).toFloat() },
            modifier = Modifier.weight(1f).height(8.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "${remaining.roundToInt()}% left",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(64.dp),
        )
    }

    val detail = buildList {
        window.resetsAtEpochMillis?.let { add("Resets ${formatReset(it)}") }
        pace?.let { add("${formatPace(it)} pace") }
    }.joinToString(" · ")

    if (detail.isNotBlank()) {
        Text(
            detail,
            style = MaterialTheme.typography.labelSmall,
            color = if (pace != null && pace > 1.2) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 110.dp),
        )
    }
}

private fun formatPace(ratio: Double): String =
    String.format(Locale.US, "%.1f×", ratio.coerceAtMost(9.9))

private fun formatReset(epochMillis: Long): String {
    val duration = Duration.between(Instant.now(), Instant.ofEpochMilli(epochMillis))
    if (duration.isNegative || duration.isZero) return "now"
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    return when {
        hours >= 48 -> "in ${hours / 24}d"
        hours >= 1 -> "in ${hours}h ${minutes}m"
        else -> "in ${minutes.coerceAtLeast(1)}m"
    }
}

private fun formatAge(epochMillis: Long): String {
    val duration = Duration.between(Instant.ofEpochMilli(epochMillis), Instant.now())
    val minutes = duration.toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        else -> "${minutes / 60}h ago"
    }
}
