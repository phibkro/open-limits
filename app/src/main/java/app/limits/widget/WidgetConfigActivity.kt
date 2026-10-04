@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.limits.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import app.limits.data.WidgetConfig
import app.limits.data.WidgetConfigStore
import app.limits.domain.ProviderId
import app.limits.ui.LimitsTheme
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val store = WidgetConfigStore(this)
        val initial = store.get(appWidgetId)

        enableEdgeToEdge()
        setContent {
            LimitsTheme {
                var selectedProvider by remember { mutableStateOf(initial.provider) }
                var detailed by remember { mutableStateOf(initial.detailed) }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("Configure widget") }) },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Provider")

                        val choices: List<Pair<ProviderId?, String>> = listOf(
                            null to "All providers",
                            ProviderId.CLAUDE to "Claude",
                            ProviderId.CODEX to "Codex",
                            ProviderId.OPENCODE_GO to "OpenCode Go",
                        )
                        choices.forEach { (provider, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedProvider = provider }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedProvider == provider,
                                    onClick = { selectedProvider = provider },
                                )
                                Text(label)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text("Detailed")
                                Text("Show bars, reset times and pacing")
                            }
                            Switch(
                                checked = detailed,
                                onCheckedChange = { detailed = it },
                            )
                        }

                        Button(
                            onClick = {
                                store.save(
                                    appWidgetId,
                                    WidgetConfig(
                                        provider = selectedProvider,
                                        detailed = detailed,
                                    ),
                                )
                                lifecycleScope.launch {
                                    val manager = GlanceAppWidgetManager(this@WidgetConfigActivity)
                                    val glanceId = manager.getGlanceIdBy(appWidgetId)
                                    LimitsWidget().update(this@WidgetConfigActivity, glanceId)

                                    val result = Intent().putExtra(
                                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                                        appWidgetId,
                                    )
                                    setResult(Activity.RESULT_OK, result)
                                    finish()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
