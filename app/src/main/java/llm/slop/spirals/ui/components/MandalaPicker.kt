package llm.slop.spirals.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import llm.slop.spirals.MandalaPatchEntity
import llm.slop.spirals.ui.theme.AppText

@Composable
fun MandalaPicker(
    patches: List<MandalaPatchEntity>,
    onPatchSelected: (String) -> Unit, // For previewing
    onPatchAdded: (String) -> Unit // For adding to the set
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Add Mandala to Set", color = AppText, modifier = Modifier.padding(bottom = 12.dp))
        
        patches.forEach { patch ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPatchSelected(patch.name) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(patch.name, color = AppText, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { onPatchAdded(patch.name) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add to set")
                }
            }
        }
    }
}
