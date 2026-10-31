package com.nuvio.app.controller

import com.nuvio.app.features.player.desktop.DesktopHostOs

actual object PlatformVirtualKeyboard {
    actual var isKeyboardActive: Boolean = false

    actual fun show() {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        isKeyboardActive = true
        WindowsVirtualKeyboardManager.showKeyboard()
    }

    actual fun hide() {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        isKeyboardActive = false
        WindowsVirtualKeyboardManager.hideKeyboard()
    }
}

