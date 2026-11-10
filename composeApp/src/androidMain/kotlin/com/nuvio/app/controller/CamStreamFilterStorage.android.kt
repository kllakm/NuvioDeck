package com.nuvio.app.controller

import java.io.File

internal actual object CamStreamFilterStorage {
    private val storageFile by lazy {
        runCatching {
            val dir = File(System.getProperty("user.home") ?: System.getProperty("java.io.tmpdir") ?: ".")
            File(dir, ".nuvio_cam_filter")
        }.getOrNull()
    }

    actual fun loadEnabled(): Boolean? = runCatching {
        storageFile?.takeIf { it.exists() }?.readText()?.trim()?.toBooleanStrictOrNull()
    }.getOrNull()

    actual fun saveEnabled(enabled: Boolean) {
        runCatching {
            storageFile?.writeText(enabled.toString())
        }
    }
}
