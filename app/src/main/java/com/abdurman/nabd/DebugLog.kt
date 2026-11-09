package com.abdurman.nabd

import android.content.Context
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// سجل داخلي يُحفظ على الجوال، ويتعرض في شاشة "مستكشف الأخطاء"
object DebugLog {
    private const val PREFS = "nabd_debug"
    private const val KEY = "log"
    private const val MAX = 12000

    fun add(ctx: Context, tag: String, msg: String, e: Throwable? = null) {
        Log.e(tag, msg, e)
        try {
            val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
            val trace = if (e == null) "" else
                "\n  ${e.javaClass.simpleName}: ${e.message}\n" +
                e.stackTrace.take(5).joinToString("\n") { "    at $it" }
            val line = "$time [$tag] $msg$trace\n"
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val old = p.getString(KEY, "") ?: ""
            p.edit().putString(KEY, (old + line).takeLast(MAX)).apply()
        } catch (ignored: Exception) {
        }
    }

    fun read(ctx: Context): String {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val header = "الجهاز: ${Build.MANUFACTURER} ${Build.MODEL} | أندرويد ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
            "----------------------------------------\n"
        val body = p.getString(KEY, "") ?: ""
        return header + (if (body.isEmpty()) "ما فيه سجلات بعد" else body)
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
