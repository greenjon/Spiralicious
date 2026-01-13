package llm.slop.spirals

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class StartupMode {
    LAST_WORKSPACE,
    MIXER,
    SET,
    MANDALA,
    SHOW
}

class AppConfig(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("app_config", Context.MODE_PRIVATE)

    var startupMode: StartupMode
        get() = prefs.getString("startup_mode", StartupMode.LAST_WORKSPACE.name)?.let {
            try {
                StartupMode.valueOf(it)
            } catch (_: IllegalArgumentException) {
                StartupMode.LAST_WORKSPACE
            }
        } ?: StartupMode.LAST_WORKSPACE
        set(value) = prefs.edit { putString("startup_mode", value.name) }

    var lastNavStackJson: String?
        get() = prefs.getString("last_nav_stack", null)
        set(value) = prefs.edit { putString("last_nav_stack", value) }

    fun saveNavStack(stack: List<NavLayer>) {
        try {
            lastNavStackJson = Json.encodeToString(stack)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadNavStack(): List<NavLayer>? {
        val json = lastNavStackJson ?: return null
        return try {
            Json.decodeFromString<List<NavLayer>>(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
