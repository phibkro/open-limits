package app.limits.domain

import kotlinx.serialization.Serializable

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
)

@Serializable
data class ProviderUsage(
    val provider: ProviderId,
    val windows: List<QuotaWindow>,
    val fetchedAtEpochMillis: Long = System.currentTimeMillis(),
) {
    val primaryWindow: QuotaWindow?
        get() = windows.minByOrNull { windowRank(it.id) }

    private fun windowRank(id: String): Int = when (id) {
        "five_hour", "rolling", "primary" -> 0
        "seven_day", "weekly", "secondary" -> 1
        "monthly" -> 2
        else -> 9
    }
}
