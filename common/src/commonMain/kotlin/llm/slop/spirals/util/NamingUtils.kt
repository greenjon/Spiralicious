package llm.slop.spirals.util

object NamingUtils {
    fun generateCloneName(originalName: String, existingNames: List<String>): String {
        var cloneNumber = 1
        var newName: String
        do {
            newName = "${originalName}_clone_${cloneNumber++}"
        } while (existingNames.contains(newName))
        return newName
    }
}
