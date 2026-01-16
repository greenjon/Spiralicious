package llm.slop.spirals.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import llm.slop.spirals.NavLayer
import llm.slop.spirals.ui.theme.AppAccent
import llm.slop.spirals.ui.theme.AppBackground
import llm.slop.spirals.ui.theme.AppText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorBreadcrumbs(
    stack: List<NavLayer>,
    onLayerClick: (Int) -> Unit,
    actions: @Composable BoxScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(8.dp)
            .border(1.dp, AppText.copy(alpha = 0.2f))
            .background(AppBackground.copy(alpha = 0.9f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Breadcrumb items with FlowRow for wrapping (max 2 lines)
        FlowRow(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            maxLines = 2
        ) {
            stack.forEachIndexed { index, layer ->
                // Skip generic "Editor" names - only show if there's actual data
                val showName = layer.data != null
                
                if (showName) {
                    val displayName = if (layer.isDirty) "${layer.name} *" else layer.name
                    val isCurrent = index == stack.lastIndex
                    
                    // Wrap each breadcrumb + arrow as a single unit so they don't break mid-item
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 1.dp)
                    ) {
                        Text(
                            text = displayName,
                            color = if (isCurrent) AppAccent else AppText.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = if (isCurrent) 13.sp else 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clickable { onLayerClick(index) }
                                .widthIn(max = 100.dp)  // Limit individual name length for wrapping
                        )
                        
                        if (index < stack.lastIndex) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = AppText.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .size(14.dp)
                                    .padding(horizontal = 1.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // Reserve fixed space for actions/menu (48dp for IconButton)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(48.dp)
                .padding(top = 2.dp)  // Align with top of flow row
        ) {
            actions()
        }
    }
}
