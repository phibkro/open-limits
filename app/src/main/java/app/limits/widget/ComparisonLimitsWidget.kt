package app.limits.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.limits.MainActivity
import app.limits.data.ComparisonOrientation
import app.limits.data.ComparisonWidgetConfig
import app.limits.data.ComparisonWidgetConfigStore
import app.limits.data.UsageStore
import app.limits.domain.MeasurementKind
import app.limits.domain.NormalizedUsage
import app.limits.domain.ProviderId
import app.limits.domain.ProviderUsage
import app.limits.domain.normalizedMeasurements
import kotlin.math.roundToInt

class ComparisonLimitsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(260.dp, 140.dp),
            DpSize(340.dp, 180.dp),
            DpSize(420.dp, 220.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = ComparisonWidgetConfigStore(context).get(appWidgetId)
        val store = UsageStore(context)
        val data = store.getAll()
        val errors = store.getErrors()

        provideContent {
            GlanceTheme {
                ComparisonContent(
                    data = data,
                    errors = errors,
                    config = config,
                    size = LocalSize.current,
                )
            }
        }
    }
}

@Composable
private fun ComparisonContent(
    data: Map<ProviderId, ProviderUsage>,
    errors: Map<ProviderId, String>,
    config: ComparisonWidgetConfig,
    size: DpSize,
) {
    val providers = ProviderId.entries.filter { it in config.providers }
    val measurements = MeasurementKind.entries.filter { it in config.measurements }
    val normalized = data.mapValues { (_, usage) -> usage.normalizedMeasurements() }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .appWidgetBackground()
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            "Compare remaining",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.height(8.dp))

        if (providers.isEmpty() || measurements.isEmpty()) {
            Text(
                "Reconfigure this widget to select providers and limits.",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
            )
            return@Column
        }

        when (config.orientation) {
            ComparisonOrientation.PROVIDERS_AS_ROWS -> ProviderRowsMatrix(
                providers = providers,
                measurements = measurements,
                data = data,
                normalized = normalized,
                errors = errors,
                showBars = config.showBars,
                totalWidth = size.width,
            )

            ComparisonOrientation.PROVIDERS_AS_COLUMNS -> ProviderColumnsMatrix(
                providers = providers,
                measurements = measurements,
                data = data,
                normalized = normalized,
                errors = errors,
                showBars = config.showBars,
                totalWidth = size.width,
            )
        }
    }
}

@Composable
private fun ProviderRowsMatrix(
    providers: List<ProviderId>,
    measurements: List<MeasurementKind>,
    data: Map<ProviderId, ProviderUsage>,
    normalized: Map<ProviderId, Map<MeasurementKind, NormalizedUsage>>,
    errors: Map<ProviderId, String>,
    showBars: Boolean,
    totalWidth: Dp,
) {
    val labelWidth = 74.dp
    val cellWidth = ((totalWidth.value - labelWidth.value - 24f) / measurements.size)
        .coerceAtLeast(44f)
        .dp

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(GlanceModifier.width(labelWidth))
        measurements.forEach { measurement ->
            MatrixHeaderCell(measurement.shortLabel, cellWidth)
        }
    }
    Spacer(GlanceModifier.height(5.dp))

    providers.forEach { provider ->
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                providerShortName(provider),
                modifier = GlanceModifier.width(labelWidth),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            measurements.forEach { measurement ->
                MatrixValueCell(
                    usage = normalized[provider]?.get(measurement),
                    stale = data[provider]?.isStale() == true,
                    failed = provider in errors,
                    width = cellWidth,
                    showBar = showBars,
                )
            }
        }
        Spacer(GlanceModifier.height(if (showBars) 8.dp else 5.dp))
    }
}

@Composable
private fun ProviderColumnsMatrix(
    providers: List<ProviderId>,
    measurements: List<MeasurementKind>,
    data: Map<ProviderId, ProviderUsage>,
    normalized: Map<ProviderId, Map<MeasurementKind, NormalizedUsage>>,
    errors: Map<ProviderId, String>,
    showBars: Boolean,
    totalWidth: Dp,
) {
    val labelWidth = 54.dp
    val cellWidth = ((totalWidth.value - labelWidth.value - 24f) / providers.size)
        .coerceAtLeast(52f)
        .dp

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(GlanceModifier.width(labelWidth))
        providers.forEach { provider ->
            MatrixHeaderCell(providerShortName(provider), cellWidth)
        }
    }
    Spacer(GlanceModifier.height(5.dp))

    measurements.forEach { measurement ->
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                measurement.shortLabel,
                modifier = GlanceModifier.width(labelWidth),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            providers.forEach { provider ->
                MatrixValueCell(
                    usage = normalized[provider]?.get(measurement),
                    stale = data[provider]?.isStale() == true,
                    failed = provider in errors,
                    width = cellWidth,
                    showBar = showBars,
                )
            }
        }
        Spacer(GlanceModifier.height(if (showBars) 8.dp else 5.dp))
    }
}

@Composable
private fun MatrixHeaderCell(label: String, width: Dp) {
    Text(
        label,
        modifier = GlanceModifier.width(width),
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

@Composable
private fun MatrixValueCell(
    usage: NormalizedUsage?,
    stale: Boolean,
    failed: Boolean,
    width: Dp,
    showBar: Boolean,
) {
    Column(modifier = GlanceModifier.width(width)) {
        Text(
            usage?.let {
                buildString {
                    append(it.remainingPercent.roundToInt())
                    append("%")
                    if (stale || failed) append(" ·")
                }
            } ?: "—",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 10.sp,
            ),
        )

        if (showBar && usage != null) {
            Spacer(GlanceModifier.height(3.dp))
            val available = (width.value - 8f).coerceAtLeast(28f)
            val filled = (available * (usage.remainingPercent / 100.0)).toFloat().dp
            Box(
                modifier = GlanceModifier
                    .width(available.dp)
                    .height(4.dp)
                    .background(GlanceTheme.colors.secondaryContainer),
            ) {
                Box(
                    modifier = GlanceModifier
                        .width(filled)
                        .height(4.dp)
                        .background(GlanceTheme.colors.primary),
                ) { }
            }
        }
    }
}

private fun providerShortName(provider: ProviderId): String = when (provider) {
    ProviderId.CLAUDE -> "Claude"
    ProviderId.CODEX -> "Codex"
    ProviderId.OPENCODE_GO -> "OpenCode"
}
