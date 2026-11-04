package com.nuvio.app.controller

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Standard Windows 11 Xbox controller button layout.
 * All physical controllers (XInput, DirectInput, DualSense/DualShock, Switch Pro)
 * are normalized into this canonical layout.
 */
enum class XboxButton(val displayLabel: String) {
    A("A"),
    B("B"),
    X("X"),
    Y("Y"),
    LB("LB"),
    RB("RB"),
    LT("LT"),
    RT("RT"),
    LS("LS"),
    RS("RS"),
    DPadUp("D-Pad"),
    DPadDown("D-Pad"),
    DPadLeft("D-Pad"),
    DPadRight("D-Pad"),
    View("View"),
    Menu("Menu"),
}

/**
 * Glyphs rendered in the bottom-right Controller Hint Toolbar.
 */
enum class XboxGlyph(val label: String) {
    A("A"),
    B("B"),
    X("X"),
    Y("Y"),
    LB("LB"),
    RB("RB"),
    LT("LT"),
    RT("RT"),
    LS("LS"),
    RS("RS"),
    DPad("D-Pad"),
    LB_RB("LB/RB"),
    LT_RT("LT/RT"),
    View("View"),
    Menu("Menu"),
}

enum class ControllerDirection {
    Up,
    Down,
    Left,
    Right,
}

sealed interface ControllerEvent {
    data class ButtonPressed(val button: XboxButton) : ControllerEvent
    data class ButtonReleased(val button: XboxButton) : ControllerEvent
    data class Navigate(val direction: ControllerDirection, val isRepeat: Boolean = false) : ControllerEvent
    data class AnalogScroll(val dx: Float, val dy: Float) : ControllerEvent
}

@Immutable
data class ControllerHintItem(
    val glyph: XboxGlyph,
    val actionLabel: String,
)

enum class ControllerScreenType {
    ProfileSelection,
    Home,
    Search,
    Library,
    Settings,
    Catalog,
    Details,
    Streams,
    Player,
    Modal,
    ModalOverlay,
}

@Immutable
data class ControllerScreenContext(
    val screenType: ControllerScreenType = ControllerScreenType.Home,
    val customHints: List<ControllerHintItem>? = null,
    val canNavigateBack: Boolean = false,
    val hasSeasonsOrTabs: Boolean = false,
    val seasonsOrTabsLabel: String? = null,
    val selectLabel: String? = null,
    val backLabel: String? = null,
    val secondaryActionLabel: String? = null,
    val tertiaryActionLabel: String? = null,
    val lbLabel: String? = null,
    val rbLabel: String? = null,
    val viewLabel: String? = null,
    val menuLabel: String? = null,
    val dPadLabel: String? = null,
    val rightStickLabel: String? = null,
)

/**
 * Central state manager for controller activity, input events, and context-aware hints.
 */
object ControllerManager {
    private val _isControllerActive = MutableStateFlow(false)
    val isControllerActive: StateFlow<Boolean> = _isControllerActive.asStateFlow()

    private val _connectedControllerCount = MutableStateFlow(0)
    val connectedControllerCount: StateFlow<Int> = _connectedControllerCount.asStateFlow()

    private val _screenContext = MutableStateFlow(ControllerScreenContext())
    val screenContext: StateFlow<ControllerScreenContext> = _screenContext.asStateFlow()

    private val _events = MutableSharedFlow<ControllerEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ControllerEvent> = _events.asSharedFlow()

    @Volatile
    private var lastControllerInputEpochMs: Long = 0L

    fun notifyControllerInput(): Boolean {
        lastControllerInputEpochMs = System.currentTimeMillis()
        val transitioned = !_isControllerActive.value
        if (transitioned) {
            _isControllerActive.value = true
        }
        return transitioned
    }

    fun notifyMouseOrKeyboardInput() {
        // Guard against synthetic AWT mouse move events fired immediately during layout/cursor changes
        val elapsed = System.currentTimeMillis() - lastControllerInputEpochMs
        if (elapsed < 45L) return
        if (_isControllerActive.value) {
            _isControllerActive.value = false
        }
    }

    fun forceDeactivateController() {
        if (_isControllerActive.value) {
            _isControllerActive.value = false
        }
    }

    fun updateConnectedCount(count: Int) {
        _connectedControllerCount.value = count
    }

    fun updateScreenContext(context: ControllerScreenContext) {
        _screenContext.value = context
    }

    fun updateScreenContext(updater: (ControllerScreenContext) -> ControllerScreenContext) {
        _screenContext.value = updater(_screenContext.value)
    }

    fun dispatchEvent(event: ControllerEvent) {
        val wokeUp = notifyControllerInput()
        if (wokeUp && event is ControllerEvent.Navigate) {
            // First movement wakes the app into controller mode without moving the selection
            ControllerFocusEngine.ensureValidFocus()
            _events.tryEmit(event)
            return
        }
        val handledByEngine = ControllerFocusEngine.handleEvent(event)
        if (!handledByEngine) {
            _events.tryEmit(event)
        } else if (event is ControllerEvent.ButtonPressed || event is ControllerEvent.Navigate) {
            // Still emit to observers that need raw notifications (e.g. player overlay activity)
            _events.tryEmit(event)
        }
    }
}

