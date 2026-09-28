# UIAgent — conversational generative UI with AndroidX A2UI

Status: implementation preview, not a released learning journey or production agent.

## Product and audience

UIAgent is a native Android conversation in which a user describes the interface they need and a configured model composes an interactive card, rail, or feed from a constrained A2UI catalog. The reference motion study supplies the warm visual language, but the product is intentionally prompt-driven rather than a scripted travel flow. It is for Android engineers learning how to put generative UI behind a safe native component boundary.

## Core interaction and MVP

The core interaction is: type a request such as “make a flight ticket from Delhi to Tokyo” or “show a weather card for Bengaluru,” receive a progressively updated native Compose surface, and continue through trusted follow-up actions.

MVP features:

- UIAgent-styled native cards composed from Text, Card, Row, Column, List, Button, CheckBox, Image, Divider, and Video.
- A Media3-backed playable video component whose model-facing identifier resolves through a client trust registry.
- Optional Pexels/Pixabay stock imagery resolved and cached by the server behind opaque UIAgent asset IDs, with visible attribution and gradient fallback.
- Built-in A2UI/Material icons restricted to an explicit token allowlist; arbitrary SVG is not accepted.
- Horizontal rails and vertical feed layouts selected by the agent.
- NDJSON transport that creates a loading surface immediately and incrementally replaces it with the validated result.
- Catalog capability metadata on every request, server-side negotiation, strict component/action validation, cancellation, bounded payloads, and debug-only loopback HTTP.

Later features include live weather/flight providers, durable conversations, authentication at a production gateway, richer custom components, server-driven catalog discovery, and token-level model streaming.

## Learning and architecture

Prerequisites are Kotlin, Compose, coroutines, HTTP, and basic JSON schema concepts. Observable outcomes are: define and version a catalog, negotiate it, render A2UI surfaces, implement media safely, stream protocol updates, separate player state from agent state, and authorize model-proposed actions.

The app remains a single Android module using Compose, immutable `StateFlow`, a screen ViewModel, a repository transport boundary, and constructor injection. The companion Node server currently calls OpenRouter; the provider credential remains only in the server environment and never enters the APK. Model output, prior UI, history, and action context are untrusted and validated before rendering or execution.

Playback controls and progress are owned locally by Media3. A2UI controls composition and semantic follow-ups; it does not round-trip play, pause, seek, or buffering through the model.

## Runtime, states, and verification

The runtime needs Android API 26+, Android Studio's configured JDK/SDK, Node.js 22+, and an OpenRouter API key. The emulator connects to `http://10.0.2.2:8787/chat`; releases accept HTTPS only. The endpoint persists, while conversations, checkbox values, and local saves remain session-only.

Observable UI states are unconfigured, empty, incrementally loading, rendered, recoverable transport failure, render failure, and unavailable media. New conversation cancels inference and removes active surfaces. Representative evaluation prompts cover a flight ticket, illustrative weather, a playable video card, a horizontal recommendation rail, a mixed vertical feed, unsupported live-data claims, malformed output, and an untrusted media URL.

Acceptance criteria:

- The exact surface catalog is selected from the client's ordered supported catalog IDs.
- A loading surface appears before inference completes and is replaced in place.
- Only validated components, bindings, media identifiers, and `ask`/local-save actions render.
- The trusted reference video plays through Media3 and an invented URL is rejected.
- Flight/weather cards disclose that generated values are illustrative when no live provider exists.
- Build, JVM tests, Node tests, lint, instrumentation tests, and live provider prompts are recorded in `docs/verification.md`.

Remaining production limitations must stay explicit: no live travel/weather grounding, authentication gateway, quota/rate-limit service, durable storage, or broad accessibility/device matrix yet.
