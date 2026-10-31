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
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.awt.image.BufferedImage
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import kotlin.math.hypot

object DesktopControllerCursorManager {
    private const val MOUSE_JITTER_THRESHOLD_PX = 5.0

    val hiddenAwtCursor: Cursor by lazy {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        Toolkit.getDefaultToolkit().createCustomCursor(image, Point(0, 0), "nuvio-controller-hidden-cursor")
    }

    val hiddenComposePointerIcon: PointerIcon by lazy {
        PointerIcon(hiddenAwtCursor)
    }

    @Volatile
    private var anchorMouseScreenPoint: Point? = null

    fun recordControllerActivated() {
        anchorMouseScreenPoint = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
    }

    fun applyCursorToWindowTree(window: Window?, hideCursor: Boolean) {
        if (window == null) return
        val targetCursor = if (hideCursor) hiddenAwtCursor else Cursor.getDefaultCursor()
        SwingUtilities.invokeLater {
            applyCursorRecursive(window, targetCursor)
            if (window is RootPaneContainer) {
                window.rootPane?.cursor = targetCursor
                window.contentPane?.cursor = targetCursor
                window.layeredPane?.cursor = targetCursor
                window.glassPane?.cursor = targetCursor
            }
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
        if (!ControllerManager.isControllerActive.value) return false
        val screenLoc = try {
            event.locationOnScreen
        } catch (_: Throwable) {
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
            }

            override fun windowLostFocus(e: WindowEvent?) {
                val pipActive = DesktopPlayerPictureInPicture.pipWindow?.isActive == true
                WindowsControllerService.isAppWindowFocused = window.isActive || window.isFocused || pipActive
            }
        }
        window.addWindowFocusListener(focusListener)
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
                        ControllerManager.notifyMouseOrKeyboardInput()
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

