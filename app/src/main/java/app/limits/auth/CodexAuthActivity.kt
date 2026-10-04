@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.limits.auth

import android.content.ClipData
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.limits.ServiceLocator
import app.limits.domain.ProviderId
import app.limits.providers.CodexProvider
import app.limits.ui.LimitsTheme

class CodexAuthActivity : ComponentActivity() {
    private var loopbackServer: CodexLoopbackServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(this)
        enableEdgeToEdge()

        setContent {
            LimitsTheme {
                var mode by remember { mutableStateOf<AuthMode?>(null) }
                var device by remember { mutableStateOf<CodexProvider.DeviceCode?>(null) }
                var error by remember { mutableStateOf<String?>(null) }
                var busy by remember { mutableStateOf(false) }
                var browserOpened by remember { mutableStateOf(false) }

                LaunchedEffect(mode) {
                    when (mode) {
                        AuthMode.BROWSER -> {
                            busy = true
                            error = null
                            runCatching {
                                val server = CodexLoopbackServer.bind().also {
                                    loopbackServer?.close()
                                    loopbackServer = it
                                }
                                val login = ServiceLocator.repository.codex
                                    .createBrowserLogin(server.redirectUri)

                                startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(login.authorizationUrl)),
                                )
                                browserOpened = true

                                val callback = server.awaitCallback(login.state)
                                ServiceLocator.repository.codex.completeBrowserLogin(
                                    login = login,
                                    code = callback.code,
                                    returnedState = callback.state,
                                )
                                ServiceLocator.repository.refresh(ProviderId.CODEX)
                                ServiceLocator.repository.refreshConnections()
                            }.onSuccess {
                                finish()
                            }.onFailure {
                                error = readableCodexAuthError(it)
                                busy = false
                            }
                        }

                        AuthMode.DEVICE -> {
                            busy = true
                            error = null
                            runCatching {
                                ServiceLocator.repository.codex.startDeviceLogin()
                            }.onSuccess {
                                device = it
                                busy = false
                            }.onFailure {
                                error = readableCodexAuthError(it)
                                busy = false
                            }
                        }

                        null -> Unit
                    }
                }

                LaunchedEffect(device?.deviceAuthId) {
                    if (mode != AuthMode.DEVICE) return@LaunchedEffect
                    val current = device ?: return@LaunchedEffect
                    busy = true
                    if (!browserOpened) {
                        runCatching {
                            startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(current.verificationUrl)),
                            )
                        }.onSuccess { browserOpened = true }
                    }

                    runCatching {
                        ServiceLocator.repository.codex.completeDeviceLogin(current)
                        ServiceLocator.repository.refresh(ProviderId.CODEX)
                        ServiceLocator.repository.refreshConnections()
                    }.onSuccess {
                        finish()
                    }.onFailure {
                        error = readableCodexAuthError(it)
                        busy = false
                    }
                }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("Connect Codex") }) },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (mode) {
                            null -> {
                                Text(
                                    "Sign in with your existing ChatGPT account.",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "Browser sign-in is recommended. Device code remains available as a fallback.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = {
                                        browserOpened = false
                                        mode = AuthMode.BROWSER
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Continue in browser")
                                }
                                OutlinedButton(
                                    onClick = {
                                        browserOpened = false
                                        mode = AuthMode.DEVICE
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Use device code")
                                }
                            }

                            AuthMode.BROWSER -> {
                                Text(
                                    if (browserOpened) {
                                        "Complete sign-in in your browser. Open Limits is waiting for the secure localhost callback."
                                    } else {
                                        "Preparing browser sign-in…"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (busy) CircularProgressIndicator()
                            }

                            AuthMode.DEVICE -> {
                                val current = device
                                if (current == null) {
                                    Text("Requesting a device code…")
                                    if (busy) CircularProgressIndicator()
                                } else {
                                    Text("Enter this code in the browser:")
                                    Text(
                                        current.userCode,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Button(onClick = {
                                        startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(current.verificationUrl),
                                            ),
                                        )
                                    }) {
                                        Text("Open sign-in")
                                    }
                                    OutlinedButton(onClick = {
                                        getSystemService(ClipboardManager::class.java)
                                            .setPrimaryClip(
                                                ClipData.newPlainText(
                                                    "Codex code",
                                                    current.userCode,
                                                ),
                                            )
                                    }) {
                                        Text("Copy code")
                                    }
                                    if (busy) {
                                        Text(
                                            "Waiting for authorization…",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }

                        error?.let { message ->
                            Text(message, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(
                                onClick = {
                                    loopbackServer?.close()
                                    loopbackServer = null
                                    device = null
                                    browserOpened = false
                                    error = null
                                    busy = false
                                    mode = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Choose another sign-in method")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        loopbackServer?.close()
        loopbackServer = null
        super.onDestroy()
    }

    private fun readableCodexAuthError(error: Throwable): String {
        val message = error.message ?: "Codex sign-in failed"
        return if (
            message.contains("Unable to resolve host", ignoreCase = true) ||
            message.contains("auth.openai.com", ignoreCase = true) &&
            message.contains("resolve", ignoreCase = true)
        ) {
            "Browser authorization may have succeeded, but Open Limits could not resolve auth.openai.com for the token exchange. This is an Android DNS/resolver issue rather than the OAuth method. Try Private DNS set to Automatic/off or temporarily disable VPN filtering, then retry."
        } else {
            message
        }
    }

    private enum class AuthMode {
        BROWSER,
        DEVICE,
    }
}
