package com.nuvio.app.controller

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

internal actual object NewArrivalsStorage {
    private const val payloadKey = "new_arrivals_payload"

    actual fun loadPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(payloadKey))

    actual fun savePayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(payloadKey))
    }

    actual fun loadAppSoundsEnabled(): Boolean {
        val raw = NSUserDefaults.standardUserDefaults.stringForKey("app_sounds_enabled")
        return if (raw == null) true else raw == "true"
    }

    actual fun saveAppSoundsEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setObject(enabled.toString(), forKey = "app_sounds_enabled")
    }
}

