package com.nuvio.app.controller

import com.nuvio.app.features.player.desktop.DesktopHostOs
import java.awt.Desktop
import java.awt.Window
import java.io.File
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

/**
 * Application-level manager for the Windows on-screen keyboard (Input Pane) and
 * window/input focus synchronization.
 *
 * Manages window focus events, activation states, and Compose text-input focus
 * restoration when the input pane opens and closes without hacking system COM interfaces.
 */
object WindowsVirtualKeyboardManager {
    private const val TABTIP_PATH = "C:\\Program Files\\Common Files\\microsoft shared\\ink\\TabTip.exe"
    private const val WM_SYSCOMMAND = 0x0112
    private const val SC_CLOSE = 0xF060L
    private const val SHOW_DEBOUNCE_MS = 500L
    private const val AUTO_FOCUS_COOLDOWN_AFTER_HIDE_MS = 850L

    @Volatile
    private var inputFocusRestorer: (() -> Unit)? = null

    @Volatile
    var lastShownAtMs: Long = 0L
        private set

    @Volatile
    var lastClosedAtMs: Long = 0L
        private set

    @Volatile
    var inputPaneTookWindowFocus: Boolean = false
        private set

    fun registerInputFocusRestorer(restorer: (() -> Unit)?) {
        inputFocusRestorer = restorer
    }

    fun restoreInputFocus(window: Window? = WindowsControllerService.appWindow) {
        SwingUtilities.invokeLater {
            if (window != null && window.isShowing) {
                (window as? RootPaneContainer)?.contentPane?.requestFocusInWindow()
            }
            runCatching { inputFocusRestorer?.invoke() }
        }
    }

    fun showKeyboard(fromAutoFocus: Boolean = false) {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        val now = System.currentTimeMillis()
        val debounceMs = if (fromAutoFocus) SHOW_DEBOUNCE_MS else 120L
        if (now - lastShownAtMs < debounceMs) return
        if (fromAutoFocus && (now - lastClosedAtMs < AUTO_FOCUS_COOLDOWN_AFTER_HIDE_MS)) return

        lastShownAtMs = now
        inputPaneTookWindowFocus = false
        PlatformVirtualKeyboard.isKeyboardActive = true

        // 1. Ensure the application window and Compose text input field hold active input focus
        // before requesting the OS input pane so Windows binds the input context to our field.
        val win = WindowsControllerService.appWindow
        if (win != null) {
            WindowsControllerService.activateAndFocusWindow(win)
        }
        restoreInputFocus(win)

        // 2. Launch TabTip.exe cleanly via standard Desktop/Shell open after Compose commits focus
        Thread {
            try {
                Thread.sleep(55)
            } catch (_: InterruptedException) {
                return@Thread
            }
            restoreInputFocus(WindowsControllerService.appWindow)
            val tabTipFile = File(TABTIP_PATH)
            val launched = runCatching {
                if (tabTipFile.exists() && Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(tabTipFile)
                    true
                } else {
                    false
                }
            }.getOrDefault(false)

            if (!launched && tabTipFile.exists()) {
                runCatching {
                    ProcessBuilder("explorer.exe", TABTIP_PATH).start()
                }
            }
            try {
                Thread.sleep(130)
            } catch (_: InterruptedException) {
                return@Thread
            }
            if (PlatformVirtualKeyboard.isKeyboardActive) {
                restoreInputFocus(WindowsControllerService.appWindow)
            }
        }.apply {
            isDaemon = true
            name = "NuvioInputPaneLauncher"
            start()
        }
    }

    fun onWindowLostFocusOrDeactivated(window: Window?) {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        if (PlatformVirtualKeyboard.isKeyboardActive) {
            inputPaneTookWindowFocus = true
            // Keep the Compose input field focused inside our window hierarchy so keystrokes
            // from the OS input pane are delivered to the search field.
            SwingUtilities.invokeLater {
                runCatching { inputFocusRestorer?.invoke() }
            }
        }
    }

    fun onWindowGainedFocusOrActivated(window: Window?) {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        val now = System.currentTimeMillis()
        if (PlatformVirtualKeyboard.isKeyboardActive) {
            if (inputPaneTookWindowFocus || (now - lastShownAtMs > 500L)) {
                PlatformVirtualKeyboard.isKeyboardActive = false
                inputPaneTookWindowFocus = false
                lastClosedAtMs = now
            }
        }
        SwingUtilities.invokeLater {
            if (window != null && window.isShowing) {
                (window as? RootPaneContainer)?.contentPane?.requestFocusInWindow()
            }
            runCatching { inputFocusRestorer?.invoke() }
            ControllerFocusEngine.ensureValidFocus()
        }
    }

    fun hideKeyboard() {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        val wasActive = PlatformVirtualKeyboard.isKeyboardActive
        PlatformVirtualKeyboard.isKeyboardActive = false
        inputPaneTookWindowFocus = false
        lastClosedAtMs = System.currentTimeMillis()

        if (wasActive) {
            runCatching {
                val u32 = WindowsControllerService.user32
                if (u32 != null) {
                    val hwndTabTip = u32.FindWindowA("IPTip_Main_Window", null)
                    if (hwndTabTip != 0L && u32.IsWindowVisible(hwndTabTip)) {
                        u32.PostMessageA(hwndTabTip, WM_SYSCOMMAND, SC_CLOSE, 0L)
                    }
                }
            }
        }

        WindowsControllerService.appWindow?.let { win ->
            WindowsControllerService.activateAndFocusWindow(win)
        }
        SwingUtilities.invokeLater {
            ControllerFocusEngine.ensureValidFocus()
        }
    }
}
