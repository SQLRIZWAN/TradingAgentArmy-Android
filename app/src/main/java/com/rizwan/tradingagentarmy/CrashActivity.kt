package com.rizwan.tradingagentarmy

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Runs in the `:crash` process so it is still alive after the main process dies.
 * Shows the raw stack trace — screenshot it and share.
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val trace = intent.getStringExtra(EXTRA_TRACE)
            ?: runCatching { File(filesDir, "crash.txt").readText() }.getOrNull()
            ?: "No crash trace captured. Check logcat: adb logcat -s FATAL AndroidRuntime:E"

        val header = TextView(this).apply {
            text = "⚠️ App crash — ye stack trace hai\n\nLong-press text karke copy karo ya screenshot le lo."
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.START
            setPadding(dp(16), dp(20), dp(16), dp(8))
        }

        val body = TextView(this).apply {
            text = trace
            setTextIsSelectable(true)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.parseColor("#FFB86B"))
            setPadding(dp(16), 0, dp(16), dp(24))
        }

        val copy = TextView(this).apply {
            text = "📋 Copy crash log"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1E2530"))
            setPadding(dp(12), dp(14), dp(12), dp(14))
            setOnClickListener { copy(trace) }
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0C0F"))
            addView(header)
            addView(copy, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), 0, dp(16), dp(8)) })
            addView(body)
        }

        setContentView(ScrollView(this).apply { addView(column) })
    }

    private fun copy(trace: String) {
        getSystemService(Context.CLIPBOARD_SERVICE)
            ?.let { it as? ClipboardManager }
            ?.setPrimaryClip(ClipData.newPlainText("crash", trace))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_TRACE = "trace"
    }
}
