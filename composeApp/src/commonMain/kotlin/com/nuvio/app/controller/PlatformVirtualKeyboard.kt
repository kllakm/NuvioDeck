package com.nuvio.app.controller

expect object PlatformVirtualKeyboard {
    var isKeyboardActive: Boolean
    fun show(fromAutoFocus: Boolean = false)
    fun hide()
    fun registerInputFocusRestorer(restorer: (() -> Unit)?)
}
