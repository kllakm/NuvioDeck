### NuvioDeck v0.1.17 Release Notes

#### Windows 11 Virtual Keyboard Gamepad Focus & Restored Profile Dismiss Fade-Out

- **Windows 11 On-Screen / Virtual Keyboard Gamepad Focus:**
  - Resolved the focus lockout issue where launching the virtual keyboard during search prevented gamepads and handheld controllers from selecting keys on the keyboard.
  - Implemented Win32 `AttachThreadInput` and `AllowSetForegroundWindow(-1)` focus orchestration in `focusKeyboardWindowAsync()`, reliably bringing `Windows.UI.Core.CoreWindow` (`TextInputHost.exe` / "Windows Input Experience") or `IPTip_Main_Window` to the active foreground.
  - Updated `WindowsControllerService.kt` so `isWindowCurrentlyFocused()` returns `false` and the input dispatcher completely yields while `PlatformVirtualKeyboard.isKeyboardActive` is true, ensuring 100% of gamepad navigation is routed to the Windows 11 virtual keyboard driver without background interference or focus-stealing from NuvioDeck.
  - Added `startKeyboardCloseWatcher()` to track virtual keyboard dismissal via `IsWindowVisible()`, automatically resetting keyboard state and smoothly restoring active foreground focus to NuvioDeck.

- **Restored Cinematic Login Screen Dismiss Fade-Out:**
  - Increased the transition delay in `ProfileSelectionScreen.kt` from 120ms to 420ms, allowing the 450ms `dismissAlpha` (1f -> 0f) and `dismissScale` (1f -> 1.04f) animation to smoothly play to completion upon profile click and PIN verification.
  - Removed early `profileSelectionTransitionActive = true` invocation and decoupled `profileSelectionLoading` from `profileOverlayVisible` in `AppGate.kt`. This prevents the abrupt black `AppLoadingContent` box from prematurely concealing the login screen, allowing the profile view to gracefully dissolve into the atmospheric `AppLaunchOverlay` backdrop and home screen.

- **Upstream Patch Registry Polish (`UPSTREAM_PATCHES.md`):**
  - Fully documented the latest login wordmark positioning, 700ms profile hover fade-outs, gate overlay dismissal timing, and virtual keyboard focus bridges in `UPSTREAM_PATCHES.md` to guarantee every customized visual enhancement is retained intact during future upstream pulls.

- **Windows MSI Installer:**
  - Packaged standalone installer `NuvioDeck-Windows-x64-0.1.17.msi`.

