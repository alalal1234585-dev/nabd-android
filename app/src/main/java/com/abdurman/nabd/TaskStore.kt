package com.abdurman.nabd

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

// يحفظ المهام، ويجدول تنبيهات النظام (تشتغل والجوال مقفل)، ويحدّث الـ widget
object TaskStore {
    private const val PREFS = "nabd"
    private const val KEY = "tasks"

    fun load(ctx: Context): JSONArray =
        try {
            JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }

    fun save(ctx: Context, json: String) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldJson = prefs.getString(KEY, "[]") ?: "[]"
        if (oldJson == json) return
        val newArr = try { JSONArray(json) } catch (e: Exception) { return }
        prefs.edit().putString(KEY, json).apply()
        cancelAll(ctx, try { JSONArray(oldJson) } catch (e: Exception) { JSONArray() })
        scheduleAll(ctx, newArr)
        NabdWidget.refreshAll(ctx)
    }

    fun reschedule(ctx: Context) = scheduleAll(ctx, load(ctx))

    fun find(ctx: Context, id: String): JSONObject? {
        val arr = load(ctx)
        for (i in 0 until arr.length()) {
            val t = arr.getJSONObject(i)
            if (t.optString("id") == id) return t
        }
        return null
    }

    private fun requestCode(id: String, kind: Int): Int = (id.hashCode() and 0x3fffffff) * 2 + kind

    private fun pendingFor(ctx: Context, t: JSONObject, kind: Int, noCreate: Boolean): PendingIntent? {
        val intent = Intent(ctx, ReminderReceiver::class.java)
            .putExtra("id", t.optString("id"))
            .putExtra("kind", kind)
            .putExtra("text", t.optString("text"))
        val flags = PendingIntent.FLAG_IMMUTABLE or
            (if (noCreate) PendingIntent.FLAG_NO_CREATE else PendingIntent.FLAG_UPDATE_CURRENT)
        return PendingIntent.getBroadcast(ctx, requestCode(t.optString("id"), kind), intent, flags)
    }

    private fun cancelAll(ctx: Context, arr: JSONArray) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until arr.length()) {
            val t = arr.getJSONObject(i)
            for (kind in 0..1) {
                pendingFor(ctx, t, kind, true)?.let { am.cancel(it) }
            }
        }
    }

    private fun scheduleAll(ctx: Context, arr: JSONArray) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        for (i in 0 until arr.length()) {
            val t = arr.getJSONObject(i)
            if (t.optBoolean("done") || t.isNull("remindAt")) continue
            val due = t.getLong("remindAt")
            val created = t.optLong("created", now)
            if (!t.optBoolean("warned")) {
                // تنبيه الاقتراب: عند ٨٠٪ من المدة
                val approach = created + ((due - created) * 0.8).toLong()
                scheduleOne(ctx, am, t, 0, adjustWork(t, approach), now)
            }
            if (!t.optBoolean("notified")) {
                scheduleOne(ctx, am, t, 1, adjustWork(t, due), now)
            }
        }
    }

    private fun scheduleOne(ctx: Context, am: AlarmManager, t: JSONObject, kind: Int, at: Long, now: Long) {
        val fire = if (at < now) now + 1000 else at
        val pi = pendingFor(ctx, t, kind, false) ?: return
        // ما يحتاج صلاحية alarm دقيقة، ويشتغل والجهاز في وضع النوم
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire, pi)
    }

    // تذكيرات الدوام: إذا الوقت خارج ٧ص إلى ٣م، تنتقل لـ ٧ الصبح
    private fun adjustWork(t: JSONObject, at: Long): Long {
        if (t.optString("cat") != "work") return at
        val c = Calendar.getInstance().apply { timeInMillis = at }
        val h = c.get(Calendar.HOUR_OF_DAY)
        if (h in 7 until 15) return at
        if (h >= 15) c.add(Calendar.DAY_OF_YEAR, 1)
        c.set(Calendar.HOUR_OF_DAY, 7)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
