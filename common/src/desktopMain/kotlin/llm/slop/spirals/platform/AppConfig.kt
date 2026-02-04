package llm.slop.spirals.platform

import llm.slop.spirals.StartupMode
import llm.slop.spirals.navigation.NavLayer

actual class AppConfig {
    actual var startupMode: StartupMode = StartupMode.LAST_WORKSPACE
    actual fun saveNavStack(navStack: List<NavLayer>) {
        // TODO: Implement for Desktop
    }
    actual fun loadNavStack(): List<NavLayer>? {
        // TODO: Implement for Desktop
        return null
    }
}

private val appConfig = AppConfig()

actual fun getAppConfig(): AppConfig = appConfig
