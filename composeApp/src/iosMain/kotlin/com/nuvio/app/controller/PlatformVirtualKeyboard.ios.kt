package com.nuvio.app.controller

actual object PlatformVirtualKeyboard {
    actual var isKeyboardActive: Boolean = false
    actual fun show(fromAutoFocus: Boolean) {}
    actual fun hide() {}
    actual fun registerInputFocusRestorer(restorer: (() -> Unit)?) {}
}
