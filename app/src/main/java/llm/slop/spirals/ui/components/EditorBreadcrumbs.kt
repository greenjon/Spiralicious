package llm.slop.spirals.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import llm.slop.spirals.NavLayer
import llm.slop.spirals.ui.theme.AppAccent
import llm.slop.spirals.ui.theme.AppBackground
import llm.slop.spirals.ui.theme.AppText

@Composable
fun EditorBreadcrumbs(
    stack: List<NavLayer>,
    onLayerClick: (Int) -> Unit,
    onMenuClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(8.dp)
            .border(1.dp, AppText.copy(alpha = 0.2f))
            .background(AppBackground.copy(alpha = 0.9f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        stack.forEachIndexed { index, layer ->
            Text(
                text = layer.name,
                color = if (index == stack.lastIndex) AppAccent else AppText,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { onLayerClick(index) }
            )
            
            if (index < stack.lastIndex) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = AppText.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp).padding(horizontal = 4.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = AppText)
        }
    }
}
