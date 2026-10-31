<div align="center">

  <img src="https://nuvio.tv/assets/nuvio-app-logo-wordmark.webp" alt="Nuvio" width="320" />

  <p>
    A free, open-source media app for Windows, macOS, and Linux.
    <br />
    Bring your own sources. Nuvio turns them into a library with artwork, ratings, subtitles, and your place saved on every screen.
  </p>

  [Website](https://nuvio.tv) · [GitHub releases](https://github.com/NuvioMedia/NuvioDesktop/releases/latest) · [Support Nuvio](https://nuvio.tv/support)

</div>

---

## 🎮 About This Fork (Windows 11 Controller Edition)

This fork of **Nuvio Desktop** is purpose-built to bring a first-class, **native Windows 11 controller & 10-foot / handheld experience** to Nuvio Desktop (ideal for HTPCs, living-room setups, Xbox/PlayStation/Switch controllers on PC, and Windows handhelds like ROG Ally, Legion Go, and Claw).

Key goals of this fork:
- **Universal Windows 11 Controller Support:** Seamless hot-plugging and input polling across XInput, DirectInput, and HID controllers (Xbox, PlayStation DualSense/DualShock 4, Nintendo Switch Pro/Joy-Con, and generic gamepads), normalized internally to standard Windows Xbox controller conventions.
- **Zero-Friction Input Switching & Auto-Hide Mouse Cursor:** Automatically hides the system mouse cursor and suppresses passive hover states the moment you touch any controller stick, trigger, or button—and immediately restores the cursor as soon as you move or click the mouse or press a key.
- **Full 2D Spatial Navigation:** Complete D-Pad and analog stick navigation across the sidebar, hero banners, horizontal carousels, grids, detail screens, stream pickers, settings, dialogs, and the native desktop video player + WebView2 OSD.
- **Dynamic Bottom-Right Xbox-Glyph Controller Hint Toolbar:** Context-aware on-screen button prompts using standard Xbox glyphs (`A`, `B`, `X`, `Y`, `LB`, `RB`, `LT`, `RT`, `LS`, `RS`, `D-Pad`, `View`, `Menu`) in a borderless dark glass pill (`rgba(12, 12, 16, 0.68)`) that appear automatically during controller use and hide when using mouse/keyboard.
- **Ultra-Smooth Cinematic Cosmic Background:** Replaces static backdrops across boot/loading, user login, and profile selection pages with a dynamic, deep velvet indigo-to-amethyst-to-crimson gradient, complete with hardware-accelerated micro-dither to eliminate color banding on 1080p and 4K screens, breathing volumetric light leaks, and subtle floating stardust motes.

---

## ⚠️ Alpha Software - Slow Development - Testers Only

Nuvio Desktop is currently in alpha and is intended only for testers. It is under development and is not suitable for daily use.

Expect breaking changes with every update. Features, settings, stored data, and compatibility may change or stop working without notice. Do not rely on this build as your primary media app, and report any issues you encounter during testing.

## Get Nuvio Desktop

Download the latest upstream build from [GitHub Releases](https://github.com/NuvioMedia/NuvioDesktop/releases/latest).

- Windows: MSI installer / Standalone `.exe`
- macOS: DMG installer
- Linux: DEB, RPM, Flatpak, or AppImage

## Build from source

```bash
git clone https://github.com/NuvioMedia/NuvioDesktop.git
cd NuvioDesktop
```

### Run the app

```bash
./gradlew :composeApp:run
```

On Windows PowerShell:

```powershell
.\gradlew.bat :composeApp:run
```

### Package the app (Windows Standalone Build)

To compile a standalone Windows application bundle (`Nuvio.exe`) without building macOS or Linux targets:

```powershell
.\gradlew.bat :composeApp:createDistributable "-Pcompose.desktop.packaging.checkJdkVendor=false" --no-configuration-cache
```

The standalone executable is output to:
`composeApp\build\compose\binaries\main\app\Nuvio\Nuvio.exe`

For platform-specific installer packaging:

Windows PowerShell (MSI):

```powershell
.\gradlew.bat :composeApp:packageReleaseMsi --rerun-tasks
```

macOS:

```bash
./scripts/build-macos-release-dmgs.sh --package-only
```

Linux:

```bash
./gradlew :composeApp:packageReleaseDeb
```

The shared app is built with Kotlin Multiplatform and Compose Multiplatform.

## License

[GNU General Public License v3.0](./LICENSE)

---

## 📋 Fork Changelog & Implementation Log

### [2026-10-31] Native Windows 11 Controller Support, Auto-Hide Cursor & Dynamic Hint Toolbar

#### 1. Universal Windows 11 Controller Input & Cursor Management
- **Multi-Backend Polling & Hot-Plug Service (`WindowsControllerService.kt`):**
  - Implemented low-latency JNA polling across both **XInput** (`XInput1_4.dll`, `XInput9_1_0.dll`, `XInput1_3.dll`) and **Windows Multimedia / DirectInput** (`winmm.dll` via `joyGetPosEx` and `joyGetDevCapsW`) with automatic hot-plug detection and XInput/winmm device deduplication.
  - Added hardware-aware input normalization for Xbox controllers, Sony DualSense / DualShock 4, Nintendo Switch Pro Controller / Joy-Con, and generic DirectInput/HID pads into a unified internal `XboxButton` and analog layout.
  - Implemented radial deadzone filtering (`0.28f`) for left/right thumbsticks, analog trigger thresholds (`0.35f`), smooth right-stick vertical scrolling (`ControllerEvent.AnalogScroll`), and directional auto-repeat (`320ms` initial delay, `115ms` repeat cadence) for D-Pad and left stick navigation.
- **`isControllerActive` State & Auto-Hide Mouse Cursor (`ControllerModels.kt`, `DesktopControllerWindowIntegration.kt`, `NativePlayerHost.kt`):**
  - Tracks `ControllerManager.isControllerActive` reactively across the app.
  - Transitions to `true` immediately on any controller button press, trigger pull, or stick movement outside the deadzone.
  - Transitions back to `false` immediately on physical mouse movement (`>= 5px` delta threshold to ignore micro-jitter), mouse button clicks, scroll wheel events, or keyboard key presses.
  - Automatically swaps the system cursor to a `1x1` transparent cursor across the AWT window tree, Compose root (`pointerHoverIcon`), and native player canvas/WebView2 overlay when `isControllerActive` is `true`, while suppressing passive hover states on cards, shelves, and navigation bars so a stationary hidden cursor never steals focus.

#### 2. 2D Spatial Navigation & Context-Aware Controls (`ControllerFocusEngine.kt`)
- **Spatial Focus Engine & Zones:**
  - Built a 2D spatial navigation engine supporting `Navigation` (sidebar / top pill bar), `Content` (hero banners, horizontal shelves, grids, detail screens, settings), `Overlay` (dialogs, bottom sheets, dropdowns, context menus), and `Player` focus zones.
  - Added `Modifier.controllerFocusable(...)` with high-contrast focus ring rendering, subtle focus scaling, automatic `BringIntoViewRequester` + `LazyListState` / `ScrollState` scrolling, and per-node custom action labels (`A`, `X`, `Y`).
  - Added seamless zone transitions between the collapsible/adaptive desktop sidebar and main content area, automatically expanding compact/adaptive sidebars while focused.
- **Screen-by-Screen & Modal Integration:**
  - Wired spatial focus and context-aware controller shortcuts (`RegisterControllerHandler` & `RegisterControllerVerticalScroll`) across:
    - App Shell & Navigation (`MainAppContent.kt`, `MainTabsDestination.kt`, `AppShellComponents.kt`, `DesktopNavigationBar.kt`)
    - Home & Hero Carousel (`HomeScreen.kt`, `HomeHeroSection.kt`)
    - Discover & Catalog Browsing (`DiscoverScreen.kt`)
    - Search & Virtual/Input Controls (`SearchScreen.kt`)
    - Library & Trakt Lists (`LibraryScreen.kt`)
    - Media Details, Season Tabs & Episode Cards (`DetailScreen.kt`)
    - Stream Selection & Filters (`StreamScreen.kt`)
    - Settings, Addons & Profile Switcher (`SettingsScreen.kt`, `AddonManagementScreen.kt`, `ProfileSelectionScreen.kt`)
    - Shared Dialogs, Bottom Sheets & Overlays (`Dialog.kt`, `BottomSheet.kt`, `Components.kt`, `Chip.kt`, `DropdownChip.kt`, `FloatingPrompt.kt`, `PosterZoomActionOverlay.kt`, `JellyTabs.kt`)
- **Desktop Native Video Player & WebView2 Overlay (`PlayerEngine.desktop.kt`, `NativePlayerController.kt`, `controls.js`, `controls.css`, `controls.html`):**
  - Bridged controller state and commands into the native mpv + WebView2 player overlay:
    - `A`: Play / Pause (or Select inside player modals)
    - `B`: Close active player modal or Exit Player
    - `X`: Open Audio Tracks modal
    - `Y`: Open Subtitles modal
    - `LB` / `RB`: Seek backward `-10s` / forward `+10s`
    - `LT` / `RT`: Volume Down / Volume Up
    - `View`: Open Sources modal
    - `Menu`: Open Episodes (series) or Playback Speed modal
    - `D-Pad` / `LS`: Spatial navigation inside player controls and modals (Audio, Subtitles, Sources, Episodes, Speed)

#### 3. Dynamic Xbox-Glyph Controller Hint Toolbar (`ControllerHintToolbar.kt`, `controls.html`, `controls.css`, `controls.js`)
- Created a non-intrusive bottom-right Controller Hint Toolbar in both the Compose UI and the native player WebView2 overlay.
- Animates in/out with smooth fade + slide transitions strictly when `isControllerActive` is `true`, and hides immediately on mouse or keyboard input.
- Always displays standard Windows Xbox controller glyphs (`A`, `B`, `X`, `Y`, `LB`, `RB`, `LT`, `RT`, `LS`, `RS`, `D-Pad`, `View`, `Menu`) and dynamically updates action labels to match the current screen, focused element, or active modal/overlay.


### [2026-10-31] Upstream Isolation & Modular Architecture Refactor

#### 1. Self-Contained Controller Architecture (`com.nuvio.app.controller`)
- Moved all controller models, spatial focus engine, glyph hint toolbar, navigation hosts, Windows XInput/DirectInput services, and cursor management into a clean dedicated package:
  - `composeApp/src/commonMain/kotlin/com/nuvio/app/controller/` (`ControllerModels.kt`, `ControllerFocusEngine.kt`, `ControllerHintToolbar.kt`, `ControllerNavigationHost.kt`)
  - `composeApp/src/desktopMain/kotlin/com/nuvio/app/controller/` (`WindowsControllerService.kt`, `DesktopControllerWindowIntegration.kt`)
- Created `ControllerNavigationHost.kt` to isolate app-shell route monitoring, context mapping, bumper tab navigation, and overlay rendering into a single modular unit.

#### 2. Upstream Merge Conflict Minimization
- Refactored core NuvioDesktop files down to minimal 1-line hook-ins:
  - `AppGate.kt`: Reduced controller overlay mounting to a single line (`ControllerGateOverlay(...)`).
  - `MainAppContent.kt`: Replaced ~180 lines of inline navigation/shortcut logic with a clean call to `ControllerNavigationHost(...)` and `ControllerMainOverlay(...)`.
  - `MiddleClickAutoscroll.kt`: Reverted completely to match upstream byte-for-byte; cursor suppression is handled at the window host level (`DesktopControllerWindowHost`).
  - `PosterZoomActionOverlay.kt`, `Dialog.kt`, `ShelfComponents.kt`, `HomeHeroSection.kt`: Reverted large block indentation diffs and restored comments, reducing file diffs by >80%.
- Verified `upstream` remote points to `https://github.com/NuvioMedia/NuvioDesktop.git`.
- Re-verified Windows standalone compilation and distributable creation (`:composeApp:createDistributable`) with clean success.

### [2026-10-31] Netflix Focus Styling, Play Halo, Top-Row Viewport Stability, Dark Glass Toolbar & Standalone Profile Fixes

#### 1. Netflix-Style Media Row Card Focus (`ControllerFocusEngine.kt`, `PosterLift.kt`)
- Updated focused media row cards to a TV-grade Netflix-inspired visual treatment:
  - Scale-up pop transition (`1.07x`) with elevated `zIndex(15f)` to ensure the active card rises cleanly above adjacent items without edge clipping.
  - Crisp, high-contrast solid white outer ring (`2.8px`) paired with a dark ambient drop-shadow (`Color.Black.copy(0.75f)`) and a subtle `1px` dark inner hairline (`#0F0F11`) so the focus boundary remains unmistakable even against high-key white artwork.
  - Sibling card dimming: non-focused cards in the active row smoothly dim to `0.82f` alpha while controller browsing is active, locking visual attention onto the selected title.

#### 2. High-Contrast Double-Ring Halo for Primary "Play" Button (`DetailActionButtons.kt`, `ControllerFocusEngine.kt`)
- Added specialized `useHaloFocusRing` rendering mode for primary white and light buttons (such as the Movie/Show detail "Play" button):
  - Employs a double-ring offset halo effect: a dark inner separation gap (`2px #0F0F11`), a crisp white outer ring (`2.8px`), and a soft luminous outer accent glow (`Color.White` / accent color).
  - Scaled to `1.05x` with elevated depth, making the selected "Play" state immediately distinguishable from unselected buttons.

#### 3. Detail Page Back Arrow Bypass (`Components.kt`)
- Updated `NuvioBackButton` with `controllerFocusable: Boolean = false` by default, excluding the top-left arrow from the controller spatial traversal graph while preserving mouse clickability.
- Universal `B` button navigation handles backing out of detail screens without getting trapped in top header buttons.

#### 4. Top-Row Horizontal Navigation Viewport Stability (`ControllerFocusEngine.kt`)
- Fixed vertical jumping/jitter when browsing Left and Right across items in rows near the top of the viewport:
  - Added generous top safe padding (`64px` buffer below viewport top threshold `180f`).
  - Suppressed vertical scroll recalculations (`suppressVerticalScroll = true`) and vertical `bringIntoView` when moving horizontally within the same row (`groupKey` or Y-aligned), ensuring pure horizontal shelf scrolling.

#### 5. Borderless Dark Glass Controller Hint Toolbar (`ControllerHintToolbar.kt`)
- Completely removed outer borders (`border: null`) and converted the container to a clean, rounded dark glass pill (`background: rgba(12, 12, 16, 0.68)`, `RoundedCornerShape(24.dp)`, `12.dp` shadow elevation).
- Cleaned up hints across all screens: removed D-Pad and left-stick clutter, displaying only active action prompts (`A`, `B`, `X`, `Y`, `LB`/`RB`, `Menu`, etc.).

#### 6. Profile Avatar Path Normalization in Standalone Builds (`ProfileModels.kt`)
- Expanded avatar resolution to support local file paths (`file://`, `file:/`, `file:///`), Windows drive letters (`C:\...`), paths containing spaces (`%20`), and common image formats (`.png`, `.jpg`, `.jpeg`, `.webp`, `.svg`, `.gif`).
- Added automatic fallback to `avatarStorageUrl` when user profile data has an `avatarId` but local catalog caching has not finished hydrating.

#### 7. Default Borderless Fullscreen Launch (`DesktopWindowModeStorage.kt`, `Main.kt`)
- Configured desktop window launch mode to open directly in Fullscreen (`fullscreen: true`) on application startup.
- Preserves live bidirectional synchronization with `F11` key and the in-app UI fullscreen toggle button.



### [2026-10-31] Ultra-Smooth Dark Cinematic Atmospheric Background (Boot, Login & Profile Selection)

#### 1. Premium Organic Celestial Color Palette (`CinematicBackground.kt`)
- Designed an ultra-smooth, organic multi-stop gradient inspired by celestial deep-space imagery:
  - Top-left velvet indigo-navy bloom (`#090A10` / `#101528`).
  - Center soft glowing deep violet and amethyst nebula core (`#1C1430` to `#3E1F5C`), dynamically infused with the active profile's accent color when available.
  - Right and bottom-right dark plum, aubergine, and crimson-tinted shadows (`#2B0C20` to `#0F050A`).
  - Deep perimeter and bottom vignetting (`#06050A` down to `#020204`) ensuring all foreground text, input fields, QR codes, and profile cards maintain crystal-clear contrast and 100% legibility.

#### 2. Strict Banding Prevention via Algorithmic Dither & Fine Film Grain
- Incorporated a high-frequency 96x96 micro-dither noise tile generated via triangular dither and rendered as a tiled GPU `ShaderBrush` layer.
- Mathematically breaks 8-bit/10-bit color step quantization, completely eliminating visible contour banding across large 1080p and 4K HDR/SDR desktop and TV panels without increasing memory or CPU overhead.

#### 3. Atmospheric Depth, Volumetric Blooms & Floating Stardust Particles
- Layered in dual desynchronized breathing cycles (20s pulse and 32s drift) that gently modulate nebula core radii and light leak streaks.
- Added a deterministic constellation of 38 soft floating stardust motes with individual twinkle phases, subtle drifting offsets, and halo glow circles, creating a rich volumetric sense of depth without frame drops or GC pressure.

#### 4. Universal Injection Across App Boot, Login & Profile Gate
- **Initial Boot & Loading Gate (`AppGate.kt`, `AppShellComponents.kt`):** Injected behind initial repository warming screens, `AppGateScreen.Loading`, and `AppLoadingContent` / `AppLaunchOverlay`.
- **User Login & Device Link (`AuthScreen.kt`):** Replaced legacy static gradients behind the email/password login and QR device-link panes.
- **Profile Selection & Switcher (`ProfileSelectionScreen.kt`, `ProfileMeshBackground.kt`):** Upgraded `ProfileMeshBackground` to delegate seamlessly to `CinematicBackground` with subtle profile color blending.

### [2026-10-31] Controller PIN Entry, Auto-Focus Profiles, Viewport Clearance & Chevron Navigation Refinements

#### 1. Immediate Controller Auto-Focus on Profile Selection Screen (`ProfileSelectionScreen.kt`)
- When the Profile Selection / Login screen loads or whenever a controller is detected (`isControllerActive` or `connectedControllerCount > 0`), the first profile card (index 0) is automatically focused via `isDefaultFocus = true`.
- Allows instant `A` button selection or immediate Left/Right traversal between profiles without requiring the user to press a directional button first to wake or initialize focus.

#### 2. Complete Controller Operability for Profile PIN Entry Modal (`PinEntryDialog.kt`, `ControllerHintToolbar.kt`)
- Added full spatial navigation and controller activation for the on-screen numeric keypad (`PinKeypad`):
  - Every digit key (`1–9`, `0`) and the Backspace/Delete key (`⌫`) are now fully controller-focusable inside `ControllerFocusZone.Overlay`, automatically defaulting focus to key `1`.
  - Mapped `A` to enter the selected digit, `X` (or the on-screen delete key) to delete the last digit, and `B` to cancel out of the PIN prompt back to profile selection.
  - Contextual Controller Hint Toolbar automatically switches to show `[A] Enter Digit`, `[X] Delete`, and `[B] Cancel` during PIN entry.
  - Seamlessly restores focus back to the active profile card when the dialog is dismissed or verified.

#### 3. Increased Top Viewport Safe-Zone Clearance for Media Rows (`ControllerFocusEngine.kt`)
- Substantially increased the top scroll buffer threshold in `scrollVerticalContainerToNode` from `180f` to `240f` with an expanded `80px` delta offset (providing `140px`–`160px` of headroom above active row headers).
- Ensures that navigating Up or Down between rows positions the entire row header title and the scaled-up poster card (`1.08x`) comfortably below the top navigation bar with zero clipping.
- Preserved top-row scroll bounds checking so the very first row never jitter-scrolls past position 0.

#### 4. Exclusion of Row Chevrons from Vertical Traversal (`ControllerFocusEngine.kt`)
- Excluded row "See All" chevron pills (`view_all_pill_...`) from general vertical (`Up`/`Down`) navigation between media rows so browsing hops directly between media cards.
- Chevrons are now strictly reachable only when the user is on the far-right (last) card of a row and navigates `Up`. Pressing `Down` on a chevron immediately returns focus straight back to that row's media cards.

#### 5. Clamped Left Navigation at Index 0 & Controller Menu Sidebar Toggle (`ControllerFocusEngine.kt`, `ControllerHintToolbar.kt`)
- Prevented rapid `Left` navigation on the first item (index 0) of content rows from accidentally hopping into and opening the left sidebar menu. Focus stays smoothly clamped at index 0.
- Sidebar menu expansion and closing is bound strictly to the controller's `Menu` (Start) and `View` buttons (`toggleNavigationZoneFocus()`), matching standard TV streaming console ergonomics.
- Updated the Controller Hint Toolbar to prominently display `[Menu] Navigation` across content screens (`Home`, `Library`, `Catalog`, `Settings`).

### [2026-10-31] Critical Bug Fixes: Startup Window Focus, Menu Binding, Route Scroll Persistence, Focus Ring Visibility & Viewport Safe-Zone

#### 1. First-Launch Fullscreen Window Focus & Pointer Deadlock Fix (`WindowsControllerService.kt`, `DesktopControllerWindowIntegration.kt`, `DesktopAppFullscreen.kt`)
- Resolved the input deadlock on initial launch into Fullscreen mode where controller presses and single mouse clicks were ignored until spam-clicking the window.
- Integrated native Windows User32 foreground activation (`SetForegroundWindow`, `BringWindowToTop`) alongside AWT `toFront()`, `requestFocus()`, and `contentPane.requestFocusInWindow()` on desktop initialization and borderless fullscreen entry.
- Updated `WindowsControllerService.isWindowCurrentlyFocused()` to dynamically verify foreground HWND ownership, ensuring controller events are never blocked on startup.

#### 2. Unbound Settings from Controller `Menu` (Start) Button (`MainAppContent.kt`, `ControllerNavigationHost.kt`, `ControllerHintToolbar.kt`)
- Completely removed the duplicate `Settings` tab navigation binding from the controller `Menu` button in `MainAppContent.kt`.
- Pressing `Menu` (Start) now strictly toggles opening/closing the left navigation sidebar menu (`toggleNavigationZoneFocus()`) without inadvertently jumping into Settings.
- Updated all screen hints across the Controller Hint Toolbar to consistently label the `Menu` button exclusively as `[Menu] Navigation`.

#### 3. Route Change Scroll Container Persistence & Right-Stick Scrolling Fix (`ControllerFocusEngine.kt`)
- Resolved the bug where vertical D-Pad traversal and Right Analog Stick continuous scrolling failed after returning from Settings/Menu to Home/Library.
- Refactored the scroll manager to use dynamic registration tracking (`getActiveScrollable()`), safely preserving background page scroll containers and immediately resolving active mounted viewports across arbitrary route and tab transitions.

#### 4. Portrait Poster Card Selection Visibility & Unified Focus Ring (`ShelfComponents.kt`, `CatalogScreen.kt`, `ControllerFocusEngine.kt`)
- Fixed clipping of portrait poster card focus rings by attaching `.posterCardClickable` before internal `.clip(cardShape)` and adding dedicated vertical padding (`top = 8.dp`, `bottom = 12.dp`) to media `LazyRow` containers.
- Implemented a unified TV-grade selection border + drop-shadow glow across ALL interactive cards:
  - Crisp solid white border: `3px solid #FFFFFF`
  - Layered ambient glow and deep drop-shadow (`0 0 18px rgba(255, 255, 255, 0.65), 0 12px 28px rgba(0, 0, 0, 0.85)`)
  - Smooth scale-up (`1.06x`) with elevated `z-index = 15f`.

#### 5. High-Contrast Play Button & Detail Page Action Buttons Focus Treatment (`ControllerFocusEngine.kt`, `DetailActionButtons.kt`)
- For all secondary action buttons next to Play (Trailer, Add to Library, Episodes, etc.): Applied the unified crisp `3px solid #FFFFFF` border with radiant outer glow—eliminating dark overlay hairlines.
- For the solid-white "Play" button specifically: Added a distinct `3px solid #090A10` dark inner separation ring separating the white button fill from the bright `3px solid #FFFFFF` outer ring, wrapped in a 24px luminous outer glow and deep shadow, ensuring maximum contrast.

#### 6. Viewport Top Safe-Zone & First-Row Snap (`ControllerFocusEngine.kt`)
- Explicitly enforced a hard minimum top safe-zone of at least `180px` from the top of the viewport to the parent row section (including the row header title above poster cards) whenever scrolling rows vertically.
- Added automatic detection for the topmost content row on the page, smoothly snapping `scrollTop` to `0` when navigating `Up` to the first row to ensure the Hero banner and header are completely visible.

### [2026-10-31] Safe-Zone Viewport Framing, Route Focus Isolation & Sleek Modern Focus Styling

#### 1. Portrait Row Safe-Zone Viewport Framing & Headroom Snapping (`ControllerFocusEngine.kt`, `Components.kt`)
- **Parent Row Container Safe-Zone Centering:** Dynamic vertical scrolling calculations now frame the entire parent row section:
  - Top Safe Zone: Guarantees at least 140px headroom below the top viewport boundary/top navigation bar, preventing tall portrait poster cards and row header text from clipping.
  - Bottom Safe Zone: Guarantees at least 120px clearance above the window bottom and hint toolbar, positioning cards safely above overlay elements.
  - Topmost Row Snap: Moving `Up` to the first row on the page snaps scroll position cleanly to 0 (`animateScrollToItem(0, 0)`), exposing hero banners and page headers.
  - Bottom Content Padding: Added at least 140px bottom padding (`tokens.spacing.screenBottom + 140.dp`) to the root page container (`NuvioScreen`), ensuring bottom-most media rows can scroll fully into view.

#### 2. Tab & Route Focus Isolation & Scrollable Target Resolution (`ControllerFocusEngine.kt`)
- Integrated `LocalScreenActive` into `Modifier.controllerFocusable` and `RegisterControllerVerticalScroll`:
  - When switching between tabs (`Home`, `Search`, `Library`, `Settings`), inactive tab nodes are automatically disabled and purged from spatial navigation candidate pools, preventing stale nodes from hijacking focus.
  - Inactive tab `LazyListState` and `ScrollState` instances are unregistered upon tab departure, ensuring that D-Pad and analog stick scrolling strictly operate on the currently active tab's scroll container.
  - On route/tab transition, stale focus IDs and remembered group histories are reset unconditionally, with `ensureValidFocus()` immediately re-acquiring focus on the active screen's top-left or default node.

#### 3. Sleek Subtle Border (2px) with Soft Diffused Glow & Outward Play Button Halo (`ControllerFocusEngine.kt`, `DetailActionButtons.kt`)
- **Modern Subtle Focus Treatment Across All Elements:**
  - Replaced heavy multi-tier solid strokes with a crisp, thin `2px solid #FFFFFF` border accompanied by a soft diffused outer white glow (`rgba(255, 255, 255, 0.45)`) and subtle drop shadow (`rgba(0, 0, 0, 0.50)`).
  - Maintained a refined TV-scale factor (`scale(1.04)`).
- **Outward Play Button Focus Halo:**
  - On solid-white primary buttons (`useHaloFocusRing = true`), the focus ring is painted strictly outside the button perimeter to preserve inner button padding, icon clearance, and typography:
    - `2px solid #090A10` dark separation ring along the button boundary.
    - `2px solid #FFFFFF` white focus outline offset outward by 2px.
    - Soft diffused outer glow (`rgba(255, 255, 255, 0.55)`) and subtle drop shadow radiating outward.

### [2026-10-31] Production Avatar Storage Resolution, Row Header Clipping Fix & Native Windows 11 Gamepad Virtual Keyboard

#### 1. Profile Avatar Image Storage Path Resolution (`build.gradle.kts`, `ServerConfiguration.kt`, `ProfileModels.kt`, `AvatarRepository.kt`)
- **Production Supabase Storage Fallback:** Set production backend URL (`https://api.nuvio.tv`) and anon key fallbacks in runtime configuration generation and `officialConfiguration()`, ensuring clean storage URL resolution even when `local.properties` or environment variables are absent.
- **Immediate Avatar Catalog Availability:** Pre-populated `AvatarRepository.standardCatalog` with `DEFAULT_STANDARD_AVATARS` to guarantee that all default profile avatars (Bram, Otto, Lin, Poppy, Bao, etc.) resolve and render immediately on boot without requiring network synchronization.
- **Normalized Avatar URLs:** Formatted `avatarStorageUrl` to cleanly prepend `https://api.nuvio.tv/storage/v1/object/public/avatars/` for all catalog items and custom avatar paths.

#### 2. Row Header Viewport Framing & Elimination of Child Scroll Conflict (`ControllerFocusEngine.kt`)
- **Eliminated Rogue Child BringIntoView:** Removed the child-level `bringIntoViewRequester` call from `.controllerFocusable` which was competing with `scrollVerticalContainerToNode` and scrolling the parent `LazyColumn` so only the card's top edge was visible, cutting off row section headers.
- **Authoritative Safe-Zone Framing:** Enhanced `scrollVerticalContainerToNode` to calculate full row dimensions including row section headers (`rowTop = bounds.top - 90f`) and bottom metadata/padding (`rowBottom = bounds.bottom + 45f`).
- **Headroom & Clearance Guarantees:** Enforced $\ge 160\text{px}$ headroom below the top navigation bar and $\ge 120\text{px}$ clearance above the window bottom/hint toolbar, ensuring the entire row—both the section heading and the full poster artwork—is 100% visible with breathing room.
- **Exploratory Scroll Settling:** Increased boundary directional scroll settling delay from 70ms to 180ms to match the 160ms animation curve before candidate search and framing.

#### 3. Native Windows 11 Touch & Gamepad Virtual Keyboard (`WindowsVirtualKeyboardManager.kt`, `PlatformVirtualKeyboard.kt`, `SearchScreen.kt`, `ControllerHintToolbar.kt`)
- **Native Gamepad Keyboard Invocation:** Integrated `WindowsVirtualKeyboardManager` using Win32 APIs:
  - Invokes the native Windows 11 Touch / Gamepad Keyboard (`TabTip.exe` / `IPTip_Main_Window`) whenever `A` is pressed on a controller-focused text input field (`isTextInput = true`), allowing immediate gamepad typing.
  - Automatically dismisses the keyboard (`WM_SYSCOMMAND / SC_CLOSE` and `WM_CLOSE`) when `B` is pressed, consuming the `B` button so the user stays on the active screen.
  - Automatically hides the keyboard upon submitting text, selecting a recent search, or navigating away via D-Pad/stick.
- **Dynamic Controller Hint Updates:** Controller Hint Toolbar displays `[A] Type with Keyboard` and `[B] Close Keyboard` whenever a text field is active.

### [2026-10-31] Input Mode Deadzones, Supabase Authentication & Collection Tab Navigation

#### 1. Controller / Mouse Input Mode Deadzones & State Switching (`ProfileSelectionScreen.kt`, `WindowsControllerService.kt`)
- **Prevented Idle Stick Jitter Reactivation:** Increased analog stick deadzone thresholds from `0.08f` to `0.25f` in `WindowsControllerService.kt`, preventing slight analog stick drift from inadvertently triggering controller mode when operating via mouse.
- **Mouse Hover Priority on Profile Selection:** Removed premature controller mode activation (`notifyControllerInput()`) from `ProfileSelectionScreen.kt` LaunchedEffects, guaranteeing that physical mouse movements switch the UI cleanly to mouse mode with functional hover highlights and clickable profile circles.

#### 2. Production Supabase Configuration & Offline PIN Resolution (`local.properties`, `ServerConfiguration.kt`)
- **Production Backend Endpoint & Anon Key:** Configured `local.properties` and runtime configurations with the production Supabase endpoint (`https://api.nuvio.tv`) and API anon key, resolving "Can't login with PIN offline yet" errors by enabling online PIN verification and cloud profile synchronization.

#### 3. Collection / Folder Navigation & Tab Bumper Controls (`FolderDetailScreen.kt`, `ControllerNavigationHost.kt`)
- **Excluded Back Arrow from Spatial Grid:** Excluded the top-left navigation back button from controller spatial navigation inside `FolderDetailScreen`, preserving standard controller back navigation via the `B` button.
- **LB / RB Tab Cycling:** Implemented `onBumperLeft` and `onBumperRight` handlers in `FolderDetailScreen` to cycle horizontally through folder category tabs using `LB` and `RB`.
- **Dynamic Context Hints:** Registered `hasSeasonsOrTabs = true` and `seasonsOrTabsLabel = "Switch Folder"` for `FolderDetailRoute` in `ControllerNavigationHost.kt`.

#### 4. Controller Hint Toolbar Cleanup (`ControllerHintToolbar.kt`)
- **Removed Unbound Trigger Prompts:** Removed non-functional `LT / RT Page Scroll` prompts from the hint toolbar across catalog and folder views.

### [2026-10-31] Deterministic Netflix/Android TV Vertical Row-by-Row Controller Navigation

#### 1. Deterministic Row-by-Row Navigation Engine (`ControllerFocusEngine.kt`)
- **Row Clustered Spatial Traversal:** Content nodes on screen are automatically clustered into discrete horizontal rows/bands (`FocusContentRow`) ordered vertically by layout geometry.
- **Immediate Next/Previous Row Focus:**
  - One press of `Down` moves immediately to the next vertical row (`currentRowIndex + 1`), eliminating the previous bug that required up to 4 presses of incremental pixel scrolling.
  - One press of `Up` moves immediately to the previous vertical row (`currentRowIndex - 1`), eliminating the bug where pressing `Up` from lower rows snapped the page all the way to index 0 (`scrollTop = 0`).
- **Off-Screen Lazily Composed Row Streaming:** When navigating down or up beyond currently composed rows in a `LazyColumn` or `LazyVerticalGrid`, the engine programmatically scrolls the container by one full shelf height, awaits layout and composition, and immediately focuses the card matching the user's horizontal position.
- **Top of Page Clamp:** Navigation only snaps to the top hero banner or transitions to the navigation bar when pressing `Up` from row 0 at the very top of the list.

#### 2. Horizontal Position & Column Memory Across Rows (`ControllerFocusEngine.kt`)
- **Smart Alignment Across Rows:** When jumping vertically between rows, the engine prioritizes:
  1. Any remembered focused card for that shelf's `groupKey`
  2. Matching item column index (`rowItemIndex`)
  3. The card with the closest horizontal coordinate (`center.x`) to the current card
- Operates identically to Android TV / Netflix navigation paradigms.

#### 3. Full Row Framing & Elimination of Header Snapping Bugs (`ControllerFocusEngine.kt`)
- **Eliminated Faulty `isFirstRow` Check:** Removed the flawed check in `scrollVerticalContainerToNode` that incorrectly treated lower rows as row 0 when upper rows were scrolled off-screen.
- **Pixel-Perfect Viewport Framing:** Guarantees $\ge 140\text{px}$ safe headroom below the top bar (so row titles/headers are never clipped) and $\ge 110\text{px}$ clearance above the bottom edge.
- **LazyGridState Support:** Added support for `LazyGridState` in `RegisterControllerVerticalScroll`, enabling seamless multi-row grid navigation in `CatalogScreen`.

### [2026-10-31] Controller Toolbar Screen Gating, Strict Chevron Rules, Row Framing Verification & Modern Windows 11 Gamepad Keyboard

#### 1. Controller Hint Toolbar Gating on Boot, Login, Profile & Loading Screens (`ControllerHintToolbar.kt`, `ControllerNavigationHost.kt`, `ControllerFocusEngine.kt`)
- **Gate Overlay Suppression:** Suppressed `ControllerHintToolbar` in `ControllerGateOverlay` so the hint toolbar remains completely hidden across splash, boot, login, profile selection, and PIN entry screens.
- **Content-Ready Visibility Enforcement:** `ControllerHintToolbar` now checks `hasMountedContent` (`hasInteractiveContent`) and `focusedSnapshot != null`, remaining 100% hidden during post-login loading spinners and only fading in once media shelf rows are rendered and focus has actively landed.

#### 2. Strict Traversal Rules for Row Header Chevrons ("See All" Arrows) (`ControllerFocusEngine.kt`)
- **Exclusion from Normal Row Navigation:** Row header chevrons (`view_all_pill_`) are strictly excluded from normal row-to-row vertical (`Up` / `Down`) candidate pools, guaranteeing that moving `Down` or `Up` between shelves always lands on a poster card.
- **Rightmost Card Access Only:** Chevrons can only be focused when the user is explicitly on the **very last (rightmost) card** of that exact same shelf and presses **`Up`**.
- **Bidirectional Return:** Pressing `Down` from a chevron returns focus directly to the poster cards in that same row; pressing `Up` from a chevron moves to a poster card in the row above; pressing `Left` returns to the rightmost card in that same row.

#### 3. Post-Frame Row Framing & Verification Engine (`ControllerFocusEngine.kt`)
- **Parent Row Container Calculation:** Bounding box calculations account for both the row title header above the posters (`rowTop = bounds.top - 80f`) and poster card height + focus glow (`rowBottom = bounds.bottom + 45f`).
- **Post-Frame Layout Verification:** `scrollVerticalContainerToNode` executes an initial safe-zone scroll (`top >= 110px`, `bottom <= viewportHeight - 100px`) and automatically schedules a verification check after 200ms settling to measure freshly laid out bounds, immediately issuing a corrective scroll if any portion remains clipped.

#### 4. Native Windows 11 Controller-Compatible Gamepad Keyboard (`WindowsVirtualKeyboardManager.kt`)
- **Complete Removal of Legacy `osk.exe`:** Stripped all legacy `osk.exe` invocations.
- **Direct COM `ITipInvocation` Integration:** On Windows 11, sets `HKCU\Software\Microsoft\TabletTip\1.7\EnableDesktopModeAutoInvoke = 1`, starts `TabTip.exe`, and directly invokes COM `ITipInvocation::Toggle(GetDesktopWindow())` via JNA vtable call (with Base64 PowerShell fallback), launching the modern controller-navigable Gamepad/Touch keyboard.




