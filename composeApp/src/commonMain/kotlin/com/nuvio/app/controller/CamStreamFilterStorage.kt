package com.nuvio.app.controller

internal expect object CamStreamFilterStorage {
    fun loadEnabled(): Boolean?
    fun saveEnabled(enabled: Boolean)
}

