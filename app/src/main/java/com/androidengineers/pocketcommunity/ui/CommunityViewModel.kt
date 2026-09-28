package com.androidengineers.pocketcommunity.ui

import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.engine.model.A2uiCoreSurfaceModel
import androidx.a2ui.model.processor.processInput
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.a2ui.model.protocol.A2uiDataPath
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidengineers.pocketcommunity.data.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

data class ConversationItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val user: Boolean = false,
    val surfaceId: String? = null,
)

data class CommunityState(
    val items: List<ConversationItem> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val trace: List<String> = emptyList(),
    val endpoint: String = "",
    val saved: Set<String> = emptySet(),
    val canRetry: Boolean = false,
)

class CommunityViewModel(
    private val repository: CommunityRepository,
    catalogs: List<A2uiCatalog>,
    initialEndpoint: String = "",
    private val saveEndpoint: (String) -> Unit = {},
) : ViewModel() {
    private val mutable = MutableStateFlow(CommunityState(endpoint = initialEndpoint))
    val state = mutable.asStateFlow()
    private val processor = A2uiMessageProcessor(catalogs)
    private val parser = A2uiMessageParser()
    private val supportedCatalogIds = catalogs.map { it.id }
    val surfaces = processor.activeSurfaces
    private var lastRequest: AgentRequest? = null
    private var job: Job? = null
    private var generation = 0
    private val componentHistory = linkedMapOf<String, JsonArray>()

    init {
        viewModelScope.launch { processor.collectMessages() }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            processor.outboundEvents.collect { event ->
                trace("OUT $event")
                when (event) {
                    is A2uiClientEventMessage -> action(event)
                    is A2uiClientErrorMessage ->
                        mutable.update {
                            it.copy(
                                error = "Unable to render the generated interface: ${event.message}",
                                canRetry = lastRequest != null,
                                items = it.items.filterNot { item -> item.surfaceId == event.surfaceId },
                            )
                        }
                }
            }
        }
    }

    fun configure(endpoint: String) {
        val clean = endpoint.trim()
        if (clean.isNotEmpty() && !validEndpoint(clean)) {
            mutable.update { it.copy(error = "Enter a valid /chat agent endpoint.") }
            return
        }
        if (clean != state.value.endpoint && state.value.items.isNotEmpty()) {
            mutable.update {
                it.copy(error = "Start a new conversation before changing the agent endpoint.")
            }
            return
        }
        saveEndpoint(clean)
        mutable.update { it.copy(endpoint = clean, error = null) }
    }

    fun reset() {
        generation++
        job?.cancel()
        componentHistory.clear()
        lastRequest = null
        surfaces.value.forEach { surface -> deleteSurface(surface.id) }
        mutable.value = CommunityState(endpoint = state.value.endpoint, saved = state.value.saved)
    }

    fun dismissError() {
        mutable.update { it.copy(error = null) }
    }

    private fun trace(value: String) {
        mutable.update { it.copy(trace = (it.trace + value).takeLast(100)) }
    }

    private fun append(text: String, user: Boolean = false) {
        mutable.update { it.copy(items = it.items + ConversationItem(text = text, user = user)) }
    }

    private fun history() =
        JsonArray(
            state.value.items
                .filter { it.surfaceId == null && it.text.isNotBlank() }
                .takeLast(12)
                .map {
                    buildJsonObject {
                        put("role", if (it.user) "user" else "assistant")
                        put("text", it.text.take(2000))
                    }
                }
        )

    private fun surfaceData(): JsonObject = buildJsonObject {
        surfaces.value.takeLast(6).forEach { surface ->
            val core = surface as? A2uiCoreSurfaceModel
            if (core != null) put(surface.id, jsonValue(core.dataModel[A2uiDataPath("/")]))
        }
    }

    fun send(prompt: String) {
        if (prompt.isBlank() || state.value.busy) return
        if (state.value.endpoint.isBlank()) {
            mutable.update {
                it.copy(error = "Connect the local UIAgent server in Setup before generating UI.")
            }
            return
        }
        val request =
            AgentRequest(
                prompt = prompt.take(2000),
                surfaceId = UUID.randomUUID().toString(),
                supportedCatalogIds = supportedCatalogIds,
                history = history(),
                surfaceData = surfaceData(),
                previousComponents = previousComponents(),
            )
        append(request.prompt, true)
        submit(request)
    }

    private fun previousComponents() =
        JsonArray(
            componentHistory.entries.toList().takeLast(3).map { (id, components) ->
                buildJsonObject {
                    put("surfaceId", id)
                    put("components", components)
                }
            }
        )

    fun retry() {
        if (!state.value.busy)
            lastRequest?.let {
                submit(
                    it.copy(
                        surfaceId = UUID.randomUUID().toString(),
                        surfaceData = surfaceData(),
                        previousComponents = previousComponents(),
                    )
                )
            }
    }

    private fun submit(request: AgentRequest) {
        lastRequest = request
        val epoch = generation
        val endpoint = state.value.endpoint
        mutable.update {
            it.copy(
                busy = true,
                error = null,
                canRetry = false,
                items = it.items + ConversationItem(surfaceId = request.surfaceId),
            )
        }
        job =
            viewModelScope.launch {
                try {
                    val reply =
                        repository.ask(endpoint, request) { raw ->
                            withContext(Dispatchers.Main.immediate) {
                                ensureActive()
                                if (epoch == generation) processIncoming(request, raw)
                            }
                        }
                    ensureActive()
                    if (epoch != generation) return@launch
                    if (reply.text.isNotBlank()) append(reply.text)
                } catch (e: CancellationException) {
                    deleteSurface(request.surfaceId)
                    throw e
                } catch (e: Exception) {
                    if (epoch == generation) {
                        deleteSurface(request.surfaceId)
                        mutable.update {
                            it.copy(
                                items =
                                    it.items.filterNot { item ->
                                        item.surfaceId == request.surfaceId
                                    },
                                error = e.message ?: "Agent unavailable. Please retry.",
                                canRetry = true,
                            )
                        }
                    }
                } finally {
                    if (epoch == generation) mutable.update { it.copy(busy = false) }
                }
            }
    }

    private fun processIncoming(request: AgentRequest, raw: String) {
        val message = Json.parseToJsonElement(raw).jsonObject
        val create = message["createSurface"]?.jsonObject
        val data = message["updateDataModel"]?.jsonObject
        val components = message["updateComponents"]?.jsonObject
        val body = create ?: data ?: components ?: error("Unsupported agent message.")
        require(body.string("surfaceId") == request.surfaceId) {
            "Unexpected surface in agent response."
        }
        create?.let {
            require(it.string("catalogId") in supportedCatalogIds) {
                "The agent selected an unsupported component catalog."
            }
        }
        (components?.get("components") as? JsonArray)?.let {
            componentHistory[request.surfaceId] = it
        }
        trace("IN $raw")
        processor.processInput(parser, raw)
    }

    private fun action(event: A2uiClientEventMessage) {
        if (state.value.busy) return
        when (event.type) {
            "ask" -> {
                val prompt = event.context["prompt"] as? String
                if (prompt.isNullOrBlank() || prompt.length > 240) {
                    mutable.update { it.copy(error = "The generated follow-up action was invalid.") }
                    return
                }
                val action = buildJsonObject {
                    put("name", event.type)
                    put("surfaceId", event.surfaceId)
                    put("componentId", event.componentId)
                    put("context", jsonValue(event.context))
                }
                val request =
                    AgentRequest(
                        prompt = prompt,
                        surfaceId = UUID.randomUUID().toString(),
                        supportedCatalogIds = supportedCatalogIds,
                        history = history(),
                        action = action,
                        surfaceData = surfaceData(),
                        previousComponents = previousComponents(),
                    )
                append(prompt, true)
                submit(request)
            }
            "save" -> {
                val itemId = event.context["itemId"] as? String
                val title = event.context["title"] as? String
                if (itemId.isNullOrBlank() || title.isNullOrBlank()) {
                    mutable.update { it.copy(error = "The generated save action was invalid.") }
                    return
                }
                mutable.update { it.copy(saved = it.saved + itemId) }
                append("Saved locally for this session: $title.")
            }
            else -> mutable.update { it.copy(error = "Unsupported generated action: ${event.type}") }
        }
    }

    private fun deleteSurface(surfaceId: String) {
        processor.processInput(
            parser,
            """{"version":"v0.9","deleteSurface":{"surfaceId":"$surfaceId"}}""",
        )
    }
}

private fun jsonValue(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Map<*, *> ->
            JsonObject(value.entries.associate { it.key.toString() to jsonValue(it.value) })
        is List<*> -> JsonArray(value.map(::jsonValue))
        else -> JsonNull
    }
