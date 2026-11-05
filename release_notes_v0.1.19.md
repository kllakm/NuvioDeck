### NuvioDeck v0.1.19 Release Notes

> **Known Issue:** The Windows on-screen virtual keyboard integration is still buggy and not yet resolved.

#### Uncompressed Upstream Poster Quality, Parallel Asset Decoding, High-DPI Login & App Rendering, Login Cursor Hiding & Settings Default Selection

- **Reverted Poster Image Compression & Added Multi-Core Parallel Asset Loading:**
  - Restored full-resolution `1536px` image decoding (`MaxDesktopSourceSizePx = 1536`, `MinCustomDownscaleRatio = 1.08f`) and `FilterQuality.High` bicubic filtering from upstream `NuvioMedia/NuvioDesktop:Dev` in `AsyncImage.desktop.kt`, along with `1.25x` High-DPI supersampling in `ScaledBitmapPainter` so posters and avatars remain razor-sharp even during `1.04x–1.08x` focus/hover scale-ups.
  - Improved asset loading performance without lowering image quality by expanding Coil's `MemoryCache` to `512MB` and `DiskCache` to `1GB`, and configuring multi-core parallel `fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(32))` and `decoderCoroutineContext(Dispatchers.Default.limitedParallelism(cpuCores))` in `PlatformImageLoader.desktop.kt`.

- **High-DPI Display Enhancements on Login Screen & Across Main App:**
  - Upgraded `CinematicBackground.kt` with a `256x256` ultra-fine sub-LSB anti-banding dither rendered in 1:1 native physical device pixel coordinates (`scale(1f / density.density)`) and 10-stop radial gradients, eliminating upscaled low-resolution grain on 1440p and 4K High-DPI displays.
  - Updated `AppBrandWordmark.kt`, `ProfileSelectionScreen.kt`, and `ProfileBackgroundBackdrop.kt` to render with `FilterQuality.High` bicubic interpolation.
  - Enhanced `desktopUiScaleForWindow` in `Theme.kt` to preserve the `1.0f..1.18f` curve up to 1080p (`1920x1080`) while smoothly scaling QHD (`1440p`) and 4K High-DPI viewports up to `1.42f`.

- **Immediate Mouse Cursor Hiding on Login Screen:**
  - Updated `DesktopControllerCursorManager` and `WindowsControllerService` to immediately apply the transparent cursor across the window hierarchy and trigger a zero-delta Win32 `SetCursorPos(pt.x, pt.y)` refresh with a `180ms` synthetic mouse-event guard whenever controller input occurs—ensuring the mouse cursor disappears immediately on the Login / Profile Selection screen even when the physical mouse has been stationary since launch, and only reappears when the mouse is physically moved (`>= 6px`).
  - Updated `ProfileAvatarCard` and `AddProfileCard` in `ProfileSelectionScreen.kt` so controller activation immediately clears `isMouseHovered`.

- **Settings Screen Default Focus on Top Left-Column Item:**
  - Marked the first item (`SettingsCategory.Look`) in `TabletSettingsScreen` with `isDefaultFocus = true`, added initial focus targeting to `firstSidebarNodeId`, and updated `ControllerFocusEngine.upsertNode` / `ensureValidFocus` to prioritize `isDefaultFocus` and `"settings_sidebar"` nodes over `"settings_content"` before user navigation begins.

- **Windows MSI Installer:**
  - Packaged standalone installer `NuvioDeck-Windows-x64-0.1.19.msi`.

