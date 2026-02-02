package llm.slop.spirals.models.mandala

/**
 * A snapshot of the original, GPU-centric data models.
 * This is preserved to aid in migrating the curated presets
 * from MandalaLibrary.kt to the new CPU-based rendering system.
 */

data class LegacyMandala4Arm(
    val a: Int,
    val b: Int,
    val c: Int,
    val d: Int
)

data class LegacyMandalaRatio(
    val id: String,
    val arms: LegacyMandala4Arm,
    // Other parameters from the old constructor would go here...
    // This is just a structural placeholder for now.
)
