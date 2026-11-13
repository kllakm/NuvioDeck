package com.nuvio.app.controller

import platform.Foundation.NSUserDefaults

internal actual object AppBackgroundStorage {
    actual fun loadGlassEnabled(): Boolean? = runCatching {
        val defaults = NSUserDefaults.standardUserDefaults
        if (defaults.objectForKey("deck_frosted_glass_background_enabled") != null) {
            defaults.boolForKey("deck_frosted_glass_background_enabled")
        } else null
    }.getOrNull()

    actual fun saveGlassEnabled(enabled: Boolean) {
        runCatching {
            NSUserDefaults.standardUserDefaults.setBool(enabled, "deck_frosted_glass_background_enabled")
        }
    }
}

