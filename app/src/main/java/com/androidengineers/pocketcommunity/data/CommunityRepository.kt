package com.androidengineers.pocketcommunity.data

import com.androidengineers.pocketcommunity.BuildConfig
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

data class AgentReply(val text: String)

data class AgentRequest(
    val prompt: String,
    val surfaceId: String,
    val supportedCatalogIds: List<String>,
    val history: JsonArray = JsonArray(emptyList()),
    val action: JsonObject? = null,
    val surfaceData: JsonObject = JsonObject(emptyMap()),
    val previousComponents: JsonArray = JsonArray(emptyList()),
)

interface CommunityRepository {
    /** Emits validated transport messages as they arrive, then returns the final assistant text. */
    suspend fun ask(
        endpoint: String,
        request: AgentRequest,
        onMessage: suspend (String) -> Unit = {},
    ): AgentReply
}

class HttpCommunityRepository : CommunityRepository {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.MINUTES)
            .readTimeout(10, TimeUnit.MINUTES)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun ask(
        endpoint: String,
        request: AgentRequest,
        onMessage: suspend (String) -> Unit,
    ) =
        withContext(Dispatchers.IO) {
            require(validEndpoint(endpoint)) { "Use a valid HTTPS agent endpoint." }
            require(request.supportedCatalogIds.isNotEmpty()) { "No A2UI catalog is available." }
            val payload = buildJsonObject {
                put("prompt", request.prompt.take(2000))
                put("surfaceId", request.surfaceId)
                put("history", request.history)
                request.action?.let { put("action", it) }
                put("surfaceData", request.surfaceData)
                put("previousComponents", request.previousComponents)
                putJsonObject("metadata") {
                    putJsonObject("a2uiClientCapabilities") {
                        put(
                            "supportedCatalogIds",
                            JsonArray(request.supportedCatalogIds.map(::JsonPrimitive)),
                        )
                    }
                    putJsonObject("transport") {
                        put("incrementalUpdates", true)
                        put("format", "ndjson")
                    }
                }
            }
            val call =
                client.newCall(
                    Request.Builder()
                        .url(endpoint)
                        .header("Accept", "application/x-ndjson, application/json")
                        .header("Cache-Control", "no-store")
                        .header("X-UIAgent-Protocol", "a2ui-v0.9")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                )
            val cancellationHandle =
                checkNotNull(currentCoroutineContext()[Job]).invokeOnCompletion(
                    onCancelling = true,
                    invokeImmediately = true,
                ) { cause ->
                    if (cause is CancellationException) call.cancel()
                }
            try {
                runInterruptible { call.execute() }.use { response ->
                    val body = response.body ?: error("Empty agent response")
                    if (!response.isSuccessful) {
                        val raw = runInterruptible { body.string() }.take(20_000)
                        val reason =
                            runCatching {
                                    Json.parseToJsonElement(raw).jsonObject.string("error")
                                }
                                .getOrDefault("")
                        error(
                            reason.ifBlank {
                                "Agent request failed (${response.code}). Please retry."
                            }
                        )
                    }

                    if (response.header("Content-Type").orEmpty().contains("application/x-ndjson")) {
                        readIncremental(body.source(), onMessage)
                    } else {
                        val raw = runInterruptible { body.string() }
                        require(raw.length <= MAX_RESPONSE_CHARS) {
                            "Agent response exceeded the limit"
                        }
                        val data = Json.parseToJsonElement(raw).jsonObject
                        data["messages"]
                            ?.jsonArray
                            ?.forEach { onMessage(it.toString()) }
                            ?: error("Agent response did not contain A2UI messages")
                        AgentReply(data.string("text"))
                    }
                }
            } finally {
                cancellationHandle.dispose()
                call.cancel()
            }
        }

    private suspend fun readIncremental(
        source: okio.BufferedSource,
        onMessage: suspend (String) -> Unit,
    ): AgentReply {
        var consumed = 0
        var finalText: String? = null
        while (true) {
            val line = runInterruptible { source.readUtf8Line() } ?: break
            consumed += line.length
            require(consumed <= MAX_RESPONSE_CHARS) { "Agent response exceeded the limit" }
            if (line.isBlank()) continue
            val packet = Json.parseToJsonElement(line).jsonObject
            when (packet.string("type")) {
                "message" -> {
                    val message = packet["message"]?.jsonObject ?: error("Invalid A2UI update")
                    onMessage(message.toString())
                }
                "complete" -> finalText = packet.string("text")
                "error" -> error(packet.string("error").ifBlank { "Agent stream failed" })
                else -> error("Unsupported agent stream event")
            }
        }
        return AgentReply(finalText ?: error("Agent stream ended before completion"))
    }

    private companion object {
        const val MAX_RESPONSE_CHARS = 300_000
    }
}

fun validEndpoint(value: String): Boolean =
    runCatching {
            val uri = URI(value)
            uri.userInfo == null &&
                uri.host != null &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
                uri.path == "/chat" &&
                (uri.scheme == "https" ||
                    (BuildConfig.DEBUG &&
                        uri.scheme == "http" &&
                        uri.host in setOf("10.0.2.2", "localhost", "127.0.0.1")))
        }
        .getOrDefault(false)
