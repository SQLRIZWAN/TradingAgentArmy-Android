package com.rizwan.tradingagentarmy.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url

@Serializable
data class ChatRequest(
    val message: String,
    val history: List<Map<String, String>> = emptyList()
)

@Serializable
data class ChatResponse(val reply: String? = null, val response: String? = null, val model: String? = null)

@Serializable
data class HealthResponse(val status: String = "ok", val model: String? = null)

@Serializable
data class PortfolioResponse(
    @SerialName("todayPnl") val todayPnl: Double = 0.0,
    @SerialName("totalPnl") val totalPnl: Double = 0.0,
    @SerialName("activeBots") val activeBots: Int = 0
)

@Serializable
data class BackendBot(
    val id: String,
    val name: String,
    val market: String = "",
    val strategy: String = "",
    val status: String = "STOPPED",
    val pnlToday: Double = 0.0,
    val pnlTotal: Double = 0.0,
    val gate1: Boolean = false,
    val gate2: Boolean = false,
    val gate3: Boolean = false,
    val version: Int = 1
)

interface BackendApiService {
    @GET
    suspend fun health(@Url url: String, @Header("Authorization") auth: String? = null): HealthResponse

    @POST
    suspend fun chat(
        @Url url: String,
        @Body body: ChatRequest,
        @Header("Authorization") auth: String? = null
    ): ChatResponse

    @GET
    suspend fun bots(@Url url: String, @Header("Authorization") auth: String? = null): List<BackendBot>

    @POST
    suspend fun deployBot(
        @Url url: String,
        @Header("Authorization") auth: String? = null
    ): retrofit2.Response<Unit>

    @GET
    suspend fun portfolio(@Url url: String, @Header("Authorization") auth: String? = null): PortfolioResponse

    @POST
    suspend fun pushToken(
        @Url url: String,
        @Body body: Map<String, String>,
        @Header("Authorization") auth: String? = null
    ): retrofit2.Response<Unit>
}
