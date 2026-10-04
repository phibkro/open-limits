package app.limits.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import app.limits.data.ComparisonWidgetConfigStore

class ComparisonLimitsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ComparisonLimitsWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = ComparisonWidgetConfigStore(context)
        appWidgetIds.forEach(store::clear)
    }
}
