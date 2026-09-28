# UIAgent A2UI verification — 2026-09-28

Status: working Android prototype with OpenRouter and local-model provider paths plus strict trust boundaries. It is not a production service deployment.

## Passed

- Debug APK, Android-test APK, JVM tests, debug lint, and release APK build successfully.
- 16 Node tests cover catalog negotiation, component/property and exact AndroidX enum restrictions, graph integrity, trusted media identifiers, built-in icon allowlisting, action allowlisting, checkbox bindings, incremental messages, provider-domain validation, bounded image download/cache, and opaque asset serving.
- 3 emulator instrumentation tests exercise the real AndroidX A2UI renderer with generated flight/weather-style surfaces, follow-up actions, incremental updates, and endpoint setup behavior.
- Android JVM tests verify that only well-formed server-issued UIAgent image references with official Pexels/Pixabay attribution URLs can resolve to the endpoint's `/media/{assetId}` route.
- Live `qwen3:14b` through Ollama generated and rendered an illustrative Bengaluru-to-Tokyo flight card in native Compose.
- After the provider switch, a live OpenRouter request using `nvidia/nemotron-3-ultra-550b-a55b:free` generated a richer Bengaluru-to-Tokyo ticket and passed validation. A second live request rendered an illustrative Bengaluru weather card with a native horizontal hourly rail on the emulator.
- NDJSON delivery is genuinely incremental: the emulator displays `Composing your interface…` before Ollama finishes inference, then replaces that same surface with the final components.
- A model-produced invalid graph is rejected. UIAgent performs one bounded repair attempt and then renders a validator-checked trusted fallback instead of accepting malformed UI.
- The playable video fallback was rendered on the emulator through Media3. Play, pause, seek, elapsed time, title, and metadata work; playback uses only the bundled `uiagent://video/reference` resource.
- Cancelling a generation from `New conversation` closes the OkHttp call. The server observes `Client disconnected`, cancels its Ollama request, releases the concurrency slot, and accepts the next prompt.
- The Android client advertises UIAgent and Basic catalog IDs in preference order on every request. The server selects the first mutual catalog for each surface.
- Server `.env` and local build configuration remain ignored. No API key is embedded in the APK.
- The Pexels/Pixabay integration was exercised with deterministic provider fixtures. A live stock-provider request is intentionally not recorded because no stock-provider API key is configured; `/health` reports an empty `imageProviders` array and the app retains its branded gradient fallback.

## Trust boundaries

- The model emits data and a constrained component graph, never Kotlin, Compose code, HTML, or arbitrary URLs.
- The model can emit only semantic image IDs and the single `uiagent://video/reference` video ID. The server may replace a semantic image with an exact, validator-approved opaque asset reference only after searching an enabled provider, validating official provider domains, and downloading a bounded image into its own cache.
- Android reconstructs the image byte URL from its configured UIAgent endpoint and the validated 32-character asset token. It never loads the model's or provider's image URL directly. Pexels/Pixabay attribution links are separately restricted to their official HTTPS domains.
- Icons are restricted to a server-side allowlist of built-in Material A2UI icon names; model-authored SVG paths or remote icon URLs are not accepted.
- Model-authored actions are limited to `ask` with a short non-URL prompt and local-only `save` with a stable ID/title. Media controls stay entirely inside Media3.
- All model output is validated for exact properties, component count, graph reachability/cycles, bindings, media IDs, and action payloads before it reaches AndroidX A2UI.
- Flight and weather values are illustrative unless supplied by the user. No live weather, airline, booking, payment, map, or calendar provider is connected.

## Remaining production work

- Replace localhost with an authenticated HTTPS service and add per-user authorization, durable quotas, observability, abuse controls, and secret management.
- Connect authoritative domain providers before presenting live weather, fares, availability, reservations, maps, or external actions.
- Persist conversations/saves if required; the current conversation and saved-item state are local and in-memory.
- Add product-specific accessibility, large-font, tablet, offline, process-death, flaky-network, and sustained-load testing.
- Replace the in-memory image cache with production storage, enforce provider quotas and terms, define retention/deletion behavior, and configure a licensed stock provider account. iStock is not enabled because it requires a separate commercial API/license arrangement.
- Free OpenRouter routes have variable latency, rate limits, and availability. The validator/repair/fallback path keeps rendering safe, but a production model/provider combination should be evaluated for layout validity, latency, availability, data handling, and cost.

## Toolchain

Android Studio bundled JDK; Gradle 9.3.1; AGP 9.1.1; Kotlin Compose plugin 2.3.20; compile SDK 37 / target SDK 36 / min SDK 26; AndroidX A2UI 1.0.0-alpha01; Media3 1.11.1. Device verification used the Pixel 9a emulator on Android 17/API 37.
