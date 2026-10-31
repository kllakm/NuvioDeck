package com.nuvio.app.controller

actual object PlatformVirtualKeyboard {
    actual var isKeyboardActive: Boolean = false
    actual fun show() {}
    actual fun hide() {}
}

