package com.nuvio.app.controller

import platform.Foundation.NSUserDefaults

internal actual object CamStreamFilterStorage {
    actual fun loadEnabled(): Boolean? = runCatching {
        val defaults = NSUserDefaults.standardUserDefaults
        if (defaults.objectForKey("deck_cam_filter_enabled") != null) {
            defaults.boolForKey("deck_cam_filter_enabled")
        } else null
    }.getOrNull()

    actual fun saveEnabled(enabled: Boolean) {
        runCatching {
            NSUserDefaults.standardUserDefaults.setBool(enabled, "deck_cam_filter_enabled")
        }
    }
}

