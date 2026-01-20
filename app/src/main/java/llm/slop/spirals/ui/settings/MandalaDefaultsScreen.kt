package llm.slop.spirals.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import llm.slop.spirals.defaults.*
import llm.slop.spirals.models.STANDARD_BEAT_VALUES
import llm.slop.spirals.ui.theme.AppAccent
import llm.slop.spirals.ui.theme.AppBackground
import llm.slop.spirals.ui.theme.AppText

// Extension function to convert float to percentage
private fun Float.toPercent(): Int = (this * 100).toInt()

/**
 * Screen for configuring default values used by randomization throughout the app.
 * These settings are used when creating new mandalas or when specific parameters
 * are not explicitly configured in Random Sets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MandalaDefaultsScreen(
    defaultsConfig: DefaultsConfig,
    onClose: () -> Unit
) {
    var armDefaults by remember { mutableStateOf(defaultsConfig.getArmDefaults()) }
    var rotationDefaults by remember { mutableStateOf(defaultsConfig.getRotationDefaults()) }
    var hueOffsetDefaults by remember { mutableStateOf(defaultsConfig.getHueOffsetDefaults()) }
    var recipeDefaults by remember { mutableStateOf(defaultsConfig.getRecipeDefaults()) }
    var feedbackDefaults by remember { mutableStateOf(defaultsConfig.getFeedbackDefaults()) }
    
    val formatBeatValue = { value: Float ->
        when {
            value < 1 -> "1/${(1/value).toInt()}"
            else -> value.toInt().toString()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header with title and close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mandala Defaults",
                    style = MaterialTheme.typography.headlineMedium,
                    color = AppText
                )
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = AppAccent)
                ) {
                    Text("Save & Close")
                }
            }
            
            Text(
                text = "These settings control the default behavior when randomizing mandalas.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppText.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // Recipe Section
            ExpandableSection(
                title = "Recipe Selection",
                titleStyle = MaterialTheme.typography.titleMedium,
                initialExpanded = true
            ) {
                Text(
                    text = "These settings control how mandala shapes are selected during randomization.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppText.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // Prefer Favorites
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = recipeDefaults.preferFavorites,
                        onCheckedChange = { 
                            recipeDefaults = recipeDefaults.copy(preferFavorites = it)
                            defaultsConfig.saveRecipeDefaults(recipeDefaults)
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AppAccent
                        )
                    )
                    Text(
                        text = "Prefer favorite recipes when randomizing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppText,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                // Petal Count Range
                Text(
                    text = "Petal Count Range: ${recipeDefaults.minPetalCount}-${recipeDefaults.maxPetalCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
                
                RangeSlider(
                    value = recipeDefaults.minPetalCount.toFloat()..recipeDefaults.maxPetalCount.toFloat(),
                    onValueChange = { range ->
                        recipeDefaults = recipeDefaults.copy(
                            minPetalCount = range.start.toInt(),
                            maxPetalCount = range.endInclusive.toInt()
                        )
                        defaultsConfig.saveRecipeDefaults(recipeDefaults)
                    },
                    valueRange = 3f..24f,
                    steps = 20,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Auto Hue Sweep
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = recipeDefaults.autoHueSweep,
                        onCheckedChange = { 
                            recipeDefaults = recipeDefaults.copy(autoHueSweep = it)
                            defaultsConfig.saveRecipeDefaults(recipeDefaults)
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AppAccent
                        )
                    )
                    Text(
                        text = "Auto-set Hue Sweep based on petal count",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppText,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                // Reset to defaults button
                OutlinedButton(
                    onClick = { 
                        defaultsConfig.resetRecipeDefaults()
                        recipeDefaults = defaultsConfig.getRecipeDefaults()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Reset to Defaults")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Arms Section
            ExpandableSection(
                title = "Arm Parameters (L1-L4)",
                titleStyle = MaterialTheme.typography.titleMedium,
                initialExpanded = false
            ) {
                Text(
                    text = "These settings control arm behavior when randomizing mandalas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppText.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // Base Length Range
                Text(
                    text = "Base Length Range: ${armDefaults.baseLengthMin}%-${armDefaults.baseLengthMax}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                RangeSlider(
                    value = armDefaults.baseLengthMin.toFloat()..armDefaults.baseLengthMax.toFloat(),
                    onValueChange = { range ->
                        armDefaults = armDefaults.copy(
                            baseLengthMin = range.start.toInt(),
                            baseLengthMax = range.endInclusive.toInt()
                        )
                        defaultsConfig.saveArmDefaults(armDefaults)
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Movement Source Probabilities
                Text(
                    text = "Movement Source Probabilities",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Beat: ${(armDefaults.beatProbability * 100).toInt()}%  LFO: ${(armDefaults.lfoProbability * 100).toInt()}%  Random: ${(armDefaults.randomProbability * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppText.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                // Three sliders for the three CV sources
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Beat (${(armDefaults.beatProbability * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppText.copy(alpha = 0.7f)
                        )
                        Slider(
                            value = armDefaults.beatProbability,
                            onValueChange = { 
                                // Adjust all three probabilities directly
                                val newBeatProb = it.coerceIn(0f, 1f)
                                
                                // If Beat is 100%, set others to 0
                                val (newLfoProb, newRandomProb) = if (newBeatProb >= 0.999f) {
                                    Pair(0f, 0f)
                                } else {
                                    // Otherwise, redistribute remaining probability
                                    val remainingProb = (1.0f - newBeatProb).coerceIn(0f, 1f)
                                    val oldRatio = if (armDefaults.lfoProbability + armDefaults.randomProbability > 0) {
                                        armDefaults.lfoProbability / (armDefaults.lfoProbability + armDefaults.randomProbability)
                                    } else {
                                        0.5f // Default to equal distribution
                                    }
                                    
                                    Pair(
                                        remainingProb * oldRatio,
                                        remainingProb * (1 - oldRatio)
                                    )
                                }
                                
                                // Update and save
                                armDefaults = armDefaults.copy(
                                    beatProbability = newBeatProb,
                                    lfoProbability = newLfoProb,
                                    randomProbability = newRandomProb
                                )
                                defaultsConfig.saveArmDefaults(armDefaults)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = AppAccent,
                                activeTrackColor = AppAccent
                            )
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LFO (${(armDefaults.lfoProbability * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppText.copy(alpha = 0.7f)
                        )
                        Slider(
                            value = armDefaults.lfoProbability,
                            onValueChange = { 
                                // Adjust all three probabilities directly
                                val newLfoProb = it.coerceIn(0f, 1f)
                                
                                // If LFO is 100%, set others to 0
                                val (newBeatProb, newRandomProb) = if (newLfoProb >= 0.999f) {
                                    Pair(0f, 0f)
                                } else {
                                    // Otherwise, redistribute remaining probability
                                    val remainingProb = (1.0f - newLfoProb).coerceIn(0f, 1f)
                                    val oldRatio = if (armDefaults.beatProbability + armDefaults.randomProbability > 0) {
                                        armDefaults.beatProbability / (armDefaults.beatProbability + armDefaults.randomProbability)
                                    } else {
                                        0.5f // Default to equal distribution
                                    }
                                    
                                    Pair(
                                        remainingProb * oldRatio,
                                        remainingProb * (1 - oldRatio)
                                    )
                                }
                                
                                // Update and save
                                armDefaults = armDefaults.copy(
                                    beatProbability = newBeatProb,
                                    lfoProbability = newLfoProb,
                                    randomProbability = newRandomProb
                                )
                                defaultsConfig.saveArmDefaults(armDefaults)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = AppAccent,
                                activeTrackColor = AppAccent
                            )
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Random (${(armDefaults.randomProbability * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppText.copy(alpha = 0.7f)
                        )
                        Slider(
                            value = armDefaults.randomProbability,
                            onValueChange = { 
                                // Adjust all three probabilities directly
                                val newRandomProb = it.coerceIn(0f, 1f)
                                
                                // If Random is 100%, set others to 0
                                val (newBeatProb, newLfoProb) = if (newRandomProb >= 0.999f) {
                                    Pair(0f, 0f)
                                } else {
                                    // Otherwise, redistribute remaining probability
                                    val remainingProb = (1.0f - newRandomProb).coerceIn(0f, 1f)
                                    val oldRatio = if (armDefaults.beatProbability + armDefaults.lfoProbability > 0) {
                                        armDefaults.beatProbability / (armDefaults.beatProbability + armDefaults.lfoProbability)
                                    } else {
                                        0.5f // Default to equal distribution
                                    }
                                    
                                    Pair(
                                        remainingProb * oldRatio,
                                        remainingProb * (1 - oldRatio)
                                    )
                                }
                                
                                // Update and save
                                armDefaults = armDefaults.copy(
                                    beatProbability = newBeatProb,
                                    lfoProbability = newLfoProb,
                                    randomProbability = newRandomProb
                                )
                                defaultsConfig.saveArmDefaults(armDefaults)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = AppAccent,
                                activeTrackColor = AppAccent
                            )
                        )
                    }
                }
                
                // Beat Division Range
                val beatValues = STANDARD_BEAT_VALUES
                val minValueIndex = beatValues.indexOfFirst { it >= armDefaults.beatDivMin }.coerceAtLeast(0)
                val maxValueIndex = beatValues.indexOfLast { it <= armDefaults.beatDivMax }.coerceAtMost(beatValues.lastIndex)
                
                Text(
                    text = "Beat Division Range: ${formatBeatValue(beatValues[minValueIndex])}-${formatBeatValue(beatValues[maxValueIndex])}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                // Labels for min and max
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatBeatValue(beatValues[minValueIndex]),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppText.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatBeatValue(beatValues[maxValueIndex]),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppText.copy(alpha = 0.7f)
                    )
                }
                
                // Dual slider
                RangeSlider(
                    value = minValueIndex.toFloat()..maxValueIndex.toFloat(),
                    onValueChange = { range ->
                        val newMin = beatValues[range.start.toInt()]
                        val newMax = beatValues[range.endInclusive.toInt()]
                        armDefaults = armDefaults.copy(
                            beatDivMin = newMin,
                            beatDivMax = newMax
                        )
                        defaultsConfig.saveArmDefaults(armDefaults)
                    },
                    valueRange = 0f..(beatValues.size - 1).toFloat(),
                    steps = beatValues.size - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Waveform Probabilities
                Text(
                    text = "Waveform Probabilities",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                Text(
                    text = "Sine: ${(armDefaults.sineProbability * 100).toInt()}%  Triangle: ${(armDefaults.triangleProbability * 100).toInt()}%  Square: ${(armDefaults.squareProbability * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppText.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sine",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppText.copy(alpha = 0.7f)
                        )
                        Slider(
                            value = armDefaults.sineProbability,
                            onValueChange = { 
                                val total = it + armDefaults.triangleProbability + armDefaults.squareProbability
                                val normalizedValue = it / total
                                val normalizedTriangle = armDefaults.triangleProbability / total
                                val normalizedSquare = armDefaults.squareProbability / total
                                
                                armDefaults = armDefaults.copy(
                                    sineProbability = normalizedValue,
                                    triangleProbability = normalizedTriangle,
                                    squareProbability = normalizedSquare
                                )
                                defaultsConfig.saveArmDefaults(armDefaults)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = AppAccent,
                                activeTrackColor = AppAccent
                            )
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Triangle",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppText.copy(alpha = 0.7f)
                        )
                        Slider(
                            value = armDefaults.triangleProbability,
                            onValueChange = { 
                                val total = armDefaults.sineProbability + it + armDefaults.squareProbability
                                val normalizedSine = armDefaults.sineProbability / total
                                val normalizedValue = it / total
                                val normalizedSquare = armDefaults.squareProbability / total
                                
                                armDefaults = armDefaults.copy(
                                    sineProbability = normalizedSine,
                                    triangleProbability = normalizedValue,
                                    squareProbability = normalizedSquare
                                )
                                defaultsConfig.saveArmDefaults(armDefaults)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = AppAccent,
                                activeTrackColor = AppAccent
                            )
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Square",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppText.copy(alpha = 0.7f)
                        )
                        Slider(
                            value = armDefaults.squareProbability,
                            onValueChange = { 
                                val total = armDefaults.sineProbability + armDefaults.triangleProbability + it
                                val normalizedSine = armDefaults.sineProbability / total
                                val normalizedTriangle = armDefaults.triangleProbability / total
                                val normalizedValue = it / total
                                
                                armDefaults = armDefaults.copy(
                                    sineProbability = normalizedSine,
                                    triangleProbability = normalizedTriangle,
                                    squareProbability = normalizedValue
                                )
                                defaultsConfig.saveArmDefaults(armDefaults)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = AppAccent,
                                activeTrackColor = AppAccent
                            )
                        )
                    }
                }
                
                // Weight Range
                Text(
                    text = "Weight Range: ${armDefaults.weightMin}% to ${armDefaults.weightMax}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                RangeSlider(
                    value = armDefaults.weightMin.toFloat()..armDefaults.weightMax.toFloat(),
                    onValueChange = { range ->
                        armDefaults = armDefaults.copy(
                            weightMin = range.start.toInt(),
                            weightMax = range.endInclusive.toInt()
                        )
                        defaultsConfig.saveArmDefaults(armDefaults)
                    },
                    valueRange = -100f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // LFO Time Range
                Text(
                    text = "LFO Time Range: ${armDefaults.lfoTimeMin.toInt()}s-${armDefaults.lfoTimeMax.toInt()}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                RangeSlider(
                    value = armDefaults.lfoTimeMin..armDefaults.lfoTimeMax,
                    onValueChange = { range ->
                        armDefaults = armDefaults.copy(
                            lfoTimeMin = range.start,
                            lfoTimeMax = range.endInclusive
                        )
                        defaultsConfig.saveArmDefaults(armDefaults)
                    },
                    valueRange = 1f..600f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Reset to defaults button
                OutlinedButton(
                    onClick = { 
                        defaultsConfig.resetArmDefaults()
                        armDefaults = defaultsConfig.getArmDefaults()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Reset to Defaults")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Rotation Section
            ExpandableSection(
                title = "Rotation Parameters",
                titleStyle = MaterialTheme.typography.titleMedium,
                initialExpanded = false
            ) {
                Text(
                    text = "These settings control rotation when randomizing mandalas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppText.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // Direction Probabilities
                Text(
                    text = "Direction Probabilities",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Clockwise: ${(rotationDefaults.clockwiseProbability * 100).toInt()}%  Counter-clockwise: ${(rotationDefaults.counterClockwiseProbability * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppText.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Slider(
                    value = rotationDefaults.clockwiseProbability,
                    onValueChange = { 
                        rotationDefaults = rotationDefaults.copy(
                            clockwiseProbability = it,
                            counterClockwiseProbability = 1 - it
                        )
                        defaultsConfig.saveRotationDefaults(rotationDefaults)
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Speed Source Probabilities
                Text(
                    text = "Speed Source Probabilities",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Beat: ${(rotationDefaults.beatProbability * 100).toInt()}%  LFO: ${(rotationDefaults.lfoProbability * 100).toInt()}%  Random: ${(rotationDefaults.randomProbability * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppText.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                // Three sliders for the three CV sources
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Beat (${(rotationDefaults.beatProbability * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppText.copy(alpha = 0.7f)
                        )
                        Slider(
                            value = rotationDefaults.beatProbability,
                            onValueChange = { 
                                // Adjust all three probabilities directly
                                val newBeatProb = it.coerceIn(0f, 1f)
                                
                                // If Beat is 100%, set others to 0
                                val (newLfoProb, newRandomProb) = if (newBeatProb >= 0.999f) {
                                    Pair(0f, 0f)
                                } else {
                                    // Otherwise, redistribute remaining probability
                                    val remainingProb = (1.0f - newBeatProb).coerceIn(0f, 1f)
                                    val oldRatio = if (rotationDefaults.lfoProbability + rotationDefaults.randomProbability > 0) {
                                        rotationDefaults.lfoProbability / (rotationDefaults.lfoProbability + rotationDefaults.randomProbability)
                                    } else {
                                        0.5f // Default to equal distribution
                                    }
                                    
                                    Pair(
                                        remainingProb * oldRatio,
                                        remainingProb * (1 - oldRatio)
                                    )
                                }
                                
                                // Update and save
                                rotationDefaults = rotationDefaults.copy(
                                    beatProbability = newBeatProb,
                                    lfoProbability = newLfoProb,
                                    randomProbability = newRandomProb
                                )
                                defaultsConfig.saveRotationDefaults(rotationDefaults)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = AppAccent,
                                activeTrackColor = AppAccent
                            )
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LFO (${(rotationDefaults.lfoProbability * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppText.copy(alpha = 0.7f)
                        )
                        Slider(
                            value = rotationDefaults.lfoProbability,
                            onValueChange = { 
                                // Adjust all three probabilities directly
                                val newLfoProb = it.coerceIn(0f, 1f)
                                
                                // If LFO is 100%, set others to 0
                                val (newBeatProb, newRandomProb) = if (newLfoProb >= 0.999f) {
                                    Pair(0f, 0f)
                                } else {
                                    // Otherwise, redistribute remaining probability
                                    val remainingProb = (1.0f - newLfoProb).coerceIn(0f, 1f)
                                    val oldRatio = if (rotationDefaults.beatProbability + rotationDefaults.randomProbability > 0) {
                                        rotationDefaults.beatProbability / (rotationDefaults.beatProbability + rotationDefaults.randomProbability)
                                    } else {
                                        0.5f // Default to equal distribution
                                    }
                                    
                                    Pair(
                                        remainingProb * oldRatio,
                                        remainingProb * (1 - oldRatio)
                                    )
                                }
                                
                                // Update and save
                                rotationDefaults = rotationDefaults.copy(
                                    beatProbability = newBeatProb,
                                    lfoProbability = newLfoProb,
                                    randomProbability = newRandomProb
                                )
                                defaultsConfig.saveRotationDefaults(rotationDefaults)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = AppAccent,
                                activeTrackColor = AppAccent
                            )
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Random (${(rotationDefaults.randomProbability * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppText.copy(alpha = 0.7f)
                        )
                        Slider(
                            value = rotationDefaults.randomProbability,
                            onValueChange = { 
                                // Adjust all three probabilities directly
                                val newRandomProb = it.coerceIn(0f, 1f)
                                
                                // If Random is 100%, set others to 0
                                val (newBeatProb, newLfoProb) = if (newRandomProb >= 0.999f) {
                                    Pair(0f, 0f)
                                } else {
                                    // Otherwise, redistribute remaining probability
                                    val remainingProb = (1.0f - newRandomProb).coerceIn(0f, 1f)
                                    val oldRatio = if (rotationDefaults.beatProbability + rotationDefaults.lfoProbability > 0) {
                                        rotationDefaults.beatProbability / (rotationDefaults.beatProbability + rotationDefaults.lfoProbability)
                                    } else {
                                        0.5f // Default to equal distribution
                                    }
                                    
                                    Pair(
                                        remainingProb * oldRatio,
                                        remainingProb * (1 - oldRatio)
                                    )
                                }
                                
                                // Update and save
                                rotationDefaults = rotationDefaults.copy(
                                    beatProbability = newBeatProb,
                                    lfoProbability = newLfoProb,
                                    randomProbability = newRandomProb
                                )
                                defaultsConfig.saveRotationDefaults(rotationDefaults)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = AppAccent,
                                activeTrackColor = AppAccent
                            )
                        )
                    }
                }
                
                // Beat Division Range
                val beatValues = STANDARD_BEAT_VALUES
                val minValueIndex = beatValues.indexOfFirst { it >= rotationDefaults.beatDivMin }.coerceAtLeast(0)
                val maxValueIndex = beatValues.indexOfLast { it <= rotationDefaults.beatDivMax }.coerceAtMost(beatValues.lastIndex)
                
                Text(
                    text = "Beat Division Range: ${formatBeatValue(beatValues[minValueIndex])}-${formatBeatValue(beatValues[maxValueIndex])}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                // Labels for min and max
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatBeatValue(beatValues[minValueIndex]),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppText.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatBeatValue(beatValues[maxValueIndex]),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppText.copy(alpha = 0.7f)
                    )
                }
                
                // Dual slider
                RangeSlider(
                    value = minValueIndex.toFloat()..maxValueIndex.toFloat(),
                    onValueChange = { range ->
                        val newMin = beatValues[range.start.toInt()]
                        val newMax = beatValues[range.endInclusive.toInt()]
                        rotationDefaults = rotationDefaults.copy(
                            beatDivMin = newMin,
                            beatDivMax = newMax
                        )
                        defaultsConfig.saveRotationDefaults(rotationDefaults)
                    },
                    valueRange = 0f..(beatValues.size - 1).toFloat(),
                    steps = beatValues.size - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // LFO Time Range
                Text(
                    text = "LFO Time Range: ${rotationDefaults.lfoTimeMin.toInt()}s-${rotationDefaults.lfoTimeMax.toInt()}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                RangeSlider(
                    value = rotationDefaults.lfoTimeMin..rotationDefaults.lfoTimeMax,
                    onValueChange = { range ->
                        rotationDefaults = rotationDefaults.copy(
                            lfoTimeMin = range.start,
                            lfoTimeMax = range.endInclusive
                        )
                        defaultsConfig.saveRotationDefaults(rotationDefaults)
                    },
                    valueRange = 1f..600f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Reset to defaults button
                OutlinedButton(
                    onClick = { 
                        defaultsConfig.resetRotationDefaults()
                        rotationDefaults = defaultsConfig.getRotationDefaults()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Reset to Defaults")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Hue Offset Section
            ExpandableSection(
                title = "Color Parameters",
                titleStyle = MaterialTheme.typography.titleMedium,
                initialExpanded = false
            ) {
                Text(
                    text = "These settings control color cycling when randomizing mandalas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppText.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // Direction Probabilities
                Text(
                    text = "Direction Probabilities",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Forward: ${(hueOffsetDefaults.forwardProbability * 100).toInt()}%  Reverse: ${(hueOffsetDefaults.reverseProbability * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppText.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Slider(
                    value = hueOffsetDefaults.forwardProbability,
                    onValueChange = { 
                        hueOffsetDefaults = hueOffsetDefaults.copy(
                            forwardProbability = it,
                            reverseProbability = 1 - it
                        )
                        defaultsConfig.saveHueOffsetDefaults(hueOffsetDefaults)
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Speed Source Probabilities
                Text(
                    text = "Speed Source Probabilities",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Beat: ${(hueOffsetDefaults.beatProbability * 100).toInt()}%  LFO: ${(hueOffsetDefaults.lfoProbability * 100).toInt()}%  Random: ${(hueOffsetDefaults.randomProbability * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppText.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                // Three sliders for the three CV sources
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Beat (${(hueOffsetDefaults.beatProbability * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppText.copy(alpha = 0.7f)
                        )
                        Slider(
                            value = hueOffsetDefaults.beatProbability,
                            onValueChange = { 
                                // Adjust all three probabilities directly
                                val newBeatProb = it.coerceIn(0f, 1f)
                                
                                // If Beat is 100%, set others to 0
                                val (newLfoProb, newRandomProb) = if (newBeatProb >= 0.999f) {
                                    Pair(0f, 0f)
                                } else {
                                    // Otherwise, redistribute remaining probability
                                    val remainingProb = (1.0f - newBeatProb).coerceIn(0f, 1f)
                                    val oldRatio = if (hueOffsetDefaults.lfoProbability + hueOffsetDefaults.randomProbability > 0) {
                                        hueOffsetDefaults.lfoProbability / (hueOffsetDefaults.lfoProbability + hueOffsetDefaults.randomProbability)
                                    } else {
                                        0.5f // Default to equal distribution
                                    }
                                    
                                    Pair(
                                        remainingProb * oldRatio,
                                        remainingProb * (1 - oldRatio)
                                    )
                                }
                                
                                // Update and save
                                hueOffsetDefaults = hueOffsetDefaults.copy(
                                    beatProbability = newBeatProb,
                                    lfoProbability = newLfoProb,
                                    randomProbability = newRandomProb
                                )
                                defaultsConfig.saveHueOffsetDefaults(hueOffsetDefaults)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = AppAccent,
                                activeTrackColor = AppAccent
                            )
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LFO (${(hueOffsetDefaults.lfoProbability * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppText.copy(alpha = 0.7f)
                        )
                        Slider(
                            value = hueOffsetDefaults.lfoProbability,
                            onValueChange = { 
                                // Adjust all three probabilities directly
                                val newLfoProb = it.coerceIn(0f, 1f)
                                
                                // If LFO is 100%, set others to 0
                                val (newBeatProb, newRandomProb) = if (newLfoProb >= 0.999f) {
                                    Pair(0f, 0f)
                                } else {
                                    // Otherwise, redistribute remaining probability
                                    val remainingProb = (1.0f - newLfoProb).coerceIn(0f, 1f)
                                    val oldRatio = if (hueOffsetDefaults.beatProbability + hueOffsetDefaults.randomProbability > 0) {
                                        hueOffsetDefaults.beatProbability / (hueOffsetDefaults.beatProbability + hueOffsetDefaults.randomProbability)
                                    } else {
                                        0.5f // Default to equal distribution
                                    }
                                    
                                    Pair(
                                        remainingProb * oldRatio,
                                        remainingProb * (1 - oldRatio)
                                    )
                                }
                                
                                // Update and save
                                hueOffsetDefaults = hueOffsetDefaults.copy(
                                    beatProbability = newBeatProb,
                                    lfoProbability = newLfoProb,
                                    randomProbability = newRandomProb
                                )
                                defaultsConfig.saveHueOffsetDefaults(hueOffsetDefaults)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = AppAccent,
                                activeTrackColor = AppAccent
                            )
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Random (${(hueOffsetDefaults.randomProbability * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppText.copy(alpha = 0.7f)
                        )
                        Slider(
                            value = hueOffsetDefaults.randomProbability,
                            onValueChange = { 
                                // Adjust all three probabilities directly
                                val newRandomProb = it.coerceIn(0f, 1f)
                                
                                // If Random is 100%, set others to 0
                                val (newBeatProb, newLfoProb) = if (newRandomProb >= 0.999f) {
                                    Pair(0f, 0f)
                                } else {
                                    // Otherwise, redistribute remaining probability
                                    val remainingProb = (1.0f - newRandomProb).coerceIn(0f, 1f)
                                    val oldRatio = if (hueOffsetDefaults.beatProbability + hueOffsetDefaults.lfoProbability > 0) {
                                        hueOffsetDefaults.beatProbability / (hueOffsetDefaults.beatProbability + hueOffsetDefaults.lfoProbability)
                                    } else {
                                        0.5f // Default to equal distribution
                                    }
                                    
                                    Pair(
                                        remainingProb * oldRatio,
                                        remainingProb * (1 - oldRatio)
                                    )
                                }
                                
                                // Update and save
                                hueOffsetDefaults = hueOffsetDefaults.copy(
                                    beatProbability = newBeatProb,
                                    lfoProbability = newLfoProb,
                                    randomProbability = newRandomProb
                                )
                                defaultsConfig.saveHueOffsetDefaults(hueOffsetDefaults)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = AppAccent,
                                activeTrackColor = AppAccent
                            )
                        )
                    }
                }
                
                // Beat Division Range
                val beatValues = STANDARD_BEAT_VALUES
                val minValueIndex = beatValues.indexOfFirst { it >= hueOffsetDefaults.beatDivMin }.coerceAtLeast(0)
                val maxValueIndex = beatValues.indexOfLast { it <= hueOffsetDefaults.beatDivMax }.coerceAtMost(beatValues.lastIndex)
                
                Text(
                    text = "Beat Division Range: ${formatBeatValue(beatValues[minValueIndex])}-${formatBeatValue(beatValues[maxValueIndex])}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                // Labels for min and max
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatBeatValue(beatValues[minValueIndex]),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppText.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatBeatValue(beatValues[maxValueIndex]),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppText.copy(alpha = 0.7f)
                    )
                }
                
                // Dual slider
                RangeSlider(
                    value = minValueIndex.toFloat()..maxValueIndex.toFloat(),
                    onValueChange = { range ->
                        val newMin = beatValues[range.start.toInt()]
                        val newMax = beatValues[range.endInclusive.toInt()]
                        hueOffsetDefaults = hueOffsetDefaults.copy(
                            beatDivMin = newMin,
                            beatDivMax = newMax
                        )
                        defaultsConfig.saveHueOffsetDefaults(hueOffsetDefaults)
                    },
                    valueRange = 0f..(beatValues.size - 1).toFloat(),
                    steps = beatValues.size - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // LFO Time Range
                Text(
                    text = "LFO Time Range: ${hueOffsetDefaults.lfoTimeMin.toInt()}s-${hueOffsetDefaults.lfoTimeMax.toInt()}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                RangeSlider(
                    value = hueOffsetDefaults.lfoTimeMin..hueOffsetDefaults.lfoTimeMax,
                    onValueChange = { range ->
                        hueOffsetDefaults = hueOffsetDefaults.copy(
                            lfoTimeMin = range.start,
                            lfoTimeMax = range.endInclusive
                        )
                        defaultsConfig.saveHueOffsetDefaults(hueOffsetDefaults)
                    },
                    valueRange = 1f..600f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Reset to defaults button
                OutlinedButton(
                    onClick = { 
                        defaultsConfig.resetHueOffsetDefaults()
                        hueOffsetDefaults = defaultsConfig.getHueOffsetDefaults()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Reset to Defaults")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Feedback Section
            ExpandableSection(
                title = "Feedback Parameters",
                titleStyle = MaterialTheme.typography.titleMedium,
                initialExpanded = false
            ) {
                Text(
                    text = "These settings control feedback effects when randomizing mandalas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppText.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // FB Decay Range
                Text(
                    text = "FB Decay Range: ${feedbackDefaults.fbDecayMin.toPercent()}-${feedbackDefaults.fbDecayMax.toPercent()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                RangeSlider(
                    value = feedbackDefaults.fbDecayMin..feedbackDefaults.fbDecayMax,
                    onValueChange = { range ->
                        feedbackDefaults = feedbackDefaults.copy(
                            fbDecayMin = range.start,
                            fbDecayMax = range.endInclusive
                        )
                        defaultsConfig.saveFeedbackDefaults(feedbackDefaults)
                    },
                    valueRange = 0f..0.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // FB Gain Range
                Text(
                    text = "FB Gain Range: ${feedbackDefaults.fbGainMin.toPercent()}-${feedbackDefaults.fbGainMax.toPercent()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                RangeSlider(
                    value = feedbackDefaults.fbGainMin..feedbackDefaults.fbGainMax,
                    onValueChange = { range ->
                        feedbackDefaults = feedbackDefaults.copy(
                            fbGainMin = range.start,
                            fbGainMax = range.endInclusive
                        )
                        defaultsConfig.saveFeedbackDefaults(feedbackDefaults)
                    },
                    valueRange = 0.5f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // FB Zoom Range
                Text(
                    text = "FB Zoom Range: ${feedbackDefaults.fbZoomMin.toPercent()}-${feedbackDefaults.fbZoomMax.toPercent()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                RangeSlider(
                    value = feedbackDefaults.fbZoomMin..feedbackDefaults.fbZoomMax,
                    onValueChange = { range ->
                        feedbackDefaults = feedbackDefaults.copy(
                            fbZoomMin = range.start,
                            fbZoomMax = range.endInclusive
                        )
                        defaultsConfig.saveFeedbackDefaults(feedbackDefaults)
                    },
                    valueRange = 0.4f..0.6f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // FB Rotate Range
                Text(
                    text = "FB Rotate Range: ${feedbackDefaults.fbRotateMin.toPercent()}-${feedbackDefaults.fbRotateMax.toPercent()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                RangeSlider(
                    value = feedbackDefaults.fbRotateMin..feedbackDefaults.fbRotateMax,
                    onValueChange = { range ->
                        feedbackDefaults = feedbackDefaults.copy(
                            fbRotateMin = range.start,
                            fbRotateMax = range.endInclusive
                        )
                        defaultsConfig.saveFeedbackDefaults(feedbackDefaults)
                    },
                    valueRange = 0.4f..0.6f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // FB Shift X Range
                Text(
                    text = "FB Shift X Range: ${feedbackDefaults.fbShiftXMin.toPercent()}-${feedbackDefaults.fbShiftXMax.toPercent()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                RangeSlider(
                    value = feedbackDefaults.fbShiftXMin..feedbackDefaults.fbShiftXMax,
                    onValueChange = { range ->
                        feedbackDefaults = feedbackDefaults.copy(
                            fbShiftXMin = range.start,
                            fbShiftXMax = range.endInclusive
                        )
                        defaultsConfig.saveFeedbackDefaults(feedbackDefaults)
                    },
                    valueRange = -0.5f..0.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // FB Shift Y Range
                Text(
                    text = "FB Shift Y Range: ${feedbackDefaults.fbShiftYMin.toPercent()}-${feedbackDefaults.fbShiftYMax.toPercent()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                RangeSlider(
                    value = feedbackDefaults.fbShiftYMin..feedbackDefaults.fbShiftYMax,
                    onValueChange = { range ->
                        feedbackDefaults = feedbackDefaults.copy(
                            fbShiftYMin = range.start,
                            fbShiftYMax = range.endInclusive
                        )
                        defaultsConfig.saveFeedbackDefaults(feedbackDefaults)
                    },
                    valueRange = -0.5f..0.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // FB Blur Range
                Text(
                    text = "FB Blur Range: ${feedbackDefaults.fbBlurMin.toPercent()}-${feedbackDefaults.fbBlurMax.toPercent()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                RangeSlider(
                    value = feedbackDefaults.fbBlurMin..feedbackDefaults.fbBlurMax,
                    onValueChange = { range ->
                        feedbackDefaults = feedbackDefaults.copy(
                            fbBlurMin = range.start,
                            fbBlurMax = range.endInclusive
                        )
                        defaultsConfig.saveFeedbackDefaults(feedbackDefaults)
                    },
                    valueRange = 0f..0.3f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppAccent,
                        activeTrackColor = AppAccent
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Reset to defaults button
                OutlinedButton(
                    onClick = { 
                        defaultsConfig.resetFeedbackDefaults()
                        feedbackDefaults = defaultsConfig.getFeedbackDefaults()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Reset to Defaults")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
                            // Reset All button
                Button(
                    onClick = { 
                        defaultsConfig.resetAllDefaults()
                        armDefaults = defaultsConfig.getArmDefaults()
                        rotationDefaults = defaultsConfig.getRotationDefaults()
                        hueOffsetDefaults = defaultsConfig.getHueOffsetDefaults()
                        recipeDefaults = defaultsConfig.getRecipeDefaults()
                        feedbackDefaults = defaultsConfig.getFeedbackDefaults()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(containerColor = AppAccent)
                ) {
                    Text("Reset All to Factory Defaults")
                }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

