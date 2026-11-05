package com.nuvio.app.controller

import com.nuvio.app.features.player.desktop.DesktopHostOs

actual object PlatformVirtualKeyboard {
    @Volatile
    actual var isKeyboardActive: Boolean = false

    actual fun show(fromAutoFocus: Boolean) {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        WindowsVirtualKeyboardManager.showKeyboard(fromAutoFocus = fromAutoFocus)
    }

    actual fun hide() {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        WindowsVirtualKeyboardManager.hideKeyboard()
    }

    actual fun registerInputFocusRestorer(restorer: (() -> Unit)?) {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        WindowsVirtualKeyboardManager.registerInputFocusRestorer(restorer)
    }
}
