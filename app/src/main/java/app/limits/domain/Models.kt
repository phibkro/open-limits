package app.limits.domain

import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
enum class ProviderId(val key: String, val displayName: String) {
    CLAUDE("claude", "Claude"),
    CODEX("codex", "Codex"),
    OPENCODE_GO("opencode_go", "OpenCode Go"),
}

@Serializable
data class QuotaWindow(
    val id: String,
    val label: String,
    val usedPercent: Double,
    val resetsAtEpochMillis: Long? = null,
    val status: String? = null,
) {
    /**
     * Ratio between quota consumed and time elapsed in the current window.
     *
     * 1.0 = exactly sustainable pace, <1 = below pace, >1 = burning faster than
     * the window replenishes. Only returned for windows whose duration is known.
     */
    fun paceRatio(nowEpochMillis: Long = System.currentTimeMillis()): Double? {
        val reset = resetsAtEpochMillis ?: return null
        val duration = estimatedDurationMillis() ?: return null
        val start = reset - duration
        if (nowEpochMillis < start || nowEpochMillis > reset) return null

        val elapsedFraction = ((nowEpochMillis - start).toDouble() / duration.toDouble())
            .coerceIn(0.01, 1.0)
        return (usedPercent.coerceIn(0.0, 100.0) / 100.0) / elapsedFraction
    }

    private fun estimatedDurationMillis(): Long? = when (id) {
        "five_hour", "rolling", "primary" -> 5L * 60 * 60 * 1000
        "seven_day", "weekly", "secondary",
        "seven_day_opus", "seven_day_sonnet" -> 7L * 24 * 60 * 60 * 1000
        else -> null
    }
}

@Serializable
data class ProviderUsage(
    val provider: ProviderId,
    val windows: List<QuotaWindow>,
    val fetchedAtEpochMillis: Long = System.currentTimeMillis(),
) {
    val primaryWindow: QuotaWindow?
        get() = windows.minByOrNull { windowRank(it.id) }

    fun ageMillis(nowEpochMillis: Long = System.currentTimeMillis()): Long =
        max(0L, nowEpochMillis - fetchedAtEpochMillis)

    fun isStale(
        nowEpochMillis: Long = System.currentTimeMillis(),
        thresholdMillis: Long = 30L * 60 * 1000,
    ): Boolean = ageMillis(nowEpochMillis) >= thresholdMillis

    private fun windowRank(id: String): Int = when (id) {
        "five_hour", "rolling", "primary" -> 0
        "seven_day", "weekly", "secondary" -> 1
        "monthly" -> 2
        else -> 9
    }
}
