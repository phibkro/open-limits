package app.limits.data

import android.content.Context
import app.limits.domain.ProviderId
import app.limits.domain.ProviderUsage
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class UsageHistorySample(
    val provider: ProviderId,
    val windowId: String,
    val windowLabel: String,
    val usedPercent: Double,
    val resetsAtEpochMillis: Long? = null,
    val capturedAtEpochMillis: Long,
)

class UsageHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("usage_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _samples = MutableStateFlow(load())
    val samples: StateFlow<List<UsageHistorySample>> = _samples.asStateFlow()

    @Synchronized
    fun append(usage: ProviderUsage) {
        val now = usage.fetchedAtEpochMillis
        val cutoff = now - RETENTION_MILLIS
        val existing = _samples.value
            .asSequence()
            .filter { it.capturedAtEpochMillis >= cutoff }
            .toMutableList()

        for (window in usage.windows) {
            val latestSameWindow = existing.asReversed().firstOrNull {
                it.provider == usage.provider && it.windowId == window.id
            }
            if (latestSameWindow != null &&
                now - latestSameWindow.capturedAtEpochMillis < MIN_SAMPLE_INTERVAL_MILLIS
            ) {
                continue
            }

            existing += UsageHistorySample(
                provider = usage.provider,
                windowId = window.id,
                windowLabel = window.label,
                usedPercent = window.usedPercent,
                resetsAtEpochMillis = window.resetsAtEpochMillis,
                capturedAtEpochMillis = now,
            )
        }

        val trimmed = existing
            .sortedBy { it.capturedAtEpochMillis }
            .takeLast(MAX_SAMPLES)

        prefs.edit().putString(KEY_SAMPLES, json.encodeToString(trimmed)).apply()
        _samples.value = trimmed
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_SAMPLES).apply()
        _samples.value = emptyList()
    }

    private fun load(): List<UsageHistorySample> {
        val raw = prefs.getString(KEY_SAMPLES, null) ?: return emptyList()
        val decoded = runCatching {
            json.decodeFromString<List<UsageHistorySample>>(raw)
        }.getOrDefault(emptyList())
        val cutoff = System.currentTimeMillis() - RETENTION_MILLIS
        return decoded
            .filter { it.capturedAtEpochMillis >= cutoff }
            .sortedBy { it.capturedAtEpochMillis }
            .takeLast(MAX_SAMPLES)
    }

    companion object {
        private const val KEY_SAMPLES = "samples"
        private const val MAX_SAMPLES = 10_000
        private const val MIN_SAMPLE_INTERVAL_MILLIS = 60_000L
        private val RETENTION_MILLIS = Duration.ofDays(30).toMillis()
    }
}
