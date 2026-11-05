package com.nuvio.app.controller

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.nuvio.app.features.player.desktop.DesktopPlayerPictureInPicture
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.awt.image.BufferedImage
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import kotlin.math.hypot

object DesktopControllerCursorManager {
    private const val MOUSE_JITTER_THRESHOLD_PX = 6.0
    private const val SYNTHETIC_MOUSE_GUARD_MS = 180L

    val hiddenAwtCursor: Cursor by lazy {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        Toolkit.getDefaultToolkit().createCustomCursor(image, Point(0, 0), "nuvio-controller-hidden-cursor")
    }

    val hiddenComposePointerIcon: PointerIcon by lazy {
        PointerIcon(hiddenAwtCursor)
    }

    @Volatile
    private var anchorMouseScreenPoint: Point? = null

    @Volatile
    private var lastKnownMouseScreenPoint: Point? = null

    @Volatile
    private var lastCursorRefreshEpochMs: Long = 0L

    fun recordControllerActivated() {
        val pt = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull() ?: lastKnownMouseScreenPoint
        if (pt != null) {
            anchorMouseScreenPoint = pt
            lastKnownMouseScreenPoint = pt
        }
        lastCursorRefreshEpochMs = System.currentTimeMillis()
    }

    fun ensureHiddenOnControllerInput(window: Window?) {
        val targetWin = window ?: WindowsControllerService.appWindow ?: return
        applyCursorToWindowTree(targetWin, hideCursor = true)
    }

    fun applyCursorToWindowTree(window: Window?, hideCursor: Boolean) {
        if (window == null) return
        val targetCursor = if (hideCursor) hiddenAwtCursor else Cursor.getDefaultCursor()
        if (hideCursor) {
            lastCursorRefreshEpochMs = System.currentTimeMillis()
        }
        val applyBlock = {
            applyCursorRecursive(window, targetCursor)
            if (window is RootPaneContainer) {
                window.rootPane?.cursor = targetCursor
                window.contentPane?.cursor = targetCursor
                window.layeredPane?.cursor = targetCursor
                window.glassPane?.cursor = targetCursor
            }
            if (hideCursor) {
                val pt = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull() ?: anchorMouseScreenPoint
                if (pt != null) {
                    anchorMouseScreenPoint = pt
                    lastCursorRefreshEpochMs = System.currentTimeMillis()
                    runCatching { WindowsControllerService.user32?.SetCursorPos(pt.x, pt.y) }
                }
            }
        }
        if (SwingUtilities.isEventDispatchThread()) {
            applyBlock()
        } else {
            SwingUtilities.invokeLater(applyBlock)
        }
    }

    private fun applyCursorRecursive(component: Component, targetCursor: Cursor) {
        // Avoid overriding NativePlayerHost when it manages its own cursor unless controller is hiding it
        if (component.cursor != targetCursor) {
            component.cursor = targetCursor
        }
        if (component is Container) {
            for (child in component.components) {
                applyCursorRecursive(child, targetCursor)
            }
        }
    }

    fun shouldWakeOnMouseMove(event: MouseEvent): Boolean {
        val screenLoc = try {
            event.locationOnScreen
        } catch (_: Throwable) {
            return false
        }
        lastKnownMouseScreenPoint = screenLoc
        if (!ControllerManager.isControllerActive.value) {
            anchorMouseScreenPoint = screenLoc
            return false
        }
        if (System.currentTimeMillis() - lastCursorRefreshEpochMs < SYNTHETIC_MOUSE_GUARD_MS) {
            return false
        }
        val anchor = anchorMouseScreenPoint
        if (anchor == null) {
            anchorMouseScreenPoint = screenLoc
            return false
        }
        val dist = hypot((screenLoc.x - anchor.x).toDouble(), (screenLoc.y - anchor.y).toDouble())
        return dist >= MOUSE_JITTER_THRESHOLD_PX
    }
}

@Composable
fun DesktopControllerWindowHost(
    window: Window,
    content: @Composable () -> Unit,
) {
    val isControllerActive by ControllerManager.isControllerActive.collectAsState()

    LaunchedEffect(Unit) {
        WindowsControllerService.startIfNeeded()
    }

    LaunchedEffect(isControllerActive, window) {
        if (isControllerActive) {
            DesktopControllerCursorManager.recordControllerActivated()
            DesktopControllerCursorManager.applyCursorToWindowTree(window, hideCursor = true)
            ControllerFocusEngine.ensureValidFocus()
        } else {
            DesktopControllerCursorManager.applyCursorToWindowTree(window, hideCursor = false)
        }
    }

    DisposableEffect(window) {
        val focusListener = object : WindowFocusListener {
            override fun windowGainedFocus(e: WindowEvent?) {
                WindowsControllerService.isAppWindowFocused = true
                WindowsVirtualKeyboardManager.onWindowGainedFocusOrActivated(window)
            }

            override fun windowLostFocus(e: WindowEvent?) {
                WindowsVirtualKeyboardManager.onWindowLostFocusOrDeactivated(window)
                val pipActive = DesktopPlayerPictureInPicture.pipWindow?.isActive == true
                WindowsControllerService.isAppWindowFocused =
                    window.isActive || window.isFocused || pipActive || PlatformVirtualKeyboard.isKeyboardActive
            }
        }
        val activationListener = object : WindowAdapter() {
            override fun windowActivated(e: WindowEvent?) {
                WindowsControllerService.isAppWindowFocused = true
                WindowsVirtualKeyboardManager.onWindowGainedFocusOrActivated(window)
            }

            override fun windowDeactivated(e: WindowEvent?) {
                WindowsVirtualKeyboardManager.onWindowLostFocusOrDeactivated(window)
                val pipActive = DesktopPlayerPictureInPicture.pipWindow?.isActive == true
                WindowsControllerService.isAppWindowFocused =
                    window.isActive || window.isFocused || pipActive || PlatformVirtualKeyboard.isKeyboardActive
            }

            override fun windowDeiconified(e: WindowEvent?) {
                WindowsControllerService.isAppWindowFocused = true
                WindowsVirtualKeyboardManager.onWindowGainedFocusOrActivated(window)
            }

            override fun windowIconified(e: WindowEvent?) {
                WindowsControllerService.isAppWindowFocused = false
            }
        }
        window.addWindowFocusListener(focusListener)
        window.addWindowListener(activationListener)
        WindowsControllerService.activateAndFocusWindow(window)

        val awtListener = AWTEventListener { event ->
            when (event) {
                is MouseWheelEvent -> {
                    if (event.id == MouseEvent.MOUSE_WHEEL) {
                        ControllerManager.notifyMouseOrKeyboardInput()
                    }
                }
                is MouseEvent -> {
                    when (event.id) {
                        MouseEvent.MOUSE_PRESSED,
                        MouseEvent.MOUSE_DRAGGED,
                        -> {
                            ControllerManager.notifyMouseOrKeyboardInput()
                        }
                        MouseEvent.MOUSE_MOVED -> {
                            if (DesktopControllerCursorManager.shouldWakeOnMouseMove(event)) {
                                ControllerManager.notifyMouseOrKeyboardInput()
                            }
                        }
                    }
                }
                is KeyEvent -> {
                    if (event.id == KeyEvent.KEY_PRESSED) {
                        if (PlatformVirtualKeyboard.isKeyboardActive) {
                            if (event.keyCode == KeyEvent.VK_ESCAPE || event.keyCode == KeyEvent.VK_ENTER) {
                                PlatformVirtualKeyboard.hide()
                            }
                        } else {
                            ControllerManager.notifyMouseOrKeyboardInput()
                        }
                    }
                }
            }
        }

        val mask = AWTEvent.MOUSE_EVENT_MASK or
            AWTEvent.MOUSE_MOTION_EVENT_MASK or
            AWTEvent.MOUSE_WHEEL_EVENT_MASK or
            AWTEvent.KEY_EVENT_MASK
        Toolkit.getDefaultToolkit().addAWTEventListener(awtListener, mask)

        onDispose {
            window.removeWindowFocusListener(focusListener)
            window.removeWindowListener(activationListener)
            Toolkit.getDefaultToolkit().removeAWTEventListener(awtListener)
            DesktopControllerCursorManager.applyCursorToWindowTree(window, hideCursor = false)
        }
    }

    Box(
        propagateMinConstraints = true,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isControllerActive) {
                    Modifier.pointerHoverIcon(
                        icon = DesktopControllerCursorManager.hiddenComposePointerIcon,
                        overrideDescendants = true,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}

