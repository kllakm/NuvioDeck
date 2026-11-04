package com.nuvio.app.controller

import com.nuvio.app.features.player.desktop.DesktopHostOs
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.io.File
import java.util.Base64

/**
 * Native Windows 11 Controller-Compatible Touch & Gamepad Keyboard Manager.
 *
 * Exclusively launches and toggles TabTip.exe / TextInputHost.exe via the Windows COM
 * ITipInvocation::Toggle interface, ensuring full native Gamepad layout support (D-Pad navigation,
 * X for Backspace, Y for Space, LB/RB for cursor positioning).
 *
 * Automatically directs OS focus to Windows Input Experience so gamepads immediately
 * control the virtual keyboard.
 *
 * osk.exe is strictly excluded as it is a legacy non-controller accessibility keyboard.
 */
object WindowsVirtualKeyboardManager {
    private const val TABTIP_PATH = "C:\\Program Files\\Common Files\\microsoft shared\\ink\\TabTip.exe"
    private const val WM_SYSCOMMAND = 0x0112
    private const val SC_CLOSE = 0xF060L
    private const val WM_CLOSE = 0x0010
    private const val SW_HIDE = 0
    private const val SW_SHOW = 5

    // CLSID {4ce576fa-83dc-4f88-951c-9d0782b4e376}
    private val CLSID_UIHostNoLaunch = byteArrayOf(
        0xfa.toByte(), 0x76.toByte(), 0xe5.toByte(), 0x4c.toByte(),
        0xdc.toByte(), 0x83.toByte(),
        0x88.toByte(), 0x4f.toByte(),
        0x95.toByte(), 0x1c.toByte(), 0x9d.toByte(), 0x07.toByte(),
        0x82.toByte(), 0xb4.toByte(), 0xe3.toByte(), 0x76.toByte(),
    )

    // IID {37c994e7-432b-4834-a2f7-dce1f13b834b}
    private val IID_ITipInvocation = byteArrayOf(
        0xe7.toByte(), 0x94.toByte(), 0xc9.toByte(), 0x37.toByte(),
        0x2b.toByte(), 0x43.toByte(),
        0x34.toByte(), 0x48.toByte(),
        0xa2.toByte(), 0xf7.toByte(), 0xdc.toByte(), 0xe1.toByte(),
        0xf1.toByte(), 0x3b.toByte(), 0x83.toByte(), 0x4b.toByte(),
    )

    internal interface Ole32Library : Library {
        fun CoInitialize(pvReserved: Pointer?): Int
        fun CoCreateInstance(
            rclsid: ByteArray,
            pUnkOuter: Pointer?,
            dwClsContext: Int,
            riid: ByteArray,
            ppv: PointerByReference,
        ): Int
        fun CoUninitialize()
    }

    private val ole32: Ole32Library? by lazy {
        if (DesktopHostOs.current == DesktopHostOs.WINDOWS) {
            runCatching { Native.load("ole32", Ole32Library::class.java) }.getOrNull()
        } else null
    }

    private var registryChecked = false

    private fun ensureDesktopModeAutoInvoke() {
        if (registryChecked) return
        registryChecked = true
        runCatching {
            ProcessBuilder(
                "reg", "add", "HKCU\\Software\\Microsoft\\TabletTip\\1.7",
                "/v", "EnableDesktopModeAutoInvoke", "/t", "REG_DWORD", "/d", "1", "/f",
            ).start()
        }
    }

    fun showKeyboard() {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        ensureDesktopModeAutoInvoke()

        // 1. Ensure TabTip.exe is running in background
        try {
            val file = File(TABTIP_PATH)
            if (file.exists()) {
                ProcessBuilder(TABTIP_PATH).start()
            }
        } catch (_: Throwable) {}

        // 2. Invoke COM ITipInvocation::Toggle(GetDesktopWindow())
        val invoked = runCatching { invokeTipCom() }.getOrDefault(false)
        if (!invoked) {
            invokeTipPowerShell()
        }

        // 3. Explicitly transfer focus to the Windows 11 Touch / Gamepad Keyboard
        focusKeyboardWindowAsync()
    }

    private fun focusKeyboardWindowAsync() {
        val u32 = WindowsControllerService.user32 ?: return
        val k32 = WindowsControllerService.kernel32
        Thread {
            for (i in 0..30) {
                try {
                    Thread.sleep(50)
                } catch (_: InterruptedException) {
                    break
                }
                var targetHwnd = u32.FindWindowA("Windows.UI.Core.CoreWindow", "Windows Input Experience")
                if (targetHwnd == 0L) {
                    targetHwnd = u32.FindWindowA("IPTip_Main_Window", null)
                }
                if (targetHwnd == 0L) {
                    targetHwnd = u32.FindWindowA("Windows.UI.Core.CoreWindow", null)
                }

                if (targetHwnd != 0L && u32.IsWindowVisible(targetHwnd)) {
                    runCatching { u32.AllowSetForegroundWindow(-1) }
                    val currentThreadId = k32?.GetCurrentThreadId() ?: 0
                    val fgHwnd = u32.GetForegroundWindow()
                    val fgThreadId = if (fgHwnd != 0L) u32.GetWindowThreadProcessId(fgHwnd, null) else 0
                    val targetThreadId = u32.GetWindowThreadProcessId(targetHwnd, null)

                    if (currentThreadId != 0 && targetThreadId != 0 && currentThreadId != targetThreadId) {
                        runCatching { u32.AttachThreadInput(currentThreadId, targetThreadId, true) }
                    }
                    if (fgThreadId != 0 && targetThreadId != 0 && fgThreadId != targetThreadId) {
                        runCatching { u32.AttachThreadInput(fgThreadId, targetThreadId, true) }
                    }

                    u32.ShowWindow(targetHwnd, SW_SHOW)
                    u32.SetForegroundWindow(targetHwnd)
                    u32.BringWindowToTop(targetHwnd)
                    u32.SetActiveWindow(targetHwnd)
                    u32.SetFocus(targetHwnd)

                    if (currentThreadId != 0 && targetThreadId != 0 && currentThreadId != targetThreadId) {
                        runCatching { u32.AttachThreadInput(currentThreadId, targetThreadId, false) }
                    }
                    if (fgThreadId != 0 && targetThreadId != 0 && fgThreadId != targetThreadId) {
                        runCatching { u32.AttachThreadInput(fgThreadId, targetThreadId, false) }
                    }
                    break
                }
            }
            startKeyboardCloseWatcher()
        }.apply {
            isDaemon = true
            name = "VirtualKeyboardFocusThread"
            start()
        }
    }

    private fun startKeyboardCloseWatcher() {
        val u32 = WindowsControllerService.user32 ?: return
        Thread {
            Thread.sleep(500)
            while (PlatformVirtualKeyboard.isKeyboardActive) {
                try {
                    Thread.sleep(150)
                } catch (_: InterruptedException) {
                    break
                }
                var targetHwnd = u32.FindWindowA("Windows.UI.Core.CoreWindow", "Windows Input Experience")
                if (targetHwnd == 0L) {
                    targetHwnd = u32.FindWindowA("IPTip_Main_Window", null)
                }
                if (targetHwnd == 0L || !u32.IsWindowVisible(targetHwnd)) {
                    PlatformVirtualKeyboard.isKeyboardActive = false
                    WindowsControllerService.appWindow?.let { win ->
                        WindowsControllerService.activateAndFocusWindow(win)
                    }
                    break
                }
            }
        }.apply {
            isDaemon = true
            name = "VirtualKeyboardCloseWatcher"
            start()
        }
    }

    private fun invokeTipCom(): Boolean {
        val o32 = ole32 ?: return false
        o32.CoInitialize(null)
        val ppv = PointerByReference()
        // CLSCTX_INPROC_SERVER (1) | CLSCTX_LOCAL_SERVER (4) = 5
        val hr = o32.CoCreateInstance(
            CLSID_UIHostNoLaunch,
            null,
            5,
            IID_ITipInvocation,
            ppv,
        )
        if (hr != 0 || ppv.value == null) return false
        try {
            val pInterface = ppv.value
            val vtable = pInterface.getPointer(0)
            // ITipInvocation method index 3: Toggle(HWND hwnd)
            val toggleFuncPtr = vtable.getPointer(3L * Native.POINTER_SIZE)
            val u32 = WindowsControllerService.user32
            val desktopHwnd = u32?.GetDesktopWindow() ?: 0L
            Function.getFunction(toggleFuncPtr).invoke(
                Int::class.java,
                arrayOf(pInterface, Pointer.createConstant(desktopHwnd)),
            )
            return true
        } finally {
            runCatching {
                val pInterface = ppv.value
                val vtable = pInterface.getPointer(0)
                val releaseFuncPtr = vtable.getPointer(2L * Native.POINTER_SIZE)
                Function.getFunction(releaseFuncPtr).invoke(Int::class.java, arrayOf(pInterface))
            }
        }
    }

    private fun invokeTipPowerShell() {
        runCatching {
            val script = "Add-Type -TypeDefinition @'\n" +
                "using System;\n" +
                "using System.Runtime.InteropServices;\n" +
                "[ComImport, Guid(\"4ce576fa-83dc-4f88-951c-9d0782b4e376\")] public class UIHostNoLaunch {}\n" +
                "[Guid(\"37c994e7-432b-4834-a2f7-dce1f13b834b\"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]\n" +
                "public interface ITipInvocation { void Toggle(IntPtr hwnd); }\n" +
                "public static class TouchKbd { [DllImport(\"user32.dll\")] public static extern IntPtr GetDesktopWindow();\n" +
                "public static void Show() { ((ITipInvocation)new UIHostNoLaunch()).Toggle(GetDesktopWindow()); } }\n" +
                "'@\n[TouchKbd]::Show()"
            val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
            ProcessBuilder("powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-EncodedCommand", encoded).start()
        }
    }

    fun hideKeyboard() {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        PlatformVirtualKeyboard.isKeyboardActive = false
        try {
            val u32 = WindowsControllerService.user32
            if (u32 != null) {
                val hwndTabTip = u32.FindWindowA("IPTip_Main_Window", null)
                if (hwndTabTip != 0L) {
                    u32.PostMessageA(hwndTabTip, WM_SYSCOMMAND, SC_CLOSE, 0L)
                    u32.PostMessageA(hwndTabTip, WM_CLOSE, 0L, 0L)
                    u32.ShowWindow(hwndTabTip, SW_HIDE)
                }
                val hwndInputHost = u32.FindWindowA("Windows.UI.Core.CoreWindow", "Windows Input Experience")
                if (hwndInputHost != 0L) {
                    u32.PostMessageA(hwndInputHost, WM_CLOSE, 0L, 0L)
                }
            }
        } catch (_: Throwable) {}

        // Restore focus to NuvioDeck window
        WindowsControllerService.appWindow?.let { win ->
            WindowsControllerService.activateAndFocusWindow(win)
        }
    }
}
