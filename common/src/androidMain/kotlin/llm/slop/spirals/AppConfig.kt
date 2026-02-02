package llm.slop.spirals

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
// import androidx.core.content.edit
// import kotlinx.serialization.builtins.ListSerializer
// import kotlinx.serialization.encodeToString
// import kotlinx.serialization.json.Json
// import llm.slop.spirals.navigation.NavLayer

actual class AppConfig(private val context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("app_config", Context.MODE_PRIVATE)

    actual var startupMode: StartupMode
        get() = StartupMode.LAST_WORKSPACE // STUB
        set(value) {}
        // get() = prefs.getString("startup_mode", StartupMode.LAST_WORKSPACE.name)?.let {
        //     try {
        //         StartupMode.valueOf(it)
        //     } catch (_: IllegalArgumentException) {
        //         StartupMode.LAST_WORKSPACE
        //     }
        // } ?: StartupMode.LAST_WORKSPACE
        // set(value) = prefs.edit { putString("startup_mode", value.name) }

    var lastNavStackJson: String?
        get() = prefs.getString("last_nav_stack", null)
        set(value) {} //prefs.edit { putString("last_nav_stack", value) }

    actual fun saveNavStack(navStack: List<*>) {
        // try {
        //     lastNavStackJson = Json.encodeToString(ListSerializer(NavLayer.serializer()), navStack)
        // } catch (e: Exception) {
        //     Log.e("AppConfig", "Failed to serialize nav stack: ${e.message}")
        //     lastNavStackJson = null
        // }
    }

    actual fun loadNavStack(): List<*>? {
        return null
        // val json = lastNavStackJson ?: return null
        // return try {
        //     Json.decodeFromString(ListSerializer(NavLayer.serializer()), json)
        // } catch (e: Exception) {
        //     Log.e("AppConfig", "Failed to deserialize nav stack: ${e.message}")
        //     null
        // }
    }
}
