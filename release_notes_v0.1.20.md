## NuvioDeck v0.1.20 — Xbox-Style Outlined Controller Hint Toolbar Badges & Continue Watching Playback Fix

> [!WARNING]
> **Known Issue (On-Screen Virtual Keyboard):** The Windows on-screen virtual keyboard (`TabTip.exe`) controller focus integration is still buggy and not yet resolved.

### Highlights

#### 1. Xbox-Style Outlined Controller Hint Toolbar Badges (`ControllerHintToolbar.kt`, `controls.css`, `controls.js`)
- **Colored Outline & Letter Design (`A`, `B`, `X`, `Y`):** Replaced solid-filled circular face button badges with Xbox-style unfilled circular outlines (`1.75.dp` stroke) where both the ring outline and the inner button letter follow the vibrant Xbox button color:
  - `A`: Xbox Green (`#38D430`)
  - `B`: Xbox Red (`#FF3B30`)
  - `X`: Xbox Blue (`#2899FF`)
  - `Y`: Xbox Amber/Yellow (`#FFC800`)
- **White Outlined Non-Colored Glyphs (`Menu`, `View`, `LB`, `RB`, `LT`, `RT`, `LS`, `RS`, `D-Pad`):** Updated all non-colored controller glyphs to render with crisp pure-white (`#FFFFFF`) outlines and white inner labels/icons with a transparent interior, keeping the Menu (three horizontal lines) icon inside a circular outline and preserving the contoured `LB`/`RB` bumper silhouette.
- **Optical Cap-Height Letter Centering (`CenteredBadgeText`):** Added a custom `Layout` using `FirstBaseline` alignment (`capHeight = fontSize.toPx() * 0.74f`) in `ControllerHintToolbar.kt` and optical vertical centering in `controls.css` so capital button letters sit geometrically centered inside circular and bumper outlines instead of being pushed down by font ascent padding.
- **Xbox-Style Rounded-Rectangle Semi-Transparent Dark Backdrop:** Updated the toolbar surface shape from a semicircle pill (`24.dp` / `999px`) to a subtle rounded rectangle (`RoundedCornerShape(8.dp)` / `border-radius: 8px`) with a clean translucent dark charcoal fill (`Color(16, 16, 18, 0.68)`).

#### 2. Fixed "Continue Watching" Playback Unavailable Bug (`MainAppContent.kt`, `MetaDetailsRepository.kt`, `PlaybackAvailability.kt`, `TraktProgressRepository.kt`, `WatchProgressMetadataProjection.kt`, `HomeScreen.kt`)
- **Canonical Playback Target Resolution (`MetaDetailsRepository.resolvePlaybackTarget`):** Resolved the bug where clicking an item in **Continue Watching** sometimes failed with `"Playback isn’t available for this title with your current setup."` even though playing from the Details page worked. Launching playback from Continue Watching now resolves `tmdb:` -> `tt` IMDb IDs, canonical episode `video.id` values from `MetaDetails.videos`, normalized media types, and pending startup addon manifests before checking stream availability.
- **Cached Embedded Streams & Hydrated `videoId` Preservation:** Updated `MetaDetailsRepository.findEmbeddedStreams` to search `cachedMetaByRequestKey` in addition to `_uiState.value.meta`, updated `TraktProgressRepository.hydrateEntriesWithAddonMeta` and `WatchProgressMetadataProjection.enrichWatchProgressEntry` to hydrate `videoId` from resolved `MetaDetails`, and prevented stale `tmdb:` fallback IDs from overwriting hydrated `videoId` values in `HomeScreen.withFallbackMetadata`.
