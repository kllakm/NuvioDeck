# Upstream Patch Registry (`UPSTREAM_PATCHES.md`)

This registry documents all files modified outside the self-contained `controller/` package relative to the upstream `NuvioMedia/NuvioDesktop` repository. It serves as an authoritative merge guide to preserve custom controller support, spatial navigation, and visual enhancements during upstream pulls.

---

## Architecture Summary
All controller polling, input normalization, spatial focus traversal, sound synthesis, and on-screen overlays reside strictly inside `composeApp/src/*/kotlin/com/nuvio/app/controller/`.
Edits to core NuvioDesktop files are minimized to lightweight 1-line hook-ins or wrapper calls to ensure effortless, conflict-free upstream git merges.

---

## Upstream Patch Registry

### 1. Build & Runtime Configuration
| File Path | Nature of Patch | Purpose / Justification |
| :--- | :--- | :--- |
| `composeApp/build.gradle.kts` | Packaging target & dependencies | Sets `packageName = "NuvioDeck"` and `menuGroup = "NuvioDeck"` to compile standalone binary as `NuvioDeck.exe`. Includes `jna` dependency in `desktopMain` and fallback API endpoint properties during build execution. |
| `composeApp/Configuration/DesktopVersion.properties` | Version properties synchronization | Sets `VERSION_NAME=0.1.20` and `VERSION_CODE=20` aligned with fork version bump. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/storage/DesktopStorage.kt` | Isolated storage & legacy migration hook | Redirects persistent storage to `%APPDATA%\NuvioDeck` (and `%LOCALAPPDATA%\NuvioDeck\Cache`). On first run, automatically and non-destructively migrates/copies `.properties` files from legacy `%APPDATA%\Nuvio`. |
| `composeApp/src/desktopMain/native/windows/player_bridge.cpp` | Native player bridge directory isolation | Redirects WebView2 user data directory and MPV shader cache directory to `%LOCALAPPDATA%\NuvioDeck\`. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/build/NuvioDeckVersionConfig.kt` | Central version & repo configuration | Standalone constants for fork version name (`0.1.20`), upstream base version (`0.1.33`), fork repo (`kllakm/NuvioDeck`), dual-version display string, and fork attribution. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/build/AppVersionPolicy.desktop.kt` | User agent & dual version policy hook | Connects `displayVersionName` and `basedOnVersionName` to `NuvioDeckVersionConfig` and sets `userAgentAppName` to `"NuvioDeck"`. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/settings/DesktopRendererSettings.desktop.kt` | Skiko render & refresh rate sync | Adds programmatic fallback settings for `skiko.fps = "-1"` and `skiko.vsync = "true"` in `applyBeforeCompose()` to unlock 90Hz/120Hz/144Hz monitors and handheld displays. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/auth/DeviceSessionRegistration.desktop.kt` | Session registration hook | Registers client device name as `"NuvioDeck"`. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/network/ServerConfiguration.kt` | Fallback configuration hook | Injects default Supabase production endpoints (`https://api.nuvio.tv`) and public keys when local properties are unset. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/updater/AppUpdaterPlatform.desktop.kt` | Auto-updater fork repo redirection | Points GitHub release source to `kllakm/NuvioDeck` and compares releases against `NuvioDeckVersionConfig.FORK_VERSION_NAME`. |
| `composeApp/src/androidFull/kotlin/com/nuvio/app/features/updater/AppUpdaterPlatform.android.kt` | Auto-updater fork repo redirection (Android) | Points release source to `kllakm/NuvioDeck`. |
| `composeApp/src/iosMain/kotlin/com/nuvio/app/features/updater/AppUpdaterPlatform.ios.kt` | Auto-updater fork repo redirection (iOS) | Points release source to `kllakm/NuvioDeck`. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/updater/AppUpdaterRepository.kt` | Graceful updater fallback | Treats HTTP 404 or missing release lists as `NoChannelReleaseException()`, preventing upstream overwrite prompts and suppressing errors. |

### 2. Root Application & Shell Mounting
| File Path | Nature of Patch | Purpose / Justification |
| :--- | :--- | :--- |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/Main.kt` | Window title & controller init hook | Sets desktop window title to `"NuvioDeck"` and invokes `DesktopControllerWindowIntegration.initialize(window)` to initialize XInput/DirectInput polling and window focus listeners. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/MainAppContent.kt` | Overlay and host mount hooks & Continue Watching playback target resolution | Mounts `ControllerNavigationHost` and `ControllerMainOverlay` at the root composition level, and resolves canonical playback targets (`MetaDetailsRepository.resolvePlaybackTarget`) when launching Continue Watching items. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/MetaDetailsRepository.kt` | Canonical playback target & cached embedded streams resolution | Adds `resolvePlaybackTarget`, `peekResolvedVideoId`, and `normalizePlaybackType`, and searches `cachedMetaByRequestKey` in `findEmbeddedStreams` so Continue Watching items resolve `tmdb:` -> `tt` IDs and episode `video.id` values identically to the Details page. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/streams/PlaybackAvailability.kt` | Normalized playback availability check | Adds `canStreamOrResolve` and normalizes media types (`movie`/`series`) so Continue Watching items with `tmdb:` IDs or pending startup addon manifests remain playable. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/AppGate.kt` | Background replacement & gate hook | Integrates `CinematicBackground` for boot/login/profile screens and mounts `ControllerGateOverlay`. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/MainTabsDestination.kt` | Key mapping hook | Exposes string keys for active destinations to allow `ControllerFocusEngine` to isolate tab navigation. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/AppShellComponents.kt` | Focusable hooks on navigation bars | Applies `.controllerFocusable` with `hideFocusRing = true` and `tokens.colors.accent.copy(alpha = 0.32f)` solid background fill to left-hand sidebar items (`sidebar_item_*`, `sidebar_profile_trigger`) and top navigation items with `groupKey = "sidebar"` and `groupKey = "top_nav"`. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/DesktopNavigationBar.kt` | Nav zone focus hook | Makes desktop navigation bar items accessible via spatial controller traversal. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/jelly/JellyTabs.kt` | Tab bar focus hook | Applies `.controllerFocusable` to jelly tab indicators with bumper cycling support. |

### 3. Core UI & Shared Components
| File Path | Nature of Patch | Purpose / Justification |
| :--- | :--- | :--- |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/AppBrandWordmark.kt` | Brand lockup & moniker badge | Renders a styled, theme-adaptive "DECK" moniker badge alongside the brand wordmark image across boot, login, and profile screens. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/SettingsAttribution.kt` | Fork attribution & dual-version display | Displays `"NuvioDeck — A controller-first fork of Nuvio, developed by kllakm..."` and `"NuvioDeck v0.1.01 (based on Nuvio v0.1.33)"`. |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | String branding hooks | Updates `app_brand_name`, `compose_player_pip_window_title`, and `compose_about_made_with` to NuvioDeck branding and fork attribution. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/Components.kt` | Vertical scroll hook in `NuvioScreen` | Registers the screen's root `LazyListState` with `RegisterControllerVerticalScroll(listState)`. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/ShelfComponents.kt` | Shelf state wrapper hook | Wraps `NuvioShelfSection` with `ProvideControllerRowLazyListState(state)` and hooks `.controllerFocusable` onto `view_all_pill_`. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/PosterLift.kt` | Universal poster card focus hook | Attaches `.controllerFocusable` to media poster cards, mapping `A` to click and `X` to long-press options. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/ProfileMeshBackground.kt` | Atmospheric background wrapper | Replaces static gradient with the ultra-smooth, noise-dithered `CinematicBackground`. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/CinematicBackground.kt` | New component file | Standalone organic deep-indigo/violet canvas background with procedural noise dithering to prevent display banding. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/BottomSheet.kt` | Modal button focus hook | Adds `.controllerFocusable` to bottom sheet action items. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/Chip.kt` | Filter chip focus hook | Adds `.controllerFocusable` to chips for D-Pad selection. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/Dialog.kt` | Dialog button focus hook | Adds `.controllerFocusable` to confirm/cancel dialog buttons and options. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/DropdownChip.kt` | Dropdown focus hook | Adds `.controllerFocusable` to selectable dropdown triggers. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/FloatingPrompt.kt` | Prompt action hook | Adds `.controllerFocusable` to floating resume prompt buttons. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/PosterZoomActionOverlay.kt` | Overlay button focus hook | Adds `.controllerFocusable` to poster zoom dialog action buttons. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/PlatformImageLoader.desktop.kt` | Persistent 1GB Disk Cache, 512MB RAM Cache & Multi-Core Parallel Decoding | Configures Coil 3 `MemoryCache` (512MB), `DiskCache` (1GB in `%LOCALAPPDATA%\NuvioDeck\Cache\image_cache`), and multi-core `fetcherCoroutineContext` / `decoderCoroutineContext` parallelism so full-resolution `1536px` media artwork loads rapidly without compression. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/AsyncImage.desktop.kt` | Upstream `1536px` Source Decode & `1.25x` High-DPI Supersampling | Restores upstream `MaxDesktopSourceSizePx = 1536`, `MinCustomDownscaleRatio = 1.08f`, and `FilterQuality.High` bicubic filtering, with `1.25x` supersampled `cacheSize` for razor-sharp rendering on High-DPI displays and focus/hover scale-ups. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/Theme.kt` | High-DPI Responsive UI Scale (`desktopUiScaleForWindow`) | Preserves the `1.0f..1.18f` scale up to `1920x1080` and smoothly scales High-DPI QHD/4K viewports up to `1.42f`. |

### 4. Features & Screens
| File Path | Nature of Patch | Purpose / Justification |
| :--- | :--- | :--- |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/HomeScreen.kt` | Vertical list registration hook | Hooks `RegisterControllerVerticalScroll(homeListState)` to drive Netflix/Android TV style row-by-row navigation. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HomeHeroSection.kt` | Hero CTA focus hook | Adds `.controllerFocusable` to the desktop hero banner play button (`home_hero_desktop_cta_${item.id}`). |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HomePosterHoverPreview.kt` | Hover preview focus hook | Prevents hover cards from conflicting with controller focus states. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/library/LibraryScreen.kt` | Library list registration hook | Registers `LazyListState` and provides controller focus for filter chips and shelves. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/catalog/CatalogScreen.kt` | Grid state registration hook | Hooks `RegisterControllerVerticalScroll(lazyGridState = gridState)` for multi-column poster grid navigation. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/collection/FolderDetailScreen.kt` | Folder bumper & grid hooks | Handles `onBumperLeft`/`onBumperRight` (`LB`/`RB`) to cycle category tabs and registers grid scroll state. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/MetaDetailsScreen.kt` | Detail screen handler hook | Registers screen-level controller handlers and action shortcuts. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/DetailActionButtons.kt` | Action buttons focus hooks & Play inversion | Adds `.controllerFocusable` to Play, Trailer, Library, and more options buttons with halo outline. Inverts the "Play" button to a sleek black background (`#0D0E15`) with white text and a 2.dp white border when controller-focused for maximum visual contrast. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/DetailSeriesContent.kt` | Season/Episode row focus & scroll hooks | Adds `.controllerFocusable` before `.clip` on season and episode cards to eliminate border clipping, wraps rows in `ProvideControllerRowLazyListState`, expands start padding (14.dp), and hooks `ControllerFocusEngine.navigateLazyRowIndex` for edge-triggered viewport-aware horizontal scrolling without premature row shifting. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/DetailTrailersSection.kt` | Trailer card focus & vertical padding hooks | Moves `.controllerFocusable` before `.clip` on trailer cards and adds `vertical = 8.dp` content padding to `LazyRow` to eliminate focus outline clipping. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/auth/AuthScreen.kt` | Auth input focus hooks | Adds `.controllerFocusable` to email/password fields and login buttons. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/profiles/ProfileSelectionScreen.kt` | Profile card Netflix styling, logo relocation & 420ms smooth exit | Replaces white outline border with Netflix-style translucent rounded filled card selection (`Color.White.copy(alpha = 0.15f)`) enclosing avatar & name, relocates `MemberBrandWordmark` to top-left, adds 700ms mouse hover/focus fade-outs, and adds 420ms animated smooth dismissal (`dismissAlpha`, `dismissScale`) on profile selection and PIN entry. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/AppGate.kt` | Gate transition & profile overlay animation | Enhances `AnimatedContent` transition to `(fadeIn(450) + scaleIn(0.96f)).togetherWith(fadeOut(350))` and profile overlay exit to `fadeOut(500) + scaleOut(1.03f)` for a smooth cinematic app entry without abrupt dismissals or premature black loading masks. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/CinematicBackground.kt` | Anti-banding dither & cinematic backdrop | Deploys 128x128 triangular-distribution micro-dither texture and unified deep wine/crimson radial gradient to eliminate color banding across login/boot/profile screens. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerSettingsRepository.kt` | Auto stream play default | Sets `StreamAutoPlayMode.FIRST_STREAM` as default so first source plays automatically. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/profiles/PinEntryDialog.kt` | Virtual keypad focus hooks | Adds `.controllerFocusable` to numeric digits `0–9`, backspace, and submit buttons for full controller PIN entry. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/profiles/AvatarRepository.kt` | Avatar fallback hook | Pre-populates avatar catalogs with default bundled images to avoid broken network lookups. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/profiles/DefaultAvatarCatalog.kt` | New catalog asset file | Standalone dictionary of default standard avatars with local asset paths. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/profiles/ProfileModels.kt` | Storage URL resolver hook | Resolves `storage_path` into full Supabase storage CDN URLs. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/search/SearchScreen.kt` | Search bar input hook & Y-button shortcut | Hooks virtual keyboard invocation on text field activation, autofocuses and triggers OS virtual keyboard on focus or 'Y' hotkey press, and yields focus without premature dismissal. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/SettingsComponents.kt` | Settings row fill-only focus hooks | Adds `.controllerFocusable` with `hideFocusRing = true` and `tokens.colors.accent.copy(alpha = 0.32f)` background fill on sidebar, nav, switch, and catalog rows. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/SettingsScreen.kt` | Settings column isolation hook | Groups sidebar and content columns via `LocalControllerGroupKey` to enforce strict vertical column isolation. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/LicensesAttributionsPage.kt` | Fork repo attribution URL | Directs desktop attribution and license repository link to `kllakm/NuvioDeck`. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/PosterZoomActionOverlay.kt` | Context menu highlight state | Replaces outer border outline with solid accent background fill on focused action rows. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/streams/StreamsScreen.kt` | Stream modal handler hook | Manages controller selection and modal exit handlers on streams list. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/streams/ProviderFilterRow.kt` | Addon filter chip focus hook | Adds `.controllerFocusable` with `hideFocusRing = true` and solid accent background fill (`0.32f`) to stream provider filter chips. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/streams/StreamCard.kt` | Stream card focus hook | Adds `.controllerFocusable` with `hideFocusRing = true` and solid accent background fill (`0.32f`) to individual stream result cards. |

### 5. Desktop Native Video Player Integration
| File Path | Nature of Patch | Purpose / Justification |
| :--- | :--- | :--- |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/controller/WindowsControllerService.kt` | Win32 focus validation hook & input pane focus recovery | Validates window visibility/focus, child window (`IsChild`, `GetAncestor`), and process ID checks, automatically restoring application window activation and controller polling when the OS input pane closes. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/controller/WindowsVirtualKeyboardManager.kt` | Application-level input pane & focus restoration manager | Launches `TabTip.exe` after establishing Compose text-input focus and coordinates window focus/activation transitions and input focus restoration when the input pane opens and closes. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/controller/DesktopControllerWindowIntegration.kt` | Window focus & activation state listener | Tracks `WindowFocusListener` and `WindowListener` activation events to restore Compose input focus and preserve controller active state across input pane sessions. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/player/PlayerEngine.desktop.kt` | Player event bridge hook | Forwards controller button presses (`A` play/pause, `B` exit, `LB`/`RB` seek) into the native player engine. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/player/desktop/NativePlayerController.kt` | Gamepad player bridge | Handles gamepad input events when native video playback is active. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/player/desktop/NativePlayerHost.kt` | Native canvas focus hook | Prevents webview/native canvas from swallowing gamepad events. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/player/desktop/DesktopAppFullscreen.kt` | Fullscreen mode hook | Ensures window focus is properly asserted when entering fullscreen on startup. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/player/desktop/DesktopWindowModeStorage.kt` | Window mode persistence hook | Persists fullscreen and windowed state preferences. |
| `composeApp/src/desktopMain/resources/player-ui/controls.css` | Player OSD styling hook | Implements controller focus styling across header, seekbar, action buttons, and modal tracks; replaces white focus borders on track rows, stream sources, and subtitle menus with standard grey/white solid background fills (`rgba(255, 255, 255, 0.24)` / `rgba(255, 255, 255, 0.34)`). |
| `composeApp/src/desktopMain/resources/player-ui/controls.html` | Player OSD markup hook | Adds gamepad hint badges to player controls markup. |
| `composeApp/src/desktopMain/resources/player-ui/controls.js` | Player OSD JS handler hook | Implements 3-tier spatial D-Pad navigation (Tier 0 header, Tier 1 seekbar, Tier 2 action row), modal focus trapping & restoration, complete virtual list traversal & auto-scroll for Stream Sources, multi-rail D-Pad navigation for Subtitles (Languages <-> Subtitles <-> Style) with DOM-tearing prevention and signature caching, OSD wake-on-D-Pad, instant pointer mode recovery on mouse clicks/moves, 3.5s controller hint toolbar idle autohide, and sequential B button navigation (dismiss OSD -> return to details page without exiting fullscreen). |

---

### 6. Asset & Branding Integration (Logo & Launcher Icons)
| Asset Category | Target Resolutions & Formats | Treatment & Deployment |
| :--- | :--- | :--- |
| Windows Launcher Icon (`composeApp/src/desktopMain/resources/icons/nuvio-app-icon-transparent.ico`) | Multi-resolution `.ico` (16x16, 24x24, 32x32, 48x48, 64x64, 128x128, 256x256) | **Regenerated with Full Mipmaps:** Replaced single 256px entry with a comprehensive multi-resolution ICO embedding 7 high-quality downsampled mipmaps for crisp Windows taskbar, window title, alt-tab, and executable icon rendering. |
| Desktop App Icons (`composeApp/src/desktopMain/resources/icons/`) | 256x256, 512x512, 1024x1024 `.png` | **Updated High-Res Assets:** Deployed the cyan-to-emerald play emblem with centered aspect-ratio geometry across 256x256 (`nuvio-app-icon-transparent.png`, `app-icon-original-transparent.png`), 512x512, and 1024x1024. |
| Compose Runtime Icons (`composeApp/src/commonMain/composeResources/drawable/`) | 256x256 `.png` | **Updated Runtime Drawables:** Deployed `app_icon_original_transparent.png` (transparent window icon) and `app_icon_original.png` (pure black field theme preview). |
| Brand Wordmarks (`composeApp/src/commonMain/composeResources/drawable/`) | 1024x324 `.png` | **Updated High-Res Wordmark:** Deployed `app_logo_wordmark.png` and `app_logo_wordmark_original.png` with the new cyan-to-emerald emblem and crisp typography on boot, login, and profile screens. |

---

### 7. Packaging & Identity Decoupling (Side-by-Side Coexistence)
| File Path | Nature of Patch | Purpose / Justification |
| :--- | :--- | :--- |
| `composeApp/build.gradle.kts` | MSI Upgrade UUID & Vendor Decoupling | Sets unique Windows MSI `upgradeUuid = "a8f4d2e1-7c3b-4e9a-9b1d-5f6e8a2c4d90"`, `vendor = "Kuma Labs"`, `packageName = "NuvioDeck"`, and enables `dirChooser = true` so NuvioDeck and upstream NuvioDesktop can install and run side-by-side without installer conflict or accidental overwrites. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/storage/DesktopStorage.kt` | Storage & Cache Directory Isolation | Routes all settings, databases, updates, and caches into dedicated `NuvioDeck` AppData and LocalAppData folders to avoid shared state corruption. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/settings/WindowsAppShortcutIconUpdater.kt` | Windows Shortcut Decoupling | Directs shortcut icon caching to `NuvioDeck/icons` and updates `NuvioDeck.lnk` shortcuts across Desktop, Start Menu, and Taskbar. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/settings/AppIconPlatform.desktop.kt` | Launcher Process Resolution | Resolves `NuvioDeck.exe` on icon change restarts. |
| `.github/workflows/update-store-source.yml` | 0-IPA Guard in Release Action | Automatically detects releases without `.ipa` assets (such as Windows MSI desktop releases) and completes cleanly with exit 0. |
