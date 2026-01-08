package llm.slop.spirals.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import llm.slop.spirals.ui.theme.AppAccent
import llm.slop.spirals.ui.theme.AppText
import kotlinx.coroutines.launch

@Composable
fun SetChipList(
    chipIds: List<String>,
    onChipTapped: (String) -> Unit,
    onChipReordered: (List<String>) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { offset.x >= it.offset && offset.x < it.offset + it.size }
                            ?.also {
                                draggedIndex = it.index
                                dragOffset = offset
                            }
                    },
                    onDragEnd = { 
                        draggedIndex?.let { startIndex ->
                            val targetItem = listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { dragOffset.x >= it.offset && dragOffset.x < it.offset + it.size }
                            
                            val targetIndex = targetItem?.index ?: startIndex

                            if (startIndex != targetIndex) {
                                val reordered = chipIds.toMutableList()
                                val item = reordered.removeAt(startIndex)
                                reordered.add(targetIndex, item)
                                onChipReordered(reordered)
                            }
                        }
                        draggedIndex = null
                        dragOffset = Offset.Zero
                    },
                    onDrag = { change, dragAmount ->
                        dragOffset += dragAmount
                        draggedIndex?.let {
                            coroutineScope.launch {
                                listState.scrollBy(-dragAmount.x)
                            }
                        }
                        change.consume()
                    }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        itemsIndexed(chipIds, key = { _, id -> id }) { index, chipId ->
            Card(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable { onChipTapped(chipId) },
                colors = CardDefaults.cardColors(containerColor = AppAccent)
            ) {
                Text(
                    text = chipId,
                    color = AppText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
