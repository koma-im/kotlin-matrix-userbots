package link.continuum.avecho

import com.github.kittinunf.result.Result
import mu.KotlinLogging
import java.awt.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

private val logger = KotlinLogging.logger {}

fun loadImageFile(path: String): Image {
    ImageIO.getCacheDirectory()
    return ImageIO.read(File(path))
}

fun loadImageBytes(byteArray: ByteArray): Result<Image, IOException> {
    ImageIO.setUseCache(false)
    val ins = ByteArrayInputStream(byteArray)
    try {
        val image = ImageIO.read(ins)
        return Result.of { image }
    } catch (e: IOException) {
        return Result.error(e)
    }
}

class TextRenderer(
        private val iconSize: Int = 42,
        private val font: Font = Font(Font.SANS_SERIF, Font.PLAIN, 18),
        private val gap: Int = 3,
        private val textWidthLim: Int = 276
) {
    private val textWrapper = TextWrapper(font = font, widthLimit = textWidthLim)

    fun generateImage(text: String, icon: Image): ByteArray {
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
    fun drawImageSquare(g2d: Graphics2D, im: Image, size: Int) {
        val sw = im.getWidth(null)
        val sh = im.getHeight(null)
        if (sw < 1 || sh < 1) {
            logger.error { "invalid image size" }
            return
        }
        val sr = sw.toFloat() / size
        val hr = sh.toFloat() / size
        val r = Math.max(sr, hr)
        val dw = sw / r
        val dx = (size - dw) / 2
        val dx1 = dx + dw
        val dh = sh / r
        val dy = (size - dh) / 2
        val dy1 = dy + dh
        g2d.drawImage(im,
                dx.toInt(), dy.toInt(), dx1.toInt(), dy1.toInt(),
                0, 0, sw, sh,
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
