package com.rizwan.tradingagentarmy

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * Forensic uncaught-exception hook.
 *
 * The report is written to filesDir/crash.txt, echoed to Logcat (tag FATAL) and
 * handed to [CrashActivity] which runs in its own process so it survives the death
 * of the main process. Screen shows the raw stack trace for a screenshot.
 */
object CrashCatcher {
    private const val TAG = "FATAL"
    private const val FILE_NAME = "crash.txt"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val trace = buildString {
                append("app: TradingAgentArmy\n")
                append("package: ").append(app.packageName).append('\n')
                append("version: ").append(runCatching {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName
                }.getOrNull()).append('\n')
                append("thread: ").append(thread.name).append('\n')
                append("time: ").append(System.currentTimeMillis()).append('\n')
                append("----\n")
                append(throwable.stackTraceToString())
            }

            Log.e(TAG, "uncaught exception", throwable)
            runCatching { File(app.filesDir, FILE_NAME).writeText(trace) }

            runCatching {
                val intent = Intent(app, CrashActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(CrashActivity.EXTRA_TRACE, trace)
                app.startActivity(intent)
            }

            // The report lives in another process; keep this one alive just long
            // enough for ActivityManager to hand it over, then die as usual.
            runCatching { Thread.sleep(700) }
            Handler(Looper.getMainLooper()).post { }

            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Runtime.getRuntime().halt(1)
            }
        }
    }
}
