package app.limits.data

import android.content.Context
import app.limits.domain.ProviderId

data class RadialWidgetConfig(
    val provider: ProviderId = ProviderId.CLAUDE,
    val showReset: Boolean = true,
    val showPace: Boolean = true,
)

class RadialWidgetConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("radial_widget_config", Context.MODE_PRIVATE)

    fun get(appWidgetId: Int): RadialWidgetConfig {
        val provider = prefs.getString(providerKey(appWidgetId), null)
            ?.let { key -> ProviderId.entries.firstOrNull { it.key == key } }
            ?: ProviderId.CLAUDE
        return RadialWidgetConfig(
            provider = provider,
            showReset = prefs.getBoolean(resetKey(appWidgetId), true),
            showPace = prefs.getBoolean(paceKey(appWidgetId), true),
        )
    }

    fun save(appWidgetId: Int, config: RadialWidgetConfig) {
        prefs.edit()
            .putString(providerKey(appWidgetId), config.provider.key)
            .putBoolean(resetKey(appWidgetId), config.showReset)
            .putBoolean(paceKey(appWidgetId), config.showPace)
            .apply()
    }

    fun clear(appWidgetId: Int) {
        prefs.edit()
            .remove(providerKey(appWidgetId))
            .remove(resetKey(appWidgetId))
            .remove(paceKey(appWidgetId))
            .apply()
    }

    private fun providerKey(id: Int) = "provider_$id"
    private fun resetKey(id: Int) = "show_reset_$id"
    private fun paceKey(id: Int) = "show_pace_$id"
}
