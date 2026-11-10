package com.nuvio.app.controller

internal expect object ReleaseWatchlistStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
}

