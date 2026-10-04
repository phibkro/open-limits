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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(this)
        enableEdgeToEdge()

        setContent {
            LimitsTheme {
                var device by remember { mutableStateOf<CodexProvider.DeviceCode?>(null) }
                var error by remember { mutableStateOf<String?>(null) }
                var busy by remember { mutableStateOf(false) }
                var browserOpened by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    runCatching { ServiceLocator.repository.codex.startDeviceLogin() }
                        .onSuccess { device = it }
                        .onFailure { error = it.message }
                }

                LaunchedEffect(device?.deviceAuthId) {
                    val current = device ?: return@LaunchedEffect
                    busy = true
                    if (!browserOpened) {
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current.verificationUrl)))
                        }.onSuccess { browserOpened = true }
                    }
                    runCatching {
                        ServiceLocator.repository.codex.completeDeviceLogin(current)
                        ServiceLocator.repository.refresh(ProviderId.CODEX)
                        ServiceLocator.repository.refreshConnections()
                    }.onSuccess {
                        finish()
                    }.onFailure {
                        error = it.message ?: "Codex sign-in failed"
                        busy = false
                    }
                }

                Scaffold(topBar = { TopAppBar(title = { Text("Connect Codex") }) }) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        val current = device
                        if (current == null && error == null) {
                            CircularProgressIndicator()
                        } else if (current != null) {
                            Text("Enter this code in the browser:")
                            Text(
                                current.userCode,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Button(onClick = {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current.verificationUrl)))
                            }) {
                                Text("Open sign-in")
                            }
                            OutlinedButton(onClick = {
                                getSystemService(ClipboardManager::class.java)
                                    .setPrimaryClip(ClipData.newPlainText("Codex code", current.userCode))
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
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
