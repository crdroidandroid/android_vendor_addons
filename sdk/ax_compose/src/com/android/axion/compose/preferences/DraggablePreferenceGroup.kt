/*
 * Copyright 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.axion.compose.preferences

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

@Composable
fun <T> DraggablePreferenceGroup(
    items: List<T>,
    itemKey: (T) -> Any,
    itemTitle: (T) -> String,
    modifier: Modifier = Modifier,
    title: String? = null,
    itemSummary: (T) -> String? = { null },
    deleteContentDescription: String? = null,
    onItemClick: (T) -> Unit,
    onItemDelete: ((T) -> Unit)? = null,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    contentBeforeItems: PreferenceGroupScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val stepPx = remember(density) { with(density) { PreferenceTokens.DraggableStep.toPx() } }
    var draggedKey by remember { mutableStateOf<Any?>(null) }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val targetIndex = dragTargetIndex(draggedIndex, dragOffset, items.size, stepPx)

    fun startDrag(key: Any, index: Int) {
        draggedKey = key
        draggedIndex = index
        dragOffset = 0f
    }

    fun updateDragOffset(dragAmount: Float) {
        if (draggedIndex == -1) {
            return
        }
        val minOffset = -draggedIndex * stepPx
        val maxOffset = (items.size - draggedIndex - 1).coerceAtLeast(0) * stepPx
        dragOffset = (dragOffset + dragAmount).coerceIn(minOffset, maxOffset)
    }

    fun commitDrag() {
        val fromIndex = draggedIndex
        val toIndex = dragTargetIndex(fromIndex, dragOffset, items.size, stepPx)
        draggedKey = null
        draggedIndex = -1
        dragOffset = 0f
        if (fromIndex != -1 && fromIndex != toIndex) {
            onMove(fromIndex, toIndex)
        }
    }

    PreferenceGroup(modifier = modifier, title = title) {
        contentBeforeItems()
        items.forEachIndexed { index, value ->
            val key = itemKey(value)
            val summary = itemSummary(value)
            val isDragging = draggedKey == key
            val displacedOffset = displacedOffset(index, draggedIndex, targetIndex, stepPx)
            item {
                key(key) {
                    DraggablePreference(
                        item = value,
                        title = itemTitle(value),
                        summary = summary,
                        deleteContentDescription = deleteContentDescription,
                        isDragging = isDragging,
                        dragOffset = if (isDragging) dragOffset else 0f,
                        displacedOffset = if (isDragging) 0f else displacedOffset,
                        isAnyDragging = draggedKey != null,
                        dragEnabled = items.size > 1,
                        onDragStart = { startDrag(key, index) },
                        onDrag = ::updateDragOffset,
                        onDragEnd = ::commitDrag,
                        onItemClick = onItemClick,
                        onItemDelete = onItemDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> DraggablePreference(
    item: T,
    title: String,
    summary: String?,
    deleteContentDescription: String?,
    isDragging: Boolean,
    dragOffset: Float,
    displacedOffset: Float,
    isAnyDragging: Boolean,
    dragEnabled: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onItemClick: (T) -> Unit,
    onItemDelete: ((T) -> Unit)?,
) {
    val density = LocalDensity.current
    val position = LocalPreferencePosition.current
    val shape = preferenceShape(position)
    val animatedDisplacement by animateFloatAsState(
        targetValue = displacedOffset,
        label = "draggablePreferenceDisplacement",
    )
    val liftScale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        label = "draggablePreferenceScale",
    )
    val liftElevation by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 0.dp,
        label = "draggablePreferenceElevation",
    )
    val liftElevationPx = with(density) { liftElevation.toPx() }
    @Composable
    fun DragHandle() {
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = null,
            tint = if (isDragging) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .size(40.dp)
                .then(
                    if (dragEnabled) {
                        Modifier.pointerInput(item) {
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial,
                                )
                                down.consume()
                                var pointerId = down.id
                                try {
                                    onDragStart()
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull {
                                            it.id == pointerId
                                        } ?: event.changes.firstOrNull {
                                            it.pressed
                                        } ?: break
                                        if (change.changedToUpIgnoreConsumed()) {
                                            val nextDown = event.changes.firstOrNull {
                                                it.pressed
                                            } ?: break
                                            pointerId = nextDown.id
                                            continue
                                        }
                                        val dragAmount = change.positionChangeIgnoreConsumed().y
                                        if (dragAmount != 0f) {
                                            change.consume()
                                            onDrag(dragAmount)
                                        }
                                    }
                                } finally {
                                    onDragEnd()
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
        )
    }

    Box(
        modifier = Modifier
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = if (isDragging) dragOffset else animatedDisplacement
                scaleX = liftScale
                scaleY = liftScale
                shadowElevation = liftElevationPx
                this.shape = shape
                clip = false
            },
    ) {
        BasePreference(
            title = title,
            summary = summary,
            customIcon = { DragHandle() },
            modifier = Modifier.clickable(enabled = !isAnyDragging) { onItemClick(item) },
            widget = {
                val deleteAction = onItemDelete
                if (deleteAction != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { deleteAction(item) },
                        modifier = deleteContentDescription?.let {
                            Modifier.semantics { contentDescription = it }
                        } ?: Modifier,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            },
        )
    }
}

private fun dragTargetIndex(fromIndex: Int, offset: Float, itemCount: Int, stepPx: Float): Int {
    if (fromIndex == -1 || itemCount < 2 || stepPx == 0f) {
        return fromIndex
    }
    return (fromIndex + (offset / stepPx).roundToInt()).coerceIn(0, itemCount - 1)
}

private fun displacedOffset(index: Int, fromIndex: Int, targetIndex: Int, stepPx: Float): Float {
    return when {
        fromIndex == -1 || targetIndex == fromIndex -> 0f
        targetIndex > fromIndex && index in (fromIndex + 1)..targetIndex -> -stepPx
        targetIndex < fromIndex && index in targetIndex until fromIndex -> stepPx
        else -> 0f
    }
}
