package link.continuum.text2img

import com.github.kittinunf.result.Result
import mu.KotlinLogging
import java.awt.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO

private val logger = KotlinLogging.logger {}

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
class TextRenderer(
        private val iconSize: Int = 51,
        private val font: Font = Font(Font.SANS_SERIF, Font.PLAIN, 22),
        private val gap: Int = 3,
        private val textWidthLim: Int = 338
) {
    private val textWrapper = TextWrapper(font = font, widthLimit = textWidthLim)

    fun generateImage(text: String, icon: BufferedImage): ByteArray {
        val (lines, textWidth) = textWrapper.splitWrap(text)
        val h = Math.max(lines.size * textWrapper.lineHeight, iconSize)
        val im = BufferedImage(iconSize + gap + textWidth, h,
                BufferedImage.TYPE_INT_ARGB)
        val g2d = im.createGraphics()
        g2d.color = Color.WHITE
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.fillRect(iconSize + gap, 0, textWidth, h)
        g2d.font = font
        drawTextLines(lines, g2d, xOffset = iconSize + gap, yOffset = 0)
        drawImageSquare(g2d, icon, iconSize)
        g2d.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(im, "png", out)
        return out.toByteArray()
    }

    /**
     * fit image in a square area
     */
    fun drawImageSquare(g2d: Graphics2D, im: BufferedImage, size: Int) {
        val sw = im.width
        val sh = im.height
        if (sw < 1 || sh < 1) {
            logger.error { "invalid image size" }
            return
        }
        val sr = sw.toFloat() / size
        val hr = sh.toFloat() / size
        val r = Math.max(sr, hr)
        val dwf = sw / r
        val dx = ((size - dwf) / 2).toInt()
        val dw = dwf.toInt()
        val dhf = sh / r
        val dy = ((size - dhf) / 2).toInt()
        val dh = dhf.toInt()
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        val sim = scaleDownQuality(im, dw, dh, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2d.drawImage(sim,
                dx, dy, dx + dw, dy + dh,
                0, 0, sim.width, sim.height,
                null)
    }

    fun drawTextLines(lines: List<String>, g2d: Graphics2D, xOffset: Int=0, yOffset: Int=0) {
        val fm = g2d.fontMetrics
        val ascent = fm.ascent
        g2d.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
        g2d.color = Color.BLACK
        for ((i, v) in lines.withIndex()) {
            val y = textWrapper.lineHeight * i + ascent + yOffset
            g2d.drawString(v, xOffset, y)
        }
    }
}

class TextWrapper(font: Font,
                  private val widthLimit: Int) {
    private val fontMetrics: FontMetrics
    val lineHeight: Int
    init {
        val graphics = BufferedImage(1,1, BufferedImage.TYPE_INT_ARGB).createGraphics()
        graphics.font = font
        fontMetrics = graphics.fontMetrics
        lineHeight = fontMetrics.height
    }

    fun splitWrap(text: String): Pair<List<String>, Int> {
        return wrapWords(splitText(text))
    }
    fun wrapWords(words: List<String>): Pair<List<String>, Int> {
        val lines = mutableListOf("")
        var longest = 0
        words.forEach { word ->
            val s = lines.last() + word
            val w =  fontMetrics.stringWidth(s)
            if (w < widthLimit) {
                longest = Math.max(longest, w)
                lines[lines.size - 1] = s
            } else {
                lines.add(word)
            }
        }
        if (longest == 0) {
            logger.warn { "got no maximum line length" }
            longest = widthLimit
        }
        return lines to longest
    }
}

/**
 * separate spaces and ideographic characters
 */
private fun splitText(text: String): List<String> {
    return separateChars(text) { c -> Character.isIdeographic(c) || Character.isSpaceChar(c)}
}

/**
 * separate characters
 */
private fun separateChars(text: String, sepChar: (Int)-> Boolean): List<String> {
    var lastSep = false
    val subStrs = mutableListOf<MutableList<Int>>()
    text.codePoints().forEach { c->
        if (sepChar(c)) {
            subStrs.add(mutableListOf(c))
            lastSep = true
        } else {
            if (lastSep || subStrs.isEmpty())
                subStrs.add(mutableListOf(c))
            else
                subStrs.last().add(c)
            lastSep = false
        }
    }
    return subStrs.map { codePoints ->
        val a = codePoints.toIntArray()
        String(a, 0, a.size)
    }
}
