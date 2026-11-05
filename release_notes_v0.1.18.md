### NuvioDeck v0.1.18 Release Notes

> **Known Issue:** The Windows on-screen virtual keyboard integration is still buggy and not yet resolved.

#### Unified Grey/White OSD Focus Styling, Updated Brand Logos, Version Sync & On-Screen Keyboard Focus Adjustments

- **Known Issue — On-Screen Virtual Keyboard:**
  - **Note:** On-screen virtual keyboard controller interaction is still buggy and remains an unresolved known issue.
  - Added application-level window focus (`WindowFocusListener`), activation state (`WindowListener`), and Compose text-input focus restoration (`registerInputFocusRestorer`) across `WindowsVirtualKeyboardManager.kt`, `DesktopControllerWindowIntegration.kt`, `WindowsControllerService.kt`, and `SearchScreen.kt`, and bound the **Y** button to jump to Search.

- **Unified Grey/White Solid Background Focus Styling:**
  - Replaced white border outlines across the left-hand main navigation sidebar (`DesktopSidebarItem`), profile drawer trigger, stream provider filter chips (`ProviderFilterRow`), stream cards (`StreamCard`), and `JellyTabs` with the clean solid background fill effect matching the Settings menu.
  - Updated the in-player OSD menus in `controls.css` (subtitles, audio tracks, stream source selector, quality chips, player modal header buttons, and transport controls) to use the standard grey/white app theme (`rgba(255, 255, 255, 0.24)` and `rgba(255, 255, 255, 0.34)` for selected items) instead of purple or white borders.

- **Updated Brand Logos (`app_logo.png` & `app_logo_wordmark.png`):**
  - Updated all app icon and wordmark usages across the entire application (Compose drawables, themed wordmarks, 256/512/1024 desktop icons, and multi-resolution Windows `.ico` files) with the updated logo and integrated `nuvio [DECK]` wordmark PNGs, removing the synthetic Compose `DECK` badge.

- **Settings & Runtime Version Synchronization (`v0.1.18`):**
  - Synchronized `AppVersionConfig` generation in `composeApp/build.gradle.kts` and routed `AboutSettingsPage.kt` / `SettingsAttribution.kt` to display the true build version `0.1.18 (18)` everywhere in the app instead of `0.6.6`.

- **Windows MSI Installer:**
  - Packaged standalone installer `NuvioDeck-Windows-x64-0.1.18.msi`.
