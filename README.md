<div align="center">

  <img src="https://github.com/kllakm/NuvioDeck/blob/Dev/composeApp/src/commonMain/composeResources/drawable/app_logo_wordmark.png?raw=true" alt="NuvioDeck" width="340" />

  <br />
  <br />

  <p>
    <a href="https://github.com/kllakm/NuvioDeck/releases/latest">
      <img src="https://img.shields.io/github/v/release/kllakm/NuvioDeck?include_prereleases&style=for-the-badge&logo=github&label=Release&color=E50914" alt="Latest Release" />
    </a>
    <a href="https://github.com/kllakm/NuvioDeck/releases">
      <img src="https://img.shields.io/badge/Platform-Windows%2010%20%7C%2011%20(x64)-0078D4?style=for-the-badge&logo=windows&logoColor=white" alt="Platform Windows 10/11" />
    </a>
    <img src="https://img.shields.io/badge/Input-Controller%20%26%20Handheld%20Ready-107C10?style=for-the-badge&logo=xbox&logoColor=white" alt="Controller & Handheld Ready" />
    <img src="https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin Multiplatform" />
    <img src="https://img.shields.io/badge/Compose-Multiplatform-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose Multiplatform" />
    <a href="./LICENSE">
      <img src="https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge" alt="License GPLv3" />
    </a>
    <a href="https://buymeacoffee.com/enufstyle">
      <img src="https://img.shields.io/badge/Support-Buy%20Me%20a%20Coffee-FFDD00?style=for-the-badge&logo=buymeacoffee&logoColor=black" alt="Support NuvioDeck" />
    </a>
  </p>

  <p>
    <strong>A free, open-source 10-foot & handheld media center for Windows 10/11.</strong>
    <br />
    Bring your own sources. <strong>NuvioDeck</strong> turns them into a rich streaming library with artwork, ratings, subtitles, and cloud progress sync—purpose-built with native controller navigation for Windows PCs, HTPCs, and handhelds.
  </p>

  <p>
    <a href="https://github.com/kllakm/NuvioDeck/releases/"><strong>⬇ Download Latest Release</strong></a>
    ·
    <a href="https://github.com/kllakm/NuvioDeck/issues"><strong>Report an Issue</strong></a>
    ·
    <a href="https://buymeacoffee.com/enufstyle"><strong>Support NuvioDeck</strong></a>
  </p>

</div>

---

## 📸 Screenshots & Showcase

<div align="center">
  <img src="screenshots/01-home-hero.jpg" alt="NuvioDeck Home Screen & Hero Banner" width="100%" />
  <p><em>Full-width Netflix-style Top Navigation Bar over the Hero Banner, Continue Watching shelf, and dynamic Xbox-style Controller Hint Toolbar.</em></p>
</div>

<br />

| **Catalog Shelves & In-Line "View All" Card** | **"New Arrivals" Notification Center** |
| :---: | :---: |
| <img src="screenshots/02-home-shelves-view-all.jpg" alt="Home Shelves & In-Line View All Card" width="100%" /> | <img src="screenshots/03-new-arrivals-notifications.jpg" alt="New Arrivals Notification Center" width="100%" /> |
| *Customizable row poster limit (`10–30`, default `15`) with an in-line semi-transparent **View All** poster card, specular cover sheen, and sleek `1.6dp` selection border.* | *10-item FIFO **New Arrivals** dropdown surfacing recently aired watchlist/history episodes and new movies with verified `1080p` / `4K` streams.* |

| **Search & Discover Grid** | **Series Details & Season Selection** |
| :---: | :---: |
| <img src="screenshots/04-search-discover.jpg" alt="Search & Discover Grid" width="100%" /> | <img src="screenshots/05-series-details.jpg" alt="Series Details & Season Selection" width="100%" /> |
| *Seamless D-Pad traversal from the search input and recent searches through Discover filter dropdowns and into the poster grid with synchronized card border scaling.* | *High-contrast inverted **Play** button focus ring, season-poster-only selection borders, vertical middle alignment, and one-press horizontal carousel reveals.* |

<br />

<div align="center">
  <img src="screenshots/06-streamless-notify-button.jpg" alt="Streamless Release Tracking & Dynamic Notify Button" width="100%" />
  <p><em>Dynamic "Play" ↔ "Notify" button swap on Details screen for unreleased media and titles without streams, with background polling and notification alerts.</em></p>
</div>

---

## 🎮 About NuvioDeck (Windows 11 Controller & Handheld Edition)

**NuvioDeck** is a specialized fork of [NuvioDesktop](https://github.com/NuvioMedia/NuvioDesktop) engineered to deliver a first-class, **native Windows 11 controller, 10-foot living-room TV, and handheld gaming PC experience**—ideal for **ROG Ally**, **Lenovo Legion Go**, **MSI Claw**, **Steam Deck (Windows)**, HTPCs, and desktop setups using Xbox, PlayStation, or Nintendo Switch controllers.

---

## ✨ Key Features

### 🧭 Redesigned Netflix-Style Top Navigation Bar & Auto-Hide Chrome
- **Always-On Floating Top Toolbar:** Replaces the collapsible left sidebar with a full-width (`fillMaxWidth()`) floating top navigation bar across all window sizes.
- **Smooth Black-to-Transparent Vertical Gradient:** Uses a rich top-to-bottom dark gradient (`96%` black at the top edge fading to `0%` transparent at the bottom) with no glass pill clutter behind the icons, ensuring crisp legibility over bright hero artwork.
- **Left, Center & Right Layout:**
  - **Far Left:** Theme-aware Nuvio icon-only graphical mark.
  - **Main Navigation Tabs:** `Home`, `Search`, `Library`, and a dedicated `Settings` tab (decoupled from the profile avatar) with solid filled pill highlights on controller focus.
  - **Far Right Controls:** Dedicated **Fullscreen / Exit Fullscreen** toggle button, **Notification Bell** with unread badge indicator, and **Profile Avatar**.
- **Full 7-Slot Controller & Bumper Tab Order:** Cycle seamlessly through `Home` → `Search` → `Library` → `Settings` → `Fullscreen` → `Notifications` → `Profile` in both directions using `RB` (forward) or `LB` (backward) with full selection of the Fullscreen toggle without skipping.
- **Context-Aware Auto-Hide on Scroll & Accidental Jump Prevention:**
  - **Home Page:** Smoothly slides upward off-screen as soon as you scroll or D-Pad navigate down, and slides immediately back into view when scrolling or navigating up.
  - **Search & Library:** Remains visible until page content scrolls up into the top bar's vertical space.
  - **Settings & Active Focus:** Stays locked in view while browsing **Settings**, while the top bar holds controller focus, or while the Notification/Profile dropdowns are open.
  - **Accidental Jump Prevention:** Navigating UP with the D-Pad inside Settings or page content smoothly scrolls and clamps at the top without hopping focus into the top navigation bar.

### 🔔 10-Item "New Arrivals & Tracked Release" Notification Center & Standalone Profile Switcher
- **Verified "New Arrivals" Detection Engine (`NewArrivalsManager`):**
  - Quiet background service with throttled addon checks (`2.5s` delay between requests) that inspects series in your **Watch History / Continue Watching** and **Watchlist** alongside catalog releases.
  - **Smart Series Filtering:** Announces new episodes strictly for series you are currently watching; for any other catalog series, it only announces brand new TV shows in general (Season 1 premieres aired within the last **7 days**), preventing unwanted notifications for random unwatched series.
  - Enforces a strict recency gate (series episodes aired within the last **7 days**; movies released within the last **120 days**, excluding older trending titles).
  - Verifies that at least one clean **`1080p`** or **`2160p` / `4K`** stream is available right now (automatically filtering out `CAM`, `TS`, `HDTS`, `TELESYNC`, and `SCREENER` sources) before creating a notification.
- **Dynamic "Play" ↔ "Notify" Button Swap & Release Watchlist (`release_watchlist.json`):**
  - On Movie and Series Details screens, automatically swaps the primary action button: displays standard **"Play"** (with play triangle icon) when playable streams exist, and dynamically replaces it with a **"Notify"** button (monochrome bell icon) when a title is unreleased, in theaters, or currently lacking streams on the user's addons.
  - Pressing `A` (or clicking) toggles release tracking on/off in `%APPDATA%\NuvioDeck\release_watchlist.json`, updating the button to an active highlighted **"Tracking"** state.
  - **Targeted Background Polling Engine:** Quietly probes stream addons strictly for items marked `TRACKING` with polite `2.5s` spacing. Once a clean HD/4K release is detected, marks the item `RELEASED` and pushes a top-priority notification directly to index 0 of the Notification Center.
- **10-Item FIFO Notification Dropdown & Visual Differentiation for Tracked Releases:**
  - Stores up to 10 notifications in `%APPDATA%\NuvioDeck\new_arrivals_notifications.json` (newest first, automatically dropping the oldest item on overflow) with 16:9 landscape thumbnails, relative timestamps, smooth viewport-edge D-Pad scrolling (scrolling only as selection approaches or crosses the bottom or top pane boundary), and direct `A`-button navigation to the title's Details page.
  - **Visual Distinction for User-Tracked Releases:** Tracked release cards render with a subtle `10%–15%` accent color wash, a `3dp` solid accent bar along the left edge, a `🔔 TRACKED RELEASE` header badge, and a vibrant unread dot that clears upon selection.
- **Standalone Profile Switcher Dropdown, Edit Profile Navigation & Rounded-Rectangle PIN Pads:**
  - Clicking or pressing `A` on the top-right Profile Photo opens an anchored Profile Switcher dropdown with D-Pad `Up`/`Down` navigation and solid highlight fills (zero text clipping).
  - Full controller traversal on **Manage / Edit Profile** starting at the top Name input and stepping cleanly through Use Primary Addons, Custom Avatar URL, Profile Background & Avatar Picker grids, Set/Remove PIN Lock, Save, and Delete, with `B` returning cleanly to the profile screen.
  - Both the Profile Switcher dropdown and the main Profile Selection screen feature a unified, spacious **rounded-rectangle PIN pad** (`RoundedCornerShape(18.dp)` dark studio surface, generous inner padding, larger tactile keys, `"PROFILE PIN"` lock header, recessed status pill with live verification spinner feedback upon entering 4 digits, and a 3×4 controller-navigable key grid with `[X] Delete` and `[B] Cancel`).

### 🎮 Universal Windows 11 Controller Support & Spatial Focus Engine
- **Multi-Backend Hardware Polling & Hot-Plugging:** Low-latency JNA polling across **XInput** (`XInput1_4.dll`, `XInput9_1_0.dll`, `XInput1_3.dll`) and **Windows Multimedia / DirectInput** (`winmm.dll`) with automatic device deduplication and normalization for Xbox, Sony DualSense / DualShock 4, Nintendo Switch Pro / Joy-Con, and built-in handheld controllers.
- **Zero-Friction Auto-Hide Mouse Cursor:** Immediately hides the system cursor (via a `1x1` transparent cursor and Win32 stationary cursor refresh) and clears passive hover states the moment any controller button, trigger, or stick is used—and restores the cursor as soon as the physical mouse moves (`>= 6px`).
- **Selected Artwork Brightening, Specular Cover Sheen & Sleek `1.6dp` Focus Ring:**
  - Focusing a poster or media card with the controller applies a smooth scale-up transition, an subtle brightening wash (`6.5%` white), and a diagonal specular sheen gradient clipped to the card's rounded corners, framed by a refined **`1.6dp` solid white selection border** and diffused outer glow.
  - **Season Poster-Only Selection:** On series detail pages, the selection border and scale effect wrap strictly around the season poster artwork (excluding the `"Season 1"` text label underneath).
  - **Synchronized Discover Card Borders:** Card depth borders in the Discover poster grid scale in lockstep with the selected poster artwork.
- **In-Line "View All" Poster Card & Customizable Row Poster Limit (`10–30`):**
  - Replaces the hard-to-reach header chevron with a full-size, semi-transparent **"View All"** poster card at the end of each catalog row with deterministic ID stability so navigating back from catalog folders returns focus directly to the "View All" card.
  - Includes a persistent **Row Poster Limit** slider (`10` to `30` posters per row, defaulting to **`15`**) under **Settings → Layout → Homescreen** styled with a pure accent fill (no white outer selection border) and adjustable via mouse drag or controller D-Pad `Left`/`Right`.
  - Fully controller-selectable **Hero Catalogs** header with accent selection fill allowing users to expand and collapse hero source toggles with controller `A`.
  - Seamless D-Pad `Right` navigation from the Settings sidebar always lands cleanly at the very top of the right content pane.
- **Single-Press Horizontal Poster Reveal & Directional Vertical Scroll Guard:**
  - Tracks unclipped root-space bounds (`unclippedBoundsInRoot`) so pressing D-Pad `Left` or `Right` toward a partially off-screen poster reveals **100% of the card on the very first press**.
  - Vertically centers the active shelf row (`visualRowCenterY` aligned to `52%` of viewport height) with `FastOutSlowInEasing` and strict directional guards so navigating UP never erroneously scrolls the page downward.
- **Season Bumper Synchronization & Direct Episode Navigation:** Traversing seasons with controller bumpers (`LB`/`RB`) dynamically keeps the controller focus ring/selection border synchronized with the active season. Pressing D-Pad `Down` from any season poster or text chip immediately routes controller focus directly to the first/active episode card of that season in horizontal carousel view.
- **Deterministic Poster Focus Restoration on Back Navigation:** Remembers exact focused poster IDs across every route (`DetailRoute`, `CatalogRoute`, `TabsRoute`) and restores focus to the exact show, movie, or collection folder you just exited when pressing `B` to go back without losing controller navigation.
- **Search Result Section Scoping & Focus Stability:** Every catalog row in search results is scoped with a unique group key, ensuring cards across multiple catalog sections have globally distinct focus IDs so duplicate search results (e.g., films matching the search term across multiple addons) never collide, cancel selection, or rebound focus back to the search bar.
- **Native Windows 11 Virtual Keyboard & Search History Controls:**
  - Pressing `Y` (or selecting any text input with `A`) scrolls the field into view, commits Compose `FocusRequester` focus, and launches the native Windows 11 touch/gamepad keyboard (`TabTip.exe` / `osk.exe`) so keystrokes always type directly into the search or login field.
  - Full mouse click and controller D-Pad traversal across **Recent Searches**, with `[X]` to delete individual terms and a focusable **Clear** button to wipe search history.

### 🕹️ Xbox-Style Outlined Controller Hint Toolbar & UI Audio
- **Xbox-Style Outlined Button Badges:** Bottom-right floating hint bar featuring unfilled circular outlines (`1.75dp` stroke) with Xbox-colored rings and optically cap-height-centered letters (`A` Green `#38D430`, `B` Red `#FF3B30`, `X` Blue `#2899FF`, `Y` Amber `#FFC800`) and crisp white outlines for `LB`, `RB`, `LT`, `RT`, `View`, and `Menu` inside a translucent dark rounded-rectangle pill (`RoundedCornerShape(8.dp)`).
- **Decluttered Contextual Prompts:** Automatically omits self-evident `A` (Accept/Open) and `D-Pad` (Navigate) hints so only meaningful contextual shortcuts (`[Y] Search`, `[X] Options / Clear`, `[LB][RB] Tabs`, `[Menu] Navigation`, `[B] Back`) are shown.
- **Persistent "App Sounds" Toggle:** Includes a toggle under **Settings → System** (enabled by default) to turn controller and UI navigation sound effects On or Off at any time.

### 🎬 3-Tier Media Player OSD & Streamlined Playback
- **3-Tier Player OSD Navigation:** Full D-Pad spatial traversal across Tier 0 (top header buttons), Tier 1 (timeline seekbar with `10s` step scrubbing), and Tier 2 (bottom transport & modal controls), with the OSD hint toolbar lifted cleanly above the seek bar.
- **Multi-Rail Subtitles Modal:** Smooth horizontal and vertical D-Pad navigation across **Languages ↔ Subtitles ↔ Style** rails without DOM focus loss.
- **Canonical "Continue Watching" & Auto-Stream Playback:**
  - Automatically resolves `tmdb:` IDs, canonical episode `video.id` entries, cached embedded streams, and pending addon manifests when launching directly from **Continue Watching**.
  - Defaults to `StreamAutoPlayMode.FIRST_STREAM` to automatically play the first verified stream source for couch-ready playback.
- **Auto-Advance on Debrid Error Video Slates:** Intelligently monitors container playback duration against metadata runtime: if a title is a movie or full-length episode (expected duration > 15 minutes) but the loaded stream duration is `<= 60 seconds` (or matches known debrid fallback/error patterns like zero-seed placeholders), automatically tears down the stream, purges it from cache, displays an informational toast (*"Stream unavailable on debrid. Trying next stream..."*), and seamlessly advances to the next best ranked candidate stream—cleanly exiting to Details if all candidates are exhausted.
- **Modern CAM & Fake Stream Filtering Regex (`CamStreamFilter`):** Reinforced stream engine rejecting deceptive cam releases disguised as 1080p/720p WEB rips across Auto-Play, New Arrivals, manual stream lists (`StreamsScreen`), and the in-player OSD stream list:
  - **Release markers:** `CAM`, `HDCAM`, `HQ-CAM`, `CAM-RIP`, `TS`, `HD-TS`, `TELESYNC`, `TC`, `TELECINE`, `SCR`, `SCREENER`, `DVD-SCR`, `WORKPRINT`, `WP`
  - **Audio & line-dubbed markers:** `LD`, `LINE`, `LINE-DUB`, `LINE-AUDIO`
  - **Ad & watermark markers:** `NoADS`, `NoLogo`, `HQ-NoADS`, `HQ-NoLogo`
  - **Known cam P2P groups:** `MTRx`, `CPG`, `Will1869`, `BiTOR`
  - **Settings Toggle:** Includes a persistent switch under **Settings → Playback → Stream Selection → Filter CAM & Telesync Releases** (enabled by default) so users who prefer upstream Nuvio's raw stream presentation can disable CAM filtering.
- **Dynamic Stream-Probing Indicator ("Play" / "Notify" Smooth Transition):** Details hero action button smoothly displays an inline circular progress indicator while background stream availability is probed, avoiding premature "Play" text flashing before transitioning to "Play", "Notify", or "Tracking".

### ⚡ High-Performance Desktop Engine & Side-by-Side Coexistence
- **High-DPI Windows Shell, Xbox App & Game Bar Tile Integration:** Embeds a true multi-resolution icon (`16x16` up to a PNG-compressed `256x256` layer) directly into `NuvioDeck.exe` and shortcuts, paired with bundled high-resolution square branding assets (`512x512`, `256x256`, `Square150x150Logo`, `Square70x70Logo`) organized into a clean `Assets\` subfolder and referenced by `NuvioDeck.VisualElementsManifest.xml` (`#0D0D0D`) so the Windows Start Menu, Game Bar, and Xbox App render crisp, high-DPI thumbnails without root directory clutter.
- **Subtle Studio Login Backdrop, Zero-Banding Frosted Glass App Backdrop & Anti-Banding Display:** Both the studio login/profile backdrop (`CinematicBackground`) and the **Frosted Glass Background** across the main app run on 32-bit floating-point SkSL GPU shaders featuring multi-point Gaussian ambient light clouds (spanning upper and lower halves of the screen with theme-tinted warmth and counter-depth), delicate rim sheen, and `1:1` physical-pixel Triangular Interleaved Gradient Noise (`IGN`) dithering prior to quantization to eliminate 8-bit contour banding cleanly—smoothly blending behind feathered hero artwork via offscreen alpha masking. Includes a toggle under **Settings → Appearance → Display** to switch between **Frosted Glass** and **Classic Flat** backdrops at any time (with pure black always preserved for **AMOLED Black**).
- **512MB Persistent Disk Image Cache & High-Refresh VSync:** Okio-backed disk cache (`%LOCALAPPDATA%\NuvioDeck\Cache\image_cache`), hardware trilinear mipmapping (`FilterQuality.Medium`), low-pause G1GC (`-XX:+UseG1GC -XX:MaxGCPauseMillis=16`), and unlocked VSync (`-Dskiko.fps=-1 -Dskiko.vsync=true`) for 90Hz / 120Hz / 144Hz displays.
- **Isolated Side-by-Side Installation:** Uses a dedicated `NuvioDeck` application identity, unique Windows MSI `upgradeUuid`, and isolated data directories (`%APPDATA%\NuvioDeck` and `%LOCALAPPDATA%\NuvioDeck\Cache`) so NuvioDeck and upstream NuvioDesktop can run side-by-side without conflict.
- **Unified Monochrome Stream Selection (App & Player OSD):** Both the main-app stream list and the in-player OSD stream selector share matching card styling (`12dp` radius, `0.32f` accent focus fill, `0.52f` active border) with flat monochrome vector/SVG metadata icons replacing emoji markers.
- **Comprehensive Settings & Folder Controller Navigation:** Split-pane Settings subpages auto-focus their top interactive control on entry, isolate `Left`/`Right` slider adjustments (Hover Preview Open Delay, Playback, Up Next, Card Depth), host **Collections** inline inside the Settings split-pane, traverse **Home Layout** catalogs and collections continuously without skipping, and center **Folders in Collections** multi-row grids reliably in a single D-Pad press during both `Up` and `Down` traversal using root-viewport bounds and unclipped item geometry.
- **Synchronized with Upstream Nuvio Desktop (`v0.1.35` — NuvioDeck `v0.1.26`):** Includes the shared cross-platform AutoSync subtitle engine (with forced subtitles & AutoSync enabled by default), smart horizontal hero backdrop framing, detailed MPV error diagnostics on playback failure, PiP window aspect-ratio auto-sizing, resilient auth session refresh, MDBList anime episode remapping, Simkl & MDBList ratings, OpenSubtitles video hash computation, custom subtitle font importer, torrent stall auto-resume, smart skip intervals, chapter markers, and multi-language localization.

---

## ⚠️ Alpha Software Notice

NuvioDeck is currently in active alpha development. Features, settings, and storage schemas may evolve between releases. Please report any bugs or controller navigation feedback via [GitHub Issues](https://github.com/kllakm/NuvioDeck/issues).

---

## 📦 Installation & Downloads

Download the latest Windows installer (`NuvioDeck-Windows-x64-0.1.26.msi`) from [**GitHub Releases**](https://github.com/kllakm/NuvioDeck/releases/).

- **Windows 10 / 11 (x64):** MSI Installer (`NuvioDeck-Windows-x64-0.1.26.msi`) or Standalone Executable (`NuvioDeck.exe`)

---

## 🛠️ Build from Source

```bash
git clone https://github.com/kllakm/NuvioDeck.git
cd NuvioDesktop
```

### Run in Development Mode (Windows PowerShell)

```powershell
.\gradlew.bat :composeApp:run
```

### Compile Windows Standalone Executable (`NuvioDeck.exe`)

```powershell
.\gradlew.bat :composeApp:createDistributable "-Pcompose.desktop.packaging.checkJdkVendor=false" --no-configuration-cache
```
The standalone application bundle is generated at:
`composeApp\build\compose\binaries\main\app\NuvioDeck\NuvioDeck.exe`

### Package Windows MSI Installer

```powershell
.\gradlew.bat :composeApp:packageMsi "-Pcompose.desktop.packaging.checkJdkVendor=false" --no-configuration-cache
```
The MSI installer is generated at:
`composeApp\build\compose\release-msis\NuvioDeck-Windows-x64-0.1.26.msi`

---

## 📄 License

Licensed under the [GNU General Public License v3.0](./LICENSE).
