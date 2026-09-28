# Media provenance

`app/src/main/res/raw/uiagent_reference.mp4` is an eight-second, size-reduced excerpt of the reference video supplied by the user at `/Users/rikteshs20/Downloads/compose-agentic-flow.mp4`. It is bundled only as trusted local demo media for the `uiagent://video/reference` catalog identifier.

The model never receives a filesystem path and cannot select an arbitrary media URL. The Android catalog resolves that exact opaque identifier to the packaged raw resource and renders it with Media3.

Generated image components begin as one of four semantic `uiagent://image/...` identifiers. When a configured Pexels or Pixabay search succeeds, the server validates and downloads the selected image into a bounded 24-hour cache, then gives Android an opaque `uiagent://asset/...` reference containing provider attribution metadata. Android loads the bytes only from the configured UIAgent server and exposes a link to the validated official provider page. No stock image is checked into this repository; the UIAgent gradient artwork remains the offline/error fallback.

iStock content is not used. Its commercial licensing and API access must be arranged separately before it can be considered for this pipeline.

`app/src/main/res/drawable-nodpi/community_art.png` is legacy sample artwork and is not used by the UIAgent UI.
