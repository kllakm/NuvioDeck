package com.nuvio.app.controller

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

internal actual object ReleaseWatchlistStorage {
    private fun getFilePath(): String? {
        val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val documentsDirectory = paths.firstOrNull() as? String ?: return null
        return "$documentsDirectory/release_watchlist.json"
    }

    actual fun loadPayload(): String? {
        val path = getFilePath() ?: return null
        return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) as? String
    }

    actual fun savePayload(payload: String) {
        val path = getFilePath() ?: return
        (payload as NSString).writeToFile(path, true, NSUTF8StringEncoding, null)
    }
}

