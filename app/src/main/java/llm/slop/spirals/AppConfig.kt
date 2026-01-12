package llm.slop.spirals

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class StartupMode {
    LAST_WORKSPACE,
    MIXER,
    SET,
    MANDALA
}

class AppConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)

    var startupMode: StartupMode
        get() = StartupMode.valueOf(prefs.getString("startup_mode", StartupMode.LAST_WORKSPACE.name)!!)
        set(value) = prefs.edit().putString("startup_mode", value.name).apply()

    var lastNavStackJson: String?
        get() = prefs.getString("last_nav_stack", null)
        set(value) = prefs.edit().putString("last_nav_stack", value).apply()

    fun saveNavStack(stack: List<NavLayer>) {
        lastNavStackJson = Json.encodeToString(stack)
    }

    fun loadNavStack(): List<NavLayer>? {
        val json = lastNavStackJson ?: return null
        return try {
            Json.decodeFromString<List<NavLayer>>(json)
        } catch (e: Exception) {
            null
        }
    }
}
