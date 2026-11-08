package com.nuvio.app.controller.audio

import com.nuvio.app.controller.NewArrivalsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ControllerSoundSettings {
    private var loaded = false
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _enabled.value = runCatching { NewArrivalsStorage.loadAppSoundsEnabled() }.getOrDefault(true)
    }

    fun isEnabled(): Boolean {
        ensureLoaded()
        return _enabled.value
    }

    fun setEnabled(value: Boolean) {
        ensureLoaded()
        _enabled.value = value
        runCatching { NewArrivalsStorage.saveAppSoundsEnabled(value) }
    }
}

/**
 * High-quality audio feedback for controller navigation, selections, and cancellations.
 * Synthesized with zero-latency Game Pass / Plex UI audio dynamics.
 */
expect object ControllerSoundPlayer {
    fun playNavigate()
    fun playSelect()
    fun playBack()
}

