package com.rizwan.tradingagentarmy.agents

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppEvents {
    private val log = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun record(type: String, detail: String) {
        synchronized(log) {
            log.addLast("${fmt.format(Date())} [$type] $detail")
            while (log.size > 60) log.removeFirst()
        }
    }

    fun recentLog(): String = synchronized(log) { log.joinToString("\n") }

    fun clear() = synchronized(log) { log.clear() }
}
