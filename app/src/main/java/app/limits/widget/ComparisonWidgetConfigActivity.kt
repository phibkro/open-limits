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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import app.limits.data.ComparisonOrientation
import app.limits.data.ComparisonWidgetConfig
import app.limits.data.ComparisonWidgetConfigStore
import app.limits.domain.MeasurementKind
import app.limits.domain.ProviderId
import app.limits.ui.LimitsTheme
import kotlinx.coroutines.launch

class ComparisonWidgetConfigActivity : ComponentActivity() {
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

        val store = ComparisonWidgetConfigStore(this)
        val initial = store.get(appWidgetId)
        enableEdgeToEdge()

        setContent {
            LimitsTheme {
                var orientation by remember { mutableStateOf(initial.orientation) }
                var providers by remember { mutableStateOf(initial.providers) }
                var measurements by remember { mutableStateOf(initial.measurements) }
                var showBars by remember { mutableStateOf(initial.showBars) }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("Configure comparison") }) },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Orientation")
                        OrientationChoice(
                            label = "Providers as rows",
                            selected = orientation == ComparisonOrientation.PROVIDERS_AS_ROWS,
                        ) { orientation = ComparisonOrientation.PROVIDERS_AS_ROWS }
                        OrientationChoice(
                            label = "Providers as columns",
                            selected = orientation == ComparisonOrientation.PROVIDERS_AS_COLUMNS,
                        ) { orientation = ComparisonOrientation.PROVIDERS_AS_COLUMNS }

                        Text("Providers")
                        ProviderId.entries.forEach { provider ->
                            CheckChoice(
                                label = provider.displayName,
                                checked = provider in providers,
                            ) { checked ->
                                providers = if (checked) providers + provider else providers - provider
                            }
                        }

                        Text("Usage measurements")
                        MeasurementKind.entries.forEach { measurement ->
                            CheckChoice(
                                label = "${measurement.shortLabel} · ${measurement.displayName}",
                                checked = measurement in measurements,
                            ) { checked ->
                                measurements = if (checked) {
                                    measurements + measurement
                                } else {
                                    measurements - measurement
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Show micro-bars")
                            Switch(
                                checked = showBars,
                                onCheckedChange = { showBars = it },
                            )
                        }

                        Button(
                            enabled = providers.isNotEmpty() && measurements.isNotEmpty(),
                            onClick = {
                                store.save(
                                    appWidgetId,
                                    ComparisonWidgetConfig(
                                        orientation = orientation,
                                        providers = providers,
                                        measurements = measurements,
                                        showBars = showBars,
                                    ),
                                )
                                lifecycleScope.launch {
                                    val manager = GlanceAppWidgetManager(this@ComparisonWidgetConfigActivity)
                                    val glanceId = manager.getGlanceIdBy(appWidgetId)
                                    ComparisonLimitsWidget().update(
                                        this@ComparisonWidgetConfigActivity,
                                        glanceId,
                                    )
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
    private fun OrientationChoice(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(label)
        }
    }

    @androidx.compose.runtime.Composable
    private fun CheckChoice(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(label)
        }
    }

    private fun finishConfiguration() {
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
