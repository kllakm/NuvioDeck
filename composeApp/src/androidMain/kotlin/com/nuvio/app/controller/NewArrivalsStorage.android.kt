package com.nuvio.app.controller

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object NewArrivalsStorage {
    private const val preferencesName = "nuvio_new_arrivals_notifications"
    private const val payloadKey = "new_arrivals_payload"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(payloadKey), null)

    actual fun savePayload(payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(payloadKey), payload)
            ?.apply()
    }

    actual fun loadAppSoundsEnabled(): Boolean =
        preferences?.getBoolean("app_sounds_enabled", true) ?: true

    actual fun saveAppSoundsEnabled(enabled: Boolean) {
        preferences?.edit()?.putBoolean("app_sounds_enabled", enabled)?.apply()
    }
}

