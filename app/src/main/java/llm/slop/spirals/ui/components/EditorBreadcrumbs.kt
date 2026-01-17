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

/**
 * EditorBreadcrumbs - Navigation header showing the editing path.
 * 
 * Displays the navigation stack as clickable breadcrumbs with wrapping support.
 * Example: "Show1 > Mix001 > Set001 > Man001"
 * 
 * KEY DESIGN DECISIONS:
 * - Uses FlowRow with maxLines=2 to wrap long paths without pushing menu off screen
 * - Each breadcrumb + arrow is a single Row unit (no mid-item line breaks)
 * - Individual names truncated at 100dp to prevent excessive width
 * - Reserves fixed 48dp for menu button (always accessible)
 * - Only shows layers with data (hides generic "Editor" labels)
 * - Extracts name from data (not layer.name) to handle rename timing issues
 * 
 * @param stack The navigation stack to display
 * @param onLayerClick Callback when a breadcrumb is clicked (triggers cascade)
 * @param actions Content for the right side (typically overflow menu button)
 * 
 * NOTE TO FUTURE AI: The breadcrumb click triggers popToLayer() which performs
 * the cascade save/link. This is a core UX pattern. See DESIGN.md for details.
 * If you modify this, test with deep paths like Show>Mixer>Set>Mandala.
 */
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
                    // Get the actual name from the data if available, otherwise use layer.name
                    val actualName = when (val data = layer.data) {
                        is llm.slop.spirals.MandalaLayerContent -> data.patch.name
                        is llm.slop.spirals.SetLayerContent -> data.set.name
                        is llm.slop.spirals.MixerLayerContent -> data.mixer.name
                        is llm.slop.spirals.ShowLayerContent -> data.show.name
                        is llm.slop.spirals.RandomSetLayerContent -> data.randomSet.name
                        null -> layer.name
                    }
                    
                    val displayName = if (layer.isDirty) "$actualName *" else actualName
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
