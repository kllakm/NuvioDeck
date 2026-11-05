package com.nuvio.app.controller

import co.touchlab.kermit.Logger
import com.nuvio.app.features.player.desktop.DesktopHostOs
import com.nuvio.app.features.player.desktop.DesktopPlayerPictureInPicture
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Structure
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Universal Windows 11 Controller Input Polling & Hot-Plug Service.
 *
 * Supports:
 * 1. XInput (`XInput1_4.dll` / `XInput9_1_0.dll` / `XInput1_3.dll`) for Xbox One, Series X|S, Xbox 360,
 *    and XInput-compatible controllers across all 4 slots with automatic hot-plugging.
 * 2. Windows Multimedia / DirectInput (`winmm.dll` `joyGetPosEx` / `joyGetDevCapsW`) for DirectInput,
 *    Sony DualSense / DualShock 4, and Nintendo Switch Pro / Joy-Con controllers in HID/DirectInput mode.
 * 3. Normalizes all physical inputs into a canonical Xbox controller layout (`A`, `B`, `X`, `Y`,
 *    `LB`, `RB`, `LT`, `RT`, `LS`, `RS`, `DPad`, `View`, `Menu`, Left Stick, Right Stick).
 * 4. Implements radial deadzone filtering and smooth directional auto-repeat / debouncing.
 */
object WindowsControllerService {
    private val log = Logger.withTag("WindowsControllerService")
    private val started = AtomicBoolean(false)

    @Volatile
    var isAppWindowFocused: Boolean = true

    @Volatile
    var appWindowHwnd: Long = 0L

    @Volatile
    var appWindow: java.awt.Window? = null

    internal interface User32Library : Library {
        fun GetForegroundWindow(): Long
        fun SetForegroundWindow(hwnd: Long): Boolean
        fun BringWindowToTop(hwnd: Long): Boolean
        fun FindWindowA(lpClassName: String?, lpWindowName: String?): Long
        fun PostMessageA(hWnd: Long, msg: Int, wParam: Long, lParam: Long): Boolean
        fun ShowWindow(hWnd: Long, nCmdShow: Int): Boolean
        fun GetAncestor(hwnd: Long, gaFlags: Int): Long
        fun GetWindowThreadProcessId(hwnd: Long, lpdwProcessId: IntArray?): Int
        fun IsChild(hWndParent: Long, hWnd: Long): Boolean
        fun GetDesktopWindow(): Long
        fun IsWindowVisible(hWnd: Long): Boolean
        fun SetActiveWindow(hWnd: Long): Long
        fun SetFocus(hWnd: Long): Long
        fun AttachThreadInput(idAttach: Int, idAttachTo: Int, fAttach: Boolean): Boolean
        fun AllowSetForegroundWindow(dwProcessId: Int): Boolean
        fun SetCursorPos(X: Int, Y: Int): Boolean
    }

    internal interface Kernel32Library : Library {
        fun GetCurrentProcessId(): Int
        fun GetCurrentThreadId(): Int
    }

    internal val user32: User32Library? by lazy {
        if (DesktopHostOs.current == DesktopHostOs.WINDOWS) {
            runCatching { Native.load("user32", User32Library::class.java) }.getOrNull()
        } else null
    }

    internal val kernel32: Kernel32Library? by lazy {
        if (DesktopHostOs.current == DesktopHostOs.WINDOWS) {
            runCatching { Native.load("kernel32", Kernel32Library::class.java) }.getOrNull()
        } else null
    }

    fun activateAndFocusWindow(window: java.awt.Window) {
        appWindow = window
        val hwnd = runCatching {
            com.nuvio.app.features.player.desktop.AwtNativeViewResolver.resolveNativeViewPointer(window)
        }.getOrDefault(0L)
        if (hwnd != 0L) {
            appWindowHwnd = hwnd
        }
        isAppWindowFocused = true
        javax.swing.SwingUtilities.invokeLater {
            window.toFront()
            window.requestFocus()
            (window as? javax.swing.RootPaneContainer)?.contentPane?.requestFocusInWindow()
            if (hwnd != 0L) {
                runCatching {
                    user32?.SetForegroundWindow(hwnd)
                    user32?.BringWindowToTop(hwnd)
                }
            }
        }
    }

    fun isWindowCurrentlyFocused(): Boolean {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return isAppWindowFocused
        val win = appWindow
        if (win != null) {
            val iconified = ((win as? java.awt.Frame)?.extendedState ?: 0) and java.awt.Frame.ICONIFIED != 0
            if (!win.isVisible || !win.isShowing || iconified) return false
            if (win.isActive || win.isFocused) {
                if (PlatformVirtualKeyboard.isKeyboardActive && WindowsVirtualKeyboardManager.inputPaneTookWindowFocus) {
                    WindowsVirtualKeyboardManager.onWindowGainedFocusOrActivated(win)
                }
                return true
            }
        }
        val hwnd = appWindowHwnd
        val user = user32
        if (user != null) {
            val fg = runCatching { user.GetForegroundWindow() }.getOrDefault(0L)
            if (fg != 0L) {
                val isOurHwnd = (hwnd != 0L && fg == hwnd) ||
                    (hwnd != 0L && runCatching { user.IsChild(hwnd, fg) }.getOrDefault(false)) ||
                    (hwnd != 0L && (
                        runCatching { user.GetAncestor(fg, 2 /* GA_ROOT */) }.getOrDefault(0L) == hwnd ||
                            runCatching { user.GetAncestor(fg, 3 /* GA_ROOTOWNER */) }.getOrDefault(0L) == hwnd
                        ))
                if (isOurHwnd) {
                    if (PlatformVirtualKeyboard.isKeyboardActive && WindowsVirtualKeyboardManager.inputPaneTookWindowFocus) {
                        WindowsVirtualKeyboardManager.onWindowGainedFocusOrActivated(win)
                    }
                    return true
                }
                val fgProcessId = IntArray(1)
                runCatching { user.GetWindowThreadProcessId(fg, fgProcessId) }
                val currentProcessId = runCatching { kernel32?.GetCurrentProcessId() ?: 0 }.getOrDefault(0)
                if (currentProcessId != 0 && fgProcessId[0] == currentProcessId) {
                    if (PlatformVirtualKeyboard.isKeyboardActive && WindowsVirtualKeyboardManager.inputPaneTookWindowFocus) {
                        WindowsVirtualKeyboardManager.onWindowGainedFocusOrActivated(win)
                    }
                    return true
                }
            } else if (PlatformVirtualKeyboard.isKeyboardActive && win != null) {
                // Foreground window became 0L when input pane closed; restore app window focus immediately
                WindowsVirtualKeyboardManager.onWindowGainedFocusOrActivated(win)
                activateAndFocusWindow(win)
                return true
            }
        }
        val pipActive = DesktopPlayerPictureInPicture.pipWindow?.isActive == true
        if (pipActive) return true
        if (PlatformVirtualKeyboard.isKeyboardActive && WindowsVirtualKeyboardManager.inputPaneTookWindowFocus) {
            return false
        }
        return isAppWindowFocused
    }

    private const val ACTIVE_POLL_INTERVAL_MS = 8L
    private const val IDLE_POLL_INTERVAL_MS = 16L
    private const val HOTPLUG_PROBE_INTERVAL_MS = 450L

    private const val LEFT_STICK_DEADZONE = 0.24f
    private const val RIGHT_STICK_DEADZONE = 0.22f
    private const val STICK_NAV_PRESS_THRESHOLD = 0.50f
    private const val STICK_NAV_RELEASE_THRESHOLD = 0.32f
    private const val TRIGGER_PRESS_THRESHOLD = 0.38f
    private const val TRIGGER_RELEASE_THRESHOLD = 0.24f

    private const val REPEAT_INITIAL_DELAY_MS = 360L
    private const val REPEAT_INTERVAL_MS = 105L
    private const val RIGHT_STICK_SCROLL_INTERVAL_MS = 16L

    // XInput button bitmasks
    private const val XINPUT_GAMEPAD_DPAD_UP = 0x0001
    private const val XINPUT_GAMEPAD_DPAD_DOWN = 0x0002
    private const val XINPUT_GAMEPAD_DPAD_LEFT = 0x0004
    private const val XINPUT_GAMEPAD_DPAD_RIGHT = 0x0008
    private const val XINPUT_GAMEPAD_START = 0x0010
    private const val XINPUT_GAMEPAD_BACK = 0x0020
    private const val XINPUT_GAMEPAD_LEFT_THUMB = 0x0040
    private const val XINPUT_GAMEPAD_RIGHT_THUMB = 0x0080
    private const val XINPUT_GAMEPAD_LEFT_SHOULDER = 0x0100
    private const val XINPUT_GAMEPAD_RIGHT_SHOULDER = 0x0200
    private const val XINPUT_GAMEPAD_A = 0x1000
    private const val XINPUT_GAMEPAD_B = 0x2000
    private const val XINPUT_GAMEPAD_X = 0x4000
    private const val XINPUT_GAMEPAD_Y = 0x8000

    private const val ERROR_SUCCESS = 0

    // winmm constants
    private const val JOYERR_NOERROR = 0
    private const val JOY_RETURNALL = 0x000000FF
    private const val JOY_POVCENTERED = 0xFFFF

    // Vendor IDs
    private const val VID_MICROSOFT = 0x045E
    private const val VID_SONY = 0x054C
    private const val VID_NINTENDO = 0x057E

    @Structure.FieldOrder(
        "wButtons",
        "bLeftTrigger",
        "bRightTrigger",
        "sThumbLX",
        "sThumbLY",
        "sThumbRX",
        "sThumbRY",
    )
    class XINPUT_GAMEPAD : Structure() {
        @JvmField var wButtons: Short = 0
        @JvmField var bLeftTrigger: Byte = 0
        @JvmField var bRightTrigger: Byte = 0
        @JvmField var sThumbLX: Short = 0
        @JvmField var sThumbLY: Short = 0
        @JvmField var sThumbRX: Short = 0
        @JvmField var sThumbRY: Short = 0
    }

    @Structure.FieldOrder("dwPacketNumber", "Gamepad")
    class XINPUT_STATE : Structure() {
        @JvmField var dwPacketNumber: Int = 0
        @JvmField var Gamepad: XINPUT_GAMEPAD = XINPUT_GAMEPAD()
    }

    private interface XInputLibrary : Library {
        fun XInputGetState(dwUserIndex: Int, pState: XINPUT_STATE): Int
    }

    @Structure.FieldOrder(
        "dwSize",
        "dwFlags",
        "dwXpos",
        "dwYpos",
        "dwZpos",
        "dwRpos",
        "dwUpos",
        "dwVpos",
        "dwButtons",
        "dwButtonNumber",
        "dwPOV",
        "dwReserved1",
        "dwReserved2",
    )
    class JOYINFOEX : Structure() {
        @JvmField var dwSize: Int = 52
        @JvmField var dwFlags: Int = JOY_RETURNALL
        @JvmField var dwXpos: Int = 0
        @JvmField var dwYpos: Int = 0
        @JvmField var dwZpos: Int = 0
        @JvmField var dwRpos: Int = 0
        @JvmField var dwUpos: Int = 0
        @JvmField var dwVpos: Int = 0
        @JvmField var dwButtons: Int = 0
        @JvmField var dwButtonNumber: Int = 0
        @JvmField var dwPOV: Int = JOY_POVCENTERED
        @JvmField var dwReserved1: Int = 0
        @JvmField var dwReserved2: Int = 0
    }

    @Structure.FieldOrder(
        "wMid",
        "wPid",
        "szPname",
        "wXmin",
        "wXmax",
        "wYmin",
        "wYmax",
        "wZmin",
        "wZmax",
        "wNumButtons",
        "wPeriodMin",
        "wPeriodMax",
        "wRmin",
        "wRmax",
        "wUmin",
        "wUmax",
        "wVmin",
        "wVmax",
        "wCaps",
        "wMaxAxes",
        "wNumAxes",
        "wMaxButtons",
        "szRegKey",
        "szOEMVxD",
    )
    class JOYCAPSW : Structure() {
        @JvmField var wMid: Short = 0
        @JvmField var wPid: Short = 0
        @JvmField var szPname: CharArray = CharArray(32)
        @JvmField var wXmin: Int = 0
        @JvmField var wXmax: Int = 65535
        @JvmField var wYmin: Int = 0
        @JvmField var wYmax: Int = 65535
        @JvmField var wZmin: Int = 0
        @JvmField var wZmax: Int = 65535
        @JvmField var wNumButtons: Int = 0
        @JvmField var wPeriodMin: Int = 0
        @JvmField var wPeriodMax: Int = 0
        @JvmField var wRmin: Int = 0
        @JvmField var wRmax: Int = 65535
        @JvmField var wUmin: Int = 0
        @JvmField var wUmax: Int = 65535
        @JvmField var wVmin: Int = 0
        @JvmField var wVmax: Int = 65535
        @JvmField var wCaps: Int = 0
        @JvmField var wMaxAxes: Int = 0
        @JvmField var wNumAxes: Int = 0
        @JvmField var wMaxButtons: Int = 0
        @JvmField var szRegKey: CharArray = CharArray(32)
        @JvmField var szOEMVxD: CharArray = CharArray(260)
    }

    private interface WinMmLibrary : Library {
        fun joyGetNumDevs(): Int
        fun joyGetPosEx(uJoyID: Int, pji: JOYINFOEX): Int
        fun joyGetDevCapsW(uJoyID: Int, pjc: JOYCAPSW, cbjc: Int): Int
    }

    private enum class DirectInputProfile {
        GENERIC,
        NINTENDO_SWITCH,
        SONY_PLAYSTATION,
        XINPUT_MIRROR,
    }

    private data class NormalizedGamepadState(
        val buttons: Set<XboxButton> = emptySet(),
        val leftStickX: Float = 0f,
        val leftStickY: Float = 0f, // Positive = Up, Negative = Down
        val rightStickX: Float = 0f,
        val rightStickY: Float = 0f, // Positive = Up, Negative = Down
        val leftTrigger: Float = 0f,
        val rightTrigger: Float = 0f,
    )

    private val xInputLib: XInputLibrary? by lazy {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return@lazy null
        val dllCandidates = listOf("XInput1_4", "XInput9_1_0", "XInput1_3")
        for (dll in dllCandidates) {
            try {
                return@lazy Native.load(dll, XInputLibrary::class.java).also {
                    log.i { "Loaded Windows XInput library: $dll" }
                }
            } catch (_: Throwable) {
                // try next
            }
        }
        log.w { "No XInput DLL could be loaded; relying on winmm fallback" }
        null
    }

    private val winMmLib: WinMmLibrary? by lazy {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return@lazy null
        try {
            Native.load("winmm", WinMmLibrary::class.java).also {
                log.i { "Loaded Windows Multimedia Joystick library: winmm.dll" }
            }
        } catch (t: Throwable) {
            log.w(t) { "Failed to load winmm.dll" }
            null
        }
    }

    fun startIfNeeded() {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        if (!started.compareAndSet(false, true)) return

        Thread(::pollLoop, "nuvio-win11-controller-service").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    private fun pollLoop() {
        val xInputStates = Array(4) { XINPUT_STATE() }
        val xInputConnected = BooleanArray(4)
        var lastHotplugProbeMs = 0L

        val joyInfo = JOYINFOEX()
        val joyCaps = JOYCAPSW()
        val joyConnected = BooleanArray(16)
        val joyProfiles = Array(16) { DirectInputProfile.GENERIC }

        var previousButtons = emptySet<XboxButton>()
        var ltPressed = false
        var rtPressed = false
        var stickDirection: ControllerDirection? = null
        var activeNavDirection: ControllerDirection? = null
        var nextRepeatEpochMs = 0L
        var lastRightStickScrollMs = 0L

        while (true) {
            try {
                val now = System.currentTimeMillis()
                val probeDisconnected = (now - lastHotplugProbeMs) >= HOTPLUG_PROBE_INTERVAL_MS
                if (probeDisconnected) {
                    lastHotplugProbeMs = now
                }

                var activeXInputCount = 0
                var mergedButtons = mutableSetOf<XboxButton>()
                var maxLeftStickX = 0f
                var maxLeftStickY = 0f
                var maxRightStickX = 0f
                var maxRightStickY = 0f
                var maxLeftTrigger = 0f
                var maxRightTrigger = 0f

                // 1. Poll XInput slots 0..3
                val xinput = xInputLib
                if (xinput != null) {
                    for (slot in 0..3) {
                        if (!xInputConnected[slot] && !probeDisconnected) continue
                        val res = xinput.XInputGetState(slot, xInputStates[slot])
                        if (res == ERROR_SUCCESS) {
                            xInputConnected[slot] = true
                            activeXInputCount++
                            val state = normalizeXInputState(
                                xInputStates[slot].Gamepad,
                                ltWasPressed = ltPressed,
                                rtWasPressed = rtPressed,
                            )
                            mergedButtons.addAll(state.buttons)
                            if (abs(state.leftStickX) > abs(maxLeftStickX)) maxLeftStickX = state.leftStickX
                            if (abs(state.leftStickY) > abs(maxLeftStickY)) maxLeftStickY = state.leftStickY
                            if (abs(state.rightStickX) > abs(maxRightStickX)) maxRightStickX = state.rightStickX
                            if (abs(state.rightStickY) > abs(maxRightStickY)) maxRightStickY = state.rightStickY
                            maxLeftTrigger = max(maxLeftTrigger, state.leftTrigger)
                            maxRightTrigger = max(maxRightTrigger, state.rightTrigger)
                        } else {
                            xInputConnected[slot] = false
                        }
                    }
                }

                // 2. Poll DirectInput / Switch / PlayStation / HID controllers via winmm
                var activeDirectInputCount = 0
                val winmm = winMmLib
                if (winmm != null) {
                    val numDevs = winmm.joyGetNumDevs().coerceAtMost(16)
                    for (joyId in 0 until numDevs) {
                        if (!joyConnected[joyId] && !probeDisconnected) continue
                        joyInfo.dwSize = joyInfo.size()
                        joyInfo.dwFlags = JOY_RETURNALL
                        val res = winmm.joyGetPosEx(joyId, joyInfo)
                        if (res == JOYERR_NOERROR) {
                            if (!joyConnected[joyId]) {
                                joyConnected[joyId] = true
                                joyProfiles[joyId] = detectDirectInputProfile(winmm, joyId, joyCaps)
                            }
                            val profile = joyProfiles[joyId]
                            // If XInput is already active, skip XInput-mirrored winmm slots so inputs aren't doubled
                            if (profile == DirectInputProfile.XINPUT_MIRROR && activeXInputCount > 0) {
                                continue
                            }
                            activeDirectInputCount++
                            val state = normalizeDirectInputState(
                                info = joyInfo,
                                profile = profile,
                                ltWasPressed = ltPressed,
                                rtWasPressed = rtPressed,
                            )
                            mergedButtons.addAll(state.buttons)
                            if (abs(state.leftStickX) > abs(maxLeftStickX)) maxLeftStickX = state.leftStickX
                            if (abs(state.leftStickY) > abs(maxLeftStickY)) maxLeftStickY = state.leftStickY
                            if (abs(state.rightStickX) > abs(maxRightStickX)) maxRightStickX = state.rightStickX
                            if (abs(state.rightStickY) > abs(maxRightStickY)) maxRightStickY = state.rightStickY
                            maxLeftTrigger = max(maxLeftTrigger, state.leftTrigger)
                            maxRightTrigger = max(maxRightTrigger, state.rightTrigger)
                        } else {
                            joyConnected[joyId] = false
                        }
                    }
                }

                val totalConnected = activeXInputCount + activeDirectInputCount
                if (totalConnected != ControllerManager.connectedControllerCount.value) {
                    ControllerManager.updateConnectedCount(totalConnected)
                }

                ltPressed = XboxButton.LT in mergedButtons
                rtPressed = XboxButton.RT in mergedButtons

                // Only dispatch input events if the app window is focused
                if (isWindowCurrentlyFocused() && totalConnected > 0) {
                    // Check for any analog stick movement past deadzone to activate controller mode immediately
                    val hasAnalogActivity = hypot(maxLeftStickX, maxLeftStickY) > 0.25f ||
                        hypot(maxRightStickX, maxRightStickY) > 0.25f ||
                        maxLeftTrigger > TRIGGER_PRESS_THRESHOLD ||
                        maxRightTrigger > TRIGGER_PRESS_THRESHOLD

                    val wasControllerInactive = !ControllerManager.isControllerActive.value
                    if (hasAnalogActivity || mergedButtons.isNotEmpty()) {
                        DesktopControllerCursorManager.recordControllerActivated()
                        SwingUtilities.invokeLater {
                            ControllerManager.notifyControllerInput()
                            DesktopControllerCursorManager.ensureHiddenOnControllerInput(appWindow)
                            if (wasControllerInactive) {
                                ControllerFocusEngine.ensureValidFocus()
                            }
                        }
                    }

                    // 3. Evaluate discrete button press / release events (excluding D-Pad which is handled via unified directional repeat)
                    val nonDPadButtons = mergedButtons.filterNot {
                        it == XboxButton.DPadUp || it == XboxButton.DPadDown ||
                            it == XboxButton.DPadLeft || it == XboxButton.DPadRight
                    }.toSet()
                    val prevNonDPadButtons = previousButtons.filterNot {
                        it == XboxButton.DPadUp || it == XboxButton.DPadDown ||
                            it == XboxButton.DPadLeft || it == XboxButton.DPadRight
                    }.toSet()

                    val newlyPressed = nonDPadButtons - prevNonDPadButtons
                    val newlyReleased = prevNonDPadButtons - nonDPadButtons

                    if (newlyPressed.isNotEmpty() || newlyReleased.isNotEmpty()) {
                        SwingUtilities.invokeLater {
                            newlyPressed.forEach { btn ->
                                ControllerManager.dispatchEvent(ControllerEvent.ButtonPressed(btn))
                            }
                            newlyReleased.forEach { btn ->
                                ControllerManager.dispatchEvent(ControllerEvent.ButtonReleased(btn))
                            }
                        }
                    }
                    previousButtons = mergedButtons

                    // 4. Evaluate unified D-Pad + Left Stick directional navigation with smooth auto-repeat
                    stickDirection = resolveStickDirection(maxLeftStickX, maxLeftStickY, stickDirection)
                    val dpadDirection = when {
                        XboxButton.DPadUp in mergedButtons -> ControllerDirection.Up
                        XboxButton.DPadDown in mergedButtons -> ControllerDirection.Down
                        XboxButton.DPadLeft in mergedButtons -> ControllerDirection.Left
                        XboxButton.DPadRight in mergedButtons -> ControllerDirection.Right
                        else -> null
                    }
                    val desiredDirection = dpadDirection ?: stickDirection

                    if (desiredDirection != null) {
                        if (wasControllerInactive) {
                            // First movement solely wakes the controller without stepping away from default focus
                            activeNavDirection = desiredDirection
                            nextRepeatEpochMs = now + REPEAT_INITIAL_DELAY_MS
                        } else if (desiredDirection != activeNavDirection) {
                            activeNavDirection = desiredDirection
                            nextRepeatEpochMs = now + REPEAT_INITIAL_DELAY_MS
                            SwingUtilities.invokeLater {
                                ControllerManager.dispatchEvent(
                                    ControllerEvent.Navigate(desiredDirection, isRepeat = false),
                                )
                            }
                        } else if (now >= nextRepeatEpochMs) {
                            nextRepeatEpochMs = now + REPEAT_INTERVAL_MS
                            SwingUtilities.invokeLater {
                                ControllerManager.dispatchEvent(
                                    ControllerEvent.Navigate(desiredDirection, isRepeat = true),
                                )
                            }
                        }
                    } else {
                        activeNavDirection = null
                    }

                    // 5. Evaluate Right Stick smooth analog scrolling
                    if ((abs(maxRightStickX) > 0.05f || abs(maxRightStickY) > 0.05f) &&
                        (now - lastRightStickScrollMs) >= RIGHT_STICK_SCROLL_INTERVAL_MS
                    ) {
                        lastRightStickScrollMs = now
                        val scrollDx = maxRightStickX
                        val scrollDy = -maxRightStickY // Invert Y so pushing stick down scrolls down
                        SwingUtilities.invokeLater {
                            ControllerManager.dispatchEvent(
                                ControllerEvent.AnalogScroll(dx = scrollDx, dy = scrollDy),
                            )
                        }
                    }
                } else {
                    previousButtons = emptySet()
                    activeNavDirection = null
                    stickDirection = null
                }

                Thread.sleep(if (totalConnected > 0) ACTIVE_POLL_INTERVAL_MS else IDLE_POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                break
            } catch (t: Throwable) {
                log.w(t) { "Error in controller polling loop" }
                runCatching { Thread.sleep(250L) }
            }
        }
    }

    private fun resolveStickDirection(
        lx: Float,
        ly: Float,
        previous: ControllerDirection?,
    ): ControllerDirection? {
        val threshold = if (previous != null) STICK_NAV_RELEASE_THRESHOLD else STICK_NAV_PRESS_THRESHOLD
        if (abs(lx) < threshold && abs(ly) < threshold) return null
        return if (abs(lx) > abs(ly)) {
            if (lx > 0f) ControllerDirection.Right else ControllerDirection.Left
        } else {
            if (ly > 0f) ControllerDirection.Up else ControllerDirection.Down
        }
    }

    private fun normalizeXInputState(
        pad: XINPUT_GAMEPAD,
        ltWasPressed: Boolean,
        rtWasPressed: Boolean,
    ): NormalizedGamepadState {
        val buttons = mutableSetOf<XboxButton>()
        val mask = pad.wButtons.toInt() and 0xFFFF

        if ((mask and XINPUT_GAMEPAD_A) != 0) buttons += XboxButton.A
        if ((mask and XINPUT_GAMEPAD_B) != 0) buttons += XboxButton.B
        if ((mask and XINPUT_GAMEPAD_X) != 0) buttons += XboxButton.X
        if ((mask and XINPUT_GAMEPAD_Y) != 0) buttons += XboxButton.Y
        if ((mask and XINPUT_GAMEPAD_LEFT_SHOULDER) != 0) buttons += XboxButton.LB
        if ((mask and XINPUT_GAMEPAD_RIGHT_SHOULDER) != 0) buttons += XboxButton.RB
        if ((mask and XINPUT_GAMEPAD_BACK) != 0) buttons += XboxButton.View
        if ((mask and XINPUT_GAMEPAD_START) != 0) buttons += XboxButton.Menu
        if ((mask and XINPUT_GAMEPAD_LEFT_THUMB) != 0) buttons += XboxButton.LS
        if ((mask and XINPUT_GAMEPAD_RIGHT_THUMB) != 0) buttons += XboxButton.RS
        if ((mask and XINPUT_GAMEPAD_DPAD_UP) != 0) buttons += XboxButton.DPadUp
        if ((mask and XINPUT_GAMEPAD_DPAD_DOWN) != 0) buttons += XboxButton.DPadDown
        if ((mask and XINPUT_GAMEPAD_DPAD_LEFT) != 0) buttons += XboxButton.DPadLeft
        if ((mask and XINPUT_GAMEPAD_DPAD_RIGHT) != 0) buttons += XboxButton.DPadRight

        val lt = ((pad.bLeftTrigger.toInt() and 0xFF) / 255f).coerceIn(0f, 1f)
        val rt = ((pad.bRightTrigger.toInt() and 0xFF) / 255f).coerceIn(0f, 1f)
        val ltThreshold = if (ltWasPressed) TRIGGER_RELEASE_THRESHOLD else TRIGGER_PRESS_THRESHOLD
        val rtThreshold = if (rtWasPressed) TRIGGER_RELEASE_THRESHOLD else TRIGGER_PRESS_THRESHOLD
        if (lt >= ltThreshold) buttons += XboxButton.LT
        if (rt >= rtThreshold) buttons += XboxButton.RT

        val rawLx = (pad.sThumbLX.toFloat() / 32767f).coerceIn(-1f, 1f)
        val rawLy = (pad.sThumbLY.toFloat() / 32767f).coerceIn(-1f, 1f)
        val (lx, ly) = applyRadialDeadzone(rawLx, rawLy, LEFT_STICK_DEADZONE)

        val rawRx = (pad.sThumbRX.toFloat() / 32767f).coerceIn(-1f, 1f)
        val rawRy = (pad.sThumbRY.toFloat() / 32767f).coerceIn(-1f, 1f)
        val (rx, ry) = applyRadialDeadzone(rawRx, rawRy, RIGHT_STICK_DEADZONE)

        return NormalizedGamepadState(
            buttons = buttons,
            leftStickX = lx,
            leftStickY = ly,
            rightStickX = rx,
            rightStickY = ry,
            leftTrigger = lt,
            rightTrigger = rt,
        )
    }

    private fun detectDirectInputProfile(
        winmm: WinMmLibrary,
        joyId: Int,
        caps: JOYCAPSW,
    ): DirectInputProfile {
        val res = winmm.joyGetDevCapsW(joyId, caps, caps.size())
        if (res != JOYERR_NOERROR) return DirectInputProfile.GENERIC
        val vid = caps.wMid.toInt() and 0xFFFF
        val pid = caps.wPid.toInt() and 0xFFFF
        val name = String(caps.szPname).trim { it <= ' ' || it == '\u0000' }.lowercase()

        return when {
            vid == VID_NINTENDO || "nintendo" in name || "pro controller" in name || "joy-con" in name -> {
                log.i { "Detected Nintendo Switch controller on slot $joyId (VID=0x${vid.toString(16)}, PID=0x${pid.toString(16)}, name='$name')" }
                DirectInputProfile.NINTENDO_SWITCH
            }
            vid == VID_SONY || "dualsense" in name || "dualshock" in name || "wireless controller" in name -> {
                log.i { "Detected Sony PlayStation controller on slot $joyId (VID=0x${vid.toString(16)}, PID=0x${pid.toString(16)}, name='$name')" }
                DirectInputProfile.SONY_PLAYSTATION
            }
            vid == VID_MICROSOFT || "xbox" in name || "xinput" in name -> {
                DirectInputProfile.XINPUT_MIRROR
            }
            else -> {
                log.i { "Detected DirectInput controller on slot $joyId (VID=0x${vid.toString(16)}, PID=0x${pid.toString(16)}, name='$name')" }
                DirectInputProfile.GENERIC
            }
        }
    }

    private fun normalizeDirectInputState(
        info: JOYINFOEX,
        profile: DirectInputProfile,
        ltWasPressed: Boolean,
        rtWasPressed: Boolean,
    ): NormalizedGamepadState {
        val buttons = mutableSetOf<XboxButton>()
        val btnMask = info.dwButtons

        fun isBtn(index: Int): Boolean = (btnMask and (1 shl index)) != 0

        when (profile) {
            DirectInputProfile.NINTENDO_SWITCH -> {
                // Switch Pro DirectInput: 0=B(South->Xbox A), 1=A(East->Xbox B), 2=Y(West->Xbox X), 3=X(North->Xbox Y)
                // 4=L(LB), 5=R(RB), 6=ZL(LT), 7=ZR(RT), 8=Minus(View), 9=Plus(Menu), 10=LStick(LS), 11=RStick(RS)
                if (isBtn(0)) buttons += XboxButton.A
                if (isBtn(1)) buttons += XboxButton.B
                if (isBtn(2)) buttons += XboxButton.X
                if (isBtn(3)) buttons += XboxButton.Y
                if (isBtn(4)) buttons += XboxButton.LB
                if (isBtn(5)) buttons += XboxButton.RB
                if (isBtn(6)) buttons += XboxButton.LT
                if (isBtn(7)) buttons += XboxButton.RT
                if (isBtn(8)) buttons += XboxButton.View
                if (isBtn(9)) buttons += XboxButton.Menu
                if (isBtn(10)) buttons += XboxButton.LS
                if (isBtn(11)) buttons += XboxButton.RS
            }
            DirectInputProfile.SONY_PLAYSTATION -> {
                // Sony DualShock 4 / DualSense DirectInput:
                // 0=Square(Xbox X), 1=Cross(Xbox A), 2=Circle(Xbox B), 3=Triangle(Xbox Y)
                // 4=L1(LB), 5=R1(RB), 6=L2(LT), 7=R2(RT), 8=Share/Create(View), 9=Options(Menu), 10=L3(LS), 11=R3(RS)
                if (isBtn(1)) buttons += XboxButton.A
                if (isBtn(2)) buttons += XboxButton.B
                if (isBtn(0)) buttons += XboxButton.X
                if (isBtn(3)) buttons += XboxButton.Y
                if (isBtn(4)) buttons += XboxButton.LB
                if (isBtn(5)) buttons += XboxButton.RB
                if (isBtn(6)) buttons += XboxButton.LT
                if (isBtn(7)) buttons += XboxButton.RT
                if (isBtn(8)) buttons += XboxButton.View
                if (isBtn(9)) buttons += XboxButton.Menu
                if (isBtn(10)) buttons += XboxButton.LS
                if (isBtn(11)) buttons += XboxButton.RS
            }
            else -> {
                // Standard DirectInput / XInput fallback mapping
                if (isBtn(0)) buttons += XboxButton.A
                if (isBtn(1)) buttons += XboxButton.B
                if (isBtn(2)) buttons += XboxButton.X
                if (isBtn(3)) buttons += XboxButton.Y
                if (isBtn(4)) buttons += XboxButton.LB
                if (isBtn(5)) buttons += XboxButton.RB
                if (isBtn(6)) buttons += XboxButton.View
                if (isBtn(7)) buttons += XboxButton.Menu
                if (isBtn(8)) buttons += XboxButton.LS
                if (isBtn(9)) buttons += XboxButton.RS
            }
        }

        // POV Hat (in hundredths of a degree: 0=North, 9000=East, 18000=South, 27000=West, 65535=Centered)
        val pov = info.dwPOV and 0xFFFF
        if (pov != JOY_POVCENTERED && pov <= 36000) {
            if (pov in 31500..36000 || pov in 0..4500) buttons += XboxButton.DPadUp
            if (pov in 4500..13500) buttons += XboxButton.DPadRight
            if (pov in 13500..22500) buttons += XboxButton.DPadDown
            if (pov in 22500..31500) buttons += XboxButton.DPadLeft
        }

        val rawLx = ((info.dwXpos - 32767.5f) / 32767.5f).coerceIn(-1f, 1f)
        val rawLy = (-(info.dwYpos - 32767.5f) / 32767.5f).coerceIn(-1f, 1f)
        val (lx, ly) = applyRadialDeadzone(rawLx, rawLy, LEFT_STICK_DEADZONE)

        val rawRx = ((info.dwZpos - 32767.5f) / 32767.5f).coerceIn(-1f, 1f)
        val rawRy = (-(info.dwRpos - 32767.5f) / 32767.5f).coerceIn(-1f, 1f)
        val (rx, ry) = applyRadialDeadzone(rawRx, rawRy, RIGHT_STICK_DEADZONE)

        return NormalizedGamepadState(
            buttons = buttons,
            leftStickX = lx,
            leftStickY = ly,
            rightStickX = rx,
            rightStickY = ry,
            leftTrigger = if (XboxButton.LT in buttons) 1f else 0f,
            rightTrigger = if (XboxButton.RT in buttons) 1f else 0f,
        )
    }

    private fun applyRadialDeadzone(x: Float, y: Float, deadzone: Float): Pair<Float, Float> {
        val mag = hypot(x, y)
        if (mag <= deadzone) return 0f to 0f
        val normalizedMag = ((mag - deadzone) / (1f - deadzone)).coerceIn(0f, 1f)
        val scale = normalizedMag / mag
        return (x * scale).coerceIn(-1f, 1f) to (y * scale).coerceIn(-1f, 1f)
    }
}

