package llm.slop.spirals.models

// This data class holds the state for a modulatable parameter.
// We keep it separate so it can be easily serialized to JSON later.
data class ModulatableParameterData(
    val baseValue: Float = 0.0f,
    val modSource: String = "",
    val modAmount: Float = 0.0f
)
