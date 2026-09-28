package com.androidengineers.pocketcommunity

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.androidengineers.pocketcommunity.data.*
import com.androidengineers.pocketcommunity.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val requests = mutableListOf<AgentRequest>()

    private fun setup(fail: Boolean = false, configured: Boolean = true) {
        val repo =
            object : CommunityRepository {
                override suspend fun ask(
                    endpoint: String,
                    request: AgentRequest,
                    onMessage: suspend (String) -> Unit,
                ): AgentReply {
                    if (fail) error("Agent unavailable")
                    requests += request
                    val messages =
                        if (request.action == null) SurfaceMessages.flight(request.surfaceId)
                        else SurfaceMessages.checklist(request.surfaceId)
                    messages.forEach { onMessage(it) }
                    return AgentReply("Generated with the negotiated UIAgent catalog.")
                }
            }
        compose.setContent {
            MaterialTheme {
                CommunityScreen(
                    androidx.lifecycle.viewmodel.compose.viewModel(
                        factory =
                            object : androidx.lifecycle.ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : androidx.lifecycle.ViewModel> create(
                                    modelClass: Class<T>
                                ): T =
                                    CommunityViewModel(
                                        repo,
                                        uiAgentCatalogs(),
                                        if (configured) "http://127.0.0.1:8787/chat" else "",
                                    ) as T
                            }
                    )
                )
            }
        }
    }

    @Test
    fun generatedCardFollowUpAndBoundChecklist() {
        setup()
        compose.onNodeWithText("Generate a flight card").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("ILLUSTRATIVE · NOT A BOOKING").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(UIAGENT_CATALOG_ID, requests.single().supportedCatalogIds.first())
        compose.onNodeWithText("Create packing checklist").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Passport").fetchSemanticsNodes().isNotEmpty()
        }
        assertNotNull(requests.last().action)
        assertTrue(requests.last().previousComponents.isNotEmpty())
        compose.onAllNodesWithText("Passport").onLast().performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("Refine list").performScrollTo().performClick()
        compose.waitUntil(10_000) { requests.size == 3 }
        assertTrue(requests.last().surfaceData.toString().contains("true"))
    }

    @Test
    fun missingEndpointRequiresSetup() {
        setup(configured = false)
        compose.onNodeWithText("Connect local agent").assertExists()
        compose.onNodeWithText("Generate a flight card").assertDoesNotExist()
        assertTrue(requests.isEmpty())
    }

    @Test
    fun agentFailureIsRecoverable() {
        setup(fail = true)
        compose.onNodeWithText("Generate a flight card").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Agent unavailable").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Dismiss").performClick()
        compose.onNodeWithText("Agent unavailable").assertDoesNotExist()
    }
}
