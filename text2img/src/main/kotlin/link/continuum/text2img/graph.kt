package link.continuum.text2img

import com.github.kittinunf.result.Result
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Transparency
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.imageio.ImageIO

fun drawTextLines(
        lines: List<String>,
        g2d: Graphics2D,
        xOffset: Int=0,
        yOffset: Int=0,
        color: Color
) {
    val fm = g2d.fontMetrics
    val ascent = fm.ascent
    g2d.color = color
    val lineHeight = fm.height
    for ((i, v) in lines.withIndex()) {
        val y = lineHeight * i + ascent + yOffset
        g2d.drawString(v, xOffset, y)
    }
}


fun loadImageBytes(byteArray: ByteArray): Result<BufferedImage, IOException> {
    ImageIO.setUseCache(false)
    val ins = ByteArrayInputStream(byteArray)
    try {
        val image = ImageIO.read(ins)
        return Result.of { image }
    } catch (e: IOException) {
        return Result.error(e)
    }
}

fun scaleDownQuality(input: BufferedImage, targetW: Int, targetH: Int, hint: Any): BufferedImage {
    var ret = input
    val type = if (input.transparency == Transparency.OPAQUE) {
        BufferedImage.TYPE_INT_RGB
    } else {
        BufferedImage.TYPE_INT_ARGB
    }
    while (true) {
        val w = ret.getWidth(null)
        val h = ret.getHeight(null)
        if (w / 2 <= targetW && h / 2 <= targetH) {
            break
        } else {
            val w1 = w/2
            val h1 = h/2
            val tmp = BufferedImage(w1, h1, type)
            val g2 = tmp.createGraphics()
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint)
            g2.drawImage(ret, 0, 0, w1, h1, null)
            g2.dispose()
            ret = tmp
        }
    }
    return ret
}
