package app.limits.widget

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import app.limits.data.UsageStore
import app.limits.data.WidgetConfig
import app.limits.data.WidgetConfigStore
import app.limits.domain.ProviderId
import app.limits.domain.ProviderUsage
import app.limits.sync.UsageRefreshWorker
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

class LimitsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(180.dp, 80.dp), DpSize(260.dp, 120.dp), DpSize(340.dp, 180.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = UsageStore(context)
        val allData = store.getAll()
        val allErrors = store.getErrors()
        val offline = !hasValidatedNetwork(context)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = WidgetConfigStore(context).get(appWidgetId)

        val providers = config.provider?.let(::listOf) ?: ProviderId.entries
        val data = allData.filterKeys { it in providers }
        val errors = allErrors.filterKeys { it in providers }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    data = data,
                    errors = errors,
                    offline = offline,
                    providers = providers,
                    config = config,
                    size = LocalSize.current,
                )
            }
        }
    }
}

@Composable
private fun WidgetContent(
    data: Map<ProviderId, ProviderUsage>,
    errors: Map<ProviderId, String>,
    offline: Boolean,
    providers: List<ProviderId>,
    config: WidgetConfig,
    size: DpSize,
) {
    val compact = size.height < 110.dp
    val stale = data.values.any { it.isStale() }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .appWidgetBackground()
            .padding(if (compact) 12.dp else 16.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                config.provider?.displayName ?: "Limits",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(GlanceModifier.defaultWeight())
            if (!compact && (offline || stale || errors.isNotEmpty())) {
                Text(
                    when {
                        offline -> "offline"
                        errors.isNotEmpty() -> "sync issue"
                        else -> "stale"
                    },
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp,
                    ),
                )
                Spacer(GlanceModifier.width(8.dp))
            }
            Text(
                "↻",
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 17.sp),
            )
        }

        if (!compact) Spacer(GlanceModifier.height(10.dp))

        if (data.isEmpty()) {
            Text(
                if (config.provider == null) "Connect providers" else "No data yet",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (!compact) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    if (config.provider == null) {
                        "Tap to set up Claude, Codex or OpenCode Go"
                    } else {
                        "Tap to connect or refresh ${config.provider.displayName}"
                    },
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp,
                    ),
                )
            }
        } else {
            providers.forEach { provider ->
                ProviderRow(
                    name = provider.displayName,
                    usage = data[provider],
                    width = size.width,
                    hasRefreshError = errors.containsKey(provider),
                    detailed = config.detailed && !compact,
                    showName = config.provider == null,
                )
                if (config.provider == null && config.detailed && !compact) {
                    Spacer(GlanceModifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    name: String,
    usage: ProviderUsage?,
    width: Dp,
    hasRefreshError: Boolean,
    detailed: Boolean,
    showName: Boolean,
) {
    val primary = usage?.primaryWindow
    val percent = primary?.usedPercent?.coerceIn(0.0, 100.0)
    val stale = usage?.isStale() == true

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showName) {
                Text(
                    name,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.defaultWeight())
            }
            if (!showName && primary != null) {
                Text(
                    primary.label,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                )
                Spacer(GlanceModifier.defaultWeight())
            }
            Text(
                percent?.let {
                    buildString {
                        append("${it.roundToInt()}%")
                        if (hasRefreshError || stale) append(" ·")
                    }
                } ?: "—",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
            )
        }

        if (detailed && width >= 230.dp && percent != null) {
            Spacer(GlanceModifier.height(4.dp))
            ProgressBar(percent, width)
        }

        if (detailed && width >= 300.dp && primary != null) {
            val detail = buildList {
                primary.resetsAtEpochMillis?.let { add(formatReset(it)) }
                primary.paceRatio()?.let { add("${formatPace(it)} pace") }
                if (stale && usage != null) add(formatAge(usage.fetchedAtEpochMillis))
                if (hasRefreshError) add("last known")
            }.joinToString(" · ")

            if (detail.isNotBlank()) {
                Spacer(GlanceModifier.height(3.dp))
                Text(
                    detail,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 9.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ProgressBar(percent: Double, totalWidth: Dp) {
    val available = (totalWidth.value - 34f).coerceAtLeast(80f)
    val filled = (available * (percent / 100.0)).toFloat().dp
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(6.dp)
            .background(GlanceTheme.colors.secondaryContainer),
    ) {
        Box(
            modifier = GlanceModifier
                .width(filled)
                .height(6.dp)
                .background(GlanceTheme.colors.primary),
        ) { }
    }
}

private fun hasValidatedNetwork(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private fun formatPace(ratio: Double): String =
    String.format(Locale.US, "%.1f×", ratio.coerceAtMost(9.9))

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

private fun formatAge(epochMillis: Long): String {
    val duration = Duration.between(Instant.ofEpochMilli(epochMillis), Instant.now())
    val minutes = duration.toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m old"
        else -> "${minutes / 60}h old"
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        UsageRefreshWorker.refreshNow(context)
    }
}
