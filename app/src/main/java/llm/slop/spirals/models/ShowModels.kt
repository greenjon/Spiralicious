package llm.slop.spirals.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ShowPatch(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val randomSetIds: List<String> = emptyList(),
    val prevTrigger: ModulatableParameterData = ModulatableParameterData(0.0f),
    val nextTrigger: ModulatableParameterData = ModulatableParameterData(0.0f),
    val randomTrigger: ModulatableParameterData = ModulatableParameterData(0.0f),
    val generateTrigger: ModulatableParameterData = ModulatableParameterData(0.0f)
)
