package com.abdurman.nabd

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.widget.RemoteViews

class NabdWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) update(ctx, mgr, id)
    }

    companion object {
        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, NabdWidget::class.java))
            for (id in ids) update(ctx, mgr, id)
        }

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_nabd)
            val arr = TaskStore.load(ctx)
            val items = (0 until arr.length())
                .map { arr.getJSONObject(it) }
                .filter { !it.optBoolean("done") }
                .sortedBy { if (it.isNull("remindAt")) Long.MAX_VALUE else it.getLong("remindAt") }
                .take(4)

            val body = StringBuilder()
            for (t in items) {
                val mark = if (t.optString("cat") == "work") "● " else "○ "
                body.append(mark).append(t.optString("text")).append("\n")
            }
            views.setTextViewText(R.id.w_title, "نبض · ${items.size} نشطة")
            views.setTextViewText(R.id.w_body, if (items.isEmpty()) "ما فيه مهام" else body.toString().trim())

            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.w_root, open)
            mgr.updateAppWidget(id, views)
        }
    }
}
