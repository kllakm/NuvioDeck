package com.nuvio.app.controller

expect object PlatformVirtualKeyboard {
    var isKeyboardActive: Boolean
    fun show()
    fun hide()
}

