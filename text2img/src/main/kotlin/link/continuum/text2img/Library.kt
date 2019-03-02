package link.continuum.text2img

import mu.KotlinLogging
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private val logger = KotlinLogging.logger {}

/**
 * generate a picture containing a picture and a sentence
 * The top left of sentence is drawn at the given offset
 * words are wrapped to fit in the given width
 * The sentence can be positioned outside the picture
 * in that case a bigger picture will be generated to contain the provided picture
 */
fun drawPicSay(
        background: BufferedImage,
        text: String,
        offset: Point,
        widthLimit: Int,
        font: Font = Font(Font.SANS_SERIF, Font.PLAIN, 22),
        color: Color = Color.BLACK,
        verticalAlign: VerticalAlign = VerticalAlign.TOP,
        horizontalAlign: HorizontalAlign = HorizontalAlign.LEFT,
        format: Format = Format.PNG,
        /**
         * fill background with color when it's not transparent
         */
        backgroundColor: Color = Color.WHITE
): ByteArray {
    val textWrapper = TextWrapper(font = font, widthLimit = widthLimit)
    val (lines, textWidth) = textWrapper.splitWrap(text)
    val textHeight = lines.size * textWrapper.lineHeight
    // Coordinates use the top left corner of the image as origin
    val rect = posRect(offset, textWidth, textHeight, verticalAlign, horizontalAlign)
    // text may be positioned at negative coordinates
    val xMin = Math.min(0, rect.topLeft.x)
    val yMin = Math.min(0, rect.topLeft.y)
    val xMax = Math.max(background.width, rect.bottomRight.x)
    val yMax = Math.max(background.height, rect.bottomRight.y)
    val transparent = when (format) {
        Format.PNG -> true
        else -> false
    }
    val t = if (transparent) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
    val w = xMax - xMin
    val h = yMax - yMin
    val im = BufferedImage(w, h, t)
    val g2d = im.createGraphics()
    if (!transparent) g2d.fillRect(0, 0, w, h)
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_GASP)
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

    g2d.drawImage(background, 0 - xMin, 0-yMin,  null)

    g2d.font = font
    drawTextLines(lines, g2d, xOffset = rect.topLeft.x - xMin, yOffset = rect.topLeft.y - yMin, color = color)

    g2d.dispose()
    val out = ByteArrayOutputStream()
    ImageIO.write(im, format.toString(), out)
    return out.toByteArray()
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
        g2d.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
        g2d.fillRect(iconSize + gap, 0, textWidth, h)
        g2d.font = font
        drawTextLines(lines, g2d, xOffset = iconSize + gap, yOffset = 0, color = Color.BLACK)
        drawImageSquare(g2d, icon, iconSize)
        g2d.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(im, "png", out)
        return out.toByteArray()
    }


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
