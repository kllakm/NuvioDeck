package com.nuvio.app.core.build

actual object AppVersionPolicy {
    actual val displayVersionName: String = NuvioDeckVersionConfig.FORK_VERSION_NAME
    actual val displayVersionCode: Int = AppVersionConfig.DESKTOP_VERSION_CODE
    actual val basedOnVersionName: String? = "Nuvio v${NuvioDeckVersionConfig.UPSTREAM_BASE_VERSION_NAME}"
    actual val userAgentAppName: String = "NuvioDeck"
}
