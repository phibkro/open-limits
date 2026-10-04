package app.limits

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.limits.auth.ClaudeAuthActivity
import app.limits.auth.CodexAuthActivity
import app.limits.auth.OpenCodeAuthActivity
import app.limits.domain.ProviderId
import app.limits.ui.HomeScreen
import app.limits.ui.LimitsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(this)
        enableEdgeToEdge()
        setContent {
            LimitsTheme {
                val vm: MainViewModel = viewModel()
                val snapshots = vm.snapshots.collectAsStateWithLifecycle().value
                val connected = vm.connections.collectAsStateWithLifecycle().value
                val errors = vm.errors.collectAsStateWithLifecycle().value
                val refreshing = vm.refreshing.collectAsStateWithLifecycle().value
                HomeScreen(
                    snapshots = snapshots,
                    connected = connected,
                    errors = errors,
                    refreshing = refreshing,
                    onRefresh = vm::refresh,
                    onConnect = { provider ->
                        val clazz = when (provider) {
                            ProviderId.CLAUDE -> ClaudeAuthActivity::class.java
                            ProviderId.CODEX -> CodexAuthActivity::class.java
                            ProviderId.OPENCODE_GO -> OpenCodeAuthActivity::class.java
                        }
                        startActivity(Intent(this, clazz))
                    },
                    onDisconnect = vm::disconnect,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ServiceLocator.init(this)
        ServiceLocator.repository.refreshConnections()
    }
}
