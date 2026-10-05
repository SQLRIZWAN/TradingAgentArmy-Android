package com.rizwan.tradingagentarmy.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** One-shot channel: Bots screen FAB -> pre-filled Chat input. */
@Singleton
class CommandBus @Inject constructor() {
    private val _prefill = MutableStateFlow("")
    val prefill: StateFlow<String> = _prefill.asStateFlow()

    fun offer(text: String) {
        _prefill.value = text
    }

    fun consume() {
        _prefill.value = ""
    }
}
