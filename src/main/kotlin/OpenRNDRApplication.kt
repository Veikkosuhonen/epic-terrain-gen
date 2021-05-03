import org.openrndr.KEY_SPACEBAR
import org.openrndr.application
import org.openrndr.color.ColorHSLa
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.noise.*
import org.openrndr.extra.parameters.ColorParameter
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.extra.parameters.IntParameter
import org.openrndr.extra.parameters.Vector3Parameter
import org.openrndr.extras.imageFit.imageFit
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.math.IntVector2
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.panel.ControlManager
import org.openrndr.panel.elements.Button
import org.openrndr.panel.elements.button
import org.openrndr.panel.elements.clicked
import org.openrndr.panel.elements.layout
import org.openrndr.panel.layout
import kotlin.collections.Map
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

fun main() = application {
    configure {
        width = 1280
        height = 1280
    }

    val size = 720;

    val map = Map(size, 42)

    var doStep = true
    var changedParams = false

    val graphics = object {
        @Vector3Parameter("Sun direction")
        var SUN_DIR = Vector3(-1.0, 1.0, -2.0)
        @DoubleParameter("Sea level", 0.1, 0.9)
        var SEA_LEVEL = 0.6
        @DoubleParameter("Snow level", 0.1, 1.5)
        var SNOW_LEVEL = 1.0
        @DoubleParameter("Slope limit", 0.001, 0.1, 3)
        var SLOPE_LIMIT = 0.01
        @DoubleParameter("River limit", 1.0, 10.0)
        var RIVER_LIMIT = 6.0
        @DoubleParameter("Glacier limit", 0.5, 1.5)
        var GLACIER_LIMIT = 0.8
        @DoubleParameter("Wetness fallof", 0.1, 1.0)
        val WETNESS_FALLOF = 0.6
    }
    val colors = object {
        @ColorParameter("Water")
        var WATER = ColorRGBa.fromHex("#0084ff")
        @ColorParameter("Cliff")
        var CLIFF = ColorRGBa.fromHex("#807d79")
        @ColorParameter("Steep cliff")
        var DARK_CLIFF = ColorRGBa.fromHex("#494f44")
        @ColorParameter("Snow")
        var SNOW = ColorRGBa.fromHex("#f2f5f7")
        @ColorParameter("Sand")
        var SAND = ColorRGBa.fromHex("#dbd797")
        @ColorParameter("Forest")
        var FOREST = ColorRGBa.fromHex("#005e34")
    }
    val simulation = object {
        @DoubleParameter("Accumulation", 1e-9, 1e-7, 9)
        var ACCUMULATION = 3e-9
        @DoubleParameter("Erosion", 0.01, 0.2, 2)
        var EROSION = 0.1
        @IntParameter("Collapse Range", 1, 4)
        var COLLAPSE_RANGE = 2
        @IntParameter("Droplet iterations", 10, 200)
        var DROPLET_ITERS = 100
        @DoubleParameter("Capacity limit", 1e-6, 1e-4, 6)
        var CAPACITY_LIMIT = 1e-6
        @DoubleParameter("Slope limit", 1e-5, 1e-3, 5)
        var DIFF_LIMIT = 1e-5
        @DoubleParameter("Evaporation", 0.01, 0.5, 2)
        var EVAPORATION = 0.1
    }

    program {
        val gui = GUI()
        gui.add(graphics, "Graphics")
        gui.add(colors, "Colors")
        gui.add(simulation, "Simulation")
        gui.onChange { name, value ->
            changedParams = true
            map.SUN_DIR = graphics.SUN_DIR.normalized
            map.SEA_LEVEL = graphics.SEA_LEVEL
            map.SNOW_LEVEL = graphics.SNOW_LEVEL
            map.SLOPE_LIMIT = graphics.SLOPE_LIMIT
            map.RIVER_LIMIT = graphics.RIVER_LIMIT
            map.GLACIER_LIMIT = graphics.GLACIER_LIMIT
            map.WETNESS_FALLOF = graphics.WETNESS_FALLOF
            map.WATER = colors.WATER
            map.CLIFF = colors.CLIFF
            map.DARK_CLIFF = colors.DARK_CLIFF
            map.SNOW = colors.SNOW
            map.SAND = colors.SAND
            map.FOREST = colors.FOREST
            map.ACCUMULATION = simulation.ACCUMULATION
            map.EROSION = simulation.EROSION
            map.COLLAPSE_RANGE = simulation.COLLAPSE_RANGE
            map.DROPLET_ITERS = simulation.DROPLET_ITERS
            map.CAPACITY_LIMIT = simulation.CAPACITY_LIMIT
            map.DIFF_LIMIT = simulation.DIFF_LIMIT
            map.EVAPORATION = simulation.EVAPORATION
        }
        extend(gui) // use gui
        //extend(ScreenRecorder()) // writes to an mp4 file

        val heightMapColorBuffer = colorBuffer(size, size, type = ColorType.FLOAT32)
        val heightMapShadow = heightMapColorBuffer.shadow

        var start = System.currentTimeMillis();

        extend {
            if (doStep) {
                map.step(frameCount.toDouble() / 144.0) // sure 144.0 fps
            }
            if (changedParams || doStep) {
                map.map.forEachIndexed { i, it ->
                    it.forEachIndexed { j, _ ->
                        heightMapShadow[i, j] = map.getColor(i, j)
                    }
                }
                heightMapShadow.upload()
                changedParams = false
            }
            val now = System.currentTimeMillis();
            drawer.imageFit(heightMapColorBuffer, 0.0, 0.0, width.toDouble(), height.toDouble())
            drawer.rectangle(580.0, 30.0, 180.0, 30.0)
            drawer.fill = ColorRGBa.BLACK
            drawer.text("frame time: ${(now - start)} ms", 600.0, 50.0)
            start = now
        }

        //Spacebar to pause
        keyboard.keyDown.listen {
            if (it.key == KEY_SPACEBAR) {
                doStep = !doStep
            }
        }
    }
}