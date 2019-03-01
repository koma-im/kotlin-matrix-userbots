package link.continuum.picsay

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.map
import link.continuum.text2img.*
import java.awt.Color
import java.awt.Font
import java.io.File
import javax.imageio.ImageIO



data class Template(
        val path: String,
        /**
         * position
         */
        val x: Int,
        val y: Int,
        /**
         * width limit of text
         */
        val width: Int,
        val fontSize: Int,
        val fontName: String = Font.SANS_SERIF,
        val fontStyle: Int = Font.PLAIN,
        val color: List<Int> = listOf(0, 0, 0),
        val hAlign: HorizontalAlign = HorizontalAlign.LEFT,
        val vAlign: VerticalAlign = VerticalAlign.TOP,
        val format: Format = Format.JPG
) {
    /**
     * make default values work with moshi
     */
    @SuppressWarnings("unused")
    private constructor(): this("", 1,2,3,4)

    fun render(input: String): Result<ByteArray, Exception> {
        val c = Color(color[0], color[1], color[2])
        return Result.of(ImageIO.read(File(path)))
                .map {
                    drawPicSay(it, input, Point(x, y), width,
                            Font(fontName, fontStyle, fontSize), color =  c,
                            horizontalAlign = hAlign,
                            verticalAlign = vAlign,
                            format = format
                    )
                }
    }
}

