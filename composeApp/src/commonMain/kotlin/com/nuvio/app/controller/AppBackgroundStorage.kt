package com.nuvio.app.controller

internal expect object AppBackgroundStorage {
    fun loadGlassEnabled(): Boolean?
    fun saveGlassEnabled(enabled: Boolean)
}

