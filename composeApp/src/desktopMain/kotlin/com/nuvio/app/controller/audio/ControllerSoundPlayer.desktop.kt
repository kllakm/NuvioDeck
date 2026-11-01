package com.nuvio.app.controller.audio

import com.nuvio.app.controller.ControllerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

actual object ControllerSoundPlayer {
    private val scope = CoroutineScope(Dispatchers.Default)

    private var navigateClip: Clip? = null
    private var selectClip: Clip? = null
    private var backClip: Clip? = null

    private var lastNavigateTime = 0L
    private const val NAVIGATE_DEBOUNCE_MS = 60L

    init {
        scope.launch {
            initClips()
        }
    }

    private fun initClips() {
        try {
            navigateClip = loadClip("/controller/audio/navigate.wav") ?: synthesizeNavigateClip()
            selectClip = loadClip("/controller/audio/select.wav") ?: synthesizeSelectClip()
            backClip = loadClip("/controller/audio/back.wav") ?: synthesizeBackClip()
        } catch (_: Throwable) {
            // Audio initialization is purely non-fatal
        }
    }

    private fun loadClip(resourcePath: String): Clip? {
        return try {
            val stream = javaClass.getResourceAsStream(resourcePath)
                ?: javaClass.getResourceAsStream("/controller/assets$resourcePath")
                ?: return null
            val audioStream = AudioSystem.getAudioInputStream(BufferedInputStream(stream))
            val clip = AudioSystem.getClip()
            clip.open(audioStream)
            tuneVolume(clip, -15.0f)
            clip
        } catch (_: Throwable) {
            null
        }
    }

    private fun tuneVolume(clip: Clip, targetGainDb: Float) {
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val gain = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                val safeGain = targetGainDb.coerceIn(gain.minimum, gain.maximum)
                gain.value = safeGain
            }
        } catch (_: Throwable) {}
    }

    private fun play(clip: Clip?) {
        if (clip == null) return
        try {
            if (clip.isRunning) {
                clip.stop()
            }
            clip.framePosition = 0
            clip.start()
        } catch (_: Throwable) {}
    }

    actual fun playNavigate() {
        if (!ControllerManager.isControllerActive.value) return
        val now = System.currentTimeMillis()
        if (now - lastNavigateTime < NAVIGATE_DEBOUNCE_MS) return
        lastNavigateTime = now

        navigateClip?.let { play(it) }
    }

    actual fun playSelect() {
        if (!ControllerManager.isControllerActive.value) return
        selectClip?.let { play(it) }
    }

    actual fun playBack() {
        if (!ControllerManager.isControllerActive.value) return
        backClip?.let { play(it) }
    }

    // High-fidelity fallback synthesizers (16-bit 44.1kHz mono PCM)
    private fun synthesizeNavigateClip(): Clip? = runCatching {
        val sampleRate = 44100f
        val duration = 0.032
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ByteArray(numSamples * 2)

        for (i in 0 until numSamples) {
            val t = i / sampleRate.toDouble()
            val clickEnv = exp(-t / 0.005)
            val thudEnv = exp(-t / 0.012)
            val click = sin(2.0 * PI * 1800.0 * t) * clickEnv * 0.12
            val thud = sin(2.0 * PI * 130.0 * t) * thudEnv * 0.14
            val sample = ((click + thud) * 32767.0).roundToInt().coerceIn(-32767, 32767).toShort()
            buffer[i * 2] = (sample.toInt() and 0xFF).toByte()
            buffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        createClipFromPcm(buffer, sampleRate)
    }.getOrNull()

    private fun synthesizeSelectClip(): Clip? = runCatching {
        val sampleRate = 44100f
        val duration = 0.090
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ByteArray(numSamples * 2)
        var phase1 = 0.0
        var phase2 = 0.0

        for (i in 0 until numSamples) {
            val t = i / sampleRate.toDouble()
            val attack = min(1.0, t / 0.006)
            val decay = exp(-t / 0.035)
            val env = attack * decay
            val freq1 = 440.0 + (520.0 * min(1.0, t / 0.050))
            val freq2 = freq1 * 1.5
            phase1 += 2.0 * PI * freq1 / sampleRate
            phase2 += 2.0 * PI * freq2 / sampleRate
            val sampleVal = (sin(phase1) * 0.18 + sin(phase2) * 0.06) * env
            val sample = (sampleVal * 32767.0).roundToInt().coerceIn(-32767, 32767).toShort()
            buffer[i * 2] = (sample.toInt() and 0xFF).toByte()
            buffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        createClipFromPcm(buffer, sampleRate)
    }.getOrNull()

    private fun synthesizeBackClip(): Clip? = runCatching {
        val sampleRate = 44100f
        val duration = 0.080
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ByteArray(numSamples * 2)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i / sampleRate.toDouble()
            val attack = min(1.0, t / 0.005)
            val decay = exp(-t / 0.028)
            val env = attack * decay
            val freq = 580.0 - (290.0 * min(1.0, t / 0.060))
            phase += 2.0 * PI * freq / sampleRate
            val sampleVal = sin(phase) * 0.17 * env
            val sample = (sampleVal * 32767.0).roundToInt().coerceIn(-32767, 32767).toShort()
            buffer[i * 2] = (sample.toInt() and 0xFF).toByte()
            buffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        createClipFromPcm(buffer, sampleRate)
    }.getOrNull()

    private fun createClipFromPcm(pcmData: ByteArray, sampleRate: Float): Clip {
        val format = AudioFormat(sampleRate, 16, 1, true, false)
        val audioStream = AudioInputStream(ByteArrayInputStream(pcmData), format, pcmData.size.toLong() / 2)
        val clip = AudioSystem.getClip()
        clip.open(audioStream)
        tuneVolume(clip, -15.0f)
        return clip
    }
}

