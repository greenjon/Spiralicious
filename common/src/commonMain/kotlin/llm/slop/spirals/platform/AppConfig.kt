package llm.slop.spirals.platform

import llm.slop.spirals.StartupMode
import llm.slop.spirals.navigation.NavLayer

expect class AppConfig {
    var startupMode: StartupMode
    fun saveNavStack(navStack: List<NavLayer>)
    fun loadNavStack(): List<NavLayer>?
}

expect fun getAppConfig(): AppConfig
