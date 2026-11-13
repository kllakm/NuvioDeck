package com.nuvio.app.controller

import com.nuvio.app.core.storage.DesktopStorage

internal actual object AppBackgroundStorage {
    actual fun loadGlassEnabled(): Boolean? = runCatching {
        DesktopStorage.store("nuvio_theme_settings")
            .getString("deck_frosted_glass_background_enabled")
            ?.toBooleanStrictOrNull()
    }.getOrNull()

    actual fun saveGlassEnabled(enabled: Boolean) {
        runCatching {
            DesktopStorage.store("nuvio_theme_settings")
                .putString("deck_frosted_glass_background_enabled", enabled.toString())
        }
    }
}

