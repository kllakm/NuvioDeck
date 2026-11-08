package com.nuvio.app.core.build

object NuvioDeckVersionConfig {
    /**
     * NuvioDeck fork version name.
     * Rule: Incremented by 1 in the last digit after each update (unless a major revision is specified).
     */
    const val FORK_VERSION_NAME = "0.1.23"

    /**
     * Upstream Nuvio base version tag that this fork is derived from.
     */
    const val UPSTREAM_BASE_VERSION_NAME = "0.1.35"

    const val FORK_NAME = "NuvioDeck"
    const val DEVELOPER_NAME = "kllakm"

    const val GITHUB_OWNER = "kllakm"
    const val GITHUB_REPO = "NuvioDeck"
    const val GITHUB_RELEASE_SOURCE_NAME = "NuvioDeck"

    val dualVersionDisplayString: String
        get() = "$FORK_NAME v$FORK_VERSION_NAME (based on Nuvio v$UPSTREAM_BASE_VERSION_NAME)"

    const val FORK_ATTRIBUTION_TEXT =
        "NuvioDeck — A controller-first fork of Nuvio, developed by kllakm. Original Nuvio created with love by Tapframe and friends."
}

