package app.limits.data

import android.content.Context
import app.limits.domain.ProviderId
import app.limits.domain.ProviderUsage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class UsageStore(context: Context) {
    private val prefs = context.getSharedPreferences("usage_snapshots", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun save(usage: ProviderUsage) {
        prefs.edit().putString(usage.provider.key, json.encodeToString(usage)).apply()
    }

    fun get(provider: ProviderId): ProviderUsage? = prefs.getString(provider.key, null)?.let {
        runCatching { json.decodeFromString<ProviderUsage>(it) }.getOrNull()
    }

    fun getAll(): Map<ProviderId, ProviderUsage> = ProviderId.entries.mapNotNull { id ->
        get(id)?.let { id to it }
    }.toMap()

    fun clear(provider: ProviderId) {
        prefs.edit().remove(provider.key).apply()
    }
}
