package com.nuvio.app.controller

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object NewArrivalsStorage {
    private val store = DesktopStorage.store("nuvio_new_arrivals_notifications")

    actual fun loadPayload(): String? =
        store.getString(ProfileScopedKey.of("new_arrivals_payload"))

    actual fun savePayload(payload: String) {
        store.putString(ProfileScopedKey.of("new_arrivals_payload"), payload)
    }

    actual fun loadAppSoundsEnabled(): Boolean =
        store.getBoolean("app_sounds_enabled") ?: true

    actual fun saveAppSoundsEnabled(enabled: Boolean) {
        store.putBoolean("app_sounds_enabled", enabled)
    }
}

