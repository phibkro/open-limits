package app.limits.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ClaudeCredential(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtEpochSeconds: Long? = null,
)

@Serializable
data class CodexCredential(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String? = null,
    val accountId: String? = null,
)

@Serializable
data class OpenCodeCredential(val apiKey: String)

class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("encrypted_credentials", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun saveClaude(value: ClaudeCredential) = put(KEY_CLAUDE, json.encodeToString(value))
    fun getClaude(): ClaudeCredential? = get(KEY_CLAUDE)?.let { runCatching { json.decodeFromString<ClaudeCredential>(it) }.getOrNull() }

    fun saveCodex(value: CodexCredential) = put(KEY_CODEX, json.encodeToString(value))
    fun getCodex(): CodexCredential? = get(KEY_CODEX)?.let { runCatching { json.decodeFromString<CodexCredential>(it) }.getOrNull() }

    fun saveOpenCode(value: OpenCodeCredential) = put(KEY_OPENCODE, json.encodeToString(value))
    fun getOpenCode(): OpenCodeCredential? = get(KEY_OPENCODE)?.let { runCatching { json.decodeFromString<OpenCodeCredential>(it) }.getOrNull() }

    fun clear(key: String) { prefs.edit().remove(key).apply() }
    fun clearClaude() = clear(KEY_CLAUDE)
    fun clearCodex() = clear(KEY_CODEX)
    fun clearOpenCode() = clear(KEY_OPENCODE)

    private fun put(key: String, plaintext: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        prefs.edit().putString(key, encoded).apply()
    }

    private fun get(key: String): String? {
        val encoded = prefs.getString(key, null) ?: return null
        return runCatching {
            val (ivPart, cipherPart) = encoded.split(':', limit = 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(ivPart, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(cipherPart, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        private const val KEY_ALIAS = "limits-credentials-v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_CLAUDE = "claude"
        private const val KEY_CODEX = "codex"
        private const val KEY_OPENCODE = "opencode_go"
    }
}
