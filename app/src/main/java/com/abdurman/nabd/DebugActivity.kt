package com.abdurman.nabd

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// شاشة مستكشف الأخطاء: تعرض التقرير مع زر نسخ
class DebugActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reportView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(32, 32, 32, 32)
        }
        fun refresh() {
            reportView.text = DebugLog.read(this)
        }
        val copyBtn = Button(this).apply {
            text = "نسخ التقرير"
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("nabd-report", DebugLog.read(this@DebugActivity)))
                Toast.makeText(this@DebugActivity, "تم نسخ التقرير", Toast.LENGTH_SHORT).show()
            }
        }
        val refreshBtn = Button(this).apply {
            text = "تحديث"
            setOnClickListener { refresh() }
        }
        val clearBtn = Button(this).apply {
            text = "مسح"
            setOnClickListener {
                DebugLog.clear(this@DebugActivity)
                refresh()
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(copyBtn)
            addView(refreshBtn)
            addView(clearBtn)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(ScrollView(this@DebugActivity).apply { addView(reportView) })
        }
        setContentView(root)
        refresh()
    }
}
