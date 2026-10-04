package app.limits.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.glance.layout.ContentScale
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import app.limits.MainActivity
import app.limits.data.RadialWidgetConfigStore
import app.limits.data.UsageStore
import app.limits.domain.MeasurementKind
import app.limits.domain.NormalizedUsage
import app.limits.domain.ProviderUsage
import app.limits.domain.normalizedMeasurements
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class RadialLimitsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(140.dp, 140.dp),
            DpSize(180.dp, 180.dp),
            DpSize(240.dp, 240.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = RadialWidgetConfigStore(context).get(appWidgetId)
        val usage = UsageStore(context).get(config.provider)

        provideContent {
            GlanceTheme {
                RadialContent(
                    context = context,
                    usage = usage,
                    size = LocalSize.current,
                    showReset = config.showReset,
                    showPace = config.showPace,
                )
            }
        }
    }
}

@Composable
private fun RadialContent(
    context: Context,
    usage: ProviderUsage?,
    size: DpSize,
    showReset: Boolean,
    showPace: Boolean,
) {
    val density = context.resources.displayMetrics.density
    val width = (size.width.value * density).roundToInt().coerceAtLeast(1)
    val height = (size.height.value * density).roundToInt().coerceAtLeast(1)
    val bitmap = renderRadialWidgetBitmap(
        context = context,
        usage = usage,
        width = width,
        height = height,
        showReset = showReset,
        showPace = showPace,
    )

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .appWidgetBackground()
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = usage?.provider?.displayName ?: "Limits radial widget",
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier.fillMaxSize(),
        )
    }
}

private fun renderRadialWidgetBitmap(
    context: Context,
    usage: ProviderUsage?,
    width: Int,
    height: Int,
    showReset: Boolean,
    showPace: Boolean,
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val density = context.resources.displayMetrics.density
    val cx = width / 2f
    val cy = height / 2f
    val diameter = min(width, height).toFloat()
    val padding = 16f * density
    val baseRadius = diameter / 2f - padding

    val trackColor = context.getColor(android.R.color.system_neutral1_300)
    val textColor = context.getColor(android.R.color.system_neutral1_900)
    val secondaryText = context.getColor(android.R.color.system_neutral2_600)
    val ringColors = intArrayOf(
        context.getColor(android.R.color.system_accent1_500),
        context.getColor(android.R.color.system_accent2_500),
        context.getColor(android.R.color.system_accent3_500),
    )

    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = trackColor
        alpha = 90
    }
    val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    val checkpointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = textColor
    }

    val normalized = usage?.normalizedMeasurements().orEmpty()
    val ordered = listOf(
        MeasurementKind.TOTAL,
        MeasurementKind.WEEKLY,
        MeasurementKind.SHORT,
    ).mapNotNull { kind -> normalized[kind] }

    val ringCount = ordered.size.coerceAtLeast(1)
    val stroke = when (ringCount) {
        1 -> diameter * 0.055f
        2 -> diameter * 0.045f
        else -> diameter * 0.038f
    }
    val ringGap = stroke * 1.75f

    if (ordered.isEmpty()) {
        textPaint.textSize = diameter * 0.095f
        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        canvas.drawText("No data", cx, cy, textPaint)
        textPaint.textSize = diameter * 0.05f
        textPaint.typeface = android.graphics.Typeface.DEFAULT
        textPaint.color = secondaryText
        canvas.drawText("Tap to connect", cx, cy + diameter * 0.1f, textPaint)
        return bitmap
    }

    ordered.forEachIndexed { index, measurement ->
        val radius = baseRadius - index * ringGap
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke
        arcPaint.color = ringColors[index % ringColors.size]

        canvas.drawArc(rect, -90f, 360f, false, trackPaint)
        canvas.drawArc(
            rect,
            -90f,
            (measurement.remainingPercent / 100.0 * 360.0).toFloat(),
            false,
            arcPaint,
        )

        drawOctagonalCheckpoints(
            canvas = canvas,
            cx = cx,
            cy = cy,
            radius = radius,
            size = stroke * 0.42f,
            activeColor = arcPaint.color,
            inactiveColor = trackColor,
            remainingPercent = measurement.remainingPercent,
            paint = checkpointPaint,
        )
    }

    val primary = normalized[MeasurementKind.SHORT]
        ?: normalized[MeasurementKind.WEEKLY]
        ?: normalized[MeasurementKind.TOTAL]
        ?: ordered.last()

    textPaint.color = textColor
    textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    textPaint.textSize = diameter * 0.085f
    canvas.drawText(usage!!.provider.displayName, cx, cy - diameter * 0.045f, textPaint)

    textPaint.textSize = diameter * 0.12f
    canvas.drawText(
        "${primary.remainingPercent.roundToInt()}%",
        cx,
        cy + diameter * 0.075f,
        textPaint,
    )

    textPaint.color = secondaryText
    textPaint.typeface = android.graphics.Typeface.DEFAULT
    textPaint.textSize = diameter * 0.047f
    canvas.drawText("left", cx, cy + diameter * 0.135f, textPaint)

    val detail = buildList {
        if (showReset) primary.resetsAtEpochMillis?.let { add(formatRadialReset(it)) }
        if (showPace) findSourceWindow(usage, primary)?.paceRatio()?.let {
            add(String.format(java.util.Locale.US, "%.1f× pace", it.coerceAtMost(9.9)))
        }
    }.joinToString(" · ")

    if (detail.isNotBlank() && diameter >= 180f * density) {
        textPaint.textSize = diameter * 0.037f
        canvas.drawText(detail, cx, cy + diameter * 0.205f, textPaint)
    }

    return bitmap
}

private fun findSourceWindow(
    usage: ProviderUsage,
    normalized: NormalizedUsage,
) = usage.windows.firstOrNull { it.id == normalized.sourceWindowId }

private fun drawOctagonalCheckpoints(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    radius: Float,
    size: Float,
    activeColor: Int,
    inactiveColor: Int,
    remainingPercent: Double,
    paint: Paint,
) {
    repeat(8) { checkpoint ->
        val angleDegrees = -90.0 + checkpoint * 45.0
        val angle = angleDegrees / 180.0 * PI
        val x = cx + cos(angle).toFloat() * radius
        val y = cy + sin(angle).toFloat() * radius
        val threshold = checkpoint * 12.5
        paint.color = if (remainingPercent + 0.001 >= threshold) activeColor else inactiveColor
        paint.alpha = if (remainingPercent + 0.001 >= threshold) 255 else 110
        canvas.drawPath(octagonPath(x, y, size), paint)
    }
}

private fun octagonPath(cx: Float, cy: Float, radius: Float): Path =
    Path().apply {
        repeat(8) { index ->
            val angle = (-22.5 + index * 45.0) / 180.0 * PI
            val x = cx + cos(angle).toFloat() * radius
            val y = cy + sin(angle).toFloat() * radius
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

private fun formatRadialReset(epochMillis: Long): String {
    val duration = Duration.between(Instant.now(), Instant.ofEpochMilli(epochMillis))
    if (duration.isNegative || duration.isZero) return "reset now"
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    return when {
        hours >= 48 -> "reset ${hours / 24}d"
        hours >= 1 -> "reset ${hours}h"
        else -> "reset ${minutes.coerceAtLeast(1)}m"
    }
}
