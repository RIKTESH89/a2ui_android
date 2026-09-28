package com.androidengineers.pocketcommunity

import com.androidengineers.pocketcommunity.data.validEndpoint
import com.androidengineers.pocketcommunity.ui.resolveUiAgentRemoteImage
import org.junit.Assert.*
import org.junit.Test

class TransportPolicyTest {
    @Test
    fun debugLoopbackChatEndpointIsAllowed() {
        assertTrue(validEndpoint("http://127.0.0.1:8787/chat"))
        assertTrue(validEndpoint("http://10.0.2.2:8787/chat"))
    }

    @Test
    fun arbitraryCleartextAndUnexpectedPathsAreRejected() {
        assertFalse(validEndpoint("http://example.com/chat"))
        assertFalse(validEndpoint("http://127.0.0.1:8787/admin"))
        assertFalse(validEndpoint("http://127.0.0.1:8787/chat?token=secret"))
    }

    @Test
    fun productionStyleHttpsChatEndpointIsAllowed() {
        assertTrue(validEndpoint("https://agent.example.com/chat"))
        assertFalse(validEndpoint("https://user:password@agent.example.com/chat"))
    }

    @Test
    fun opaqueUiAgentAssetResolvesAgainstTheConfiguredAgent() {
        val reference =
            "uiagent://asset/0123456789abcdef0123456789abcdef" +
                "?provider=pexels&credit=Ana+Example" +
                "&source=https%3A%2F%2Fwww.pexels.com%2Fphoto%2Ftokyo-123%2F"
        val image = resolveUiAgentRemoteImage("http://127.0.0.1:8787/chat", reference)
        assertEquals(
            "http://127.0.0.1:8787/media/0123456789abcdef0123456789abcdef",
            image?.imageUrl,
        )
        assertEquals("Photo by Ana Example · Pexels", image?.attribution)
    }

    @Test
    fun opaqueUiAgentAssetRejectsUntrustedSourceAndMalformedToken() {
        assertNull(
            resolveUiAgentRemoteImage(
                "http://127.0.0.1:8787/chat",
                "uiagent://asset/0123456789abcdef0123456789abcdef" +
                    "?provider=pexels&credit=Mallory&source=https%3A%2F%2Fevil.example%2F1",
            )
        )
        assertNull(
            resolveUiAgentRemoteImage(
                "http://127.0.0.1:8787/chat",
                "uiagent://asset/not-a-token?provider=pexels&credit=Ana" +
                    "&source=https%3A%2F%2Fwww.pexels.com%2Fphoto%2F1",
            )
        )
    }

    @Test
    fun signedServerlessAssetResolvesAgainstTheConfiguredAgent() {
        val token = "eyJ2IjoxLCJwIjoicGV4ZWxzIiwidSI6Imh0dHBzIn0." + "a".repeat(64)
        val reference =
            "uiagent://asset/$token?provider=pexels&credit=Ana+Example" +
                "&source=https%3A%2F%2Fwww.pexels.com%2Fphoto%2Ftokyo-123%2F"
        val image = resolveUiAgentRemoteImage("https://uiagent.vercel.app/chat", reference)
        assertEquals("https://uiagent.vercel.app/media/$token", image?.imageUrl)
    }
}
