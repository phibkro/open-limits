package app.limits.providers

import app.limits.data.ClaudeCredential
import app.limits.data.CredentialStore
import app.limits.domain.ProviderId
import app.limits.domain.ProviderUsage
import app.limits.domain.QuotaWindow
import app.limits.network.Http
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ClaudeProvider(
    private val credentials: CredentialStore,
    private val http: Http,
) : QuotaProvider {
    override val id = ProviderId.CLAUDE
    private val json = Json { ignoreUnknownKeys = true }

    override fun isConnected() = credentials.getClaude() != null

    override suspend fun fetch(): ProviderUsage {
        val credential = ensureFresh(credentials.getClaude() ?: error("Claude is not connected"))
        var response = requestUsage(credential.accessToken)
        if (response.code == 401 || response.code == 403) {
            val refreshed = refresh(credential) ?: error("Claude sign-in expired")
            response = requestUsage(refreshed.accessToken)
        }
        if (response.code !in 200..299) error("Claude usage request failed (${response.code})")
        val dto = json.decodeFromString<UsageDto>(response.body)
        return ProviderUsage(
            provider = id,
            windows = listOfNotNull(
                dto.fiveHour?.toWindow("five_hour", "5 hour"),
                dto.sevenDay?.toWindow("seven_day", "Weekly"),
                dto.sevenDaySonnet?.toWindow("seven_day_sonnet", "Sonnet weekly"),
                dto.sevenDayOpus?.toWindow("seven_day_opus", "Opus weekly"),
            ),
        )
    }

    suspend fun exchangeAuthorizationCode(code: String, verifier: String, redirectUri: String, state: String): ClaudeCredential {
        val body = buildJsonObject {
            put("grant_type", "authorization_code")
            put("client_id", CLIENT_ID)
            put("code", code)
            put("state", state)
            put("code_verifier", verifier)
            put("redirect_uri", redirectUri)
        }.toString()
        val response = http.postJson(
            TOKEN_ENDPOINT,
            body,
            mapOf("Accept" to "application/json", "User-Agent" to "claude-cli/2.1.0 (external, cli)"),
        )
        if (response.code !in 200..299) error("Claude OAuth exchange failed (${response.code})")
        val dto = json.decodeFromString<TokenDto>(response.body)
        val credential = ClaudeCredential(
            accessToken = dto.accessToken ?: error("Claude OAuth response omitted access token"),
            refreshToken = dto.refreshToken,
            expiresAtEpochSeconds = dto.expiresIn?.let { Instant.now().epochSecond + it },
        )
        credentials.saveClaude(credential)
        return credential
    }

    override fun disconnect() = credentials.clearClaude()

    private suspend fun requestUsage(accessToken: String) = http.get(
        "https://api.anthropic.com/api/oauth/usage",
        mapOf(
            "Authorization" to "Bearer $accessToken",
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "anthropic-beta" to "oauth-2025-04-20",
            "User-Agent" to "claude-code/2.1.0",
        ),
    )

    private suspend fun ensureFresh(credential: ClaudeCredential): ClaudeCredential {
        val expires = credential.expiresAtEpochSeconds ?: return credential
        if (expires > Instant.now().epochSecond + 300) return credential
        return refresh(credential) ?: credential
    }

    private suspend fun refresh(credential: ClaudeCredential): ClaudeCredential? {
        val token = credential.refreshToken ?: return null
        val body = buildJsonObject {
            put("grant_type", "refresh_token")
            put("client_id", CLIENT_ID)
            put("refresh_token", token)
        }.toString()
        val response = http.postJson(
            TOKEN_ENDPOINT,
            body,
            mapOf("Accept" to "application/json", "User-Agent" to "claude-cli/2.1.0 (external, cli)"),
        )
        if (response.code !in 200..299) return null
        val dto = json.decodeFromString<TokenDto>(response.body)
        val updated = ClaudeCredential(
            accessToken = dto.accessToken ?: return null,
            refreshToken = dto.refreshToken ?: credential.refreshToken,
            expiresAtEpochSeconds = dto.expiresIn?.let { Instant.now().epochSecond + it },
        )
        credentials.saveClaude(updated)
        return updated
    }

    private fun WindowDto.toWindow(id: String, label: String) = QuotaWindow(
        id = id,
        label = label,
        usedPercent = (utilization ?: 0.0).coerceIn(0.0, 100.0),
        resetsAtEpochMillis = resetsAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
    )

    @Serializable private data class UsageDto(
        @SerialName("five_hour") val fiveHour: WindowDto? = null,
        @SerialName("seven_day") val sevenDay: WindowDto? = null,
        @SerialName("seven_day_opus") val sevenDayOpus: WindowDto? = null,
        @SerialName("seven_day_sonnet") val sevenDaySonnet: WindowDto? = null,
    )
    @Serializable private data class WindowDto(val utilization: Double? = null, @SerialName("resets_at") val resetsAt: String? = null)
    @Serializable private data class TokenDto(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
    )

    companion object {
        const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
        const val REDIRECT_URI = "https://console.anthropic.com/oauth/code/callback"
        const val TOKEN_ENDPOINT = "https://claude.ai/v1/oauth/token"
    }
}
