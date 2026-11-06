package com.abdurman.nabd

import android.content.Context
import android.webkit.JavascriptInterface

// الجسر بين الصفحة (JavaScript) والكود الأصلي
class Bridge(private val ctx: Context) {
    @JavascriptInterface
    fun saveTasks(json: String) {
        TaskStore.save(ctx, json)
    }

    @JavascriptInterface
    fun showNotice(title: String, body: String) {
        Notifier.show(ctx, body, title, body)
    }
}
