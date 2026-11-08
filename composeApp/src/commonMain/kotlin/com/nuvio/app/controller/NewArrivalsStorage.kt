package com.nuvio.app.controller

internal expect object NewArrivalsStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
    fun loadAppSoundsEnabled(): Boolean
    fun saveAppSoundsEnabled(enabled: Boolean)
}

