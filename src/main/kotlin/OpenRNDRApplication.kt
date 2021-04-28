import org.openrndr.application
import org.openrndr.color.ColorHSLa
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.noise.*
import org.openrndr.extras.imageFit.imageFit
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.math.IntVector2
import org.openrndr.math.Vector2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

fun main() = application {
    configure {
        width = 1280
        height = 1280
    }

    val size = 1024;

    val map = Map(size, 42)

    program {

        val heightMapColorBuffer = colorBuffer(size, size, type = ColorType.FLOAT32)
        val heightMapShadow = heightMapColorBuffer.shadow

        extend(ScreenRecorder()) // writes to an mp4 file
        var start = System.currentTimeMillis();
        extend {
            map.step(frameCount.toDouble() / 144.0) // sure 144.0 fps

            map.map.forEachIndexed { i, it ->
                it.forEachIndexed { j, _ ->
                    heightMapShadow[i, j] = map.getColor(i, j)
                }
            }
            heightMapShadow.upload()
            val now = System.currentTimeMillis();
            drawer.imageFit(heightMapColorBuffer, 0.0, 0.0, width.toDouble(), height.toDouble())
            drawer.rectangle(80.0, 80.0, 180.0, 30.0)
            drawer.fill = ColorRGBa.BLACK
            drawer.text("frame time: ${(now - start) / 1000} s", 100.0, 100.0)
            start = now
        }
    }
}