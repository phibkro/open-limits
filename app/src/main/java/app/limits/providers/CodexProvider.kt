package app.limits.providers

import android.util.Base64
import app.limits.data.CodexCredential
import app.limits.data.CredentialStore
import app.limits.domain.ProviderId
import app.limits.domain.ProviderUsage
import app.limits.domain.QuotaWindow
import app.limits.network.Http
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class CodexProvider(
    private val credentials: CredentialStore,
    private val http: Http,
) : QuotaProvider {
    override val id = ProviderId.CODEX
    private val json = Json { ignoreUnknownKeys = true }

    override fun isConnected() = credentials.getCodex() != null

    override suspend fun fetch(): ProviderUsage {
        var credential = ensureFresh(credentials.getCodex() ?: error("Codex is not connected"))
        var response = requestUsage(credential)
        if (response.code == 401 || response.code == 403) {
            credential = refresh(credential)
            response = requestUsage(credential)
        }
        if (response.code !in 200..299) error("Codex usage request failed (${response.code})")
        val dto = json.decodeFromString<UsageDto>(response.body)
        val windows = listOfNotNull(dto.rateLimit?.primaryWindow, dto.rateLimit?.secondaryWindow)
            .mapIndexed { index, window -> window.toDomain(index) }
            .sortedBy { rank(it) }
        return ProviderUsage(provider = id, windows = windows)
    }

    suspend fun startDeviceLogin(): DeviceCode {
        val response = http.postJson(
            "$AUTH_BASE/api/accounts/deviceauth/usercode",
            buildJsonObject { put("client_id", CLIENT_ID) }.toString(),
            mapOf("Accept" to "application/json"),
        )
        if (response.code !in 200..299) error("Codex sign-in could not start (${response.code})")
        val dto = json.decodeFromString<DeviceCodeDto>(response.body)
        return DeviceCode(
            verificationUrl = "$AUTH_BASE/codex/device",
            userCode = dto.userCode,
            deviceAuthId = dto.deviceAuthId,
            intervalSeconds = dto.interval?.let { value ->
                runCatching { value.jsonPrimitive.content.toLong() }.getOrNull()
            } ?: 5,
        )
    }

    suspend fun completeDeviceLogin(device: DeviceCode): CodexCredential {
        val deadline = System.currentTimeMillis() + 15 * 60 * 1000L
        var authorization: DeviceAuthorizationDto? = null
        while (authorization == null && System.currentTimeMillis() < deadline) {
            val response = http.postJson(
                "$AUTH_BASE/api/accounts/deviceauth/token",
                json.encodeToString(
                    DevicePollDto(deviceAuthId = device.deviceAuthId, userCode = device.userCode),
                ),
                mapOf("Accept" to "application/json"),
            )
            when {
                response.code in 200..299 -> authorization = json.decodeFromString(response.body)
                response.code == 403 || response.code == 404 -> delay(device.intervalSeconds.coerceAtLeast(1) * 1000L)
                else -> error("Codex authorization failed (${response.code})")
            }
        }
        val auth = authorization ?: error("Codex sign-in timed out")
        val response = http.postForm(
            "$AUTH_BASE/oauth/token",
            mapOf(
                "grant_type" to "authorization_code",
                "code" to auth.authorizationCode,
                "redirect_uri" to "$AUTH_BASE/deviceauth/callback",
                "client_id" to CLIENT_ID,
                "code_verifier" to auth.codeVerifier,
            ),
            mapOf("Accept" to "application/json"),
        )
        if (response.code !in 200..299) error("Codex token exchange failed (${response.code})")
        val token = json.decodeFromString<TokenDto>(response.body)
        val access = token.accessToken ?: error("Codex token exchange omitted access token")
        val refresh = token.refreshToken ?: error("Codex token exchange omitted refresh token")
        val credential = CodexCredential(
            accessToken = access,
            refreshToken = refresh,
            idToken = token.idToken,
            accountId = accountId(token.idToken, access),
        )
        credentials.saveCodex(credential)
        return credential
    }

    override fun disconnect() = credentials.clearCodex()

    private suspend fun requestUsage(credential: CodexCredential) = http.get(
        "https://chatgpt.com/backend-api/wham/usage",
        buildMap {
            put("Authorization", "Bearer ${credential.accessToken}")
            put("Accept", "application/json")
            put("User-Agent", "codex-cli")
            credential.accountId?.let { put("ChatGPT-Account-Id", it) }
        },
    )

    private suspend fun ensureFresh(credential: CodexCredential): CodexCredential {
        val exp = jwtPayload(credential.accessToken)?.get("exp")?.jsonPrimitive?.content?.toLongOrNull()
        return if (exp != null && exp <= Instant.now().epochSecond + 300) refresh(credential) else credential
    }

    private suspend fun refresh(credential: CodexCredential): CodexCredential {
        val response = http.postJson(
            "$AUTH_BASE/oauth/token",
            buildJsonObject {
                put("client_id", CLIENT_ID)
                put("grant_type", "refresh_token")
                put("refresh_token", credential.refreshToken)
            }.toString(),
            mapOf("Accept" to "application/json"),
        )
        if (response.code !in 200..299) error("Codex sign-in expired")
        val token = json.decodeFromString<TokenDto>(response.body)
        val access = token.accessToken ?: error("Codex refresh omitted access token")
        val updated = CodexCredential(
            accessToken = access,
            refreshToken = token.refreshToken ?: credential.refreshToken,
            idToken = token.idToken ?: credential.idToken,
            accountId = accountId(token.idToken ?: credential.idToken, access) ?: credential.accountId,
        )
        credentials.saveCodex(updated)
        return updated
    }

    private fun WindowDto.toDomain(index: Int): QuotaWindow {
        val seconds = limitWindowSeconds ?: 0L
        val id = when {
            seconds in 17_000L..19_000L -> "five_hour"
            seconds in 600_000L..610_000L -> "weekly"
            index == 0 -> "primary"
            else -> "secondary"
        }
        val label = when (id) {
            "five_hour" -> "5 hour"
            "weekly" -> "Weekly"
            "primary" -> "Primary"
            else -> "Secondary"
        }
        return QuotaWindow(
            id = id,
            label = label,
            usedPercent = (usedPercent ?: 0.0).coerceIn(0.0, 100.0),
            resetsAtEpochMillis = resetAt?.times(1000),
        )
    }

    private fun rank(window: QuotaWindow) = when (window.id) {
        "five_hour" -> 0
        "weekly" -> 1
        else -> 2
    }

    private fun accountId(idToken: String?, accessToken: String): String? =
        idToken?.let(::jwtPayload)?.findAccountId() ?: jwtPayload(accessToken).findAccountId()

    private fun JsonObject?.findAccountId(): String? {
        if (this == null) return null
        get("chatgpt_account_id")?.jsonPrimitive?.content?.let { return it }
        return get("https://api.openai.com/auth")?.jsonObject
            ?.get("chatgpt_account_id")?.jsonPrimitive?.content
    }

    private fun jwtPayload(token: String): JsonObject? = runCatching {
        val encoded = token.split('.').getOrNull(1) ?: return null
        val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
        val bytes = Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
        json.parseToJsonElement(String(bytes, Charsets.UTF_8)).jsonObject
    }.getOrNull()

    data class DeviceCode(
        val verificationUrl: String,
        val userCode: String,
        val deviceAuthId: String,
        val intervalSeconds: Long,
    )

    @Serializable private data class DeviceCodeDto(
        @SerialName("device_auth_id") val deviceAuthId: String,
        @SerialName("user_code") val userCode: String,
        val interval: JsonElement? = null,
    )
    @Serializable private data class DevicePollDto(
        @SerialName("device_auth_id") val deviceAuthId: String,
        @SerialName("user_code") val userCode: String,
    )
    @Serializable private data class DeviceAuthorizationDto(
        @SerialName("authorization_code") val authorizationCode: String,
        @SerialName("code_verifier") val codeVerifier: String,
    )
    @Serializable private data class TokenDto(
        @SerialName("id_token") val idToken: String? = null,
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
    )
    @Serializable private data class UsageDto(@SerialName("rate_limit") val rateLimit: RateLimitDto? = null)
    @Serializable private data class RateLimitDto(
        @SerialName("primary_window") val primaryWindow: WindowDto? = null,
        @SerialName("secondary_window") val secondaryWindow: WindowDto? = null,
    )
    @Serializable private data class WindowDto(
        @SerialName("used_percent") val usedPercent: Double? = null,
        @SerialName("limit_window_seconds") val limitWindowSeconds: Long? = null,
        @SerialName("reset_at") val resetAt: Long? = null,
    )

    companion object {
        const val AUTH_BASE = "https://auth.openai.com"
        const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    }
}
