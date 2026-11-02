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
| `composeApp/Configuration/DesktopVersion.properties` | Version properties synchronization | Sets `VERSION_NAME=0.1.10` and `VERSION_CODE=10` aligned with fork version bump. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/storage/DesktopStorage.kt` | Isolated storage & legacy migration hook | Redirects persistent storage to `%APPDATA%\NuvioDeck` (and `%LOCALAPPDATA%\NuvioDeck\Cache`). On first run, automatically and non-destructively migrates/copies `.properties` files from legacy `%APPDATA%\Nuvio`. |
| `composeApp/src/desktopMain/native/windows/player_bridge.cpp` | Native player bridge directory isolation | Redirects WebView2 user data directory and MPV shader cache directory to `%LOCALAPPDATA%\NuvioDeck\`. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/build/NuvioDeckVersionConfig.kt` | Central version & repo configuration | Standalone constants for fork version name (`0.1.10`), upstream base version (`0.1.33`), fork repo (`kllakm/NuvioDeck`), dual-version display string, and fork attribution. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/build/AppVersionPolicy.desktop.kt` | User agent & dual version policy hook | Connects `displayVersionName` and `basedOnVersionName` to `NuvioDeckVersionConfig` and sets `userAgentAppName` to `"NuvioDeck"`. |
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
| `composeApp/src/commonMain/kotlin/com/nuvio/app/MainAppContent.kt` | Overlay and host mount hooks | Mounts `ControllerNavigationHost` and `ControllerMainOverlay` at the root composition level. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/AppGate.kt` | Background replacement & gate hook | Integrates `CinematicBackground` for boot/login/profile screens and mounts `ControllerGateOverlay`. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/MainTabsDestination.kt` | Key mapping hook | Exposes string keys for active destinations to allow `ControllerFocusEngine` to isolate tab navigation. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/AppShellComponents.kt` | Focusable hooks on navigation bars | Applies `.controllerFocusable` to sidebar icons and top navigation items with `groupKey = "sidebar"` and `groupKey = "top_nav"`. |
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
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/DetailActionButtons.kt` | Action buttons focus hooks | Adds `.controllerFocusable` to Play, Trailer, Library, and more options buttons with halo outline. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/DetailSeriesContent.kt` | Season/Episode row focus & scroll hooks | Adds `.controllerFocusable` to season pills/cards and episode lists, wraps rows in `ProvideControllerRowLazyListState`, expands start padding (14.dp) to eliminate left border clipping, and hooks `ControllerFocusEngine.navigateLazyRowIndex` for edge-triggered viewport-aware horizontal scrolling without premature row shifting. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/auth/AuthScreen.kt` | Auth input focus hooks | Adds `.controllerFocusable` to email/password fields and login buttons. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/profiles/ProfileSelectionScreen.kt` | Profile card auto-focus hook | Adds `.controllerFocusable` to profile circles with auto-focusing on first card upon controller detection. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/profiles/PinEntryDialog.kt` | Virtual keypad focus hooks | Adds `.controllerFocusable` to numeric digits `0–9`, backspace, and submit buttons for full controller PIN entry. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/profiles/AvatarRepository.kt` | Avatar fallback hook | Pre-populates avatar catalogs with default bundled images to avoid broken network lookups. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/profiles/DefaultAvatarCatalog.kt` | New catalog asset file | Standalone dictionary of default standard avatars with local asset paths. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/profiles/ProfileModels.kt` | Storage URL resolver hook | Resolves `storage_path` into full Supabase storage CDN URLs. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/search/SearchScreen.kt` | Search bar input hook | Hooks virtual keyboard invocation on text field activation. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/SettingsComponents.kt` | Settings row focus hooks | Adds `.controllerFocusable` to preference toggle rows and sliders. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/SettingsScreen.kt` | Settings column isolation hook | Groups sidebar and content columns via `LocalControllerGroupKey` to enforce strict vertical column isolation. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/LicensesAttributionsPage.kt` | Fork repo attribution URL | Directs desktop attribution and license repository link to `kllakm/NuvioDeck`. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/PosterZoomActionOverlay.kt` | Context menu highlight state | Replaces outer border outline with solid accent background fill on focused action rows. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/streams/StreamsScreen.kt` | Stream modal handler hook | Manages controller selection and modal exit handlers on streams list. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/streams/ProviderFilterRow.kt` | Addon filter chip focus hook | Adds `.controllerFocusable` to stream provider filter chips. |
| `composeApp/src/commonMain/kotlin/com/nuvio/app/features/streams/StreamCard.kt` | Stream card focus hook | Adds `.controllerFocusable` to individual stream result cards. |

### 5. Desktop Native Video Player Integration
| File Path | Nature of Patch | Purpose / Justification |
| :--- | :--- | :--- |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/controller/WindowsControllerService.kt` | Win32 focus validation hook | Adds child window (`IsChild`, `GetAncestor`) and process ID (`GetWindowThreadProcessId`, `GetCurrentProcessId`) checks so controller polling loop remains active when focus shifts to player WebView2 child windows. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/controller/DesktopControllerWindowIntegration.kt` | AWT window focus loss guard | Verifies Win32 process/child focus before relinquishing controller focus on `windowLostFocus`. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/player/PlayerEngine.desktop.kt` | Player event bridge hook | Forwards controller button presses (`A` play/pause, `B` exit, `LB`/`RB` seek) into the native player engine. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/player/desktop/NativePlayerController.kt` | Gamepad player bridge | Handles gamepad input events when native video playback is active. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/player/desktop/NativePlayerHost.kt` | Native canvas focus hook | Prevents webview/native canvas from swallowing gamepad events. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/player/desktop/DesktopAppFullscreen.kt` | Fullscreen mode hook | Ensures window focus is properly asserted when entering fullscreen on startup. |
| `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/player/desktop/DesktopWindowModeStorage.kt` | Window mode persistence hook | Persists fullscreen and windowed state preferences. |
| `composeApp/src/desktopMain/resources/player-ui/controls.css` | Player OSD styling hook | Adds high-contrast controller focus rings (`2.5px solid #ffffff`, purple outer glow, `1.06x` scale) across header, seekbar, action buttons, and modal tracks. |
| `composeApp/src/desktopMain/resources/player-ui/controls.html` | Player OSD markup hook | Adds gamepad hint badges to player controls markup. |
| `composeApp/src/desktopMain/resources/player-ui/controls.js` | Player OSD JS handler hook | Implements 3-tier spatial D-Pad navigation (Tier 0 header, Tier 1 seekbar, Tier 2 action row), modal focus trapping & restoration, OSD wake-on-D-Pad, instant pointer mode recovery on mouse clicks/moves, 3.5s controller hint toolbar idle autohide, and sequential B button navigation (dismiss OSD -> return to details page without exiting fullscreen). |

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
