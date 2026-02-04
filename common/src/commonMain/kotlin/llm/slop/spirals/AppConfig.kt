package llm.slop.spirals

// import llm.slop.spirals.navigation.NavLayer

// expect class AppConfig {
//     var startupMode: StartupMode
//     fun saveNavStack(navStack: List<NavLayer>)
//     fun loadNavStack(): List<NavLayer>?
// }

enum class StartupMode {
    LAST_WORKSPACE,
    MIXER,
    SET,
    MANDALA,
    SHOW
}
