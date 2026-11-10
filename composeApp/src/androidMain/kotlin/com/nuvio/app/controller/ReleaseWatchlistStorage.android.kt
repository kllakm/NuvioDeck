package com.nuvio.app.controller

import android.content.Context
import java.io.File

internal actual object ReleaseWatchlistStorage {
    private var file: File? = null

    fun initialize(context: Context) {
        file = File(context.filesDir, "release_watchlist.json")
    }

    actual fun loadPayload(): String? = runCatching {
        file?.takeIf { it.exists() }?.readText()
    }.getOrNull()

    actual fun savePayload(payload: String) {
        runCatching {
            file?.writeText(payload)
        }
    }
}

