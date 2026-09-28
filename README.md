# UIAgent A2UI

UIAgent is a prompt-driven generative UI app for Android. Type the interface you need—such as an illustrative flight ticket, weather card, packing checklist, playable video, horizontal recommendation rail, or mixed feed—and an LLM composes a constrained A2UI surface rendered as native Jetpack Compose.

The visual language is inspired by the supplied motion reference, but the flow is not scripted. Android owns the catalog, rendering, playback and action authorization. The configured provider chooses only the composition and content allowed by that catalog.

## Implemented vertical slice

- Official AndroidX A2UI parser, message processor, runtime and Compose surface renderer.
- Versioned UIAgent hybrid catalog plus Basic Catalog fallback registration.
- Catalog capability metadata on every request and server-side best-match negotiation.
- Native Text, Row, Column, List, Card, Button, CheckBox, Divider, Image and Video.
- Real Pexels/Pixabay imagery through a server-side search, validation, download/proxy and opaque asset-ID pipeline; branded gradients remain the fallback.
- Safe built-in AndroidX A2UI icons selected from an explicit allowlist.
- Media3/ExoPlayer playback with lifecycle cleanup and a client-side trusted media registry.
- Horizontal rails through A2UI `List`; vertical feeds use `Column` because the conversation host already scrolls vertically.
- Incremental NDJSON transport: a loading surface renders before slow local inference completes, then updates in place.
- Bounded payloads, strict graph/property/action/media validation, request cancellation, one-model concurrency, request IDs, no redirects, and release-only HTTPS policy.

No live weather or flight provider is connected. Cards must label generated facts as illustrative; they are not bookings, tickets, live status, or forecasts.

## Run

Open only this `a2ui/` directory in Android Studio. The project pins its AndroidX A2UI/Compose toolchain and adds Media3 1.11.1 for video plus Coil 3.6.3 for server-proxied images.

```sh
cd server
cp .env.example .env
# Add OPENROUTER_API_KEY to .env
# Optionally add PEXELS_API_KEY and/or PIXABAY_API_KEY for real images
npm start
```

Run the Android `app` configuration. In **Setup**, use:

- Emulator: `http://10.0.2.2:8787/chat`
- Physical device after `adb reverse tcp:8787 tcp:8787`: `http://127.0.0.1:8787/chat`
- Stable Vercel deployment: `https://uiagent-a2ui.vercel.app/chat`

Try prompts such as:

- `Create an illustrative flight ticket from Bengaluru to Tokyo, departing 09:40 and arriving 20:15`
- `Create an illustrative weather card for Bengaluru with an hourly forecast rail`
- `Create a playable video card with a title, metadata and follow-up action`
- `Create a vertical travel dashboard feed with a ticket, weather and packing checklist`

The Vercel deployment and laptop server are independent, so the stable endpoint remains available when the laptop is off. The configured OpenRouter free model can still be rate-limited or temporarily unavailable. The loading A2UI surface appears immediately while the remote request is running.

## Message flow

1. Android sends the prompt and ordered `supportedCatalogIds`.
2. The server chooses the first mutually supported catalog.
3. It streams `createSurface`, initial data, and a loading component tree.
4. OpenRouter returns a component composition—not Kotlin or executable UI code.
5. The server validates the complete graph, bindings, trusted media identifiers and actions.
6. Final `updateDataModel` and `updateComponents` messages replace the loading tree in the same surface.
7. AndroidX A2UI renders native Compose components. Media3 owns local video playback state.

Buttons may either send a bounded follow-up prompt back to UIAgent or save a label locally for the current session. Arbitrary URLs, WebViews, functions and model-generated media locations are rejected.

## Verification

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
cd server && npm test
```

See [the app brief](docs/app-brief.md) and [verification record](docs/verification.md). The A2UI libraries remain alpha, and this sample still needs a production auth gateway, live data tools, persistent conversations, broader accessibility/device evaluation and operational safeguards before company deployment.
