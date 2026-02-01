package llm.slop.spirals

import llm.slop.spirals.navigation.NavLayer

actual class AppConfig {
    actual var startupMode: StartupMode = StartupMode.LAST_WORKSPACE
    private var stack: List<NavLayer>? = null

    actual fun saveNavStack(navStack: List<NavLayer>) {
        stack = navStack
    }

    actual fun loadNavStack(): List<NavLayer>? {
        return stack
    }
}
