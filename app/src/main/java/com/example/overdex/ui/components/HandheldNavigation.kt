package com.example.overdex.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * UI-agnostic controller for handheld-style navigation.
 */
@Stable
class HandheldNavigationController(
    initialIndex: Int = 0,
    private val itemCount: () -> Int,
    private val onActivate: (Int) -> Unit = {}
) {
    var selectedIndex by mutableIntStateOf(initialIndex)
        internal set

    companion object {
        fun Saver(
            itemCount: () -> Int,
            onActivate: (Int) -> Unit
        ): Saver<HandheldNavigationController, Int> = Saver(
            save = { it.selectedIndex },
            restore = { HandheldNavigationController(it, itemCount, onActivate) }
        )
    }

    /**
     * Clamps the selection if the item count decreases.
     */
    fun updateSelection() {
        val count = itemCount()
        if (count > 0 && selectedIndex >= count) {
            selectedIndex = count - 1
        }
    }

    fun moveUp() {
        if (selectedIndex > 0) selectedIndex--
    }

    fun moveDown() {
        if (selectedIndex < itemCount() - 1) selectedIndex++
    }

    fun setIndex(index: Int) {
        val count = itemCount()
        if (index in 0 until count) {
            selectedIndex = index
        }
    }

    /**
     * Handle touch selection logic (first tap selects, second tap activates).
     */
    fun handleTouch(index: Int) {
        if (selectedIndex == index) {
            activate()
        } else {
            setIndex(index)
        }
    }

    fun activate() {
        onActivate(selectedIndex)
    }
}

/**
 * Generic state holder for interface-driven focus navigation.
 * Tracks logical identity [T] instead of integer positions.
 */
@Stable
class HandheldFocusManager<T>(
    initialItem: T? = null
) {
    var currentItem by mutableStateOf(initialItem)
        private set

    private var items by mutableStateOf(emptyList<T>())

    /**
     * Updates the navigation graph. If the previous [currentItem] is no longer 
     * present, selects the nearest valid neighbor.
     */
    fun updateItems(newItems: List<T>) {
        val oldItems = items
        val oldItem = currentItem
        items = newItems

        if (newItems.isEmpty()) {
            currentItem = null
            return
        }

        if (oldItem == null || !newItems.contains(oldItem)) {
            val oldIndex = oldItems.indexOf(oldItem)
            currentItem = if (oldIndex != -1) {
                newItems[oldIndex.coerceIn(0, newItems.size - 1)]
            } else {
                newItems.first()
            }
        }
    }

    fun moveUp() {
        val index = items.indexOf(currentItem)
        if (index > 0) {
            currentItem = items[index - 1]
        }
    }

    fun moveDown() {
        val index = items.indexOf(currentItem)
        if (index != -1 && index < items.size - 1) {
            currentItem = items[index + 1]
        }
    }

    fun moveLeft() { /* Interface-specific implementation */ }
    fun moveRight() { /* Interface-specific implementation */ }
}

@Composable
fun <T> rememberHandheldFocusManager(
    initialItem: T? = null
): HandheldFocusManager<T> {
    return remember { HandheldFocusManager(initialItem) }
}

@Composable
fun rememberHandheldNavigationController(
    initialIndex: Int = 0,
    itemCount: () -> Int,
    onActivate: (Int) -> Unit = {}
): HandheldNavigationController {
    val controller = rememberSaveable(
        saver = HandheldNavigationController.Saver(itemCount, onActivate)
    ) {
        HandheldNavigationController(initialIndex, itemCount, onActivate)
    }
    
    // Auto-clamp when list content changes
    LaunchedEffect(itemCount()) {
        controller.updateSelection()
    }
    
    return controller
}

/**
 * Synchronizes a LazyListState with a HandheldNavigationController.
 */
@Composable
fun HandheldListSync(
    listState: LazyListState,
    selectedIndex: Int,
    listIndexMapping: (Int) -> Int? = { it },
    totalItems: Int
) {
    LaunchedEffect(selectedIndex) {
        val listIndex = listIndexMapping(selectedIndex) ?: return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isEmpty() || totalItems == 0) return@LaunchedEffect

        val firstVisible = visibleItems.first().index
        val lastVisible = visibleItems.last().index

        if (listIndex < firstVisible || listIndex > lastVisible) {
            listState.animateScrollToItem(listIndex)
        } else if (listIndex <= firstVisible && listIndex > 0) {
            listState.animateScrollToItem(listIndex - 1)
        } else if (listIndex >= lastVisible && listIndex < totalItems - 1) {
            listState.animateScrollToItem(listState.firstVisibleItemIndex + 1)
        }
    }
}

/**
 * Synchronizes a selected index with BringIntoViewRequesters.
 * Useful for non-Lazy lists (standard scrollable columns).
 */
@Composable
fun <T> HandheldFocusSync(
    selectedIndex: Int,
    items: List<T>,
    requesters: Map<T, BringIntoViewRequester>
) {
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices) {
            val key = items[selectedIndex]
            requesters[key]?.bringIntoView()
        }
    }
}

/**
 * Reusable pointer input modifier for press-and-hold repetition with acceleration.
 */
fun Modifier.repeatableAction(
    onAction: () -> Unit
): Modifier = this.pointerInput(Unit) {
    detectTapGestures(
        onPress = {
            var currentDelay = 400L
            val minDelay = 60L
            val acceleration = 0.8f
            
            onAction()
            
            coroutineScope {
                val job = launch {
                    delay(currentDelay)
                    while (true) {
                        onAction()
                        delay(currentDelay)
                        currentDelay = (currentDelay * acceleration).toLong().coerceAtLeast(minDelay)
                    }
                }
                
                try {
                    awaitRelease()
                } finally {
                    job.cancel()
                }
            }
        }
    )
}
