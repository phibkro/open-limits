@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.limits.auth

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.limits.ServiceLocator
import app.limits.domain.ProviderId
import app.limits.providers.ClaudeProvider
import app.limits.ui.LimitsTheme
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.launch

class ClaudeAuthActivity : ComponentActivity() {
    private val verifier = randomUrlSafe(48)
    private val state = randomUrlSafe(24)
    private val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
    )

    private val authorizationUrl: String
        get() = "https://claude.ai/oauth/authorize?" +
            "code=true" +
            "&client_id=${ClaudeProvider.CLIENT_ID}" +
            "&response_type=code" +
            "&redirect_uri=${URLEncoder.encode(ClaudeProvider.REDIRECT_URI, "UTF-8")}" +
            "&scope=${URLEncoder.encode("org:create_api_key user:profile user:inference", "UTF-8")}" +
            "&state=$state" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(this)
        enableEdgeToEdge()

        setContent {
            LimitsTheme {
                var pastedCode by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                var browserOpened by remember { mutableStateOf(false) }

                fun openBrowser() {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl)))
                    }.onSuccess {
                        browserOpened = true
                        error = null
                    }.onFailure {
                        error = "Could not open the system browser"
                    }
                }

                fun completeSignIn() {
                    if (busy) return
                    val parsed = parseAuthorizationCode(pastedCode)
                    if (parsed == null) {
                        error = "Paste the authentication code shown by Claude after authorization"
                        return
                    }
                    val (code, returnedState) = parsed
                    if (returnedState != null && returnedState != state) {
                        error = "OAuth state did not match. Start sign-in again."
                        return
                    }

                    busy = true
                    error = null
                    lifecycleScope.launch {
                        runCatching {
                            ServiceLocator.repository.claude.exchangeAuthorizationCode(
                                code = code,
                                verifier = verifier,
                                redirectUri = ClaudeProvider.REDIRECT_URI,
                                state = state,
                            )
                            ServiceLocator.repository.refresh(ProviderId.CLAUDE)
                            ServiceLocator.repository.refreshConnections()
                        }.onSuccess {
                            finish()
                        }.onFailure {
                            error = it.message ?: "Claude sign-in failed"
                            busy = false
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    if (!browserOpened) openBrowser()
                }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("Connect Claude") }) },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            "Claude sign-in opens in your system browser so it can reuse your existing Claude and Google login.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "After you authorize Claude, the browser will show an authentication code. Copy that code, return here, and paste it below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        OutlinedButton(
                            onClick = ::openBrowser,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open Claude sign-in")
                        }

                        OutlinedTextField(
                            value = pastedCode,
                            onValueChange = { pastedCode = it },
                            label = { Text("Authentication code") },
                            supportingText = { Text("You can also paste the full callback URL.") },
                            singleLine = false,
                            visualTransformation = VisualTransformation.None,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedButton(
                            onClick = {
                                val clipboard = getSystemService(ClipboardManager::class.java)
                                val text = clipboard.primaryClip
                                    ?.getItemAt(0)
                                    ?.coerceToText(this@ClaudeAuthActivity)
                                    ?.toString()
                                if (!text.isNullOrBlank()) pastedCode = text
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Paste from clipboard")
                        }

                        Button(
                            enabled = !busy && pastedCode.isNotBlank(),
                            onClick = ::completeSignIn,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busy) {
                                CircularProgressIndicator()
                            } else {
                                Text("Complete sign-in")
                            }
                        }

                        error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    private fun parseAuthorizationCode(raw: String): Pair<String, String?>? {
        val value = raw.trim()
        if (value.isBlank()) return null

        if (value.startsWith("https://") || value.startsWith("http://")) {
            val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
            val rawCode = uri.getQueryParameter("code") ?: return null
            val code = rawCode.substringBefore('#').trim()
            val returnedState = uri.getQueryParameter("state")
                ?: rawCode.substringAfter('#', "").takeIf { it.isNotBlank() }
                ?: uri.fragment?.takeIf { it.isNotBlank() }
            return code.takeIf { it.isNotBlank() }?.let { it to returnedState }
        }

        val code = value.substringBefore('#').trim()
        val returnedState = value.substringAfter('#', "").trim().takeIf { it.isNotBlank() }
        return code.takeIf { it.isNotBlank() }?.let { it to returnedState }
    }

    companion object {
        private fun randomUrlSafe(bytes: Int): String {
            val value = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value)
        }
    }
}
