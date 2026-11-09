package com.abdurman.nabd

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.json.JSONObject

class StackService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory =
        StackFactory(applicationContext, intent.getStringExtra("cat") ?: "work")
}

class StackFactory(private val ctx: Context, private val kind: String) : RemoteViewsService.RemoteViewsFactory {
    private var items: List<JSONObject> = emptyList()
    private var firstCallLogged = false

    override fun onCreate() {
        DebugLog.add(ctx, "NabdStack", "onCreate $kind")
    }

    override fun onDataSetChanged() {
        try {
            val now = System.currentTimeMillis()
            val arr = TaskStore.load(ctx)
            val active = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { !it.optBoolean("done") }
            val filtered = when (kind) {
                "late" -> active.filter { !it.isNull("remindAt") && it.optLong("remindAt") <= now }
                "work" -> active.filter { it.optString("cat") == "work" }
                else -> active.filter { it.optString("cat") != "work" }
            }
            items = filtered.sortedBy { if (it.isNull("remindAt")) Long.MAX_VALUE else it.optLong("remindAt") }
            DebugLog.add(ctx, "NabdStack", "data $kind count=${items.size}")
        } catch (e: Exception) {
            DebugLog.add(ctx, "NabdStack", "data failed $kind", e)
            items = emptyList()
        }
    }

    override fun onDestroy() {}

    override fun getCount(): Int = items.size

    override fun getViewAt(pos: Int): RemoteViews {
        if (!firstCallLogged) {
            firstCallLogged = true
            DebugLog.add(ctx, "NabdStack", "getViewAt first call $kind pos=$pos")
        }
        return try {
            val rv = RemoteViews(ctx.packageName, R.layout.item_card)
            if (pos < 0 || pos >= items.size) return rv
            try {
                fillCard(rv, items[pos])
            } catch (e: Exception) {
                DebugLog.add(ctx, "NabdStack", "card failed $kind pos=$pos", e)
                rv.setTextViewText(R.id.c_text, "خطأ في عرض المهمة")
            }
            rv
        } catch (e: Exception) {
            DebugLog.add(ctx, "NabdStack", "getViewAt crashed $kind pos=$pos", e)
            RemoteViews(ctx.packageName, R.layout.item_card)
        }
    }

    private fun fillCard(rv: RemoteViews, t: JSONObject) {
        rv.setTextViewText(R.id.c_text, t.optString("text"))
        rv.setTextViewText(R.id.c_when, remain(t))
        val color = when (kind) {
            "late" -> 0xFFE0584B.toInt()
            "work" -> 0xFF2FA58C.toInt()
            else -> 0xFF8A6FD1.toInt()
        }
        rv.setInt(R.id.c_strip, "setBackgroundColor", color)
    }

    override fun getLoadingView(): RemoteViews =
        RemoteViews(ctx.packageName, R.layout.item_card).apply { setTextViewText(R.id.c_text, "...") }

    override fun getViewTypeCount(): Int = 1
    override fun getItemId(pos: Int): Long = pos.toLong()
    override fun hasStableIds(): Boolean = false

    private fun remain(t: JSONObject): String {
        if (t.isNull("remindAt")) return "بدون موعد"
        val diff = t.optLong("remindAt") - System.currentTimeMillis()
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
}
