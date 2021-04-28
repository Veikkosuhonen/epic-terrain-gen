import org.openrndr.color.ColorRGBa
import org.openrndr.color.mix
import org.openrndr.math.IntVector2
import org.openrndr.math.Vector3
import org.openrndr.math.clamp
import kotlin.math.*

class Map(val size: Int, val seed: Int) {

    // The fractal perlin noise maps have a lot of magic numbers. Im sorry.

    // grainMap is a noise map which determines the graininess of other maps
    val grainMap = Array(size) { i -> DoubleArray(size) { j -> noise(i, j, size, seed * 4, grain=0.4)} }

    val terrain_detail = 0.05
    // the height map
    val map = Array(size) { i -> DoubleArray(size) { j -> noise(i, j, size, seed, grain = grainMap[i][j] + terrain_detail) * (1.0 + (i.toDouble() + j.toDouble()) / (2 * size)) } }
    // hardnessMap determines how strongly erosion happens
    val hardnessMap = Array(size) { i -> DoubleArray(size) { j -> noise(j, i, size, seed * 2, grain = grainMap[j][i]) } }
    // structureMap determines how much the ground collapses (gets smoothened). Mostly very close to 1. Same seed as hardnessMap tho.
    val structureMap = Array(size) { i -> DoubleArray(size) { j -> fx1(noise(j, i, size, seed * 2, grain = grainMap[j][i]) * 3, 40) } }
    // wetnessMap represents how much water is in the soil
    val wetnessMap = Array(size) {DoubleArray(size)}

    // Scroll to the bottom for the simulation parameters (those which have been separated from the code)

    /**
     * Each frame, some actions are done on each position (pixel) of the map.
     */
    fun step(u_time: Double) {
        //SUN_DIR = Vector3(sin(u_time), cos(u_time), -3.0).normalized // rotate sun
        map.forEachIndexed { i, it ->
            it.forEachIndexed { j, _ ->
                erode(i, j)
                collapse(i, j)
                evaporate(i, j)
            }
        }
    }

    private fun erode(x: Int, y: Int) {
        var pos = IntVector2(x, y)
        if (map[pos.x][pos.y] < SEA_LEVEL) return // don't do erosion in the sea
        var mass = 0.0 // how much mass the droplet is carrying
        var capacity = 0.001 // loosely represents the size of the droplet
        // move the droplet DROPLET_ITERS times
        for (iter in 1..DROPLET_ITERS) {
            wetnessMap[pos.x][pos.y] += 0.001 // every droplet increases wetness by the same amount. Simple but might not be accurate
            val oldHeight = map[pos.x][pos.y]
            val oldPos = pos.copy()
            // check neighboring positions and move to the lowest one
            for (i in -1..1) {
                for (j in -1..1) {
                    val d0 = IntVector2(i, j) + pos
                    val inBounds = (d0.x in 0 until size) && (d0.y in 0 until size)
                    if (inBounds) {
                        pos = if (map[d0.x][d0.y] < map[pos.x][pos.y]) d0 else pos
                    }
                }
            }
            if (map[pos.x][pos.y] < SEA_LEVEL) { // instantly dissolves in the sea
                map[pos.x][pos.y] = map[pos.x][pos.y] + mass
                break
            }

            val softness = 1.0 - hardnessMap[pos.x][pos.y] + wetnessMap[x][y] * 0.01 // one magic number
            val diff = map[pos.x][pos.y] - oldHeight

            capacity *= clamp(wetnessMap[pos.x][pos.y] + (1.0 - RIVER_LIMIT), 0.9, 0.99) // capacity naturally decreases but less if the ground is wet. Two magic numbers
            capacity -= diff * ACCUMULATION // gains capacity depending on how steep the ground is.
            val gain = (capacity - mass) * softness * EROSION // gains or loses mass depending on how much capacity it has ana how soft the ground is
            mass += gain
            map[oldPos.x][oldPos.y] = map[oldPos.x][oldPos.y] - gain

            if (capacity < CAPACITY_LIMIT || abs(diff) < DIFF_LIMIT ||iter == DROPLET_ITERS) { // die and drop its mass in the spot
                map[pos.x][pos.y] = map[pos.x][pos.y] + mass
                break
            }
        }
    }

    /**
     * Averages the height with neighboring positions.
     * The strength of the averaging depends on structure and wetness in the position.
     * This is usually quite a small effect.
     */
    private fun collapse(x: Int, y: Int) {
        val pos = IntVector2(x, y)
        var heightAround = 0.0
        var neighbors = 0
        for (i in -COLLAPSE_RANGE..COLLAPSE_RANGE) {
            for (j in -COLLAPSE_RANGE..COLLAPSE_RANGE) {
                val d = IntVector2(i, j) + pos
                val inBounds = (d.x in 0 until size) && (d.y in 0 until size)
                if (inBounds) {
                    heightAround += map[d.x][d.y]
                    neighbors++
                }
            }
        }
        heightAround /= neighbors
        val ratio = structureMap[x][y] - wetnessMap[x][y] * 0.01
        map[x][y] = map[x][y] * ratio + heightAround * (1 - ratio)
    }

    /**
     * Reduces the wetness in the position.
     */
    private fun evaporate(x: Int, y: Int) {
        // evaporate a fraction of the wetness, less when its wet already
        val wetFactor = max(1.0 - wetnessMap[x][y] / RIVER_LIMIT, 0.1)
        val change = wetFactor * EVAPORATION * wetnessMap[x][y]
        wetnessMap[x][y] -= change
    }

    /**
     * Calculates and returns the 3d gradient vector in the position, which points down the slope.
     */
    private fun gradient(x: Int, y: Int): Vector3 {
        var vec = Vector3(0.0)
        for (i in -1..1) {
            for (j in -1..1) {
                val d = IntVector2(i, j) + IntVector2(x, y)
                val inBounds = (d.x in 0 until size) && (d.y in 0 until size)
                if (inBounds) {
                    val diff = map[x][y] - map[d.x][d.y]
                    val g = Vector3(i.toDouble(), j.toDouble(), diff)
                    vec += g * diff
                }
            }
        }
        return vec.normalized
    }

    /**
     * Returns the average wetness in and around the position.
     */
    private fun wetness(x: Int, y: Int): Double {
        var wetness = 0.0
        for (i in -1..1) {
            for (j in -1..1) {
                val d = IntVector2(i, j) + IntVector2(x, y)
                val inBounds = (d.x in 0 until size) && (d.y in 0 until size)
                if (inBounds) {
                    wetness += wetnessMap[d.x][d.y] * (1.0 - abs(i) * WETNESS_FALLOF) * (1.0 - abs(j) * WETNESS_FALLOF)
                }
            }
        }
        return wetness
    }

    /**
     * Determines and returns the color of the map in the position.
     * Conceptually and semantically pretty much the same as a main method in a fragment shader.
     */
    fun getColor(x: Int, y: Int): ColorRGBa {
        val height = map[x][y]
        if (height < SEA_LEVEL) {
            return WATER
        } //else if (height * 100 % 2 < 0.1) return LINE
        val gradient = gradient(x, y)
        val slope = abs(gradient.z)
        val col = getTerrainCol(height, slope, wetness(x, y))
        val lit = clamp(1.0 - gradient.dot(SUN_DIR), 0.5, 1.5)
        return col.shade(lit)
    }

    /**
     * Determines the terrain color (or terrain type) depending on height, slope and wetness
     */
    private fun getTerrainCol(height: Double, slope: Double, wetness: Double): ColorRGBa {
        if (height > SNOW_LEVEL && wetness > GLACIER_LIMIT) { return SNOW }
        if (wetness > RIVER_LIMIT) { return WATER }
        if (height > SEA_LEVEL + SAND_THICKNESS) {
            val s = (slope - SLOPE_LIMIT) / SLOPE_LIMIT
            val niceHeight = max((SNOW_LEVEL - height) / (SNOW_LEVEL - SEA_LEVEL), 0.0)
            val nice = niceHeight * (1.0 - s + wetness * 6)
            return mix(mix(CLIFF, DARK_CLIFF, nice), FOREST, nice)
        }
        return SAND
    }

    val WATER = ColorRGBa.fromHex("#0084ff")
    val CLIFF = ColorRGBa.fromHex("#807d79")
    val DARK_CLIFF = ColorRGBa.fromHex("#494f44")
    val SNOW = ColorRGBa.fromHex("#f2f5f7")
    val SAND = ColorRGBa.fromHex("#dbd797")
    val FOREST = ColorRGBa.fromHex("#005e34")
    val LINE = ColorRGBa.fromHex("#000000")

    var SUN_DIR = Vector3(-1.0, 1.0, -2.0).normalized
    val UP = Vector3(0.0, 0.0, -1.0).normalized

    // These params mostly affect the colors but not the simulation logic
    val SEA_LEVEL = 0.6
    val SNOW_LEVEL = 1.0
    val SAND_THICKNESS = 0.002

    val SLOPE_LIMIT = 0.01
    val RIVER_LIMIT = 6
    val GLACIER_LIMIT = 0.8
    val WETNESS_FALLOF = 0.6

    // These params affect the erosion process
    val ACCUMULATION = 3e-8
    val EROSION = 0.3
    val COLLAPSE_RANGE = 2
    val DROPLET_ITERS = 300
    val CAPACITY_LIMIT = 0.000001
    val DIFF_LIMIT = 0.00005

    val EVAPORATION = 0.1

    /**
     * Magic function, which turns most values into 1 except those that are close to 0.
     */
    fun fx1(x: Double, k: Int): Double {
        var l = x * x
        l = -1 / (k * l + 1) + 1
        return l
    }
}