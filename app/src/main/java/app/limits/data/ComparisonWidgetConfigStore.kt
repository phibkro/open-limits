package app.limits.data

import android.content.Context
import app.limits.domain.MeasurementKind
import app.limits.domain.ProviderId

enum class ComparisonOrientation {
    PROVIDERS_AS_ROWS,
    PROVIDERS_AS_COLUMNS,
}

data class ComparisonWidgetConfig(
    val orientation: ComparisonOrientation = ComparisonOrientation.PROVIDERS_AS_ROWS,
    val providers: Set<ProviderId> = ProviderId.entries.toSet(),
    val measurements: Set<MeasurementKind> = MeasurementKind.entries.toSet(),
    val showBars: Boolean = true,
)

class ComparisonWidgetConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("comparison_widget_config", Context.MODE_PRIVATE)

    fun get(appWidgetId: Int): ComparisonWidgetConfig {
        val providers = decodeProviders(prefs.getString(providersKey(appWidgetId), null))
        val measurements = decodeMeasurements(prefs.getString(measurementsKey(appWidgetId), null))
        val orientation = prefs.getString(orientationKey(appWidgetId), null)
            ?.let { runCatching { ComparisonOrientation.valueOf(it) }.getOrNull() }
            ?: ComparisonOrientation.PROVIDERS_AS_ROWS

        return ComparisonWidgetConfig(
            orientation = orientation,
            providers = providers.ifEmpty { ProviderId.entries.toSet() },
            measurements = measurements.ifEmpty { MeasurementKind.entries.toSet() },
            showBars = prefs.getBoolean(barsKey(appWidgetId), true),
        )
    }

    fun save(appWidgetId: Int, config: ComparisonWidgetConfig) {
        prefs.edit()
            .putString(orientationKey(appWidgetId), config.orientation.name)
            .putString(providersKey(appWidgetId), config.providers.joinToString(",") { it.name })
            .putString(measurementsKey(appWidgetId), config.measurements.joinToString(",") { it.name })
            .putBoolean(barsKey(appWidgetId), config.showBars)
            .apply()
    }

    fun clear(appWidgetId: Int) {
        prefs.edit()
            .remove(orientationKey(appWidgetId))
            .remove(providersKey(appWidgetId))
            .remove(measurementsKey(appWidgetId))
            .remove(barsKey(appWidgetId))
            .apply()
    }

    private fun decodeProviders(raw: String?): Set<ProviderId> =
        raw.orEmpty().split(',').mapNotNull { token ->
            runCatching { ProviderId.valueOf(token) }.getOrNull()
        }.toSet()

    private fun decodeMeasurements(raw: String?): Set<MeasurementKind> =
        raw.orEmpty().split(',').mapNotNull { token ->
            runCatching { MeasurementKind.valueOf(token) }.getOrNull()
        }.toSet()

    private fun orientationKey(id: Int) = "orientation_$id"
    private fun providersKey(id: Int) = "providers_$id"
    private fun measurementsKey(id: Int) = "measurements_$id"
    private fun barsKey(id: Int) = "show_bars_$id"
}
