package com.nuvio.app.controller

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nuvio.app.controller.audio.ControllerSoundPlayer
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class ControllerFocusZone {
    Navigation,
    Content,
    Overlay,
}

data class ControllerFocusNodeSnapshot(
    val id: String,
    val zone: ControllerFocusZone,
    val groupKey: String?,
    val selectLabel: String,
    val secondaryActionLabel: String?,
    val tertiaryActionLabel: String?,
    val hasSecondaryAction: Boolean,
    val hasTertiaryAction: Boolean,
    val isTextInput: Boolean = false,
)

internal class ControllerFocusNode(
    val id: String,
    var zone: ControllerFocusZone,
    var groupKey: String?,
    var boundsInRoot: Rect = Rect.Zero,
    var enabled: Boolean = true,
    var isDefaultFocus: Boolean = false,
    var isTextInput: Boolean = false,
    var selectLabel: String = "Select",
    var secondaryActionLabel: String? = null,
    var tertiaryActionLabel: String? = null,
    var onSelect: () -> Unit = {},
    var onSecondaryAction: (() -> Unit)? = null,
    var onTertiaryAction: (() -> Unit)? = null,
    var onFocused: (() -> Unit)? = null,
    var onFocusedWithOption: ((allowVerticalScroll: Boolean) -> Unit)? = null,
    var onMoveUp: (() -> Boolean)? = null,
    var onMoveDown: (() -> Boolean)? = null,
    var onMoveLeft: (() -> Boolean)? = null,
    var onMoveRight: (() -> Boolean)? = null,
    var rowLazyListState: LazyListState? = null,
    var rowItemIndex: Int = -1,
    var rowTotalCount: Int = 0,
    var coroutineScope: CoroutineScope? = null,
) {
    val isVisibleAndValid: Boolean
        get() = enabled && boundsInRoot.width > 2f && boundsInRoot.height > 2f

    fun toSnapshot(): ControllerFocusNodeSnapshot = ControllerFocusNodeSnapshot(
        id = id,
        zone = zone,
        groupKey = groupKey,
        selectLabel = selectLabel,
        secondaryActionLabel = secondaryActionLabel,
        tertiaryActionLabel = tertiaryActionLabel,
        hasSecondaryAction = onSecondaryAction != null,
        hasTertiaryAction = onTertiaryAction != null,
        isTextInput = isTextInput,
    )
}

data class ControllerGlobalHandlers(
    val onBack: (() -> Boolean)? = null,
    val onBumperLeft: (() -> Boolean)? = null,
    val onBumperRight: (() -> Boolean)? = null,
    val onTriggerLeft: (() -> Boolean)? = null,
    val onTriggerRight: (() -> Boolean)? = null,
    val onActionX: (() -> Boolean)? = null,
    val onActionY: (() -> Boolean)? = null,
    val onViewButton: (() -> Boolean)? = null,
    val onMenuButton: (() -> Boolean)? = null,
    val onEnterNavigationZone: (() -> Unit)? = null,
    val onExitNavigationZone: (() -> Unit)? = null,
)

val LocalControllerVerticalListState = compositionLocalOf<LazyListState?> { null }
val LocalControllerVerticalScrollState = compositionLocalOf<ScrollState?> { null }
val LocalControllerRowLazyListState = compositionLocalOf<LazyListState?> { null }
val LocalControllerGroupKey = compositionLocalOf<String?> { null }
val LocalControllerFocusZone = compositionLocalOf { ControllerFocusZone.Content }

@Composable
fun ProvideControllerRowLazyListState(
    state: LazyListState,
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalControllerRowLazyListState provides state,
        content = content,
    )
}

object ControllerFocusEngine {
    private val nodes = LinkedHashMap<String, ControllerFocusNode>()

    private val _focusedNodeId = MutableStateFlow<String?>(null)
    val focusedNodeId: StateFlow<String?> = _focusedNodeId.asStateFlow()

    private val _focusedNodeSnapshot = MutableStateFlow<ControllerFocusNodeSnapshot?>(null)
    val focusedNodeSnapshot: StateFlow<ControllerFocusNodeSnapshot?> = _focusedNodeSnapshot.asStateFlow()

    private val _isNavigationZoneExpanded = MutableStateFlow(false)
    val isNavigationZoneExpanded: StateFlow<Boolean> = _isNavigationZoneExpanded.asStateFlow()

    private val _hasInteractiveContent = MutableStateFlow(false)
    val hasInteractiveContent: StateFlow<Boolean> = _hasInteractiveContent.asStateFlow()

    private fun updateInteractiveContentStatus() {
        _hasInteractiveContent.value = nodes.values.any { it.zone == ControllerFocusZone.Content && it.isVisibleAndValid }
    }

    private var lastFocusedByZone = mutableMapOf<ControllerFocusZone, String>()
    private var lastFocusedByGroup = mutableMapOf<String, String>()

    data class ControllerScrollableEntry(
        val lazyListState: LazyListState? = null,
        val scrollState: ScrollState? = null,
        val lazyGridState: LazyGridState? = null,
        val scope: CoroutineScope,
    )

    private val registeredScrollables = mutableListOf<ControllerScrollableEntry>()
    private var activeCoroutineScope: CoroutineScope? = null
    private var activeVerticalScrollJob: Job? = null
    private val SnappyEaseOut = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
    private var manualSidebarOpen = false

    private val handlerStack = mutableListOf<Pair<String, ControllerGlobalHandlers>>()
    private var customPlayerEventHandler: ((ControllerEvent) -> Boolean)? = null

    fun setPlayerEventHandler(handler: ((ControllerEvent) -> Boolean)?) {
        customPlayerEventHandler = handler
    }

    fun registerGlobalHandlers(key: String, handlers: ControllerGlobalHandlers) {
        handlerStack.removeAll { it.first == key }
        handlerStack.add(key to handlers)
    }

    fun unregisterGlobalHandlers(key: String) {
        handlerStack.removeAll { it.first == key }
    }

    fun registerVerticalScrollable(
        lazyListState: LazyListState? = null,
        scrollState: ScrollState? = null,
        lazyGridState: LazyGridState? = null,
        scope: CoroutineScope? = null,
    ) {
        if (scope != null) activeCoroutineScope = scope
        if (scope == null) return
        registeredScrollables.removeAll {
            (lazyListState != null && it.lazyListState === lazyListState) ||
            (scrollState != null && it.scrollState === scrollState) ||
            (lazyGridState != null && it.lazyGridState === lazyGridState)
        }
        registeredScrollables.add(ControllerScrollableEntry(lazyListState, scrollState, lazyGridState, scope))
    }

    fun unregisterVerticalScrollable(
        lazyListState: LazyListState? = null,
        scrollState: ScrollState? = null,
        lazyGridState: LazyGridState? = null,
    ) {
        registeredScrollables.removeAll {
            (lazyListState != null && it.lazyListState === lazyListState) ||
            (scrollState != null && it.scrollState === scrollState) ||
            (lazyGridState != null && it.lazyGridState === lazyGridState)
        }
    }

    internal fun getActiveScrollable(): ControllerScrollableEntry? {
        val valid = registeredScrollables.filter { entry ->
            val l = entry.lazyListState
            val s = entry.scrollState
            val g = entry.lazyGridState
            (l != null && l.layoutInfo.visibleItemsInfo.isNotEmpty()) ||
            (g != null && g.layoutInfo.visibleItemsInfo.isNotEmpty()) ||
            (s != null && s.maxValue > 0) ||
            (l != null) || (g != null)
        }
        return valid.lastOrNull { entry ->
            entry.lazyListState?.layoutInfo?.visibleItemsInfo?.isNotEmpty() == true ||
            entry.lazyGridState?.layoutInfo?.visibleItemsInfo?.isNotEmpty() == true
        } ?: valid.lastOrNull() ?: registeredScrollables.lastOrNull()
    }

    fun setNavigationZoneExpanded(expanded: Boolean) {
        _isNavigationZoneExpanded.value = expanded
    }

    private var activeRouteKey: String? = null

    fun onRouteOrTabChanged(routeKey: Any? = null) {
        val newRouteStr = routeKey?.toString()
        val routeChanged = activeRouteKey != null && activeRouteKey != newRouteStr
        activeRouteKey = newRouteStr

        // Purge nodes whose enabled is false or whose bounds are empty
        val invalidIds = nodes.entries.filter { !it.value.isVisibleAndValid }.map { it.key }
        invalidIds.forEach { nodes.remove(it) }

        if (routeChanged) {
            _focusedNodeId.value = null
            _focusedNodeSnapshot.value = null
            lastFocusedByZone.clear()
            lastFocusedByGroup.clear()
            manualSidebarOpen = false
            _isNavigationZoneExpanded.value = false
        } else {
            val current = _focusedNodeId.value?.let { nodes[it] }
            if (current == null || !current.isVisibleAndValid) {
                _focusedNodeId.value = null
                _focusedNodeSnapshot.value = null
            }
        }
        updateInteractiveContentStatus()
        ensureValidFocus()
    }

    internal fun upsertNode(node: ControllerFocusNode) {
        nodes[node.id] = node
        updateInteractiveContentStatus()
        val currentId = _focusedNodeId.value
        val activeOverlayNodes = nodes.values.filter { it.zone == ControllerFocusZone.Overlay && it.isVisibleAndValid }
        if (activeOverlayNodes.isNotEmpty()) {
            val currentNode = currentId?.let { nodes[it] }
            if (currentNode == null || currentNode.zone != ControllerFocusZone.Overlay || !currentNode.isVisibleAndValid) {
                val target = activeOverlayNodes.firstOrNull { it.isDefaultFocus }
                    ?: activeOverlayNodes.minWithOrNull(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))
                if (target != null) {
                    focusNodeInternal(target, triggerScroll = false)
                }
            } else if (currentNode.id == node.id) {
                _focusedNodeSnapshot.value = node.toSnapshot()
            }
            return
        }

        if (currentId == node.id) {
            _focusedNodeSnapshot.value = node.toSnapshot()
            return
        }

        val currentNode = currentId?.let { nodes[it] }
        if (node.zone == ControllerFocusZone.Content && node.isVisibleAndValid) {
            if (currentNode == null || !currentNode.isVisibleAndValid || (currentNode.zone == ControllerFocusZone.Navigation && !manualSidebarOpen)) {
                if (node.isDefaultFocus || currentId == null || currentNode?.zone == ControllerFocusZone.Navigation) {
                    val contentNodes = nodes.values.filter { it.zone == ControllerFocusZone.Content && it.isVisibleAndValid }
                    val target = contentNodes.firstOrNull { it.isDefaultFocus }
                        ?: contentNodes.minWithOrNull(
                            compareBy<ControllerFocusNode> { (it.boundsInRoot.top / 32f).toInt() }
                                .thenBy { it.boundsInRoot.left },
                        )
                    if (target != null) {
                        _isNavigationZoneExpanded.value = false
                        focusNodeInternal(target, triggerScroll = false)
                        return
                    }
                }
            }
        }
    }

    internal fun removeNode(id: String) {
        val removed = nodes.remove(id)
        if (_focusedNodeId.value == id) {
            _focusedNodeId.value = null
            _focusedNodeSnapshot.value = null
            // If an overlay just closed, restore Content focus; otherwise pick nearest node in same zone
            if (removed?.zone == ControllerFocusZone.Overlay) {
                val remainingOverlay = nodes.values.filter { it.zone == ControllerFocusZone.Overlay && it.isVisibleAndValid }
                if (remainingOverlay.isNotEmpty()) {
                    focusNodeInternal(remainingOverlay.first(), triggerScroll = false)
                    return
                }
                val restoredContentId = lastFocusedByZone[ControllerFocusZone.Content]
                val restoredContent = restoredContentId?.let { nodes[it] }?.takeIf { it.isVisibleAndValid }
                if (restoredContent != null) {
                    focusNodeInternal(restoredContent, triggerScroll = false)
                    return
                }
            }
            ensureValidFocus(preferBounds = removed?.boundsInRoot, preferZone = removed?.zone)
        }
        updateInteractiveContentStatus()
    }

    fun requestFocus(id: String, triggerScroll: Boolean = true): Boolean {
        val node = nodes[id]?.takeIf { it.isVisibleAndValid } ?: return false
        focusNodeInternal(node, triggerScroll = triggerScroll)
        return true
    }

    fun clearFocusInZone(zone: ControllerFocusZone) {
        val current = _focusedNodeId.value?.let { nodes[it] }
        if (current?.zone == zone) {
            _focusedNodeId.value = null
            _focusedNodeSnapshot.value = null
        }
    }

    fun ensureValidFocus(
        preferBounds: Rect? = null,
        preferZone: ControllerFocusZone? = null,
    ): Boolean {
        val overlayNodes = nodes.values.filter { it.zone == ControllerFocusZone.Overlay && it.isVisibleAndValid }
        if (overlayNodes.isNotEmpty()) {
            val current = _focusedNodeId.value?.let { nodes[it] }
            if (current != null && current.zone == ControllerFocusZone.Overlay && current.isVisibleAndValid) {
                return true
            }
            val target = overlayNodes.firstOrNull { it.isDefaultFocus }
                ?: overlayNodes.minWithOrNull(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))
            if (target != null) {
                focusNodeInternal(target, triggerScroll = false)
                return true
            }
        }

        val current = _focusedNodeId.value?.let { nodes[it] }
        if (current != null && current.isVisibleAndValid) {
            return true
        }

        val contentNodes = nodes.values.filter { it.zone == ControllerFocusZone.Content && it.isVisibleAndValid }
        if (contentNodes.isNotEmpty()) {
            if (preferBounds != null && preferZone == ControllerFocusZone.Content) {
                val nearest = contentNodes.minByOrNull {
                    hypot(
                        (it.boundsInRoot.center.x - preferBounds.center.x).toDouble(),
                        (it.boundsInRoot.center.y - preferBounds.center.y).toDouble(),
                    )
                }
                if (nearest != null) {
                    focusNodeInternal(nearest, triggerScroll = false)
                    return true
                }
            }
            val rememberedId = lastFocusedByZone[ControllerFocusZone.Content]
            val remembered = rememberedId?.let { id -> contentNodes.firstOrNull { it.id == id } }
            val defaultNode = contentNodes.firstOrNull { it.isDefaultFocus }
            val topLeftNode = contentNodes.minWithOrNull(
                compareBy<ControllerFocusNode> { (it.boundsInRoot.top / 32f).toInt() }
                    .thenBy { it.boundsInRoot.left },
            )
            val chosen = defaultNode ?: remembered ?: topLeftNode
            if (chosen != null) {
                focusNodeInternal(chosen, triggerScroll = false)
                return true
            }
        }

        val isContentRoute = activeRouteKey?.let {
            it.contains("Home", ignoreCase = true) ||
            it.contains("Library", ignoreCase = true) ||
            it.contains("Catalog", ignoreCase = true) ||
            it.contains("TabsRoute", ignoreCase = true)
        } == true

        if (!isContentRoute || manualSidebarOpen) {
            val navNodes = nodes.values.filter { it.zone == ControllerFocusZone.Navigation && it.isVisibleAndValid }
            if (navNodes.isNotEmpty()) {
                val chosen = navNodes.firstOrNull { it.isDefaultFocus } ?: navNodes.first()
                focusNodeInternal(chosen, triggerScroll = false)
                return true
            }
        }
        return false
    }

    private fun focusNodeInternal(
        node: ControllerFocusNode,
        triggerScroll: Boolean,
        suppressVerticalScroll: Boolean = false,
    ) {
        val previousId = _focusedNodeId.value
        _focusedNodeId.value = node.id
        _focusedNodeSnapshot.value = node.toSnapshot()
        lastFocusedByZone[node.zone] = node.id
        node.groupKey?.let { group ->
            lastFocusedByGroup[group] = node.id
        }
        if (node.zone == ControllerFocusZone.Navigation) {
            _isNavigationZoneExpanded.value = true
            invokeGlobalHandler { it.onEnterNavigationZone?.invoke(); false }
        } else if (node.zone == ControllerFocusZone.Content && _isNavigationZoneExpanded.value) {
            _isNavigationZoneExpanded.value = false
            manualSidebarOpen = false
            invokeGlobalHandler { it.onExitNavigationZone?.invoke(); false }
        }
        if (previousId != null && previousId != node.id) {
            ControllerSoundPlayer.playNavigate()
        }
        if (triggerScroll) {
            node.onFocusedWithOption?.invoke(!suppressVerticalScroll) ?: node.onFocused?.invoke()
            if (!suppressVerticalScroll) {
                scrollVerticalContainerToNode(node)
            }
            if (node.rowLazyListState != null) {
                scrollHorizontalContainerToNode(node)
            }
        }
    }

    private var isNavigatingLazyRow = false

    fun navigateLazyRowIndex(
        lazyListState: LazyListState,
        targetIndex: Int,
        totalItems: Int,
        isMovingRight: Boolean,
        coroutineScope: CoroutineScope,
        targetFocusId: String,
        edgeMarginPx: Float = 120f,
    ): Boolean {
        if (targetIndex < 0 || targetIndex >= totalItems) return false

        val layoutInfo = lazyListState.layoutInfo
        val viewportStart = layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = layoutInfo.viewportEndOffset.toFloat().takeIf { it > 100f } ?: 1920f
        val leftSafeZone = viewportStart + edgeMarginPx
        val rightSafeZone = (viewportEnd - edgeMarginPx).coerceAtLeast(leftSafeZone + 100f)

        val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }

        isNavigatingLazyRow = true

        if (targetItem != null) {
            val itemStart = targetItem.offset.toFloat()
            val itemEnd = (targetItem.offset + targetItem.size).toFloat()

            if (isMovingRight) {
                if (itemEnd <= rightSafeZone) {
                    // Fully within safe viewport zone: DO NOT scroll, just transfer focus directly
                    requestFocus(targetFocusId)
                    isNavigatingLazyRow = false
                    return true
                } else {
                    // Approaches or exceeds right safe threshold: scroll just enough
                    val delta = (itemEnd - rightSafeZone + 30f).coerceAtLeast(0f)
                    coroutineScope.launch {
                        try {
                            runCatching {
                                lazyListState.animateScrollBy(delta, tween(180, easing = SnappyEaseOut))
                            }
                            for (attempt in 0..5) {
                                if (requestFocus(targetFocusId)) break
                                delay(35)
                            }
                        } finally {
                            isNavigatingLazyRow = false
                        }
                    }
                    return true
                }
            } else {
                // Moving Left
                if (targetIndex == 0) {
                    if (lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0) {
                        requestFocus(targetFocusId)
                        isNavigatingLazyRow = false
                    } else {
                        coroutineScope.launch {
                            try {
                                runCatching { lazyListState.animateScrollToItem(0, 0) }
                                for (attempt in 0..5) {
                                    if (requestFocus(targetFocusId)) break
                                    delay(35)
                                }
                            } finally {
                                isNavigatingLazyRow = false
                            }
                        }
                    }
                    return true
                }

                if (itemStart >= leftSafeZone) {
                    // Fully within safe viewport zone: DO NOT scroll, just transfer focus directly
                    requestFocus(targetFocusId)
                    isNavigatingLazyRow = false
                    return true
                } else {
                    // Approaches or exceeds left safe threshold: scroll just enough left
                    val delta = itemStart - leftSafeZone - 30f
                    coroutineScope.launch {
                        try {
                            runCatching {
                                lazyListState.animateScrollBy(delta, tween(180, easing = SnappyEaseOut))
                            }
                            for (attempt in 0..5) {
                                if (requestFocus(targetFocusId)) break
                                delay(35)
                            }
                        } finally {
                            isNavigatingLazyRow = false
                        }
                    }
                    return true
                }
            }
        } else {
            // Target item is off-screen (not yet composed in visibleItemsInfo)
            if (isMovingRight) {
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
                val avgSize = layoutInfo.visibleItemsInfo.map { it.size }.average().toFloat().takeIf { it > 20f } ?: 180f
                val delta = if (lastVisible != null) {
                    ((lastVisible.offset + lastVisible.size) - rightSafeZone + avgSize + 30f).coerceAtLeast(avgSize)
                } else {
                    avgSize + edgeMarginPx
                }
                coroutineScope.launch {
                    try {
                        runCatching {
                            lazyListState.animateScrollBy(delta, tween(200, easing = SnappyEaseOut))
                        }
                        for (attempt in 0..6) {
                            if (requestFocus(targetFocusId)) break
                            delay(40)
                        }
                    } finally {
                        isNavigatingLazyRow = false
                    }
                }
                return true
            } else {
                // Moving Left off-screen
                if (targetIndex == 0) {
                    coroutineScope.launch {
                        try {
                            runCatching { lazyListState.animateScrollToItem(0, 0) }
                            for (attempt in 0..6) {
                                if (requestFocus(targetFocusId)) break
                                delay(40)
                            }
                        } finally {
                            isNavigatingLazyRow = false
                        }
                    }
                } else {
                    val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull()
                    val avgSize = layoutInfo.visibleItemsInfo.map { it.size }.average().toFloat().takeIf { it > 20f } ?: 180f
                    val delta = if (firstVisible != null) {
                        (firstVisible.offset - leftSafeZone - avgSize - 30f).coerceAtMost(-avgSize)
                    } else {
                        -(avgSize + edgeMarginPx)
                    }
                    coroutineScope.launch {
                        try {
                            runCatching {
                                lazyListState.animateScrollBy(delta, tween(200, easing = SnappyEaseOut))
                            }
                            for (attempt in 0..6) {
                                if (requestFocus(targetFocusId)) break
                                delay(40)
                            }
                        } finally {
                            isNavigatingLazyRow = false
                        }
                    }
                }
                return true
            }
        }
    }

    private fun scrollHorizontalContainerToNode(node: ControllerFocusNode) {
        if (isNavigatingLazyRow) return
        val rowState = node.rowLazyListState ?: return
        val scope = node.coroutineScope ?: activeCoroutineScope ?: return
        val bounds = node.boundsInRoot
        if (bounds.width <= 0f || bounds.height <= 0f) return

        val viewportWidth = rowState.layoutInfo.viewportSize.width.toFloat().takeIf { it > 100f } ?: 1920f
        val leftSafeZone = 120f
        val rightSafeZone = viewportWidth - 140f

        if (bounds.right > rightSafeZone) {
            val delta = bounds.right - rightSafeZone + 40f
            scope.launch {
                runCatching { rowState.animateScrollBy(delta, tween(160, easing = SnappyEaseOut)) }
            }
        } else if (bounds.left < leftSafeZone) {
            val delta = bounds.left - leftSafeZone - 40f
            scope.launch {
                runCatching { rowState.animateScrollBy(delta, tween(160, easing = SnappyEaseOut)) }
            }
        }
    }

    private data class FocusContentRow(
        val top: Float,
        val bottom: Float,
        val centerY: Float,
        val nodes: List<ControllerFocusNode>,
        val groupKey: String?,
        val rowLazyListState: LazyListState?,
    )

    private fun getComposedContentRows(): List<FocusContentRow> {
        val contentNodes = nodes.values
            .filter { it.zone == ControllerFocusZone.Content && it.isVisibleAndValid && !it.id.startsWith("view_all_pill_") }
            .sortedBy { it.boundsInRoot.center.y }
        if (contentNodes.isEmpty()) return emptyList()

        val rows = mutableListOf<MutableList<ControllerFocusNode>>()
        for (node in contentNodes) {
            val matchingRow = rows.firstOrNull { cluster ->
                val sample = cluster.first()
                if (node.rowLazyListState != null && sample.rowLazyListState != null) {
                    node.rowLazyListState === sample.rowLazyListState
                } else if (!node.groupKey.isNullOrEmpty() && node.groupKey == sample.groupKey) {
                    true
                } else {
                    val sampleCenterY = cluster.map { it.boundsInRoot.center.y }.average().toFloat()
                    val overlap = min(node.boundsInRoot.bottom, sample.boundsInRoot.bottom) -
                        max(node.boundsInRoot.top, sample.boundsInRoot.top)
                    val minHeight = min(node.boundsInRoot.height, sample.boundsInRoot.height).coerceAtLeast(1f)
                    (overlap / minHeight) >= 0.35f || abs(node.boundsInRoot.center.y - sampleCenterY) <= 40f
                }
            }
            if (matchingRow != null) {
                matchingRow.add(node)
            } else {
                rows.add(mutableListOf(node))
            }
        }

        return rows.map { list ->
            val sortedList = list.sortedBy { it.boundsInRoot.left }
            val minTop = sortedList.minOf { it.boundsInRoot.top }
            val maxBottom = sortedList.maxOf { it.boundsInRoot.bottom }
            val avgCenterY = sortedList.map { it.boundsInRoot.center.y }.average().toFloat()
            val sample = sortedList.first()
            FocusContentRow(
                top = minTop,
                bottom = maxBottom,
                centerY = avgCenterY,
                nodes = sortedList,
                groupKey = sample.groupKey,
                rowLazyListState = sample.rowLazyListState,
            )
        }.sortedBy { it.centerY }
    }

    private fun selectBestTargetNodeInRow(
        current: ControllerFocusNode,
        row: FocusContentRow,
    ): ControllerFocusNode? {
        val candidates = row.nodes.filter { !it.id.startsWith("view_all_pill_") }
        if (candidates.isEmpty()) return null

        if (!row.groupKey.isNullOrEmpty()) {
            val rememberedId = lastFocusedByGroup[row.groupKey]
            if (rememberedId != null) {
                val rememberedNode = candidates.firstOrNull { it.id == rememberedId }
                if (rememberedNode != null) return rememberedNode
            }
        }

        if (current.rowItemIndex >= 0) {
            val matchingIndexNode = candidates.firstOrNull { it.rowItemIndex == current.rowItemIndex }
            if (matchingIndexNode != null) return matchingIndexNode
        }

        val currentCenterX = current.boundsInRoot.center.x
        return candidates.minByOrNull { abs(it.boundsInRoot.center.x - currentCenterX) }
    }

    private fun scrollVerticalContainerToNode(node: ControllerFocusNode) {
        if (node.zone != ControllerFocusZone.Content) return
        val activeScroll = getActiveScrollable()
        val listState = activeScroll?.lazyListState
        val gridState = activeScroll?.lazyGridState
        val scrollState = activeScroll?.scrollState
        val scope = activeScroll?.scope ?: activeCoroutineScope ?: node.coroutineScope ?: return
        val bounds = node.boundsInRoot
        if (bounds.height <= 0f) return

        val isHeroNode = node.groupKey == "home_hero" || node.id.startsWith("home_hero_")
        if (isHeroNode) {
            activeVerticalScrollJob?.cancel()
            activeVerticalScrollJob = scope.launch {
                if (listState != null && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
                    runCatching { listState.animateScrollToItem(0, 0) }
                } else if (gridState != null && (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0)) {
                    runCatching { gridState.animateScrollToItem(0, 0) }
                } else if (scrollState != null && scrollState.value > 0) {
                    runCatching { scrollState.animateScrollTo(0) }
                }
            }
            return
        }

        val minTopSafeZone = 110f

        activeVerticalScrollJob?.cancel()
        activeVerticalScrollJob = scope.launch {
            val vh = listState?.layoutInfo?.viewportSize?.height?.toFloat()
                ?: gridState?.layoutInfo?.viewportSize?.height?.toFloat()
                ?: 900f
            val maxBottomSafeZone = vh - 180f

            // Full row container: heading text ~80px above card, card bottom + 120px safe-zone clearance below card
            val rowTop = node.boundsInRoot.top - 80f
            val rowBottom = node.boundsInRoot.bottom + 120f

            var scrolled = false
            if (rowBottom > maxBottomSafeZone) {
                val delta = rowBottom - maxBottomSafeZone
                if (listState != null) runCatching { listState.animateScrollBy(delta, tween(200, easing = SnappyEaseOut)) }
                else if (gridState != null) runCatching { gridState.animateScrollBy(delta, tween(200, easing = SnappyEaseOut)) }
                else if (scrollState != null) runCatching { scrollState.animateScrollBy(delta, tween(200, easing = SnappyEaseOut)) }
                scrolled = true
            } else if (rowTop < minTopSafeZone) {
                val delta = rowTop - minTopSafeZone
                if (listState != null) runCatching { listState.animateScrollBy(delta, tween(200, easing = SnappyEaseOut)) }
                else if (gridState != null) runCatching { gridState.animateScrollBy(delta, tween(200, easing = SnappyEaseOut)) }
                else if (scrollState != null) runCatching { scrollState.animateScrollBy(delta, tween(200, easing = SnappyEaseOut)) }
                scrolled = true
            }

            // Post-layout / post-frame verification check:
            // Await animation settling and layout composition
            delay(if (scrolled) 220 else 80)
            val updatedNode = nodes[node.id] ?: node
            val updatedBounds = updatedNode.boundsInRoot
            if (updatedBounds.height <= 0f) return@launch

            val updatedRowTop = updatedBounds.top - 80f
            val updatedRowBottom = updatedBounds.bottom + 120f
            val updatedVh = listState?.layoutInfo?.viewportSize?.height?.toFloat()
                ?: gridState?.layoutInfo?.viewportSize?.height?.toFloat()
                ?: vh
            val updatedMaxBottomSafeZone = updatedVh - 180f

            if (updatedRowBottom > updatedMaxBottomSafeZone) {
                val correctiveDelta = updatedRowBottom - updatedMaxBottomSafeZone
                if (correctiveDelta > 6f) {
                    if (listState != null) runCatching { listState.animateScrollBy(correctiveDelta, tween(160, easing = SnappyEaseOut)) }
                    else if (gridState != null) runCatching { gridState.animateScrollBy(correctiveDelta, tween(160, easing = SnappyEaseOut)) }
                    else if (scrollState != null) runCatching { scrollState.animateScrollBy(correctiveDelta, tween(160, easing = SnappyEaseOut)) }
                }
            } else if (updatedRowTop < minTopSafeZone) {
                val correctiveDelta = updatedRowTop - minTopSafeZone
                if (correctiveDelta < -6f) {
                    if (listState != null) runCatching { listState.animateScrollBy(correctiveDelta, tween(160, easing = SnappyEaseOut)) }
                    else if (gridState != null) runCatching { gridState.animateScrollBy(correctiveDelta, tween(160, easing = SnappyEaseOut)) }
                    else if (scrollState != null) runCatching { scrollState.animateScrollBy(correctiveDelta, tween(160, easing = SnappyEaseOut)) }
                }
            }
        }
    }

    fun handleEvent(event: ControllerEvent): Boolean {
        // If native player is active and handles the event directly, delegate first
        val overlayNodes = nodes.values.filter { it.zone == ControllerFocusZone.Overlay && it.isVisibleAndValid }
        if (overlayNodes.isEmpty()) {
            val playerHandler = customPlayerEventHandler
            if (playerHandler != null && playerHandler(event)) {
                return true
            }
        }

        return when (event) {
            is ControllerEvent.Navigate -> handleNavigate(event.direction)
            is ControllerEvent.ButtonPressed -> handleButtonPressed(event.button)
            is ControllerEvent.ButtonReleased -> false
            is ControllerEvent.AnalogScroll -> handleAnalogScroll(event.dx, event.dy)
        }
    }

    private fun handleAnalogScroll(dx: Float, dy: Float): Boolean {
        val activeScroll = getActiveScrollable()
        val listState = activeScroll?.lazyListState
        val gridState = activeScroll?.lazyGridState
        val scrollState = activeScroll?.scrollState
        val scope = activeScroll?.scope ?: activeCoroutineScope ?: nodes.values.firstNotNullOfOrNull { it.coroutineScope } ?: return false
        if (abs(dy) > 0.05f) {
            val pixels = dy * 32f
            if (listState != null) {
                scope.launch { runCatching { listState.scrollBy(pixels) } }
                return true
            } else if (gridState != null) {
                scope.launch { runCatching { gridState.scrollBy(pixels) } }
                return true
            } else if (scrollState != null) {
                scope.launch { runCatching { scrollState.scrollBy(pixels) } }
                return true
            }
        }
        if (abs(dx) > 0.1f) {
            val current = _focusedNodeId.value?.let { nodes[it] }
            val rowState = current?.rowLazyListState
            if (rowState != null) {
                val pixels = dx * 24f
                scope.launch { runCatching { rowState.scrollBy(pixels) } }
                return true
            }
        }
        return false
    }

    private fun handleButtonPressed(button: XboxButton): Boolean {
        val current = _focusedNodeId.value?.let { nodes[it] }?.takeIf { it.isVisibleAndValid }
        return when (button) {
            XboxButton.DPadUp -> handleNavigate(ControllerDirection.Up)
            XboxButton.DPadDown -> handleNavigate(ControllerDirection.Down)
            XboxButton.DPadLeft -> handleNavigate(ControllerDirection.Left)
            XboxButton.DPadRight -> handleNavigate(ControllerDirection.Right)
            XboxButton.A -> {
                ControllerSoundPlayer.playSelect()
                if (current == null) {
                    ensureValidFocus()
                    true
                } else {
                    if (current.isTextInput) {
                        PlatformVirtualKeyboard.show()
                    }
                    current.onSelect()
                    true
                }
            }
            XboxButton.B -> {
                ControllerSoundPlayer.playBack()
                if (PlatformVirtualKeyboard.isKeyboardActive) {
                    PlatformVirtualKeyboard.hide()
                    return true
                }
                PlatformVirtualKeyboard.hide()
                // If currently in Sidebar/Navigation zone, B returns focus to Content
                if (current?.zone == ControllerFocusZone.Navigation) {
                    manualSidebarOpen = false
                    val contentNodes = nodes.values.filter { it.zone == ControllerFocusZone.Content && it.isVisibleAndValid }
                    if (contentNodes.isNotEmpty()) {
                        val remembered = lastFocusedByZone[ControllerFocusZone.Content]?.let { id ->
                            contentNodes.firstOrNull { it.id == id }
                        } ?: contentNodes.first()
                        focusNodeInternal(remembered, triggerScroll = true)
                        return true
                    }
                }
                invokeGlobalHandler { it.onBack?.invoke() == true }
            }
            XboxButton.X -> {
                val secondary = current?.onSecondaryAction
                if (secondary != null) {
                    secondary.invoke()
                    true
                } else {
                    invokeGlobalHandler { it.onActionX?.invoke() == true }
                }
            }
            XboxButton.Y -> {
                val tertiary = current?.onTertiaryAction
                if (tertiary != null) {
                    tertiary.invoke()
                    true
                } else {
                    invokeGlobalHandler { it.onActionY?.invoke() == true }
                }
            }
            XboxButton.LB -> invokeGlobalHandler { it.onBumperLeft?.invoke() == true }
            XboxButton.RB -> invokeGlobalHandler { it.onBumperRight?.invoke() == true }
            XboxButton.LT -> {
                if (invokeGlobalHandler { it.onTriggerLeft?.invoke() == true }) {
                    true
                } else {
                    scrollPage(up = true)
                }
            }
            XboxButton.RT -> {
                if (invokeGlobalHandler { it.onTriggerRight?.invoke() == true }) {
                    true
                } else {
                    scrollPage(up = false)
                }
            }
            XboxButton.View -> {
                if (invokeGlobalHandler { it.onViewButton?.invoke() == true }) {
                    true
                } else {
                    toggleNavigationZoneFocus()
                }
            }
            XboxButton.Menu -> {
                if (invokeGlobalHandler { it.onMenuButton?.invoke() == true }) {
                    true
                } else {
                    toggleNavigationZoneFocus()
                }
            }
            XboxButton.LS, XboxButton.RS -> false
        }
    }

    private fun toggleNavigationZoneFocus(): Boolean {
        val current = _focusedNodeId.value?.let { nodes[it] }
        if (current?.zone == ControllerFocusZone.Navigation) {
            manualSidebarOpen = false
            val contentNodes = nodes.values.filter { it.zone == ControllerFocusZone.Content && it.isVisibleAndValid }
            val target = lastFocusedByZone[ControllerFocusZone.Content]?.let { id ->
                contentNodes.firstOrNull { it.id == id }
            } ?: contentNodes.firstOrNull()
            if (target != null) {
                focusNodeInternal(target, triggerScroll = true)
                return true
            }
        } else {
            manualSidebarOpen = true
            val navNodes = nodes.values.filter { it.zone == ControllerFocusZone.Navigation && it.isVisibleAndValid }
            val target = lastFocusedByZone[ControllerFocusZone.Navigation]?.let { id ->
                navNodes.firstOrNull { it.id == id }
            } ?: navNodes.firstOrNull { it.isDefaultFocus } ?: navNodes.firstOrNull()
            if (target != null) {
                focusNodeInternal(target, triggerScroll = false)
                return true
            }
        }
        return false
    }

    private fun scrollPage(up: Boolean): Boolean {
        val activeScroll = getActiveScrollable()
        val listState = activeScroll?.lazyListState
        val gridState = activeScroll?.lazyGridState
        val scrollState = activeScroll?.scrollState
        val scope = activeScroll?.scope ?: activeCoroutineScope ?: nodes.values.firstNotNullOfOrNull { it.coroutineScope } ?: return false
        val delta = if (up) -520f else 520f
        if (listState != null) {
            scope.launch {
                runCatching { listState.animateScrollBy(delta, tween(220)) }
                delay(240)
                val visibleContent = nodes.values.filter { it.zone == ControllerFocusZone.Content && it.isVisibleAndValid }
                val target = if (up) {
                    visibleContent.minByOrNull { it.boundsInRoot.top }
                } else {
                    visibleContent.maxByOrNull { it.boundsInRoot.bottom }
                }
                if (target != null) {
                    focusNodeInternal(target, triggerScroll = false)
                }
            }
            return true
        } else if (gridState != null) {
            scope.launch {
                runCatching { gridState.animateScrollBy(delta, tween(220)) }
                delay(240)
                val visibleContent = nodes.values.filter { it.zone == ControllerFocusZone.Content && it.isVisibleAndValid }
                val target = if (up) {
                    visibleContent.minByOrNull { it.boundsInRoot.top }
                } else {
                    visibleContent.maxByOrNull { it.boundsInRoot.bottom }
                }
                if (target != null) {
                    focusNodeInternal(target, triggerScroll = false)
                }
            }
            return true
        } else if (scrollState != null) {
            scope.launch {
                runCatching { scrollState.animateScrollBy(delta, tween(220)) }
            }
            return true
        }
        return false
    }

    private fun invokeGlobalHandler(block: (ControllerGlobalHandlers) -> Boolean): Boolean {
        for (i in handlerStack.indices.reversed()) {
            if (block(handlerStack[i].second)) return true
        }
        return false
    }

    private fun handleContentVerticalNavigation(
        current: ControllerFocusNode,
        direction: ControllerDirection,
    ): Boolean {
        val composedRows = getComposedContentRows()

        // 1. Strict handling if currently focused on a row's chevron ("See All" arrow):
        if (current.id.startsWith("view_all_pill_")) {
            if (direction == ControllerDirection.Down) {
                // Pressing Down MUST return focus back to the poster cards in that exact same row!
                val sameRowCards = nodes.values.filter {
                    it.zone == ControllerFocusZone.Content &&
                        it.isVisibleAndValid &&
                        !it.id.startsWith("view_all_pill_") &&
                        it.boundsInRoot.top >= current.boundsInRoot.bottom - 10f &&
                        it.boundsInRoot.top <= current.boundsInRoot.bottom + 140f
                }
                val target = sameRowCards.maxByOrNull { it.boundsInRoot.center.x }
                    ?: composedRows.firstOrNull { it.centerY > current.boundsInRoot.center.y }?.nodes?.lastOrNull()
                if (target != null) {
                    focusNodeInternal(target, triggerScroll = true)
                    return true
                }
            } else if (direction == ControllerDirection.Up) {
                // Pressing Up from the chevron moves to a poster card in the row above!
                val rowAbove = composedRows.lastOrNull { it.bottom < current.boundsInRoot.top + 30f }
                if (rowAbove != null) {
                    val target = selectBestTargetNodeInRow(current, rowAbove)
                    if (target != null) {
                        focusNodeInternal(target, triggerScroll = true)
                        return true
                    }
                }
            }
        }

        val currentRowIndex = composedRows.indexOfFirst { row -> row.nodes.any { it.id == current.id } }
        val currentRow = if (currentRowIndex != -1) composedRows[currentRowIndex] else null

        // 2. Strict Rule for Row Header Chevrons:
        // A row's chevron can ONLY be focused when:
        // (1) The user is currently focused on the very last (rightmost) poster card of that exact same row, AND
        // (2) The user presses Up.
        if (direction == ControllerDirection.Up && !current.id.startsWith("view_all_pill_")) {
            val isFarRightCard = currentRow != null && (
                current.id == currentRow.nodes.lastOrNull()?.id ||
                (current.rowItemIndex >= 0 && current.rowTotalCount > 0 && current.rowItemIndex == current.rowTotalCount - 1) ||
                currentRow.nodes.none { it.boundsInRoot.center.x > current.boundsInRoot.center.x + 15f }
            )
            if (isFarRightCard) {
                val chevrons = nodes.values.filter {
                    it.zone == ControllerFocusZone.Content && it.isVisibleAndValid && it.id.startsWith("view_all_pill_")
                }
                val chevronAbove = chevrons.firstOrNull {
                    it.boundsInRoot.bottom <= current.boundsInRoot.top + 35f &&
                        it.boundsInRoot.top >= current.boundsInRoot.top - 120f &&
                        abs(it.boundsInRoot.center.x - current.boundsInRoot.center.x) < 260f
                }
                if (chevronAbove != null) {
                    focusNodeInternal(chevronAbove, triggerScroll = true)
                    return true
                }
            }
        }

        val activeScroll = getActiveScrollable()
        val listState = activeScroll?.lazyListState
        val gridState = activeScroll?.lazyGridState
        val scrollState = activeScroll?.scrollState
        val scope = activeScroll?.scope ?: activeCoroutineScope ?: current.coroutineScope

        if (direction == ControllerDirection.Down) {
            if (currentRowIndex != -1 && currentRowIndex < composedRows.lastIndex) {
                val nextRow = composedRows[currentRowIndex + 1]
                val targetNode = selectBestTargetNodeInRow(current, nextRow)
                if (targetNode != null) {
                    focusNodeInternal(targetNode, triggerScroll = true)
                    return true
                }
            }

            val canScrollDown = (listState != null && listState.canScrollForward) ||
                (gridState != null && gridState.canScrollForward) ||
                (scrollState != null && scrollState.canScrollForward)

            if (canScrollDown && scope != null) {
                val rowHeight = currentRow?.let { it.bottom - it.top + 70f } ?: (current.boundsInRoot.height + 125f)
                val scrollDistance = max(rowHeight, 420f)

                activeVerticalScrollJob?.cancel()
                activeVerticalScrollJob = scope.launch {
                    if (listState != null) {
                        runCatching { listState.animateScrollBy(scrollDistance, tween(200, easing = SnappyEaseOut)) }
                    } else if (gridState != null) {
                        runCatching { gridState.animateScrollBy(scrollDistance, tween(200, easing = SnappyEaseOut)) }
                    } else if (scrollState != null) {
                        runCatching { scrollState.animateScrollBy(scrollDistance, tween(200, easing = SnappyEaseOut)) }
                    }
                    delay(160)
                    val refreshedRows = getComposedContentRows()
                    val oldCenterY = currentRow?.centerY ?: current.boundsInRoot.center.y
                    val newRow = refreshedRows.firstOrNull { it.centerY > oldCenterY + 40f }
                        ?: refreshedRows.lastOrNull()
                    if (newRow != null) {
                        val target = selectBestTargetNodeInRow(current, newRow)
                        if (target != null) {
                            focusNodeInternal(target, triggerScroll = true)
                        }
                    }
                }
                return true
            }
            return true
        } else if (direction == ControllerDirection.Up) {
            if (currentRowIndex > 0) {
                val prevRow = composedRows[currentRowIndex - 1]
                val targetNode = selectBestTargetNodeInRow(current, prevRow)
                if (targetNode != null) {
                    focusNodeInternal(targetNode, triggerScroll = true)
                    return true
                }
            }

            val canScrollUp = (listState != null && listState.canScrollBackward) ||
                (gridState != null && gridState.canScrollBackward) ||
                (scrollState != null && scrollState.canScrollBackward)

            if (canScrollUp && scope != null) {
                val rowHeight = currentRow?.let { it.bottom - it.top + 70f } ?: (current.boundsInRoot.height + 125f)
                val scrollDistance = max(rowHeight, 420f)

                val isNearTop = (listState != null && listState.firstVisibleItemIndex <= 1 && listState.firstVisibleItemScrollOffset < 150) ||
                    (gridState != null && gridState.firstVisibleItemIndex == 0)

                activeVerticalScrollJob?.cancel()
                activeVerticalScrollJob = scope.launch {
                    if (isNearTop) {
                        if (listState != null) runCatching { listState.animateScrollToItem(0, 0) }
                        else if (gridState != null) runCatching { gridState.animateScrollToItem(0, 0) }
                        else if (scrollState != null) runCatching { scrollState.animateScrollTo(0) }
                    } else {
                        if (listState != null) runCatching { listState.animateScrollBy(-scrollDistance, tween(200, easing = SnappyEaseOut)) }
                        else if (gridState != null) runCatching { gridState.animateScrollBy(-scrollDistance, tween(200, easing = SnappyEaseOut)) }
                        else if (scrollState != null) runCatching { scrollState.animateScrollBy(-scrollDistance, tween(200, easing = SnappyEaseOut)) }
                    }
                    delay(160)
                    val refreshedRows = getComposedContentRows()
                    val oldCenterY = currentRow?.centerY ?: current.boundsInRoot.center.y
                    val newRow = refreshedRows.lastOrNull { it.centerY < oldCenterY - 40f }
                        ?: refreshedRows.firstOrNull()
                    if (newRow != null) {
                        val target = selectBestTargetNodeInRow(current, newRow)
                        if (target != null) {
                            focusNodeInternal(target, triggerScroll = true)
                        }
                    }
                }
                return true
            }

            val navNodes = nodes.values.filter { it.zone == ControllerFocusZone.Navigation && it.isVisibleAndValid }
            if (navNodes.isNotEmpty()) {
                val target = navNodes.firstOrNull { it.isDefaultFocus }
                    ?: lastFocusedByZone[ControllerFocusZone.Navigation]?.let { id -> navNodes.firstOrNull { it.id == id } }
                    ?: navNodes.minByOrNull { abs(it.boundsInRoot.center.x - current.boundsInRoot.center.x) }
                if (target != null) {
                    focusNodeInternal(target, triggerScroll = false)
                    return true
                }
            }
            return true
        }

        return false
    }

    private fun handleNavigate(direction: ControllerDirection): Boolean {
        if (PlatformVirtualKeyboard.isKeyboardActive) {
            PlatformVirtualKeyboard.hide()
        }
        val overlayNodes = nodes.values.filter { it.zone == ControllerFocusZone.Overlay && it.isVisibleAndValid }
        val activeZone = if (overlayNodes.isNotEmpty()) {
            ControllerFocusZone.Overlay
        } else {
            val current = _focusedNodeId.value?.let { nodes[it] }?.takeIf { it.isVisibleAndValid }
            if (current == null) {
                return ensureValidFocus()
            }
            current.zone
        }

        val current = _focusedNodeId.value?.let { nodes[it] }?.takeIf { it.isVisibleAndValid && it.zone == activeZone }
            ?: run {
                return ensureValidFocus()
            }

        // Deterministic Netflix/Android TV row navigation for Content zone Up and Down
        if (activeZone == ControllerFocusZone.Content &&
            (direction == ControllerDirection.Down || direction == ControllerDirection.Up)
        ) {
            return handleContentVerticalNavigation(current, direction)
        }

        // If currently focused on a View All pill and pressing Left, return to that shelf row's rightmost card
        if (activeZone == ControllerFocusZone.Content && current.id.startsWith("view_all_pill_") && direction == ControllerDirection.Left) {
            val contentNodes = nodes.values.filter { it.zone == ControllerFocusZone.Content && it.isVisibleAndValid && !it.id.startsWith("view_all_pill_") }
            val sameRowCard = contentNodes.filter { it.boundsInRoot.top >= current.boundsInRoot.bottom - 10f && it.boundsInRoot.top <= current.boundsInRoot.bottom + 140f }
                .maxByOrNull { it.boundsInRoot.center.x }
            if (sameRowCard != null) {
                focusNodeInternal(sameRowCard, triggerScroll = true)
                return true
            }
        }

        // Check custom node directional overrides first
        val customHandled = when (direction) {
            ControllerDirection.Up -> current.onMoveUp?.invoke() == true
            ControllerDirection.Down -> current.onMoveDown?.invoke() == true
            ControllerDirection.Left -> current.onMoveLeft?.invoke() == true
            ControllerDirection.Right -> current.onMoveRight?.invoke() == true
        }
        if (customHandled) return true

        val zoneCandidates = nodes.values.filter {
            it.zone == activeZone && it.isVisibleAndValid && it.id != current.id
        }

        val nextInZone = findBestSpatialCandidate(current, zoneCandidates, direction)
        if (nextInZone != null) {
            val isHorizontal = direction == ControllerDirection.Left || direction == ControllerDirection.Right
            val isSameRow = isHorizontal && (
                (current.groupKey != null && current.groupKey == nextInZone.groupKey) ||
                abs(current.boundsInRoot.center.y - nextInZone.boundsInRoot.center.y) < 45f
            )
            focusNodeInternal(nextInZone, triggerScroll = true, suppressVerticalScroll = isSameRow)
            return true
        }

        // If moving Left or Right at the edge of a horizontal LazyRow shelf, scroll the LazyRow to reveal more items!
        if ((direction == ControllerDirection.Right || direction == ControllerDirection.Left) &&
            current.rowLazyListState != null
        ) {
            val rowState = current.rowLazyListState!!
            val canScroll = if (direction == ControllerDirection.Right) {
                rowState.canScrollForward
            } else {
                rowState.canScrollBackward
            }
            if (canScroll) {
                val scope = current.coroutineScope ?: activeCoroutineScope
                if (scope != null) {
                    val groupKey = current.groupKey
                    val currentBounds = current.boundsInRoot
                    val scrollDelta = if (direction == ControllerDirection.Right) {
                        max(currentBounds.width * 1.5f, 260f)
                    } else {
                        -max(currentBounds.width * 1.5f, 260f)
                    }
                    scope.launch {
                        runCatching { rowState.animateScrollBy(scrollDelta, tween(160, easing = SnappyEaseOut)) }
                        delay(100)
                        val refreshed = nodes.values.filter {
                            it.zone == activeZone &&
                                it.isVisibleAndValid &&
                                it.id != current.id &&
                                (groupKey == null || it.groupKey == groupKey)
                        }
                        val newlyVisible = findBestSpatialCandidate(current, refreshed, direction)
                        if (newlyVisible != null) {
                            focusNodeInternal(newlyVisible, triggerScroll = true, suppressVerticalScroll = true)
                        }
                    }
                    return true
                }
            }
        }

        // Zone transitions between Navigation (Sidebar / TopBar) and Content
        if (activeZone == ControllerFocusZone.Content) {
            if (direction == ControllerDirection.Left) {
                // Clamped at index 0 of content: do not hop to sidebar menu on Left stick/D-pad.
                // Sidebar navigation is strictly toggled via Controller Menu (Start) or View buttons.
                return true
            } else if (direction == ControllerDirection.Up) {
                // First check if vertical list can scroll up to reveal upper content/hero
                val activeScroll = getActiveScrollable()
                val listState = activeScroll?.lazyListState
                val scrollState = activeScroll?.scrollState
                val canScrollUp = (listState != null && listState.canScrollBackward) ||
                    (scrollState != null && scrollState.canScrollBackward)
                if (canScrollUp) {
                    val scope = activeScroll?.scope ?: activeCoroutineScope ?: current.coroutineScope
                    if (scope != null) {
                        scope.launch {
                            if (listState != null) {
                                runCatching { listState.animateScrollBy(-360f, tween(160)) }
                            } else if (scrollState != null) {
                                runCatching { scrollState.animateScrollBy(-360f, tween(160)) }
                            }
                            delay(180)
                            val refreshed = nodes.values.filter {
                                it.zone == ControllerFocusZone.Content && it.isVisibleAndValid && it.id != current.id
                            }
                            val candidate = findBestSpatialCandidate(current, refreshed, ControllerDirection.Up)
                            if (candidate != null) {
                                focusNodeInternal(candidate, triggerScroll = true)
                            }
                        }
                        return true
                    }
                }
                // At the very top of Content: transition to TopBar Navigation if present
                val navNodes = nodes.values.filter { it.zone == ControllerFocusZone.Navigation && it.isVisibleAndValid }
                if (navNodes.isNotEmpty()) {
                    val target = navNodes.firstOrNull { it.isDefaultFocus }
                        ?: lastFocusedByZone[ControllerFocusZone.Navigation]?.let { id -> navNodes.firstOrNull { it.id == id } }
                        ?: navNodes.minByOrNull { abs(it.boundsInRoot.center.x - current.boundsInRoot.center.x) }
                    if (target != null) {
                        focusNodeInternal(target, triggerScroll = false)
                        return true
                    }
                }
            } else if (direction == ControllerDirection.Down) {
                // Scroll vertical container down to compose the next shelf/row
                val activeScroll = getActiveScrollable()
                val listState = activeScroll?.lazyListState
                val scrollState = activeScroll?.scrollState
                val canScrollDown = (listState != null && listState.canScrollForward) ||
                    (scrollState != null && scrollState.canScrollForward)
                if (canScrollDown) {
                    val scope = activeScroll?.scope ?: activeCoroutineScope ?: current.coroutineScope
                    if (scope != null) {
                        scope.launch {
                            if (listState != null) {
                                runCatching { listState.animateScrollBy(360f, tween(160)) }
                            } else if (scrollState != null) {
                                runCatching { scrollState.animateScrollBy(360f, tween(160)) }
                            }
                            delay(180)
                            val refreshed = nodes.values.filter {
                                it.zone == ControllerFocusZone.Content && it.isVisibleAndValid && it.id != current.id
                            }
                            val candidate = findBestSpatialCandidate(current, refreshed, ControllerDirection.Down)
                            if (candidate != null) {
                                focusNodeInternal(candidate, triggerScroll = true)
                            }
                        }
                        return true
                    }
                }
            }
        } else if (activeZone == ControllerFocusZone.Navigation) {
            if (direction == ControllerDirection.Right || direction == ControllerDirection.Down) {
                val contentNodes = nodes.values.filter { it.zone == ControllerFocusZone.Content && it.isVisibleAndValid }
                if (contentNodes.isNotEmpty()) {
                    val rememberedId = lastFocusedByZone[ControllerFocusZone.Content]
                    val target = rememberedId?.let { id -> contentNodes.firstOrNull { it.id == id } }
                        ?: contentNodes.firstOrNull { it.isDefaultFocus }
                        ?: contentNodes.minWithOrNull(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))
                    if (target != null) {
                        focusNodeInternal(target, triggerScroll = true)
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun findBestSpatialCandidate(
        current: ControllerFocusNode,
        candidates: List<ControllerFocusNode>,
        direction: ControllerDirection,
    ): ControllerFocusNode? {
        val from = current.boundsInRoot
        val fromCenter = from.center

        if (direction == ControllerDirection.Left || direction == ControllerDirection.Right) {
            // 1. First look for candidates in the same groupKey or vertically overlapping row
            val sameRowCandidates = candidates.filter { candidate ->
                val to = candidate.boundsInRoot
                val toCenter = to.center
                val isCorrectHorizontalDir = if (direction == ControllerDirection.Right) {
                    toCenter.x > fromCenter.x + 8f
                } else {
                    toCenter.x < fromCenter.x - 8f
                }
                if (!isCorrectHorizontalDir) return@filter false

                val sameGroup = current.groupKey != null && current.groupKey == candidate.groupKey &&
                    abs(toCenter.y - fromCenter.y) < max(from.height, to.height) * 0.85f
                val verticalOverlap = min(from.bottom, to.bottom) - max(from.top, to.top)
                val minHeight = min(from.height, to.height).coerceAtLeast(1f)
                sameGroup || (verticalOverlap / minHeight) >= 0.25f || abs(toCenter.y - fromCenter.y) <= 36f
            }

            if (sameRowCandidates.isNotEmpty()) {
                return sameRowCandidates.minByOrNull { candidate ->
                    val toCenter = candidate.boundsInRoot.center
                    val dx = abs(toCenter.x - fromCenter.x)
                    val dy = abs(toCenter.y - fromCenter.y)
                    val groupBonus = if (current.groupKey != null && current.groupKey == candidate.groupKey) -50f else 0f
                    dx + dy * 3.5f + groupBonus
                }
            }
            return null
        }

        // Up or Down navigation
        val directionalCandidates = candidates.filter { candidate ->
            val isChevron = candidate.id.startsWith("view_all_pill_")
            if (isChevron) {
                // Chevrons are ONLY reachable when direction is Up AND current is on the far-right (last) card of that row
                if (direction == ControllerDirection.Up) {
                    val isCurrentChevron = current.id.startsWith("view_all_pill_")
                    val isFarRightCard = !isCurrentChevron && (
                        (current.rowItemIndex >= 0 && current.rowTotalCount > 0 && current.rowItemIndex == current.rowTotalCount - 1) ||
                        candidates.none { other ->
                            other.groupKey != null && other.groupKey == current.groupKey &&
                                other.boundsInRoot.center.x > fromCenter.x + 10f
                        }
                    )
                    val to = candidate.boundsInRoot
                    val isDirectlyAbove = to.center.y < fromCenter.y - 14f && abs(to.center.y - fromCenter.y) < 320f
                    if (!isFarRightCard || !isDirectlyAbove) return@filter false
                } else {
                    // Down navigation never targets a chevron
                    return@filter false
                }
            }

            // If current IS a chevron, pressing Down must return straight back to that row's cards
            if (current.id.startsWith("view_all_pill_") && direction == ControllerDirection.Down) {
                if (isChevron) return@filter false
            }

            val to = candidate.boundsInRoot
            val toCenter = to.center
            if (direction == ControllerDirection.Down) {
                toCenter.y > fromCenter.y + 14f && !(current.groupKey != null && current.groupKey == candidate.groupKey && abs(toCenter.y - fromCenter.y) < from.height * 0.6f)
            } else {
                toCenter.y < fromCenter.y - 14f && !(current.groupKey != null && current.groupKey == candidate.groupKey && abs(toCenter.y - fromCenter.y) < from.height * 0.6f)
            }
        }

        if (directionalCandidates.isEmpty()) return null

        // Find the closest vertical row/band in that direction first
        val nearestVerticalDistance = directionalCandidates.minOf { candidate ->
            val to = candidate.boundsInRoot
            if (direction == ControllerDirection.Down) {
                max(0f, to.top - from.bottom) + abs(to.center.y - fromCenter.y) * 0.35f
            } else {
                max(0f, from.top - to.bottom) + abs(fromCenter.y - to.center.y) * 0.35f
            }
        }

        // Consider candidates in the nearest vertical band (within a reasonable vertical tolerance)
        val bandTolerance = 110f
        val bandCandidates = directionalCandidates.filter { candidate ->
            val to = candidate.boundsInRoot
            val vDist = if (direction == ControllerDirection.Down) {
                max(0f, to.top - from.bottom) + abs(to.center.y - fromCenter.y) * 0.35f
            } else {
                max(0f, from.top - to.bottom) + abs(fromCenter.y - to.center.y) * 0.35f
            }
            vDist <= nearestVerticalDistance + bandTolerance
        }

        // If the target band belongs to a groupKey where we have a remembered focused node (and it's a horizontal shelf, not a multi-row grid), prefer the remembered node or closest X
        val bestBandCandidate = bandCandidates.minByOrNull { candidate ->
            val to = candidate.boundsInRoot
            val dy = abs(to.center.y - fromCenter.y)
            val dx = abs(to.center.x - fromCenter.x)
            val horizontalOverlap = max(0f, min(from.right, to.right) - max(from.left, to.left))
            val overlapBonus = if (horizontalOverlap > 0f) -60f else 0f
            dy * 2.2f + dx * 0.85f + overlapBonus
        }

        return bestBandCandidate
    }
}

private var autoControllerNodeCounter = 0L

/**
 * Registers global/screen-level controller action handlers for the active composition scope.
 */
@Composable
fun RegisterControllerHandler(
    key: String = "",
    screenContext: ControllerScreenContext? = null,
    onBack: (() -> Boolean)? = null,
    onBumperLeft: (() -> Boolean)? = null,
    onBumperRight: (() -> Boolean)? = null,
    onShoulderLeft: (() -> Boolean)? = null,
    onShoulderRight: (() -> Boolean)? = null,
    onTriggerLeft: (() -> Boolean)? = null,
    onTriggerRight: (() -> Boolean)? = null,
    onActionX: (() -> Boolean)? = null,
    onActionY: (() -> Boolean)? = null,
    onSecondaryAction: (() -> Boolean)? = null,
    onTertiaryAction: (() -> Boolean)? = null,
    onViewButton: (() -> Boolean)? = null,
    onMenuButton: (() -> Boolean)? = null,
    onViewAction: (() -> Boolean)? = null,
    onMenuAction: (() -> Boolean)? = null,
    onEnterNavigationZone: (() -> Unit)? = null,
    onExitNavigationZone: (() -> Unit)? = null,
) {
    val effectiveKey = remember(key) {
        key.ifEmpty { "controller_handler_${++autoControllerNodeCounter}" }
    }
    val latestBack by rememberUpdatedState(onBack)
    val latestLB by rememberUpdatedState(onBumperLeft ?: onShoulderLeft)
    val latestRB by rememberUpdatedState(onBumperRight ?: onShoulderRight)
    val latestLT by rememberUpdatedState(onTriggerLeft)
    val latestRT by rememberUpdatedState(onTriggerRight)
    val latestX by rememberUpdatedState(onActionX ?: onSecondaryAction)
    val latestY by rememberUpdatedState(onActionY ?: onTertiaryAction)
    val latestView by rememberUpdatedState(onViewButton ?: onViewAction)
    val latestMenu by rememberUpdatedState(onMenuButton ?: onMenuAction)
    val latestEnterNav by rememberUpdatedState(onEnterNavigationZone)
    val latestExitNav by rememberUpdatedState(onExitNavigationZone)

    LaunchedEffect(screenContext) {
        if (screenContext != null) {
            ControllerManager.updateScreenContext(screenContext)
        }
    }

    DisposableEffect(effectiveKey) {
        ControllerFocusEngine.registerGlobalHandlers(
            key = effectiveKey,
            handlers = ControllerGlobalHandlers(
                onBack = { latestBack?.invoke() == true },
                onBumperLeft = { latestLB?.invoke() == true },
                onBumperRight = { latestRB?.invoke() == true },
                onTriggerLeft = { latestLT?.invoke() == true },
                onTriggerRight = { latestRT?.invoke() == true },
                onActionX = { latestX?.invoke() == true },
                onActionY = { latestY?.invoke() == true },
                onViewButton = { latestView?.invoke() == true },
                onMenuButton = { latestMenu?.invoke() == true },
                onEnterNavigationZone = { latestEnterNav?.invoke() },
                onExitNavigationZone = { latestExitNav?.invoke() },
            ),
        )
        onDispose {
            ControllerFocusEngine.unregisterGlobalHandlers(effectiveKey)
        }
    }
}

/**
 * Registers a vertical LazyListState or ScrollState so the controller spatial engine
 * can automatically scroll shelves/rows into view and handle LT/RT page jumps and RS scrolling.
 */
@Composable
fun RegisterControllerVerticalScroll(
    lazyListState: LazyListState? = null,
    scrollState: ScrollState? = null,
    lazyGridState: LazyGridState? = null,
) {
    val isScreenActive = LocalScreenActive.current
    val scope = rememberCoroutineScope()
    DisposableEffect(lazyListState, scrollState, lazyGridState, isScreenActive) {
        if (isScreenActive) {
            ControllerFocusEngine.registerVerticalScrollable(
                lazyListState = lazyListState,
                scrollState = scrollState,
                lazyGridState = lazyGridState,
                scope = scope,
            )
        }
        onDispose {
            ControllerFocusEngine.unregisterVerticalScrollable(
                lazyListState = lazyListState,
                scrollState = scrollState,
                lazyGridState = lazyGridState,
            )
        }
    }
}

/**
 * Makes any UI element focusable and navigable by the Windows 11 Controller Spatial Navigation Engine,
 * automatically scrolling it into view when focused and drawing a high-contrast visual focus ring.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.controllerFocusable(
    id: String = "",
    zone: ControllerFocusZone? = null,
    groupKey: String? = null,
    enabled: Boolean = true,
    isDefaultFocus: Boolean = false,
    isTextInput: Boolean = false,
    shape: Shape? = null,
    cornerRadius: Dp = 12.dp,
    focusScale: Float = 1.04f,
    scaleOnFocus: Float? = null,
    useHaloFocusRing: Boolean = false,
    hideFocusRing: Boolean = false,
    selectLabel: String = "Select",
    actionLabel: String? = null,
    secondaryActionLabel: String? = null,
    tertiaryActionLabel: String? = null,
    onFocused: (() -> Unit)? = null,
    onSecondaryAction: (() -> Unit)? = null,
    onTertiaryAction: (() -> Unit)? = null,
    onMoveUp: (() -> Boolean)? = null,
    onMoveDown: (() -> Boolean)? = null,
    onMoveLeft: (() -> Boolean)? = null,
    onMoveRight: (() -> Boolean)? = null,
    onSelect: () -> Unit,
): Modifier = composed {
    val effectiveId = remember(id) {
        id.ifEmpty { "ctrl_node_${++autoControllerNodeCounter}" }
    }
    val effectiveSelectLabel = actionLabel ?: selectLabel
    val effectiveFocusScale = scaleOnFocus ?: focusScale
    val isControllerActive by ControllerManager.isControllerActive.collectAsState()
    val isScreenActive = LocalScreenActive.current
    val effectiveEnabled = enabled && isScreenActive
    val focusedNodeId by ControllerFocusEngine.focusedNodeId.collectAsState()
    val isFocused = effectiveEnabled && isControllerActive && focusedNodeId == effectiveId

    val ambientZone = LocalControllerFocusZone.current
    val ambientGroupKey = LocalControllerGroupKey.current
    val ambientRowState = LocalControllerRowLazyListState.current
    val effectiveZone = zone ?: ambientZone
    val effectiveGroupKey = groupKey ?: ambientGroupKey

    val coroutineScope = rememberCoroutineScope()

    val latestOnSelect by rememberUpdatedState(onSelect)
    val latestOnSecondary by rememberUpdatedState(onSecondaryAction)
    val latestOnTertiary by rememberUpdatedState(onTertiaryAction)
    val latestOnFocused by rememberUpdatedState(onFocused)
    val latestMoveUp by rememberUpdatedState(onMoveUp)
    val latestMoveDown by rememberUpdatedState(onMoveDown)
    val latestMoveLeft by rememberUpdatedState(onMoveLeft)
    val latestMoveRight by rememberUpdatedState(onMoveRight)

    var latestBounds by remember { mutableStateOf(Rect.Zero) }

    val node = remember(effectiveId) {
        ControllerFocusNode(
            id = effectiveId,
            zone = effectiveZone,
            groupKey = effectiveGroupKey,
        )
    }

    node.zone = effectiveZone
    node.groupKey = effectiveGroupKey
    node.enabled = effectiveEnabled
    node.isDefaultFocus = isDefaultFocus
    node.isTextInput = isTextInput
    node.selectLabel = effectiveSelectLabel
    node.secondaryActionLabel = secondaryActionLabel
    node.tertiaryActionLabel = tertiaryActionLabel
    node.onSelect = { latestOnSelect() }
    node.onSecondaryAction = latestOnSecondary
    node.onTertiaryAction = latestOnTertiary
    node.onMoveUp = latestMoveUp
    node.onMoveDown = latestMoveDown
    node.onMoveLeft = latestMoveLeft
    node.onMoveRight = latestMoveRight
    node.rowLazyListState = ambientRowState
    node.coroutineScope = coroutineScope
    node.onFocusedWithOption = { _ ->
        latestOnFocused?.invoke()
    }
    node.onFocused = {
        latestOnFocused?.invoke()
    }

    DisposableEffect(effectiveId, effectiveEnabled) {
        if (effectiveEnabled) {
            ControllerFocusEngine.upsertNode(node)
        } else {
            ControllerFocusEngine.removeNode(effectiveId)
        }
        onDispose {
            ControllerFocusEngine.removeNode(effectiveId)
        }
    }

    LaunchedEffect(
        effectiveId,
        effectiveEnabled,
        effectiveZone,
        effectiveGroupKey,
        effectiveSelectLabel,
        secondaryActionLabel,
        tertiaryActionLabel,
        onSecondaryAction != null,
        onTertiaryAction != null,
    ) {
        if (effectiveEnabled) {
            ControllerFocusEngine.upsertNode(node)
        }
    }

    val focusedSnapshot by ControllerFocusEngine.focusedNodeSnapshot.collectAsState()
    val isSiblingActiveInGroup = !isFocused && isControllerActive && effectiveGroupKey != null &&
        focusedSnapshot?.groupKey == effectiveGroupKey
    val targetAlpha = if (isSiblingActiveInGroup) 0.82f else 1f
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 150),
        label = "controller_sibling_alpha",
    )

    val scale by animateFloatAsState(
        targetValue = if (isFocused) effectiveFocusScale else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "controller_focus_scale",
    )

    this
        .onGloballyPositioned { coordinates ->
            val bounds = if (coordinates.isAttached) coordinates.boundsInRoot() else Rect.Zero
            if (bounds != latestBounds) {
                latestBounds = bounds
                node.boundsInRoot = bounds
                if (effectiveEnabled) {
                    ControllerFocusEngine.upsertNode(node)
                }
            }
        }
        .zIndex(if (isFocused) 15f else 0f)
        .graphicsLayer {
            if (scale != 1f) {
                scaleX = scale
                scaleY = scale
            }
            if (animatedAlpha != 1f) {
                alpha = animatedAlpha
            }
        }
        .drawWithContent {
            drawContent()
            if (isFocused && !hideFocusRing) {
                if (useHaloFocusRing) {
                    // Play Button Halo / High-Contrast Focus Ring:
                    // Rendered strictly OUTSIDE the button perimeter so it never encroaches into the white button fill or icon:
                    // 1. Dark separation ring (2px solid #090A10) right at the button boundary
                    // 2. Crisp white outline (2px solid #FFFFFF) offset outward by 2px
                    // 3. Diffused outer white glow (rgba(255, 255, 255, 0.55)) fading outward
                    // 4. Outer subtle drop shadow
                    val sepStrokePx = 2.dp.toPx()
                    val whiteStrokePx = 2.dp.toPx()
                    val glowStrokePx = 5.dp.toPx()
                    val shadowStrokePx = 6.dp.toPx()

                    val outlineOffsetPx = 2.dp.toPx()
                    val glowOffsetPx = 4.dp.toPx()
                    val shadowOffsetPx = 7.dp.toPx()

                    if (shape != null) {
                        // 1. Boundary separation ring
                        drawOutline(
                            outline = shape.createOutline(size, layoutDirection, this),
                            color = Color(0xFF090A10),
                            style = Stroke(width = sepStrokePx),
                        )
                        // 2. White outline offset outward by 2px
                        translate(left = -outlineOffsetPx, top = -outlineOffsetPx) {
                            val expandedSize = Size(size.width + outlineOffsetPx * 2, size.height + outlineOffsetPx * 2)
                            drawOutline(
                                outline = shape.createOutline(expandedSize, layoutDirection, this),
                                color = Color.White,
                                style = Stroke(width = whiteStrokePx),
                            )
                        }
                        // 3. Diffused white glow
                        translate(left = -glowOffsetPx, top = -glowOffsetPx) {
                            val expandedSize = Size(size.width + glowOffsetPx * 2, size.height + glowOffsetPx * 2)
                            drawOutline(
                                outline = shape.createOutline(expandedSize, layoutDirection, this),
                                color = Color.White.copy(alpha = 0.55f),
                                style = Stroke(width = glowStrokePx),
                            )
                        }
                        // 4. Subtle outer shadow
                        translate(left = -shadowOffsetPx, top = -shadowOffsetPx) {
                            val expandedSize = Size(size.width + shadowOffsetPx * 2, size.height + shadowOffsetPx * 2)
                            drawOutline(
                                outline = shape.createOutline(expandedSize, layoutDirection, this),
                                color = Color.Black.copy(alpha = 0.50f),
                                style = Stroke(width = shadowStrokePx),
                            )
                        }
                    } else {
                        val radiusPx = cornerRadius.toPx()
                        // 1. Boundary separation ring
                        drawRoundRect(
                            color = Color(0xFF090A10),
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = CornerRadius(radiusPx, radiusPx),
                            style = Stroke(width = sepStrokePx),
                        )
                        // 2. White outline offset outward by 2px
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(-outlineOffsetPx, -outlineOffsetPx),
                            size = Size(size.width + outlineOffsetPx * 2, size.height + outlineOffsetPx * 2),
                            cornerRadius = CornerRadius(radiusPx + outlineOffsetPx, radiusPx + outlineOffsetPx),
                            style = Stroke(width = whiteStrokePx),
                        )
                        // 3. Diffused white glow
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.55f),
                            topLeft = Offset(-glowOffsetPx, -glowOffsetPx),
                            size = Size(size.width + glowOffsetPx * 2, size.height + glowOffsetPx * 2),
                            cornerRadius = CornerRadius(radiusPx + glowOffsetPx, radiusPx + glowOffsetPx),
                            style = Stroke(width = glowStrokePx),
                        )
                        // 4. Subtle outer shadow
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.50f),
                            topLeft = Offset(-shadowOffsetPx, -shadowOffsetPx),
                            size = Size(size.width + shadowOffsetPx * 2, size.height + shadowOffsetPx * 2),
                            cornerRadius = CornerRadius(radiusPx + shadowOffsetPx, radiusPx + shadowOffsetPx),
                            style = Stroke(width = shadowStrokePx),
                        )
                    }
                } else {
                    // Subtle Modern Focus Ring across all cards/elements:
                    // 1. Subtle drop shadow
                    // 2. Soft diffused white glow (rgba(255, 255, 255, 0.45))
                    // 3. Crisp thin 2px solid white primary border
                    val shadowStrokePx = 8.dp.toPx()
                    val glowStrokePx = 5.dp.toPx()
                    val whiteBorderStrokePx = 2.dp.toPx()

                    if (shape != null) {
                        val outline = shape.createOutline(size, layoutDirection, this)
                        drawOutline(
                            outline = outline,
                            color = Color.Black.copy(alpha = 0.50f),
                            style = Stroke(width = shadowStrokePx),
                        )
                        drawOutline(
                            outline = outline,
                            color = Color.White.copy(alpha = 0.45f),
                            style = Stroke(width = glowStrokePx),
                        )
                        drawOutline(
                            outline = outline,
                            color = Color.White,
                            style = Stroke(width = whiteBorderStrokePx),
                        )
                    } else {
                        val radiusPx = cornerRadius.toPx()
                        val cr = CornerRadius(radiusPx, radiusPx)
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.50f),
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = cr,
                            style = Stroke(width = shadowStrokePx),
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.45f),
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = cr,
                            style = Stroke(width = glowStrokePx),
                        )
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = cr,
                            style = Stroke(width = whiteBorderStrokePx),
                        )
                    }
                }
            }
        }
}

/**
 * Convenience check for whether a specific controller node currently holds active spatial focus.
 */
@Composable
fun isControllerNodeFocused(id: String): Boolean {
    val isControllerActive by ControllerManager.isControllerActive.collectAsState()
    val focusedNodeId by ControllerFocusEngine.focusedNodeId.collectAsState()
    return isControllerActive && focusedNodeId == id
}


