package com.nuvio.app.controller

import com.nuvio.app.core.storage.DesktopStorage
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal actual object ReleaseWatchlistStorage {
    private val file by lazy {
        DesktopStorage.rootDir.resolve("release_watchlist.json")
    }

    actual fun loadPayload(): String? = runCatching {
        if (file.exists()) file.readText() else null
    }.getOrNull()

    actual fun savePayload(payload: String) {
        runCatching {
            Files.createDirectories(file.parent)
            file.writeText(payload)
        }
    }
}

