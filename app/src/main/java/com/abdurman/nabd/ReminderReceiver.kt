package com.abdurman.nabd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val text = intent.getStringExtra("text") ?: "نبض"
        val kind = intent.getIntExtra("kind", 1)
        val t = TaskStore.find(ctx, id)
        // إذا المهمة انجزت أو انحذفت، ما نرسل شي
        if (t == null || t.optBoolean("done")) return
        val title = if (kind == 0) "اقترب موعد" else "انتهى وقت"
        Notifier.show(ctx, id, title, text)
    }
}
