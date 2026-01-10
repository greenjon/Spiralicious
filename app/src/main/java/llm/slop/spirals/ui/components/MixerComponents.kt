package llm.slop.spirals.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import llm.slop.spirals.*
import llm.slop.spirals.models.*
import llm.slop.spirals.ui.theme.AppAccent
import llm.slop.spirals.ui.theme.AppBackground
import llm.slop.spirals.ui.theme.AppText
import llm.slop.spirals.cv.ModulatableParameter
import llm.slop.spirals.ui.ModulatorRow
import kotlinx.serialization.json.Json

@Composable
fun SourceStrip(
    index: Int,
    patch: MixerPatch,
    onPatchChange: (MixerPatch) -> Unit,
    mainRenderer: SpiralRenderer?,
    onPickSet: () -> Unit,
    onPickMandala: () -> Unit,
    allSets: List<MandalaSetEntity>,
    identity: String,
    onOffAlignment: Alignment,
    focusedId: String,
    onFocusChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val slot = patch.slots[index]
    val slotId = "S${index + 1}"
    val gainId = "G${index + 1}"
    val prevNextId = "PN${index + 1}"

    Column(
        modifier = modifier.padding(1.dp).wrapContentHeight().border(1.dp, AppText.copy(alpha = 0.1f)).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Source $identity",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = AppText,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 0.dp)
        )

        Box(modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f)
            .background(Color.Black)
            .border(1.dp, AppText.copy(alpha = 0.2f))
            .clickable {
                val newSlots = patch.slots.toMutableList()
                newSlots[index] = slot.copy(enabled = !slot.enabled)
                onPatchChange(patch.copy(slots = newSlots))
            }
        ) {
            if (slot.enabled) {
                if (slot.isPopulated()) {
                    StripPreview(monitorSource = "${index + 1}", patch = patch, mainRenderer = mainRenderer)
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No source loaded", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp, textAlign = TextAlign.Center)
                    }
                }
                
                Text(
                    text = "ON",
                    modifier = Modifier.align(onOffAlignment).padding(horizontal = 2.dp, vertical = 0.dp),
                    style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, shadow = Shadow(color = Color.Black, blurRadius = 3f))
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "OFF",
                        style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, shadow = Shadow(color = Color.Black, blurRadius = 3f))
                    )
                    Text(
                        text = "Tap to enable",
                        style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp, fontWeight = FontWeight.Normal, shadow = Shadow(color = Color.Black, blurRadius = 3f))
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = if (slot.sourceIsSet) "Mandala Set" else "Mandala",
            style = MaterialTheme.typography.labelSmall,
            color = if (focusedId == slotId) AppAccent else AppText.copy(alpha = 0.7f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable {
                onFocusChange(slotId)
                val newSlots = patch.slots.toMutableList()
                newSlots[index] = slot.copy(sourceIsSet = !slot.sourceIsSet)
                onPatchChange(patch.copy(slots = newSlots))
            }.padding(2.dp)
        )
        
        val displayName = if (slot.sourceIsSet) {
            allSets.find { it.id == slot.mandalaSetId }?.name ?: "Pick Set"
        } else {
            slot.selectedMandalaId ?: "Pick Man"
        }
        
        Button(
            onClick = { if (slot.sourceIsSet) onPickSet() else onPickMandala() },
            modifier = Modifier.fillMaxWidth().height(28.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppText.copy(alpha = 0.1f)),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text(displayName, style = MaterialTheme.typography.labelSmall, color = AppText, maxLines = 1, textAlign = TextAlign.Center, fontSize = 8.sp)
        }
        
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth().clickable { onFocusChange(prevNextId) },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val arrowColor = if (focusedId == prevNextId) AppAccent else AppText
            IconButton(
                onClick = {
                    onFocusChange(prevNextId)
                    if (slot.sourceIsSet) {
                        val set = allSets.find { it.id == slot.mandalaSetId }
                        set?.let {
                            val ids = Json.decodeFromString<List<String>>(it.jsonOrderedMandalaIds)
                            if (ids.isNotEmpty()) {
                                val nextIdx = if (slot.currentIndex.baseValue <= 0) ids.size - 1 else slot.currentIndex.baseValue.toInt() - 1
                                val newSlots = patch.slots.toMutableList()
                                newSlots[index] = slot.copy(currentIndex = slot.currentIndex.copy(baseValue = nextIdx.toFloat()))
                                onPatchChange(patch.copy(slots = newSlots))
                            }
                        }
                    }
                },
                modifier = Modifier.size(36.dp),
                enabled = slot.sourceIsSet
            ) { Icon(Icons.Default.KeyboardArrowLeft, null, tint = if (slot.sourceIsSet) arrowColor else arrowColor.copy(alpha = 0.2f), modifier = Modifier.size(32.dp)) }
            
            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    onFocusChange(prevNextId)
                    if (slot.sourceIsSet) {
                        val set = allSets.find { it.id == slot.mandalaSetId }
                        set?.let {
                            val ids = Json.decodeFromString<List<String>>(it.jsonOrderedMandalaIds)
                            if (ids.isNotEmpty()) {
                                val nextIdx = (slot.currentIndex.baseValue.toInt() + 1) % ids.size
                                val newSlots = patch.slots.toMutableList()
                                newSlots[index] = slot.copy(currentIndex = slot.currentIndex.copy(baseValue = nextIdx.toFloat()))
                                onPatchChange(patch.copy(slots = newSlots))
                            }
                        }
                    }
                },
                modifier = Modifier.size(36.dp),
                enabled = slot.sourceIsSet
            ) { Icon(Icons.Default.KeyboardArrowRight, null, tint = if (slot.sourceIsSet) arrowColor else arrowColor.copy(alpha = 0.2f), modifier = Modifier.size(32.dp)) }
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onFocusChange(gainId) }) {
            KnobView(
                currentValue = slot.gain.baseValue,
                onValueChange = { newValue ->
                    onFocusChange(gainId)
                    val newSlots = patch.slots.toMutableList()
                    newSlots[index] = slot.copy(gain = slot.gain.copy(baseValue = newValue))
                    onPatchChange(patch.copy(slots = newSlots))
                },
                onInteractionFinished = {},
                knobSize = 44.dp,
                showValue = true,
                focused = focusedId == gainId
            )
            Text("GAIN", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = if (focusedId == gainId) AppAccent else AppText)
        }
    }
}

@Composable
fun MonitorStrip(
    group: String,
    patch: MixerPatch,
    onPatchChange: (MixerPatch) -> Unit,
    mainRenderer: SpiralRenderer?,
    hasToggle: Boolean,
    viewSet1A2: Boolean,
    onToggleViewSet: (Boolean) -> Unit,
    focusedId: String,
    onFocusChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupData = when(group) {
        "A" -> patch.mixerA
        "B" -> patch.mixerB
        else -> patch.mixerF
    }
    
    val headerText = when(group) {
        "A" -> "Mixer A"
        "B" -> "Mixer B"
        else -> "Final Mixer"
    }

    val modeId = "M${group}_MODE"
    val balId = "M${group}_BAL"
    val mixId = "M${group}_MIX"
    val gainId = "M${group}_GAIN"
    
    Column(
        modifier = modifier.padding(1.dp).wrapContentHeight().border(1.dp, AppText.copy(alpha = 0.1f)).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = headerText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = AppText,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 0.dp)
        )

        Box(modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f)
            .background(Color.Black)
            .border(1.dp, AppText.copy(alpha = 0.2f))
            .clickable {
                if (group == "A") onToggleViewSet(false)
                else if (group == "B") onToggleViewSet(true)
            }
        ) {
            StripPreview(monitorSource = group, patch = patch, mainRenderer = mainRenderer)

            if (hasToggle) {
                Row(modifier = Modifier.align(Alignment.TopCenter), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(text = "1/2", style = TextStyle(color = if (viewSet1A2) AppAccent else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, shadow = Shadow(color = Color.Black, blurRadius = 3f)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "3/4", style = TextStyle(color = if (!viewSet1A2) AppAccent else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, shadow = Shadow(color = Color.Black, blurRadius = 3f)))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        var modeExpanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.clickable { onFocusChange(modeId) }) {
            Text(
                text = MixerMode.values()[groupData.mode.baseValue.toInt()].name,
                style = MaterialTheme.typography.labelSmall,
                color = if (focusedId == modeId) AppAccent else AppText.copy(alpha = 0.7f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onFocusChange(modeId); modeExpanded = true }.padding(2.dp)
            )
            DropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }, containerColor = AppBackground) {
                MixerMode.values().forEachIndexed { index, m ->
                    DropdownMenuItem(text = { Text(m.name, fontSize = 11.sp) }, onClick = {
                        val newGroup = groupData.copy(mode = groupData.mode.copy(baseValue = index.toFloat()))
                        onPatchChange(updateGroup(patch, group, newGroup))
                        modeExpanded = false
                    })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(2.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onFocusChange(balId) }) {
                Text("BAL", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = if (focusedId == balId) AppAccent else AppText)
                KnobView(
                    currentValue = groupData.balance.baseValue,
                    onValueChange = { newValue ->
                        onFocusChange(balId)
                        val newGroup = groupData.copy(balance = groupData.balance.copy(baseValue = newValue))
                        onPatchChange(updateGroup(patch, group, newGroup))
                    },
                    onInteractionFinished = {},
                    knobSize = 44.dp,
                    showValue = true,
                    focused = focusedId == balId
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onFocusChange(mixId) }) {
                Text("MIX", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = if (focusedId == mixId) AppAccent else AppText)
                KnobView(
                    currentValue = groupData.mix.baseValue,
                    onValueChange = { newValue ->
                        onFocusChange(mixId)
                        val newGroup = groupData.copy(mix = groupData.mix.copy(baseValue = newValue))
                        onPatchChange(updateGroup(patch, group, newGroup))
                    },
                    onInteractionFinished = {},
                    knobSize = 44.dp,
                    showValue = true,
                    focused = focusedId == mixId
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onFocusChange(gainId) }) {
            KnobView(
                currentValue = groupData.gain.baseValue,
                onValueChange = { newValue ->
                    onFocusChange(gainId)
                    val newGroup = groupData.copy(gain = groupData.gain.copy(baseValue = newValue))
                    onPatchChange(updateGroup(patch, group, newGroup))
                },
                onInteractionFinished = {},
                knobSize = 44.dp,
                showValue = true,
                focused = focusedId == gainId
            )
            Text("GAIN", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = if (focusedId == gainId) AppAccent else AppText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MixerCvEditor(
    patch: MixerPatch,
    focusedId: String,
    onPatchUpdate: (MixerPatch) -> Unit
) {
    val scrollState = rememberScrollState()
    
    val focusedParamData = remember(patch, focusedId) {
        when {
            focusedId.startsWith("G") -> {
                val idx = (focusedId.last().digitToIntOrNull() ?: 1) - 1
                if (idx in 0..3) patch.slots[idx].gain else null
            }
            focusedId.startsWith("PN") -> {
                val idx = (focusedId.last().digitToIntOrNull() ?: 1) - 1
                if (idx in 0..3) patch.slots[idx].currentIndex else null
            }
            focusedId.startsWith("MA_") -> {
                when(focusedId.removePrefix("MA_")) {
                    "MODE" -> patch.mixerA.mode
                    "BAL" -> patch.mixerA.balance
                    "MIX" -> patch.mixerA.mix
                    "GAIN" -> patch.mixerA.gain
                    else -> null
                }
            }
            focusedId.startsWith("MB_") -> {
                when(focusedId.removePrefix("MB_")) {
                    "MODE" -> patch.mixerB.mode
                    "BAL" -> patch.mixerB.balance
                    "MIX" -> patch.mixerB.mix
                    "GAIN" -> patch.mixerB.gain
                    else -> null
                }
            }
            focusedId.startsWith("MF_") -> {
                when(focusedId.removePrefix("MF_")) {
                    "MODE" -> patch.mixerF.mode
                    "BAL" -> patch.mixerF.balance
                    "MIX" -> patch.mixerF.mix
                    "GAIN" -> patch.mixerF.gain
                    else -> null
                }
            }
            else -> null
        }
    }

    if (focusedParamData == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a parameter to patch CV", color = AppText.copy(alpha = 0.5f))
        }
        return
    }

    val tempParam = remember(focusedId, focusedParamData) {
        ModulatableParameter(baseValue = focusedParamData.baseValue).apply {
            modulators.addAll(focusedParamData.modulators)
        }
    }
    
    var refreshCount by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Column(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
            key(focusedId, refreshCount) {
                tempParam.modulators.forEachIndexed { index, mod ->
                    ModulatorRow(
                        mod = mod,
                        onUpdate = { updatedMod ->
                            tempParam.modulators[index] = updatedMod
                            syncMixerParam(patch, focusedId, tempParam, onPatchUpdate)
                        },
                        onInteractionFinished = { syncMixerParam(patch, focusedId, tempParam, onPatchUpdate) },
                        onRemove = {
                            tempParam.modulators.removeAt(index)
                            refreshCount++
                            syncMixerParam(patch, focusedId, tempParam, onPatchUpdate)
                        }
                    )
                    HorizontalDivider(color = AppText.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                }

                ModulatorRow(
                    mod = null,
                    onUpdate = { newMod ->
                        tempParam.modulators.add(newMod)
                        refreshCount++
                        syncMixerParam(patch, focusedId, tempParam, onPatchUpdate)
                    },
                    onInteractionFinished = { syncMixerParam(patch, focusedId, tempParam, onPatchUpdate) },
                    onRemove = {}
                )
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

private fun syncMixerParam(patch: MixerPatch, id: String, param: ModulatableParameter, onUpdate: (MixerPatch) -> Unit) {
    val data = ModulatableParameterData(baseValue = param.baseValue, modulators = param.modulators.toList())
    val newPatch = when {
        id.startsWith("G") -> {
            val idx = (id.last().digitToIntOrNull() ?: 1) - 1
            val newSlots = patch.slots.toMutableList()
            newSlots[idx] = newSlots[idx].copy(gain = data)
            patch.copy(slots = newSlots)
        }
        id.startsWith("PN") -> {
            val idx = (id.last().digitToIntOrNull() ?: 1) - 1
            val newSlots = patch.slots.toMutableList()
            newSlots[idx] = newSlots[idx].copy(currentIndex = data)
            patch.copy(slots = newSlots)
        }
        id.startsWith("MA_") -> {
            val group = patch.mixerA
            val newGroup = when(id.removePrefix("MA_")) {
                "MODE" -> group.copy(mode = data)
                "BAL" -> group.copy(balance = data)
                "MIX" -> group.copy(mix = data)
                "GAIN" -> group.copy(gain = data)
                else -> group
            }
            patch.copy(mixerA = newGroup)
        }
        id.startsWith("MB_") -> {
            val group = patch.mixerB
            val newGroup = when(id.removePrefix("MB_")) {
                "MODE" -> group.copy(mode = data)
                "BAL" -> group.copy(balance = data)
                "MIX" -> group.copy(mix = data)
                "GAIN" -> group.copy(gain = data)
                else -> group
            }
            patch.copy(mixerB = newGroup)
        }
        id.startsWith("MF_") -> {
            val group = patch.mixerF
            val newGroup = when(id.removePrefix("MF_")) {
                "MODE" -> group.copy(mode = data)
                "BAL" -> group.copy(balance = data)
                "MIX" -> group.copy(mix = data)
                "GAIN" -> group.copy(gain = data)
                else -> group
            }
            patch.copy(mixerF = newGroup)
        }
        else -> patch
    }
    onUpdate(newPatch)
}

@Composable
fun StripPreview(monitorSource: String, patch: MixerPatch, mainRenderer: SpiralRenderer?) {
    AndroidView(
        factory = { ctx ->
            SpiralSurfaceView(ctx).apply {
                mainRenderer?.let { mr ->
                    (0..3).forEach { i ->
                        renderer.setSlotSource(i, mr.getSlotSource(i))
                    }
                }
                setMixerState(patch, monitorSource)
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            mainRenderer?.let { mr ->
                (0..3).forEach { i ->
                    view.renderer.setSlotSource(i, mr.getSlotSource(i))
                }
            }
            view.setMixerState(patch, monitorSource)
        }
    )
}

private fun updateGroup(patch: MixerPatch, group: String, data: MixerGroupData): MixerPatch {
    return when(group) {
        "A" -> patch.copy(mixerA = data)
        "B" -> patch.copy(mixerB = data)
        else -> patch.copy(mixerF = data)
    }
}
