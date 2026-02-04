package llm.slop.spirals.display

actual fun readShaderResource(path: String): String {
    val resourcePath = if (path.startsWith("/")) path else "/$path"
    return object {}.javaClass.getResource(resourcePath)?.readText() 
        ?: throw RuntimeException("Shader not found: $resourcePath")
}
