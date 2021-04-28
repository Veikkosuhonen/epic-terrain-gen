import org.openrndr.extra.noise.perlinHermite
import org.openrndr.extra.noise.random
import org.openrndr.extra.noise.simplex

fun noise(x0: Int, y0: Int, n: Int, seed: Int, grain: Double = 0.4): Double {
    val x = x0.toDouble() / n - 39.0
    val y = y0.toDouble() / n + 72.0

    var height = 0.0
    var max = 0.0
    val octaves = 32
    var mag = 1.0
    for (i in 1..octaves) {
        val offset = i * 10.0
        val scale = i * i
        val x1 = x * scale + offset
        val y1 = y * scale - offset
        height += perlinHermite(seed, x1, y1) * mag
        max += 0.4 * mag
        mag *= grain
    }
    return (height + max) / (2 * max)
}