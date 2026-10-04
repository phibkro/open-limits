package app.limits.data

import android.content.Context
import app.limits.domain.ProviderId

data class WidgetConfig(
    val provider: ProviderId? = null,
    val detailed: Boolean = true,
)

class WidgetConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("widget_config", Context.MODE_PRIVATE)

    fun get(appWidgetId: Int): WidgetConfig {
        val providerKey = prefs.getString(providerKey(appWidgetId), null)
        val provider = ProviderId.entries.firstOrNull { it.key == providerKey }
        return WidgetConfig(
            provider = provider,
            detailed = prefs.getBoolean(detailedKey(appWidgetId), true),
        )
    }

    fun save(appWidgetId: Int, config: WidgetConfig) {
        prefs.edit()
            .putString(providerKey(appWidgetId), config.provider?.key)
            .putBoolean(detailedKey(appWidgetId), config.detailed)
            .apply()
    }

    fun clear(appWidgetId: Int) {
        prefs.edit()
            .remove(providerKey(appWidgetId))
            .remove(detailedKey(appWidgetId))
            .apply()
    }

    private fun providerKey(id: Int) = "provider_$id"
    private fun detailedKey(id: Int) = "detailed_$id"
}
