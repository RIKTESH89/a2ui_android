# UIAgent development server

Node.js 22+ server for the UIAgent AndroidX A2UI client. It has no runtime npm dependencies and currently defaults to OpenRouter with `nvidia/nemotron-3-ultra-550b-a55b:free`.

```sh
cp .env.example .env
# Add OPENROUTER_API_KEY to .env
npm start
npm test
```

`GET /health` reports the model, A2UI protocol, server-supported catalog IDs, and incremental transport. `POST /chat` accepts a bounded prompt/context payload. The client includes `metadata.a2uiClientCapabilities.supportedCatalogIds`, ordered by preference; the server selects the first mutually supported catalog for the lifetime of the new surface.

When the client accepts `application/x-ndjson`, the server immediately emits `createSurface`, an empty data model, and a loading component tree. After inference and strict validation it emits final data/components and a completion record. Disconnecting the Android client cancels the upstream model call. Non-streaming JSON remains available as a compatibility fallback.

For real card imagery, add either `PEXELS_API_KEY` or `PIXABAY_API_KEY`. The model still emits only semantic `uiagent://image/...` identifiers. On the local server, images use a bounded 24-hour in-memory cache. When `MEDIA_SIGNING_KEY` is configured, the service instead issues 24-hour HMAC-signed asset references that any serverless instance can validate and proxy from `/media/{assetId}`. Android displays provider/contributor attribution and keeps the gradient artwork as a failure fallback. Pixabay images are downloaded rather than permanently hotlinked. iStock is intentionally not integrated without a separately licensed commercial account/API agreement.

## Vercel deployment

The repository includes Vercel functions for the same public contract: `GET /health`, `POST /chat`, and `GET /media/{assetId}`. Link the `a2ui` directory to a Vercel project, then configure these Production environment variables in the Vercel dashboard or CLI:

- `LLM_PROVIDER=openrouter`
- `OPENROUTER_API_KEY` and `OPENROUTER_MODEL`
- `IMAGE_PROVIDERS=pexels` and `PEXELS_API_KEY`
- `MEDIA_SIGNING_KEY`, set to a randomly generated secret of at least 32 characters
- `MAX_CONCURRENT_REQUESTS=1`

Deploy with `vercel --prod`, verify `https://your-project.vercel.app/health`, and enter `https://your-project.vercel.app/chat` in the Android app. The Vercel endpoint is independent of the laptop server. The function has a 300-second maximum duration; free-model availability and upstream rate limits can still cause failed requests. Before production use, add authentication, durable distributed rate limiting, abuse controls, logging with redaction, and an uptime/alerting policy.

The server validates component names/properties, graph reachability/depth, catalog choice, media identifiers, data paths, action shapes, text and payload sizes. The model can request only `uiagent://` media identifiers; Android resolves those through a trusted registry. Generated `ask` actions can only return a short non-URL prompt, and `save` is local-only.

No live flight or weather provider is connected. The model is instructed to label invented values as illustrative and the validator is not a factuality proof. This service binds to loopback, limits concurrent inference (default one), bounds headers/body/timeouts, disables caching, and adds request IDs. It is still a development service: deploy behind authenticated HTTPS with user authorization, distributed rate limits, audit/abuse controls, and operational monitoring.

Configuration:

- `LLM_PROVIDER=openrouter|ollama|gemini`
- `OPENROUTER_MODEL` and `OPENROUTER_API_KEY`
- `IMAGE_PROVIDERS=pexels,pixabay` controls provider preference
- `PEXELS_API_KEY` and `PIXABAY_API_KEY` are optional server-only image credentials
- `MEDIA_SIGNING_KEY` enables stateless HMAC-signed image references for serverless deployment
- `OLLAMA_BASE_URL`, restricted to loopback
- `OLLAMA_MODEL` (default `qwen3:14b`)
- `GEMINI_MODEL` and `GEMINI_API_KEY` for the optional cloud provider
- `MAX_CONCURRENT_REQUESTS` from 1–4
- `PORT` (default 8787)
