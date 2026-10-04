package app.limits.providers

import app.limits.data.CredentialStore
import app.limits.domain.ProviderId
import app.limits.domain.ProviderUsage
import app.limits.domain.QuotaWindow
import app.limits.network.Http
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OpenCodeProvider(
    private val credentials: CredentialStore,
    private val http: Http,
) : QuotaProvider {
    override val id = ProviderId.OPENCODE_GO
    private val json = Json { ignoreUnknownKeys = true }

    override fun isConnected() = credentials.getOpenCode() != null

    override suspend fun fetch(): ProviderUsage {
        val credential = credentials.getOpenCode() ?: error("OpenCode Go is not connected")
        val response = http.get(
            "https://opencode.ai/zen/go/v1/usage",
            mapOf("Authorization" to "Bearer ${credential.apiKey}", "Accept" to "application/json"),
        )
        if (response.code !in 200..299) error("OpenCode Go usage request failed (${response.code})")
        val dto = json.decodeFromString<ResponseDto>(response.body)
        return ProviderUsage(
            provider = id,
            windows = listOfNotNull(
                dto.usage.rolling?.toDomain("rolling", "Rolling"),
                dto.usage.weekly?.toDomain("weekly", "Weekly"),
                dto.usage.monthly?.toDomain("monthly", "Monthly"),
            ),
        )
    }

    override fun disconnect() = credentials.clearOpenCode()

    private fun UsageDto.toDomain(id: String, label: String) = QuotaWindow(
        id = id,
        label = label,
        usedPercent = percent.toDouble().coerceIn(0.0, 100.0),
        resetsAtEpochMillis = runCatching { Instant.parse(resetsAt).toEpochMilli() }.getOrNull(),
        status = status,
    )

    @Serializable private data class ResponseDto(val usage: WindowsDto)
    @Serializable private data class WindowsDto(
        val rolling: UsageDto? = null,
        val weekly: UsageDto? = null,
        val monthly: UsageDto? = null,
    )
    @Serializable private data class UsageDto(val status: String, val percent: Int, val resetsAt: String)
}
