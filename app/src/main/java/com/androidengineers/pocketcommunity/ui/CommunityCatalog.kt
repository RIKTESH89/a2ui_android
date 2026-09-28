package com.androidengineers.pocketcommunity.ui

import android.net.Uri
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.model.catalog.basiccatalog.createBasicCatalogFunctions
import androidx.a2ui.model.catalog.functions.A2uiLocaleProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.androidengineers.pocketcommunity.R
import com.androidengineers.pocketcommunity.data.validEndpoint
import coil3.compose.AsyncImage
import java.net.URI
import java.net.URLDecoder

const val UIAGENT_CATALOG_ID = "https://uiagent.example/a2ui/catalog/v1/catalog.json"
const val BASIC_CATALOG_ID = "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json"

/**
 * UIAgent is a versioned hybrid catalog: it keeps the interoperable Basic Catalog component schema
 * while supplying the app's own media trust registry and warm native visual system.
 */
data class UiAgentRemoteImage(
    val imageUrl: String,
    val attribution: String,
    val sourceUrl: String,
)

fun uiAgentCatalog(
    catalogId: String = UIAGENT_CATALOG_ID,
    imageResolver: (String) -> UiAgentRemoteImage? = { null },
): A2uiCatalog {
    val image =
        MaterialA2uiBasicCatalogV1Defaults.image { url, description, _, modifier, onError ->
            UiAgentArtwork(url, description, modifier, onError, imageResolver)
        }
    val video =
        MaterialA2uiBasicCatalogV1Defaults.video { url, modifier, onError ->
            UiAgentVideoPlayer(url, modifier, onError)
        }
    val audio =
        MaterialA2uiBasicCatalogV1Defaults.audioPlayer { _, _, modifier, onError ->
            LaunchedEffect(Unit) { onError(UnsupportedOperationException("Audio is not available")) }
            Text("Audio unavailable", modifier)
        }

    return A2uiCatalog(
        catalogId = catalogId,
        components =
            listOf(
                MaterialA2uiBasicCatalogV1Defaults.text,
                image,
                MaterialA2uiBasicCatalogV1Defaults.icon,
                video,
                audio,
                MaterialA2uiBasicCatalogV1Defaults.row,
                MaterialA2uiBasicCatalogV1Defaults.column,
                MaterialA2uiBasicCatalogV1Defaults.list,
                MaterialA2uiBasicCatalogV1Defaults.card,
                MaterialA2uiBasicCatalogV1Defaults.tabs,
                MaterialA2uiBasicCatalogV1Defaults.modal,
                MaterialA2uiBasicCatalogV1Defaults.divider,
                MaterialA2uiBasicCatalogV1Defaults.button,
                MaterialA2uiBasicCatalogV1Defaults.textField,
                MaterialA2uiBasicCatalogV1Defaults.checkBox,
                MaterialA2uiBasicCatalogV1Defaults.choicePicker,
                MaterialA2uiBasicCatalogV1Defaults.slider,
                MaterialA2uiBasicCatalogV1Defaults.dateTimeInput,
            ),
        functions =
            createBasicCatalogFunctions(
                urlOpener = { error("Generated surfaces cannot open arbitrary URLs") },
                messageFormatter = { pattern, _, _ -> pattern },
                localeProvider = A2uiLocaleProvider.Default,
            ),
    )
}

fun uiAgentCatalogs(imageResolver: (String) -> UiAgentRemoteImage? = { null }): List<A2uiCatalog> =
    listOf(
        uiAgentCatalog(UIAGENT_CATALOG_ID, imageResolver),
        uiAgentCatalog(BASIC_CATALOG_ID, imageResolver),
    )

fun resolveUiAgentRemoteImage(endpoint: String, reference: String): UiAgentRemoteImage? =
    runCatching {
            if (!validEndpoint(endpoint)) return null
            val asset = URI(reference)
            if (asset.scheme != "uiagent" || asset.host != "asset") return null
            val token = asset.path.removePrefix("/")
            val localToken = Regex("[a-f0-9]{32}")
            val signedToken = Regex("[A-Za-z0-9_-]{1,1900}\\.[a-f0-9]{64}")
            if (!localToken.matches(token) && !signedToken.matches(token)) return null
            val query =
                asset.rawQuery
                    ?.split('&')
                    ?.mapNotNull { field ->
                        val split = field.split('=', limit = 2)
                        if (split.size != 2) null
                        else
                            URLDecoder.decode(split[0], "UTF-8") to
                                URLDecoder.decode(split[1], "UTF-8")
                    }
                    ?.toMap()
                    .orEmpty()
            val provider = query["provider"] ?: return null
            val credit = query["credit"]?.takeIf { it.length in 1..80 } ?: return null
            val source = URI(query["source"] ?: return null)
            val expectedDomain =
                when (provider) {
                    "pexels" -> "pexels.com"
                    "pixabay" -> "pixabay.com"
                    else -> return null
                }
            if (
                source.scheme != "https" ||
                    source.userInfo != null ||
                    !trustedDomain(source.host, expectedDomain)
            ) return null
            val agent = URI(endpoint)
            val imageUrl =
                URI(agent.scheme, null, agent.host, agent.port, "/media/$token", null, null)
                    .toString()
            UiAgentRemoteImage(
                imageUrl = imageUrl,
                attribution =
                    if (provider == "pexels") "Photo by $credit · Pexels"
                    else "$credit · Pixabay",
                sourceUrl = source.toString(),
            )
        }
        .getOrNull()

private fun trustedDomain(host: String?, domain: String): Boolean =
    host == domain || host?.endsWith(".$domain") == true

@Composable
private fun UiAgentArtwork(
    url: String,
    description: String?,
    modifier: Modifier,
    onError: (Throwable?) -> Unit,
    imageResolver: (String) -> UiAgentRemoteImage?,
) {
    val remote = imageResolver(url)
    if (remote != null) {
        UiAgentRemoteArtwork(remote, description, modifier)
        return
    }
    val palette =
        when (url) {
            "uiagent://image/travel" -> listOf(Color(0xFFD45F3B), Color(0xFFF0B78D))
            "uiagent://image/weather" -> listOf(Color(0xFF638A68), Color(0xFFB9D5B5))
            "uiagent://image/media" -> listOf(Color(0xFF5D4B8A), Color(0xFFC7B9ED))
            "uiagent://image/generic" -> listOf(Color(0xFFBD4C2F), Color(0xFFF0CDBB))
            else -> null
        }
    if (palette == null) {
        LaunchedEffect(url) { onError(IllegalArgumentException("Untrusted image identifier")) }
        Text("Image unavailable", modifier)
        return
    }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(palette))
                .semantics { contentDescription = description ?: "UIAgent generated artwork" },
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = "✦  UIAGENT",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(18.dp),
        )
    }
}

@Composable
private fun UiAgentRemoteArtwork(
    image: UiAgentRemoteImage,
    description: String?,
    modifier: Modifier,
) {
    var failed by remember(image.imageUrl) { mutableStateOf(false) }
    if (failed) {
        UiAgentGradientArtwork(
            description = description,
            modifier = modifier,
            palette = listOf(Color(0xFFBD4C2F), Color(0xFFF0CDBB)),
        )
        return
    }
    val uriHandler = LocalUriHandler.current
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.BottomStart,
    ) {
        AsyncImage(
            model = image.imageUrl,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            onError = { failed = true },
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = image.attribution,
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            modifier =
                Modifier
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.64f))
                    .clickable { runCatching { uriHandler.openUri(image.sourceUrl) } }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun UiAgentGradientArtwork(
    description: String?,
    modifier: Modifier,
    palette: List<Color>,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(palette))
                .semantics { contentDescription = description ?: "UIAgent generated artwork" },
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = "✦  UIAGENT",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(18.dp),
        )
    }
}

@Composable
private fun UiAgentVideoPlayer(
    url: String,
    modifier: Modifier,
    onError: (Throwable?) -> Unit,
) {
    val context = LocalContext.current
    val mediaUri =
        when (url) {
            "uiagent://video/reference" ->
                Uri.parse("android.resource://${context.packageName}/${R.raw.uiagent_reference}")
            else -> null
        }
    if (mediaUri == null) {
        LaunchedEffect(url) { onError(IllegalArgumentException("Untrusted video identifier")) }
        Text("Video unavailable", modifier)
        return
    }

    val currentOnError by rememberUpdatedState(onError)
    val player =
        remember(mediaUri) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(mediaUri))
                playWhenReady = false
                prepare()
            }
        }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) = currentOnError(error)
            }
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) player.pause()
            }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = true
                this.player = player
            }
        },
        update = { it.player = player },
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp)),
    )
}
