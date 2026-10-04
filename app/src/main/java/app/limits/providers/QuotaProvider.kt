package app.limits.providers

import app.limits.domain.ProviderId
import app.limits.domain.ProviderUsage

interface QuotaProvider {
    val id: ProviderId
    fun isConnected(): Boolean
    suspend fun fetch(): ProviderUsage
    fun disconnect()
}
