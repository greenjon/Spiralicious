package llm.slop.spirals

import kotlinx.serialization.Serializable

@Serializable
enum class StartupMode {
    LAST_WORKSPACE,
    MIXER,
    SET,
    MANDALA,
    SHOW
}
