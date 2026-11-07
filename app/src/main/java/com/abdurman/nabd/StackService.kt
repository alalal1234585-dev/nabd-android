package com.abdurman.nabd

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.json.JSONObject

// خدمة البطاقات المكدسة: كل نوع له مصدر بيانات خاص (من الـ Intent)
class StackService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory =
        StackFactory(applicationContext, intent.getStringExtra("cat") ?: "work")
}

class StackFactory(private val ctx: Context, private val kind: String) : RemoteViewsService.RemoteViewsFactory {
    private var items: List<JSONObject> = emptyList()

    override fun onCreate() {}

    // الترتيب دائماً من الأقرب موعداً، وهذا يرجع الترتيب كل ما تحدث البيانات
    override fun onDataSetChanged() {
        val now = System.currentTimeMillis()
        val arr = TaskStore.load(ctx)
        val active = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { !it.optBoolean("done") }
        val filtered = when (kind) {
            "late" -> active.filter { !it.isNull("remindAt") && it.optLong("remindAt") <= now }
            "work" -> active.filter { it.optString("cat") == "work" }
            else -> active.filter { it.optString("cat") != "work" }
        }
        items = filtered.sortedBy { if (it.isNull("remindAt")) Long.MAX_VALUE else it.optLong("remindAt") }
    }

    override fun onDestroy() {}

    override fun getCount(): Int = items.size

    override fun getViewAt(pos: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.item_card)
        if (pos < 0 || pos >= items.size) return rv
        try {
            fillCard(rv, items[pos])
        } catch (e: Exception) {
            Log.e("NabdStack", "card failed for $kind at $pos", e)
            rv.setTextViewText(R.id.c_text, "خطأ في عرض المهمة")
        }
        return rv
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

    override fun getLoadingView(): RemoteViews? = null
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
