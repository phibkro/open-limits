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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.limits.MainActivity
import app.limits.data.UsageStore
import app.limits.domain.ProviderId
import app.limits.domain.ProviderUsage
import app.limits.sync.UsageRefreshWorker
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

class LimitsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(180.dp, 80.dp), DpSize(260.dp, 120.dp), DpSize(340.dp, 180.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = UsageStore(context).getAll()
        provideContent {
            GlanceTheme {
                WidgetContent(data, LocalSize.current)
            }
        }
    }
}

@Composable
private fun WidgetContent(data: Map<ProviderId, ProviderUsage>, size: DpSize) {
    val compact = size.height < 110.dp
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .appWidgetBackground()
            .padding(if (compact) 12.dp else 16.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Limits",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "↻",
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 17.sp),
            )
        }
        if (!compact) Spacer(GlanceModifier.height(10.dp))
        ProviderId.entries.forEach { provider ->
            ProviderRow(provider.displayName, data[provider], size.width)
            if (!compact) Spacer(GlanceModifier.height(8.dp))
        }
    }
}

@Composable
private fun ProviderRow(name: String, usage: ProviderUsage?, width: Dp) {
    val primary = usage?.primaryWindow
    val percent = primary?.usedPercent?.coerceIn(0.0, 100.0)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.defaultWeight())
            Text(
                percent?.let { "${it.roundToInt()}%" } ?: "—",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            if (width >= 300.dp && primary?.resetsAtEpochMillis != null) {
                Spacer(GlanceModifier.width(8.dp))
                Text(formatReset(primary.resetsAtEpochMillis), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp))
            }
        }
        if (width >= 230.dp && percent != null) {
            Spacer(GlanceModifier.height(4.dp))
            ProgressBar(percent, width)
        }
    }
}

@Composable
private fun ProgressBar(percent: Double, totalWidth: Dp) {
    val available = (totalWidth.value - 34f).coerceAtLeast(80f)
    val filled = (available * (percent / 100.0)).toFloat().dp
    Box(
        modifier = GlanceModifier.fillMaxWidth().height(6.dp).background(GlanceTheme.colors.secondaryContainer),
    ) {
        Box(
            modifier = GlanceModifier.width(filled).height(6.dp).background(GlanceTheme.colors.primary),
        ) { }
    }
}

private fun formatReset(epochMillis: Long): String {
    val d = Duration.between(Instant.now(), Instant.ofEpochMilli(epochMillis))
    if (d.isNegative || d.isZero) return "now"
    val h = d.toHours()
    val m = d.toMinutes() % 60
    return when {
        h >= 48 -> "${h / 24}d"
        h >= 1 -> "${h}h"
        else -> "${m.coerceAtLeast(1)}m"
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        UsageRefreshWorker.refreshNow(context)
    }
}
