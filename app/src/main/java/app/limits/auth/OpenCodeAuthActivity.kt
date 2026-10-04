@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.limits.auth

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
import androidx.compose.material3.MaterialTheme
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
import app.limits.domain.ProviderId
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
                Scaffold(topBar = { TopAppBar(title = { Text("Connect OpenCode Go") }) }) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Paste the OpenCode Go API key used by your subscription. It is encrypted with Android Keystore on this phone.")
                        OutlinedTextField(
                            value = key,
                            onValueChange = { key = it },
                            label = { Text("API key") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(
                            onClick = {
                                lifecycleScope.launch {
                                    runCatching {
                                        require(key.isNotBlank()) { "Enter an API key" }
                                        ServiceLocator.repository.saveOpenCodeKey(key)
                                        ServiceLocator.repository.refresh(ProviderId.OPENCODE_GO)
                                    }.onSuccess { finish() }.onFailure { error = it.message }
                                }
                            },
                        ) { Text("Save") }
                    }
                }
            }
        }
    }
}
