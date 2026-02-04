package llm.slop.spirals.util

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.spirals.models.PatchData

object PatchMapper {
    fun toJson(patchData: PatchData): String {
        return Json.encodeToString(patchData)
    }
}
