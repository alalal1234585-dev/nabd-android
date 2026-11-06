package com.abdurman.nabd

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
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
            val now = System.currentTimeMillis()
            val arr = TaskStore.load(ctx)
            val all = (0 until arr.length()).map { arr.getJSONObject(it) }
            val active = all.filter { !it.optBoolean("done") }

            // العدادات الثلاثة
            val work = active.count { it.optString("cat") == "work" }
            val life = active.count { it.optString("cat") != "work" }
            val late = active.count { !it.isNull("remindAt") && it.getLong("remindAt") <= now }
            views.setTextViewText(R.id.w_work_n, work.toString())
            views.setTextViewText(R.id.w_life_n, life.toString())
            views.setTextViewText(R.id.w_late_n, late.toString())

            // أقرب مهمة موعدها لسه ما صار
            val next = active
                .filter { !it.isNull("remindAt") && it.getLong("remindAt") > now }
                .minByOrNull { it.getLong("remindAt") }

            if (next != null) {
                val due = next.getLong("remindAt")
                val created = next.optLong("created", now)
                val span = (due - created).coerceAtLeast(1L)
                val pct = (((now - created).toDouble() / span) * 100).toInt().coerceIn(0, 100)
                // العداد نظام Chronometer: يتحرك لوحده بدون تحديث الـ widget
                val base = SystemClock.elapsedRealtime() + (due - now)
                val catLabel = if (next.optString("cat") == "work") "دوام" else "خاص"
                views.setViewVisibility(R.id.w_count, View.VISIBLE)
                views.setViewVisibility(R.id.w_none, View.GONE)
                views.setChronometerCountDown(R.id.w_count, true)
                views.setChronometer(R.id.w_count, base, null, true)
                views.setTextViewText(R.id.w_next_lbl, "أقرب موعد · $catLabel")
                views.setTextViewText(R.id.w_next_text, next.optString("text"))
                views.setProgressBar(R.id.w_bar, 100, pct, false)
            } else {
                views.setViewVisibility(R.id.w_count, View.GONE)
                views.setViewVisibility(R.id.w_none, View.VISIBLE)
                views.setTextViewText(R.id.w_next_lbl, "ما فيه موعد قادم")
                val first = active.firstOrNull()
                views.setTextViewText(R.id.w_next_text, first?.optString("text") ?: "القائمة فاضية")
                views.setProgressBar(R.id.w_bar, 100, 0, false)
            }

            // الضغط على الـ widget أو الزر + يفتح التطبيق
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.w_root, open)
            views.setOnClickPendingIntent(R.id.w_plus, open)
            mgr.updateAppWidget(id, views)
        }
    }
}
