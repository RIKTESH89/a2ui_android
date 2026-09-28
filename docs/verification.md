# UIAgent A2UI verification — 2026-09-28

Status: working Android prototype with OpenRouter and local-model provider paths plus strict trust boundaries. A stable Vercel HTTPS deployment is live, but it is not yet a production-hardened company service.

## Passed

- Debug APK, Android-test APK, JVM tests, debug lint, and release APK build successfully.
- 18 Node tests cover catalog negotiation, component/property and exact AndroidX enum restrictions, graph integrity, trusted media identifiers, built-in icon allowlisting, action allowlisting, checkbox bindings, incremental messages, provider-domain validation, bounded image download/cache, opaque asset serving, stateless signed media, and tamper rejection.
- 3 emulator instrumentation tests exercise the real AndroidX A2UI renderer with generated flight/weather-style surfaces, follow-up actions, incremental updates, and endpoint setup behavior.
- Android JVM tests verify that only well-formed local or signed serverless UIAgent image references with official Pexels/Pixabay attribution URLs can resolve to the endpoint's `/media/{assetId}` route.
- Live `qwen3:14b` through Ollama generated and rendered an illustrative Bengaluru-to-Tokyo flight card in native Compose.
- After the provider switch, a live OpenRouter request using `nvidia/nemotron-3-ultra-550b-a55b:free` generated a richer Bengaluru-to-Tokyo ticket and passed validation. A second live request rendered an illustrative Bengaluru weather card with a native horizontal hourly rail on the emulator.
- NDJSON delivery is genuinely incremental: the emulator displays `Composing your interface…` before Ollama finishes inference, then replaces that same surface with the final components.
- A model-produced invalid graph is rejected. UIAgent performs one bounded repair attempt and then renders a validator-checked trusted fallback instead of accepting malformed UI.
- The playable video fallback was rendered on the emulator through Media3. Play, pause, seek, elapsed time, title, and metadata work; playback uses only the bundled `uiagent://video/reference` resource.
- Cancelling a generation from `New conversation` closes the OkHttp call. The server observes `Client disconnected`, cancels its Ollama request, releases the concurrency slot, and accepts the next prompt.
- The Android client advertises UIAgent and Basic catalog IDs in preference order on every request. The server selects the first mutual catalog for each surface.
- Server `.env` and local build configuration remain ignored. No API key is embedded in the APK.
- The production Vercel smoke test generated a streamed birthday invitation surface through OpenRouter, resolved its semantic image through Pexels, and downloaded the resulting signed `/media/{assetId}` URL as a 28,840-byte JPEG. `/health` reports OpenRouter configured and Pexels enabled.
- `https://uiagent-a2ui.vercel.app/health` and `https://uiagent-a2ui.vercel.app/chat` are live independently of the laptop server. The local loopback server and existing Cloudflare development tunnel also remain healthy.

## Trust boundaries

- The model emits data and a constrained component graph, never Kotlin, Compose code, HTML, or arbitrary URLs.
- The model can emit only semantic image IDs and the single `uiagent://video/reference` video ID. The server may replace a semantic image with an exact, validator-approved opaque asset reference only after searching an enabled provider and validating official provider domains. Local assets use a bounded cache; Vercel assets use an expiring HMAC-signed reference that is revalidated before the server proxies bounded image bytes.
- Android reconstructs the image byte URL from its configured UIAgent endpoint and a validated local or signed asset token. It never loads the model's or provider's image URL directly. Pexels/Pixabay attribution links are separately restricted to their official HTTPS domains.
- Icons are restricted to a server-side allowlist of built-in Material A2UI icon names; model-authored SVG paths or remote icon URLs are not accepted.
- Model-authored actions are limited to `ask` with a short non-URL prompt and local-only `save` with a stable ID/title. Media controls stay entirely inside Media3.
- All model output is validated for exact properties, component count, graph reachability/cycles, bindings, media IDs, and action payloads before it reaches AndroidX A2UI.
- Flight and weather values are illustrative unless supplied by the user. No live weather, airline, booking, payment, map, or calendar provider is connected.

## Remaining production work

- Put authentication and per-user authorization in front of the Vercel HTTPS service, then add durable distributed quotas, observability, abuse controls, and an operational alerting policy. Secrets are encrypted in Vercel, but rotation and access policy still need to be defined.
- Connect authoritative domain providers before presenting live weather, fares, availability, reservations, maps, or external actions.
- Persist conversations/saves if required; the current conversation and saved-item state are local and in-memory.
- Add product-specific accessibility, large-font, tablet, offline, process-death, flaky-network, and sustained-load testing.
- Decide whether the signed on-demand image proxy is sufficient or add production object storage/CDN caching; enforce provider quotas and terms, define retention behavior, and configure a licensed stock provider account. iStock is not enabled because it requires a separate commercial API/license arrangement.
- Free OpenRouter routes have variable latency, rate limits, and availability. The validator/repair/fallback path keeps rendering safe, but a production model/provider combination should be evaluated for layout validity, latency, availability, data handling, and cost.

## Toolchain

Android Studio bundled JDK; Gradle 9.3.1; AGP 9.1.1; Kotlin Compose plugin 2.3.20; compile SDK 37 / target SDK 36 / min SDK 26; AndroidX A2UI 1.0.0-alpha01; Media3 1.11.1. Device verification used the Pixel 9a emulator on Android 17/API 37.
