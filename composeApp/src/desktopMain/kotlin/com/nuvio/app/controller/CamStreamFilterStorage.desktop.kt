package com.nuvio.app.controller

import com.nuvio.app.core.storage.DesktopStorage

internal actual object CamStreamFilterStorage {
    actual fun loadEnabled(): Boolean? = runCatching {
        DesktopStorage.store("player_settings").getString("deck_cam_filter_enabled")?.toBooleanStrictOrNull()
    }.getOrNull()

    actual fun saveEnabled(enabled: Boolean) {
        runCatching {
            DesktopStorage.store("player_settings").putString("deck_cam_filter_enabled", enabled.toString())
        }
    }
}

