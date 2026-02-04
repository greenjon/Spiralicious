package llm.slop.spirals.display

import llm.slop.spirals.platform.getAppContext

actual fun readShaderResource(path: String): String {
    val assetPath = path.removePrefix("/")
    return getAppContext().assets.open(assetPath).bufferedReader().use { it.readText() }
}
