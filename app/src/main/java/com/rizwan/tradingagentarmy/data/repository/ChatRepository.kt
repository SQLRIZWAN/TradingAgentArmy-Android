package com.rizwan.tradingagentarmy.data.repository

import com.rizwan.tradingagentarmy.data.firebase.FirebaseSync
import com.rizwan.tradingagentarmy.data.local.ChatDao
import com.rizwan.tradingagentarmy.data.local.ChatMessageEntity
import com.rizwan.tradingagentarmy.data.remote.ChatTurn
import com.rizwan.tradingagentarmy.domain.model.AiResult
import com.rizwan.tradingagentarmy.domain.model.ChatMessage
import com.rizwan.tradingagentarmy.domain.model.MessageRole
import com.rizwan.tradingagentarmy.domain.usecase.GetAiResponseUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val dao: ChatDao,
    private val ai: GetAiResponseUseCase,
    private val firebase: FirebaseSync
) {
    fun messages(): Flow<List<ChatMessage>> = dao.observeAll().map { list ->
        list.map {
            ChatMessage(
                id = it.id,
                role = runCatching { MessageRole.valueOf(it.role) }.getOrDefault(MessageRole.AI),
                content = it.content,
                model = it.model,
                timestamp = it.timestamp
            )
        }
    }

    /** Ascending conversation turns straight from Room (last = newest). */
    suspend fun historyTurns(): List<ChatTurn> =
        dao.recent().asReversed().map {
            ChatTurn(if (it.role == "USER") "user" else "assistant", it.content)
        }

    suspend fun addUser(text: String): Long =
        dao.insert(
            ChatMessageEntity(role = "USER", content = text, model = null, timestamp = System.currentTimeMillis())
        )

    suspend fun addPendingAi(): Long =
        dao.insert(
            ChatMessageEntity(role = "AI", content = "", model = null, timestamp = System.currentTimeMillis())
        )

    /**
     * Streams an assistant reply into row [aiId].
     * [historyParam] must already exclude the current user turn.
     */
    suspend fun streamReply(
        userText: String,
        historyParam: List<ChatTurn>,
        aiId: Long,
        onDelta: suspend (String) -> Unit
    ): AiResult {
        val result = ai.stream(userText, historyParam, onDelta)
        dao.updateContent(aiId, result.text, result.model)
        firebase.pushChat(
            ChatMessageEntity(
                id = aiId, role = "AI", content = result.text,
                model = result.model, timestamp = System.currentTimeMillis()
            )
        )
        return result
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clear() = dao.clear()

    suspend fun count() = dao.count()
}
