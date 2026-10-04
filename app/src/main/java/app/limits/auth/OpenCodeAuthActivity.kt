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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.limits.ServiceLocator
import app.limits.ui.LimitsTheme
import kotlinx.coroutines.launch

class OpenCodeAuthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(this)
        enableEdgeToEdge()

        setContent {
            LimitsTheme {
                var key by remember { mutableStateOf("") }
                var error by remember { mutableStateOf<String?>(null) }
                var busy by remember { mutableStateOf(false) }

                fun pasteFromClipboard() {
                    val clipboard = getSystemService(ClipboardManager::class.java)
                    val text = clipboard.primaryClip
                        ?.getItemAt(0)
                        ?.coerceToText(this@OpenCodeAuthActivity)
                        ?.toString()
                        ?.trim()
                    if (!text.isNullOrBlank()) {
                        key = text
                        error = null
                    }
                }

                fun connect() {
                    if (busy) return
                    lifecycleScope.launch {
                        busy = true
                        error = null
                        runCatching {
                            ServiceLocator.repository.connectOpenCode(key)
                        }.onSuccess {
                            finish()
                        }.onFailure {
                            error = it.message ?: "OpenCode Go connection failed"
                            busy = false
                        }
                    }
                }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("Connect OpenCode Go") }) },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            "OpenCode Go currently uses an API key rather than an OAuth client flow.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Open your OpenCode account, sign in, copy the API key for your Go subscription, then return here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Button(
                            onClick = {
                                startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://opencode.ai/auth"),
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open OpenCode sign-in")
                        }

                        OutlinedButton(
                            onClick = ::pasteFromClipboard,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Paste key from clipboard")
                        }

                        OutlinedTextField(
                            value = key,
                            onValueChange = {
                                key = it
                                error = null
                            },
                            label = { Text("OpenCode Go API key") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }

                        Button(
                            enabled = !busy && key.isNotBlank(),
                            onClick = ::connect,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busy) CircularProgressIndicator()
                            else Text("Connect")
                        }
                    }
                }
            }
        }
    }
}
