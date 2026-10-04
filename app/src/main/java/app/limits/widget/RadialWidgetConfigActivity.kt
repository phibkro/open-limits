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
import app.limits.data.RadialWidgetConfig
import app.limits.data.RadialWidgetConfigStore
import app.limits.domain.ProviderId
import app.limits.ui.LimitsTheme
import kotlinx.coroutines.launch

class RadialWidgetConfigActivity : ComponentActivity() {
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

        val store = RadialWidgetConfigStore(this)
        val initial = store.get(appWidgetId)
        enableEdgeToEdge()

        setContent {
            LimitsTheme {
                var provider by remember { mutableStateOf(initial.provider) }
                var showReset by remember { mutableStateOf(initial.showReset) }
                var showPace by remember { mutableStateOf(initial.showPace) }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("Configure radial widget") }) },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Provider")
                        ProviderId.entries.forEach { choice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { provider = choice }
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = provider == choice,
                                    onClick = { provider = choice },
                                )
                                Text(choice.displayName)
                            }
                        }

                        ToggleRow("Show reset time", showReset) { showReset = it }
                        ToggleRow("Show pacing", showPace) { showPace = it }

                        Button(
                            onClick = {
                                store.save(
                                    appWidgetId,
                                    RadialWidgetConfig(
                                        provider = provider,
                                        showReset = showReset,
                                        showPace = showPace,
                                    ),
                                )
                                lifecycleScope.launch {
                                    val manager = GlanceAppWidgetManager(this@RadialWidgetConfigActivity)
                                    val glanceId = manager.getGlanceIdBy(appWidgetId)
                                    RadialLimitsWidget().update(this@RadialWidgetConfigActivity, glanceId)
                                    finishConfiguration()
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

    @androidx.compose.runtime.Composable
    private fun ToggleRow(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }

    private fun finishConfiguration() {
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
