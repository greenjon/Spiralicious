package llm.slop.spirals

import kotlin.math.abs

data class MandalaParams(
    val omega1: Int = 1,
    val omega2: Int = -2,
    val omega3: Int = 4,
    val l1: Float = 0.4f,
    val l2: Float = 0.3f,
    val l3: Float = 0.2f,
    val phi1: Float = 0f,
    val phi2: Float = 0f,
    val phi3: Float = 0f,
    val thickness: Float = 0.01f
) {
    fun computeSymmetryInfo(): SymmetryInfo {
        val d12 = abs(omega1 - omega2)
        val d23 = abs(omega2 - omega3)
        val d31 = abs(omega3 - omega1)

        val commonGcd = gcd(d12, gcd(d23, d31))
        
        return SymmetryInfo(
            gcd = commonGcd,
            period = if (commonGcd != 0) (2.0 * Math.PI / commonGcd).toFloat() else (2.0 * Math.PI).toFloat(),
            symmetryOrder = commonGcd
        )
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val temp = y
            y = x % y
            x = temp
        }
        return abs(x)
    }
}

data class SymmetryInfo(
    val gcd: Int,
    val period: Float,
    val symmetryOrder: Int
)
