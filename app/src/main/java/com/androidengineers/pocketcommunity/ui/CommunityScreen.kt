package com.androidengineers.pocketcommunity.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.material3.a2ui.A2uiSurface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidengineers.pocketcommunity.data.validEndpoint

private val UiAgentOrange = Color(0xFFBD4C2F)
private val UiAgentCream = Color(0xFFF8F2E9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(vm: CommunityViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val surfaces by vm.surfaces.collectAsStateWithLifecycle()
    var prompt by rememberSaveable { mutableStateOf("") }
    var inspect by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var endpoint by rememberSaveable { mutableStateOf("") }
    val scroll = rememberLazyListState()
    LaunchedEffect(state.items.size) {
        if (state.items.isNotEmpty()) scroll.animateScrollToItem(state.items.lastIndex + 1)
    }

    Scaffold(
        containerColor = UiAgentCream,
        topBar = {
            Column(
                Modifier.statusBarsPadding()
                    .fillMaxWidth()
                    .background(UiAgentCream.copy(alpha = 0.96f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = UiAgentOrange,
                        shape = CircleShape,
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✦", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("UIAgent", fontWeight = FontWeight.Bold)
                        Text(
                            if (state.endpoint.isBlank()) "Agent disconnected"
                            else "UIAgent · A2UI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { inspect = true }) { Text("‹›  Inspect") }
                            TextButton(
                                onClick = {
                                    endpoint = state.endpoint
                                    settings = true
                                }
                            ) {
                                Text("Setup")
                            }
                        }
                    }
                }
                if (state.items.isNotEmpty()) {
                    TextButton(onClick = vm::reset, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("New conversation")
                    }
                }
            }
        },
        bottomBar = {
            Row(
                Modifier.navigationBarsPadding()
                    .imePadding()
                    .fillMaxWidth()
                    .background(UiAgentCream)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(onClick = {}, enabled = false) { Text("+") }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it.take(2000) },
                    placeholder = { Text("Message UIAgent…") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    maxLines = 4,
                    enabled = state.endpoint.isNotBlank(),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.7f),
                            focusedBorderColor = UiAgentOrange,
                            unfocusedBorderColor = Color(0xFFE4D9CC),
                        ),
                )
                FilledIconButton(
                    onClick = {
                        val message = prompt
                        prompt = ""
                        vm.send(message)
                    },
                    enabled = prompt.isNotBlank() && !state.busy && state.endpoint.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = UiAgentOrange),
                ) {
                    Text("↑", style = MaterialTheme.typography.titleLarge)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 10.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.items.isEmpty()) {
                item { EmptyAtlas(state.endpoint.isNotBlank(), state.busy, vm::send) { settings = true } }
            }
            items(state.items, key = { it.id }) { item ->
                if (item.surfaceId != null) {
                    val surface = surfaces.find { it.id == item.surfaceId }
                    if (surface != null) {
                        A2uiSurface(surfaceModel = surface, modifier = Modifier.fillMaxWidth())
                    } else {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(22.dp),
                            shadowElevation = 2.dp,
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = UiAgentOrange,
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("Opening a native surface…")
                            }
                        }
                    }
                } else {
                    ConversationBubble(item)
                }
            }
            if (state.busy) {
                item {
                    Text(
                        "UIAgent is composing with the negotiated catalog…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.error?.let { error ->
                item {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            Row {
                                if (state.canRetry) {
                                    TextButton(onClick = vm::retry, enabled = !state.busy) {
                                        Text("Retry")
                                    }
                                }
                                TextButton(onClick = vm::dismissError) { Text("Dismiss") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (inspect) {
        ModalBottomSheet(onDismissRequest = { inspect = false }) {
            Text(
                "A2UI inspector",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(20.dp),
            )
            Text(
                "Incremental inbound messages, selected catalog and trusted outbound actions",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            SelectionContainer {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    contentPadding = PaddingValues(20.dp),
                ) {
                    items(state.trace) {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
    if (settings) {
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text("Connect UIAgent") },
            text = {
                Column {
                    Text(
                        "Use http://10.0.2.2:8787/chat on the emulator or an HTTPS endpoint in production. UIAgent negotiates its supported A2UI catalogs on every message."
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        endpoint,
                        { endpoint = it },
                        label = { Text("Agent /chat endpoint") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.configure(endpoint)
                        settings = false
                    },
                    enabled = validEndpoint(endpoint) && !state.busy,
                ) {
                    Text("Connect")
                }
            },
            dismissButton = { TextButton(onClick = { settings = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyAtlas(
    configured: Boolean,
    busy: Boolean,
    send: (String) -> Unit,
    setup: () -> Unit,
) {
    Column(Modifier.padding(top = 34.dp, bottom = 20.dp)) {
        Text(
            "What should UIAgent create?",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Describe a useful card, a horizontal rail, a feed, or a playable media surface. Every result is native Compose generated through a constrained catalog.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))
        if (!configured) {
            Button(onClick = setup, colors = ButtonDefaults.buttonColors(containerColor = UiAgentOrange)) {
                Text("Connect local agent")
            }
        } else {
            val suggestions =
                listOf(
                    "Create an illustrative flight ticket from Bengaluru to Tokyo, departing 09:40 and arriving 20:15",
                    "Create an illustrative weather card for Bengaluru with an hourly forecast rail",
                    "Create a playable video card with a title, metadata and follow-up action",
                    "Create a vertical travel dashboard feed with a ticket, weather and packing checklist",
                )
            suggestions.forEachIndexed { index, suggestion ->
                if (index == 0) {
                    Button(
                        onClick = { send(suggestion) },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = UiAgentOrange),
                    ) {
                        Text("Generate a flight card")
                    }
                } else {
                    OutlinedButton(
                        onClick = { send(suggestion) },
                        enabled = !busy,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Text(
                            when (index) {
                                1 -> "Generate weather + hourly rail"
                                2 -> "Generate a playable video card"
                                else -> "Generate a mixed feed"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationBubble(item: ConversationItem) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (item.user) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (item.user) UiAgentOrange else Color.White,
            shape =
                RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (item.user) 20.dp else 6.dp,
                    bottomEnd = if (item.user) 6.dp else 20.dp,
                ),
            shadowElevation = if (item.user) 1.dp else 2.dp,
            modifier = Modifier.widthIn(max = 330.dp),
        ) {
            Text(
                item.text,
                Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                color = if (item.user) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
