package com.nuvio.app.controller

import java.io.File

internal actual object AppBackgroundStorage {
    private val storageFile by lazy {
        runCatching {
            val dir = File(System.getProperty("user.home") ?: System.getProperty("java.io.tmpdir") ?: ".")
            File(dir, ".nuvio_frosted_glass_bg")
        }.getOrNull()
    }

    actual fun loadGlassEnabled(): Boolean? = runCatching {
        storageFile?.takeIf { it.exists() }?.readText()?.trim()?.toBooleanStrictOrNull()
    }.getOrNull()

    actual fun saveGlassEnabled(enabled: Boolean) {
        runCatching {
            storageFile?.writeText(enabled.toString())
        }
    }
}

