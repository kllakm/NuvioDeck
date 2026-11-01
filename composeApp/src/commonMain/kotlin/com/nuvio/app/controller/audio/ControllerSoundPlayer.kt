package com.nuvio.app.controller.audio

/**
 * High-quality audio feedback for controller navigation, selections, and cancellations.
 * Synthesized with zero-latency Game Pass / Plex UI audio dynamics.
 */
expect object ControllerSoundPlayer {
    fun playNavigate()
    fun playSelect()
    fun playBack()
}

