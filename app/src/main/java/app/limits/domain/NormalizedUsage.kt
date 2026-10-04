package app.limits.domain

enum class MeasurementKind(
    val shortLabel: String,
    val displayName: String,
) {
    SHORT("5h", "Short"),
    WEEKLY("Week", "Weekly"),
    TOTAL("Total", "Total"),
}

data class NormalizedUsage(
    val provider: ProviderId,
    val measurement: MeasurementKind,
    val remainingPercent: Double,
    val resetsAtEpochMillis: Long?,
    val sourceWindowId: String,
    val sourceLabel: String,
) {
    val usedPercent: Double
        get() = (100.0 - remainingPercent).coerceIn(0.0, 100.0)
}

fun ProviderUsage.normalizedMeasurements(): Map<MeasurementKind, NormalizedUsage> {
    fun exact(vararg ids: String): QuotaWindow? =
        ids.firstNotNullOfOrNull { id -> windows.firstOrNull { it.id == id } }

    val short = when (provider) {
        ProviderId.CLAUDE -> exact("five_hour")
        ProviderId.CODEX -> exact("five_hour", "primary")
        ProviderId.OPENCODE_GO -> exact("rolling")
    }

    val weekly = when (provider) {
        ProviderId.CLAUDE -> exact("seven_day")
        ProviderId.CODEX -> exact("weekly", "secondary")
        ProviderId.OPENCODE_GO -> exact("weekly")
    }

    val total = when (provider) {
        ProviderId.OPENCODE_GO -> exact("monthly")
        ProviderId.CLAUDE -> windows.firstOrNull { it.id == "monthly" }
        ProviderId.CODEX -> windows.firstOrNull { it.id == "monthly" }
    }

    return buildMap {
        fun putWindow(kind: MeasurementKind, window: QuotaWindow?) {
            if (window == null) return
            put(
                kind,
                NormalizedUsage(
                    provider = provider,
                    measurement = kind,
                    remainingPercent = window.remainingPercent,
                    resetsAtEpochMillis = window.resetsAtEpochMillis,
                    sourceWindowId = window.id,
                    sourceLabel = window.label,
                ),
            )
        }

        putWindow(MeasurementKind.SHORT, short)
        putWindow(MeasurementKind.WEEKLY, weekly)
        putWindow(MeasurementKind.TOTAL, total)
    }
}
