package com.nuvio.app.controller

import co.touchlab.kermit.Logger
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Native Linux gamepad poller using standard Linux `joydev` (`/dev/input/js*`) with
 * `evdev` (`/dev/input/event*`) fallback via JNA `libc`.
 *
 * Normalizes Xbox, Sony DualSense/DualShock, Nintendo Switch Pro/Joy-Con, Steam Deck,
 * and generic Linux HID controllers into the shared [WindowsControllerService.NormalizedGamepadState]
 * model so D-Pad, sticks, triggers, buttons, cursor auto-hide, and [ControllerHintToolbar] prompts
 * behave identically across Windows and Linux.
 */
internal object LinuxControllerPoller {
    private val log = Logger.withTag("LinuxControllerPoller")

    private const val MAX_JS_SLOTS = 16
    private const val MAX_EVDEV_SLOTS = 32
    private const val O_RDONLY_NONBLOCK = 0x800 // O_RDONLY (0) | O_NONBLOCK (04000)
    private const val EAGAIN = 11

    // joydev ioctls (Linux <linux/joystick.h>)
    private const val JSIOCGAXES = 0x80016a11L
    private const val JSIOCGBUTTONS = 0x80016a12L
    private const val JSIOCGAXMAP = 0x80406a32L
    private const val JSIOCGBTNMAP = 0x84006a34L

    // evdev ioctls (Linux <linux/input.h>)
    private const val EVIOCGABS_BASE = 0x80184540L

    // joydev event types
    private const val JS_EVENT_BUTTON = 0x01
    private const val JS_EVENT_AXIS = 0x02
    private const val JS_EVENT_INIT = 0x80
    private const val JS_PACKET_SIZE = 8

    // evdev event types
    private const val EV_KEY = 0x01
    private const val EV_ABS = 0x03

    // Linux input key codes
    private const val BTN_SOUTH = 0x130
    private const val BTN_EAST = 0x131
    private const val BTN_C = 0x132
    private const val BTN_NORTH = 0x133
    private const val BTN_WEST = 0x134
    private const val BTN_Z = 0x135
    private const val BTN_TL = 0x136
    private const val BTN_TR = 0x137
    private const val BTN_TL2 = 0x138
    private const val BTN_TR2 = 0x139
    private const val BTN_SELECT = 0x13a
    private const val BTN_START = 0x13b
    private const val BTN_MODE = 0x13c
    private const val BTN_THUMBL = 0x13d
    private const val BTN_THUMBR = 0x13e
    private const val BTN_DPAD_UP = 0x220
    private const val BTN_DPAD_DOWN = 0x221
    private const val BTN_DPAD_LEFT = 0x222
    private const val BTN_DPAD_RIGHT = 0x223

    // Linux input axis codes
    private const val ABS_X = 0x00
    private const val ABS_Y = 0x01
    private const val ABS_Z = 0x02
    private const val ABS_RX = 0x03
    private const val ABS_RY = 0x04
    private const val ABS_RZ = 0x05
    private const val ABS_GAS = 0x09
    private const val ABS_BRAKE = 0x0a
    private const val ABS_HAT0X = 0x10
    private const val ABS_HAT0Y = 0x11

    // USB Vendor IDs
    private const val VID_MICROSOFT = 0x045E
    private const val VID_SONY = 0x054C
    private const val VID_NINTENDO = 0x057E
    private const val VID_VALVE = 0x28DE

    private interface LinuxLibC : Library {
        fun open(path: String, oflag: Int): Int
        fun read(fd: Int, buf: ByteArray, count: Int): Int
        fun close(fd: Int): Int
        fun ioctl(fd: Int, request: NativeLong, arg: ByteArray): Int
        fun ioctl(fd: Int, request: NativeLong, arg: ShortArray): Int
        fun ioctl(fd: Int, request: NativeLong, arg: IntArray): Int
    }

    private enum class LinuxControllerProfile {
        XBOX_OR_GENERIC,
        SONY_PLAYSTATION,
        NINTENDO_SWITCH,
    }

    private data class EvdevAbsRange(
        val min: Int,
        val max: Int,
    )

    private class JsDeviceState(
        val slot: Int,
        val fd: Int,
        val name: String,
        val sysfsDeviceKey: String?,
        val profile: LinuxControllerProfile,
        val axMap: IntArray,
        val btnMap: IntArray,
        val rawButtons: BooleanArray = BooleanArray(btnMap.size),
        val rawAxes: IntArray = IntArray(axMap.size),
    )

    private class EvdevDeviceState(
        val slot: Int,
        val fd: Int,
        val name: String,
        val sysfsDeviceKey: String?,
        val profile: LinuxControllerProfile,
        val absRanges: MutableMap<Int, EvdevAbsRange> = HashMap(),
        val pressedKeyCodes: MutableSet<Int> = HashSet(),
        val axisValues: MutableMap<Int, Int> = HashMap(),
    )

    internal data class PollResult(
        val connectedCount: Int,
        val state: WindowsControllerService.NormalizedGamepadState,
    )

    private val libc: LinuxLibC? by lazy {
        try {
            Native.load("c", LinuxLibC::class.java)
        } catch (t: Throwable) {
            log.w(t) { "Failed to load Linux libc for controller polling" }
            null
        }
    }

    private val jsDevices = arrayOfNulls<JsDeviceState>(MAX_JS_SLOTS)
    private val evdevDevices = arrayOfNulls<EvdevDeviceState>(MAX_EVDEV_SLOTS)
    private val evdevPacketSize = if (Native.POINTER_SIZE == 8) 24 else 16

    fun poll(
        probeDisconnected: Boolean,
        ltWasPressed: Boolean,
        rtWasPressed: Boolean,
    ): PollResult {
        val c = libc ?: return PollResult(0, WindowsControllerService.NormalizedGamepadState())

        if (probeDisconnected) {
            probeJsDevices(c)
            probeEvdevFallbackDevices(c)
        }

        val activeJsKeys = HashSet<String>()
        var connectedCount = 0
        val mergedButtons = mutableSetOf<XboxButton>()
        var maxLeftStickX = 0f
        var maxLeftStickY = 0f
        var maxRightStickX = 0f
        var maxRightStickY = 0f
        var maxLeftTrigger = 0f
        var maxRightTrigger = 0f

        for (slot in 0 until MAX_JS_SLOTS) {
            val dev = jsDevices[slot] ?: continue
            if (!drainJsEvents(c, dev)) {
                closeJsSlot(c, slot)
                continue
            }
            dev.sysfsDeviceKey?.let { activeJsKeys.add(it) }
            connectedCount++
            val state = normalizeJsDevice(dev, ltWasPressed, rtWasPressed)
            mergedButtons.addAll(state.buttons)
            if (kotlin.math.abs(state.leftStickX) > kotlin.math.abs(maxLeftStickX)) maxLeftStickX = state.leftStickX
            if (kotlin.math.abs(state.leftStickY) > kotlin.math.abs(maxLeftStickY)) maxLeftStickY = state.leftStickY
            if (kotlin.math.abs(state.rightStickX) > kotlin.math.abs(maxRightStickX)) maxRightStickX = state.rightStickX
            if (kotlin.math.abs(state.rightStickY) > kotlin.math.abs(maxRightStickY)) maxRightStickY = state.rightStickY
            maxLeftTrigger = max(maxLeftTrigger, state.leftTrigger)
            maxRightTrigger = max(maxRightTrigger, state.rightTrigger)
        }

        for (slot in 0 until MAX_EVDEV_SLOTS) {
            val dev = evdevDevices[slot] ?: continue
            if (dev.sysfsDeviceKey != null && dev.sysfsDeviceKey in activeJsKeys) {
                closeEvdevSlot(c, slot)
                continue
            }
            if (!drainEvdevEvents(c, dev)) {
                closeEvdevSlot(c, slot)
                continue
            }
            connectedCount++
            val state = normalizeEvdevDevice(dev, ltWasPressed, rtWasPressed)
            mergedButtons.addAll(state.buttons)
            if (kotlin.math.abs(state.leftStickX) > kotlin.math.abs(maxLeftStickX)) maxLeftStickX = state.leftStickX
            if (kotlin.math.abs(state.leftStickY) > kotlin.math.abs(maxLeftStickY)) maxLeftStickY = state.leftStickY
            if (kotlin.math.abs(state.rightStickX) > kotlin.math.abs(maxRightStickX)) maxRightStickX = state.rightStickX
            if (kotlin.math.abs(state.rightStickY) > kotlin.math.abs(maxRightStickY)) maxRightStickY = state.rightStickY
            maxLeftTrigger = max(maxLeftTrigger, state.leftTrigger)
            maxRightTrigger = max(maxRightTrigger, state.rightTrigger)
        }

        return PollResult(
            connectedCount = connectedCount,
            state = WindowsControllerService.NormalizedGamepadState(
                buttons = mergedButtons,
                leftStickX = maxLeftStickX,
                leftStickY = maxLeftStickY,
                rightStickX = maxRightStickX,
                rightStickY = maxRightStickY,
                leftTrigger = maxLeftTrigger,
                rightTrigger = maxRightTrigger,
            ),
        )
    }

    private fun probeJsDevices(c: LinuxLibC) {
        for (slot in 0 until MAX_JS_SLOTS) {
            if (jsDevices[slot] != null) continue
            val devNode = "/dev/input/js$slot"
            if (!File(devNode).exists()) continue

            val fd = c.open(devNode, O_RDONLY_NONBLOCK)
            if (fd < 0) continue

            val sysfsDir = File("/sys/class/input/js$slot/device")
            val sysfsKey = runCatching { sysfsDir.canonicalPath }.getOrNull()
            val (vid, pid, name) = readDeviceIdentity(sysfsDir, "Linux Joydev $slot")
            val profile = classifyProfile(vid, name)

            val axisCount = queryByteIoctl(c, fd, JSIOCGAXES, fallback = 8).coerceIn(1, 64)
            val buttonCount = queryByteIoctl(c, fd, JSIOCGBUTTONS, fallback = 24).coerceIn(1, 512)
            val axMap = queryAxisMap(c, fd, axisCount)
            val btnMap = queryButtonMap(c, fd, buttonCount)

            val dev = JsDeviceState(
                slot = slot,
                fd = fd,
                name = name,
                sysfsDeviceKey = sysfsKey,
                profile = profile,
                axMap = axMap,
                btnMap = btnMap,
            )
            // Drain initial synthetic JS_EVENT_INIT events immediately so startup state reflects idle/held state
            drainJsEvents(c, dev)
            jsDevices[slot] = dev
            log.i {
                "Opened Linux joydev $devNode: name='$name', vid=0x${vid.toString(16)}, pid=0x${pid.toString(16)}, profile=$profile"
            }
        }
    }

    private fun probeEvdevFallbackDevices(c: LinuxLibC) {
        val activeJsKeys = jsDevices.mapNotNull { it?.sysfsDeviceKey }.toSet()
        for (slot in 0 until MAX_EVDEV_SLOTS) {
            if (evdevDevices[slot] != null) continue
            val devNode = "/dev/input/event$slot"
            if (!File(devNode).exists()) continue

            val sysfsInputDir = File("/sys/class/input/event$slot")
            val sysfsDeviceDir = File(sysfsInputDir, "device")
            if (!isGamepadSysfsNode(sysfsInputDir, sysfsDeviceDir)) continue

            val sysfsKey = runCatching { sysfsDeviceDir.canonicalPath }.getOrNull()
            if (sysfsKey != null && sysfsKey in activeJsKeys) continue

            val fd = c.open(devNode, O_RDONLY_NONBLOCK)
            if (fd < 0) continue

            val (vid, pid, name) = readDeviceIdentity(sysfsDeviceDir, "Linux Evdev $slot")
            val profile = classifyProfile(vid, name)
            val dev = EvdevDeviceState(
                slot = slot,
                fd = fd,
                name = name,
                sysfsDeviceKey = sysfsKey,
                profile = profile,
            )
            queryEvdevAbsRanges(c, dev)
            drainEvdevEvents(c, dev)
            evdevDevices[slot] = dev
            log.i {
                "Opened Linux evdev fallback $devNode: name='$name', vid=0x${vid.toString(16)}, pid=0x${pid.toString(16)}, profile=$profile"
            }
        }
    }

    private fun isGamepadSysfsNode(sysfsInputDir: File, sysfsDeviceDir: File): Boolean {
        val ueventText = runCatching { File(sysfsInputDir, "uevent").readText() }.getOrNull().orEmpty()
        val hasJsSibling = runCatching {
            sysfsDeviceDir.listFiles()?.any { it.name.startsWith("js") } == true
        }.getOrDefault(false)
        if ("ID_INPUT_JOYSTICK=1" in ueventText || hasJsSibling) {
            return true
        }
        val keyCapsHex = runCatching {
            File(sysfsDeviceDir, "capabilities/key").readText().trim()
        }.getOrNull().orEmpty()
        if (keyCapsHex.isNotEmpty() && hasEvdevGamepadButtonBits(keyCapsHex)) {
            return true
        }
        return false
    }

    private fun hasEvdevGamepadButtonBits(capsLine: String): Boolean {
        // capabilities/key prints space-separated hex words in big-endian word order; BTN_SOUTH is 0x130 (bit 304)
        val words = capsLine.split(Regex("\\s+")).filter { it.isNotBlank() }
        val longBits = Native.POINTER_SIZE * 8
        val wordFromEnd = BTN_SOUTH / longBits
        if (words.size <= wordFromEnd) return false
        val wordHex = words[words.size - 1 - wordFromEnd]
        val wordVal = wordHex.toULongOrNull(16) ?: return false
        val bitMask = 1UL shl (BTN_SOUTH % longBits)
        return (wordVal and bitMask) != 0UL
    }

    private fun readDeviceIdentity(sysfsDeviceDir: File, fallbackName: String): Triple<Int, Int, String> {
        val vid = runCatching {
            File(sysfsDeviceDir, "id/vendor").readText().trim().toInt(16)
        }.getOrDefault(0)
        val pid = runCatching {
            File(sysfsDeviceDir, "id/product").readText().trim().toInt(16)
        }.getOrDefault(0)
        val name = runCatching {
            File(sysfsDeviceDir, "name").readText().trim().ifEmpty { fallbackName }
        }.getOrDefault(fallbackName)
        return Triple(vid, pid, name)
    }

    private fun classifyProfile(vid: Int, name: String): LinuxControllerProfile {
        val lower = name.lowercase()
        return when {
            vid == VID_SONY ||
                "dualsense" in lower ||
                "dualshock" in lower ||
                "wireless controller" in lower ||
                "playstation" in lower -> LinuxControllerProfile.SONY_PLAYSTATION
            vid == VID_NINTENDO ||
                "nintendo" in lower ||
                "pro controller" in lower ||
                "joy-con" in lower -> LinuxControllerProfile.NINTENDO_SWITCH
            vid == VID_MICROSOFT || vid == VID_VALVE || "xbox" in lower -> LinuxControllerProfile.XBOX_OR_GENERIC
            else -> LinuxControllerProfile.XBOX_OR_GENERIC
        }
    }

    private fun queryByteIoctl(c: LinuxLibC, fd: Int, request: Long, fallback: Int): Int {
        val buf = ByteArray(1)
        val res = c.ioctl(fd, NativeLong(request and 0xFFFFFFFFL), buf)
        return if (res >= 0) (buf[0].toInt() and 0xFF) else fallback
    }

    private fun queryAxisMap(c: LinuxLibC, fd: Int, axisCount: Int): IntArray {
        val buf = ByteArray(64)
        val res = c.ioctl(fd, NativeLong(JSIOCGAXMAP and 0xFFFFFFFFL), buf)
        if (res >= 0) {
            return IntArray(axisCount) { idx -> buf[idx].toInt() and 0xFF }
        }
        val standard = intArrayOf(ABS_X, ABS_Y, ABS_Z, ABS_RX, ABS_RY, ABS_RZ, ABS_HAT0X, ABS_HAT0Y)
        return IntArray(axisCount) { idx -> standard.getOrElse(idx) { idx } }
    }

    private fun queryButtonMap(c: LinuxLibC, fd: Int, buttonCount: Int): IntArray {
        val buf = ShortArray(512)
        val res = c.ioctl(fd, NativeLong(JSIOCGBTNMAP and 0xFFFFFFFFL), buf)
        if (res >= 0) {
            return IntArray(buttonCount) { idx -> buf[idx].toInt() and 0xFFFF }
        }
        val standard = intArrayOf(
            BTN_SOUTH, BTN_EAST, BTN_NORTH, BTN_WEST,
            BTN_TL, BTN_TR, BTN_SELECT, BTN_START,
            BTN_MODE, BTN_THUMBL, BTN_THUMBR,
            BTN_DPAD_UP, BTN_DPAD_DOWN, BTN_DPAD_LEFT, BTN_DPAD_RIGHT,
        )
        return IntArray(buttonCount) { idx -> standard.getOrElse(idx) { BTN_SOUTH + idx } }
    }

    private fun queryEvdevAbsRanges(c: LinuxLibC, dev: EvdevDeviceState) {
        val axes = intArrayOf(ABS_X, ABS_Y, ABS_Z, ABS_RX, ABS_RY, ABS_RZ, ABS_GAS, ABS_BRAKE, ABS_HAT0X, ABS_HAT0Y)
        val absInfo = IntArray(6) // struct input_absinfo { value, minimum, maximum, fuzz, flat, resolution }
        for (axis in axes) {
            val req = (EVIOCGABS_BASE + axis) and 0xFFFFFFFFL
            val res = c.ioctl(dev.fd, NativeLong(req), absInfo)
            if (res >= 0) {
                dev.absRanges[axis] = EvdevAbsRange(min = absInfo[1], max = absInfo[2])
            }
        }
    }

    private fun drainJsEvents(c: LinuxLibC, dev: JsDeviceState): Boolean {
        val buf = ByteArray(JS_PACKET_SIZE * 64)
        while (true) {
            val n = c.read(dev.fd, buf, buf.size)
            if (n > 0) {
                val count = n / JS_PACKET_SIZE
                val bb = ByteBuffer.wrap(buf, 0, count * JS_PACKET_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until count) {
                    bb.getInt() // time ms (unused)
                    val value = bb.getShort().toInt()
                    val type = bb.get().toInt() and 0xFF and JS_EVENT_INIT.inv()
                    val number = bb.get().toInt() and 0xFF
                    when (type) {
                        JS_EVENT_BUTTON -> {
                            if (number < dev.rawButtons.size) {
                                dev.rawButtons[number] = value != 0
                            }
                        }
                        JS_EVENT_AXIS -> {
                            if (number < dev.rawAxes.size) {
                                dev.rawAxes[number] = value
                            }
                        }
                    }
                }
                if (n < buf.size) return true
            } else if (n == 0) {
                return false
            } else {
                return Native.getLastError() == EAGAIN
            }
        }
    }

    private fun drainEvdevEvents(c: LinuxLibC, dev: EvdevDeviceState): Boolean {
        val buf = ByteArray(evdevPacketSize * 64)
        while (true) {
            val n = c.read(dev.fd, buf, buf.size)
            if (n > 0) {
                val count = n / evdevPacketSize
                val bb = ByteBuffer.wrap(buf, 0, count * evdevPacketSize).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until count) {
                    if (evdevPacketSize == 24) {
                        bb.getLong() // tv_sec
                        bb.getLong() // tv_usec
                    } else {
                        bb.getInt()
                        bb.getInt()
                    }
                    val type = bb.getShort().toInt() and 0xFFFF
                    val code = bb.getShort().toInt() and 0xFFFF
                    val value = bb.getInt()
                    when (type) {
                        EV_KEY -> {
                            if (value != 0) {
                                dev.pressedKeyCodes.add(code)
                            } else {
                                dev.pressedKeyCodes.remove(code)
                            }
                        }
                        EV_ABS -> dev.axisValues[code] = value
                    }
                }
                if (n < buf.size) return true
            } else if (n == 0) {
                return false
            } else {
                return Native.getLastError() == EAGAIN
            }
        }
    }

    private fun closeJsSlot(c: LinuxLibC, slot: Int) {
        val dev = jsDevices[slot] ?: return
        jsDevices[slot] = null
        runCatching { c.close(dev.fd) }
        log.i { "Closed disconnected Linux joydev slot $slot (${dev.name})" }
    }

    private fun closeEvdevSlot(c: LinuxLibC, slot: Int) {
        val dev = evdevDevices[slot] ?: return
        evdevDevices[slot] = null
        runCatching { c.close(dev.fd) }
        log.i { "Closed disconnected Linux evdev slot $slot (${dev.name})" }
    }

    private fun normalizeJsDevice(
        dev: JsDeviceState,
        ltWasPressed: Boolean,
        rtWasPressed: Boolean,
    ): WindowsControllerService.NormalizedGamepadState {
        val buttons = mutableSetOf<XboxButton>()
        val isRawHidDs4Buttons = dev.profile == LinuxControllerProfile.SONY_PLAYSTATION &&
            dev.btnMap.any { it == BTN_C }

        for (idx in dev.rawButtons.indices) {
            if (!dev.rawButtons[idx]) continue
            val code = dev.btnMap.getOrElse(idx) { BTN_SOUTH + idx }
            if (isRawHidDs4Buttons) {
                mapRawPlayStationButtonIndex(idx)?.let { buttons += it }
            } else {
                mapLinuxKeyCode(code, dev.profile)?.let { buttons += it }
            }
        }

        var rawLx = 0f
        var rawLy = 0f
        var rawRx = 0f
        var rawRy = 0f
        var lt = 0f
        var rt = 0f

        for (idx in dev.rawAxes.indices) {
            val raw = dev.rawAxes[idx]
            val axisCode = dev.axMap.getOrElse(idx) { idx }
            when (axisCode) {
                ABS_X -> rawLx = (raw / 32767f).coerceIn(-1f, 1f)
                ABS_Y -> rawLy = (-raw / 32767f).coerceIn(-1f, 1f)
                ABS_RX -> rawRx = (raw / 32767f).coerceIn(-1f, 1f)
                ABS_RY -> rawRy = (-raw / 32767f).coerceIn(-1f, 1f)
                ABS_Z, ABS_BRAKE -> lt = max(lt, ((raw + 32767f) / 65534f).coerceIn(0f, 1f))
                ABS_RZ, ABS_GAS -> rt = max(rt, ((raw + 32767f) / 65534f).coerceIn(0f, 1f))
                ABS_HAT0X -> {
                    if (raw < -16000) buttons += XboxButton.DPadLeft
                    if (raw > 16000) buttons += XboxButton.DPadRight
                }
                ABS_HAT0Y -> {
                    if (raw < -16000) buttons += XboxButton.DPadUp
                    if (raw > 16000) buttons += XboxButton.DPadDown
                }
            }
        }

        val ltThreshold = if (ltWasPressed) WindowsControllerService.TRIGGER_RELEASE_THRESHOLD else WindowsControllerService.TRIGGER_PRESS_THRESHOLD
        val rtThreshold = if (rtWasPressed) WindowsControllerService.TRIGGER_RELEASE_THRESHOLD else WindowsControllerService.TRIGGER_PRESS_THRESHOLD
        if (lt >= ltThreshold) buttons += XboxButton.LT
        if (rt >= rtThreshold) buttons += XboxButton.RT

        val (lx, ly) = WindowsControllerService.applyRadialDeadzone(rawLx, rawLy, WindowsControllerService.LEFT_STICK_DEADZONE)
        val (rx, ry) = WindowsControllerService.applyRadialDeadzone(rawRx, rawRy, WindowsControllerService.RIGHT_STICK_DEADZONE)

        return WindowsControllerService.NormalizedGamepadState(
            buttons = buttons,
            leftStickX = lx,
            leftStickY = ly,
            rightStickX = rx,
            rightStickY = ry,
            leftTrigger = lt,
            rightTrigger = rt,
        )
    }

    private fun normalizeEvdevDevice(
        dev: EvdevDeviceState,
        ltWasPressed: Boolean,
        rtWasPressed: Boolean,
    ): WindowsControllerService.NormalizedGamepadState {
        val buttons = mutableSetOf<XboxButton>()
        val isRawHidDs4Buttons = dev.profile == LinuxControllerProfile.SONY_PLAYSTATION &&
            (BTN_C in dev.pressedKeyCodes || dev.absRanges[ABS_X]?.let { it.min == 0 && it.max == 255 } == true)

        for (code in dev.pressedKeyCodes) {
            if (isRawHidDs4Buttons && code in BTN_SOUTH..BTN_THUMBR) {
                mapRawPlayStationButtonIndex(code - BTN_SOUTH)?.let { buttons += it }
            } else {
                mapLinuxKeyCode(code, dev.profile)?.let { buttons += it }
            }
        }

        fun normalizeSignedAxis(axisCode: Int, fallbackMin: Int = -32768, fallbackMax: Int = 32767): Float {
            val raw = dev.axisValues[axisCode] ?: return 0f
            val range = dev.absRanges[axisCode]
            val min = range?.min ?: fallbackMin
            val max = range?.max ?: fallbackMax
            if (max <= min) return 0f
            val center = (min + max) / 2f
            val halfSpan = (max - min) / 2f
            return ((raw - center) / halfSpan).coerceIn(-1f, 1f)
        }

        fun normalizeTriggerAxis(axisCode: Int, fallbackMin: Int = 0, fallbackMax: Int = 255): Float {
            val raw = dev.axisValues[axisCode] ?: return 0f
            val range = dev.absRanges[axisCode]
            val min = range?.min ?: fallbackMin
            val max = range?.max ?: fallbackMax
            if (max <= min) return 0f
            return ((raw - min).toFloat() / (max - min).toFloat()).coerceIn(0f, 1f)
        }

        val rawLx = normalizeSignedAxis(ABS_X)
        val rawLy = -normalizeSignedAxis(ABS_Y)
        val rawRx = normalizeSignedAxis(ABS_RX)
        val rawRy = -normalizeSignedAxis(ABS_RY)
        val lt = max(normalizeTriggerAxis(ABS_Z), normalizeTriggerAxis(ABS_BRAKE))
        val rt = max(normalizeTriggerAxis(ABS_RZ), normalizeTriggerAxis(ABS_GAS))

        val hatX = dev.axisValues[ABS_HAT0X] ?: 0
        if (hatX < 0) buttons += XboxButton.DPadLeft
        if (hatX > 0) buttons += XboxButton.DPadRight
        val hatY = dev.axisValues[ABS_HAT0Y] ?: 0
        if (hatY < 0) buttons += XboxButton.DPadUp
        if (hatY > 0) buttons += XboxButton.DPadDown

        val ltThreshold = if (ltWasPressed) WindowsControllerService.TRIGGER_RELEASE_THRESHOLD else WindowsControllerService.TRIGGER_PRESS_THRESHOLD
        val rtThreshold = if (rtWasPressed) WindowsControllerService.TRIGGER_RELEASE_THRESHOLD else WindowsControllerService.TRIGGER_PRESS_THRESHOLD
        if (lt >= ltThreshold) buttons += XboxButton.LT
        if (rt >= rtThreshold) buttons += XboxButton.RT

        val (lx, ly) = WindowsControllerService.applyRadialDeadzone(rawLx, rawLy, WindowsControllerService.LEFT_STICK_DEADZONE)
        val (rx, ry) = WindowsControllerService.applyRadialDeadzone(rawRx, rawRy, WindowsControllerService.RIGHT_STICK_DEADZONE)

        return WindowsControllerService.NormalizedGamepadState(
            buttons = buttons,
            leftStickX = lx,
            leftStickY = ly,
            rightStickX = rx,
            rightStickY = ry,
            leftTrigger = lt,
            rightTrigger = rt,
        )
    }

    private fun mapRawPlayStationButtonIndex(index: Int): XboxButton? = when (index) {
        0 -> XboxButton.X       // Square -> Xbox X
        1 -> XboxButton.A       // Cross -> Xbox A
        2 -> XboxButton.B       // Circle -> Xbox B
        3 -> XboxButton.Y       // Triangle -> Xbox Y
        4 -> XboxButton.LB
        5 -> XboxButton.RB
        6 -> XboxButton.LT
        7 -> XboxButton.RT
        8 -> XboxButton.View    // Share/Create -> View
        9 -> XboxButton.Menu    // Options -> Menu
        10 -> XboxButton.LS
        11 -> XboxButton.RS
        else -> null
    }

    private fun mapLinuxKeyCode(code: Int, profile: LinuxControllerProfile): XboxButton? = when (code) {
        BTN_SOUTH -> XboxButton.A
        BTN_EAST -> XboxButton.B
        BTN_NORTH -> when (profile) {
            LinuxControllerProfile.SONY_PLAYSTATION -> XboxButton.Y // hid-playstation Triangle
            else -> XboxButton.X
        }
        BTN_WEST -> when (profile) {
            LinuxControllerProfile.SONY_PLAYSTATION -> XboxButton.X // hid-playstation Square
            else -> XboxButton.Y
        }
        BTN_TL -> XboxButton.LB
        BTN_TR -> XboxButton.RB
        BTN_TL2 -> XboxButton.LT
        BTN_TR2 -> XboxButton.RT
        BTN_SELECT -> XboxButton.View
        BTN_START -> XboxButton.Menu
        BTN_THUMBL -> XboxButton.LS
        BTN_THUMBR -> XboxButton.RS
        BTN_DPAD_UP -> XboxButton.DPadUp
        BTN_DPAD_DOWN -> XboxButton.DPadDown
        BTN_DPAD_LEFT -> XboxButton.DPadLeft
        BTN_DPAD_RIGHT -> XboxButton.DPadRight
        else -> null
    }
}
