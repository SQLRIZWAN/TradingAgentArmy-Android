package com.rizwan.tradingagentarmy.agents

/**
 * Parses operator commands from the Army Chat into a typed [Command].
 * Execution lives in [AgentArmy] so the dependency graph stays acyclic.
 */
sealed class Command {
    data object Help : Command()
    data object Hello : Command()
    data object StartArmy : Command()
    data object StopArmy : Command()
    data class RunRound(val brief: String) : Command()
    data class Scalp(val on: Boolean) : Command()
    data class SetSymbol(val symbol: String) : Command()
    data class Kill(val on: Boolean) : Command()
    data object RiskStatus : Command()
    data object Positions : Command()
    data object Bots : Command()
    data object Status : Command()
}

object Commander {

    private val startWords = listOf("start army", "start 24/7", "start service", "army start", "chalu karo", "start karo", "army on")
    private val stopWords = listOf("stop army", "stop service", "army stop", "band karo", "stop karo", "army off")

    fun parse(raw: String): Command? {
        val text = raw.trim()
        val q = text.lowercase()
        if (q.isBlank()) return null

        if (q == "help" || q == "?" || q.contains("command")) return Command.Help
        if (q == "hi" || q == "hello" || q == "hey" || q.startsWith("hi ") || q.startsWith("hello ")) return Command.Hello
        if (startWords.any { q == it || q.startsWith(it) }) return Command.StartArmy
        if (stopWords.any { q == it || q.startsWith(it) }) return Command.StopArmy

        if (q.contains("run round") || q == "round" || q.startsWith("round ") ||
            q.contains("analysis karo") || q.contains("analyse") || q.contains("analyze")
        ) return Command.RunRound(if (text.length > 18) text else "")

        if (q.contains("scalp")) {
            val off = q.contains("off") || q.contains("band") || q.contains("disable")
            return Command.Scalp(!off)
        }

        Regex("(?:set\\s+)?symbol\\s+([A-Za-z0-9/._-]{3,20})").find(q)?.let {
            return Command.SetSymbol(it.groupValues[1].uppercase().replace("/", ""))
        }

        if (q.contains("kill")) {
            val off = q.contains("off") || q.contains("disable") || q.contains("band")
            return Command.Kill(!off)
        }

        if (q.contains("risk") && (q.contains("status") || q.contains("report") || q.contains("dikhao")))
            return Command.RiskStatus

        if (q.contains("position") || q.contains("open trade") || q.contains("holdings"))
            return Command.Positions

        if (q == "bots" || q.contains("bot status") || q.contains("list bots"))
            return Command.Bots

        if (q == "status" || q.contains("kya chal raha"))
            return Command.Status

        return null
    }
}
