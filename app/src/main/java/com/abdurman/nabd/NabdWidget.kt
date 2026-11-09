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
import org.json.JSONObject

class NabdWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        DebugLog.add(ctx, "NabdWidget", "onUpdate ids=${ids.size}")
        for (id in ids) update(ctx, mgr, id)
    }

    companion object {
        private val rowIds = mapOf(
            "work" to listOf(R.id.w_row_work_0, R.id.w_row_work_1, R.id.w_row_work_2),
            "life" to listOf(R.id.w_row_life_0, R.id.w_row_life_1, R.id.w_row_life_2),
            "late" to listOf(R.id.w_row_late_0, R.id.w_row_late_1, R.id.w_row_late_2)
        )
        private val stackIds = mapOf("work" to R.id.w_stack_work, "life" to R.id.w_stack_life, "late" to R.id.w_stack_late)
        private val emptyIds = mapOf("work" to R.id.w_empty_work, "life" to R.id.w_empty_life, "late" to R.id.w_empty_late)

        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, NabdWidget::class.java))
            for (id in ids) update(ctx, mgr, id)
        }

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            try {
                doUpdate(ctx, mgr, id)
            } catch (e: Exception) {
                DebugLog.add(ctx, "NabdWidget", "update failed", e)
            }
        }

        private fun remainText(t: JSONObject, now: Long): String {
            if (t.isNull("remindAt")) return "بدون موعد"
            val diff = t.optLong("remindAt") - now
            val min = 60_000L
            val hour = 3_600_000L
            val day = 86_400_000L
            return when {
                diff <= 0 -> "متأخر"
                diff < hour -> "${diff / min + 1} د"
                diff < day -> "${diff / hour + 1} س"
                else -> "${diff / day + 1} يوم"
            }
        }

        private fun doUpdate(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_nabd)
            val now = System.currentTimeMillis()
            val arr = TaskStore.load(ctx)
            val all = (0 until arr.length()).map { arr.getJSONObject(it) }
            val active = all.filter { !it.optBoolean("done") }

            val byCat = mapOf(
                "work" to active.filter { it.optString("cat") == "work" },
                "life" to active.filter { it.optString("cat") != "work" },
                "late" to active.filter { !it.isNull("remindAt") && it.optLong("remindAt") <= now }
            ).mapValues { (_, list) ->
                list.sortedBy { if (it.isNull("remindAt")) Long.MAX_VALUE else it.optLong("remindAt") }
            }

            views.setTextViewText(R.id.w_work_n, byCat.getValue("work").size.toString())
            views.setTextViewText(R.id.w_life_n, byCat.getValue("life").size.toString())
            views.setTextViewText(R.id.w_late_n, byCat.getValue("late").size.toString())

            for ((cat, list) in byCat) {
                val rows = rowIds.getValue(cat)
                for ((i, rowId) in rows.withIndex()) {
                    when {
                        i == 2 && list.size > 3 -> {
                            views.setTextViewText(rowId, "+${list.size - 2} أخرى")
                            views.setViewVisibility(rowId, View.VISIBLE)
                        }
                        i < list.size && !(i == 2 && list.size > 3) -> {
                            val t = list[i]
                            views.setTextViewText(rowId, "${t.optString("text")}  ·  ${remainText(t, now)}")
                            views.setViewVisibility(rowId, View.VISIBLE)
                        }
                        else -> views.setViewVisibility(rowId, View.GONE)
                    }
                }
                views.setViewVisibility(stackIds.getValue(cat), if (list.isEmpty()) View.GONE else View.VISIBLE)
                views.setViewVisibility(emptyIds.getValue(cat), if (list.isEmpty()) View.VISIBLE else View.GONE)
            }
            DebugLog.add(ctx, "NabdWidget", "rows work=${byCat["work"]!!.size} life=${byCat["life"]!!.size} late=${byCat["late"]!!.size}")

            val next = active
                .filter { !it.isNull("remindAt") && it.optLong("remindAt") > now }
                .minByOrNull { it.optLong("remindAt") }

            if (next != null) {
                val due = next.optLong("remindAt")
                val created = next.optLong("created", now)
                val span = (due - created).coerceAtLeast(1L)
                val pct = (((now - created).toDouble() / span) * 100).toInt().coerceIn(0, 100)
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

            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val debug = PendingIntent.getActivity(
                ctx, 2, Intent(ctx, DebugActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.w_root, open)
            views.setOnClickPendingIntent(R.id.w_plus, open)
            views.setOnClickPendingIntent(R.id.w_title, debug)

            mgr.updateAppWidget(id, views)
        }
    }
}
