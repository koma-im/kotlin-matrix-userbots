package link.continuum.text2img


import mu.KotlinLogging
import java.awt.Font
import java.awt.FontMetrics
import java.awt.image.BufferedImage

private val logger = KotlinLogging.logger {}

/**
 * try to unescape unicode escape of the form \u{XXXX}
 * where XXXX is a code point in hexadecimal form
 * leaves any portion untouched if it can't be converted
 */
fun unescapeUnicode(escaped: String): String {
    val leftDelimiter = "\\u{"
    val rightDelimiter = "}"
    val sb = StringBuilder()
    var input = escaped
    loop@ while (true) {
        val partition = partStr3(input, leftDelimiter, rightDelimiter)
        when (partition) {
            is Partition.Parts -> {
                sb.append(partition.before)
                val unescape = strCodeStr(partition.inner)
                if (unescape != null) {
                    sb.append(unescape)
                } else {
                    sb.append(leftDelimiter)
                    sb.append(partition.inner)
                    sb.append(rightDelimiter)
                }
                input = partition.after
            }
            is Partition.Unbreakable -> {
                sb.append(partition.input)
                break@loop
            }
        }
    }
    return sb.toString()
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

/**
 * parse string as code point and convert to string
 */
private fun strCodeStr(input: String): String? {
    val code = input.toIntOrNull(radix = 16)?:return null
    val a = listOf(code).toIntArray()
    try {
        return String(a, 0, a.size)
    } catch (e: Exception) {
        logger.warn { "invalid string code poing $input, $e" }
        return null
    }
}

/**
 *
 */
private fun partStr3(input: String,
                     leftDelimiter: String,
                     rightDelimiter: String
): Partition? {
    val indexLeft = input.indexOf(leftDelimiter)
    if (indexLeft == -1) {
        return Partition.Unbreakable(input)
    }
    val rest = input.substring(indexLeft + leftDelimiter.length)
    val indexRight = rest.indexOf(rightDelimiter)
    if (indexRight == -1) {
        return Partition.Unbreakable(input)
    }
    return Partition.Parts(input.substring(0, indexLeft),
            rest.substring(0, indexRight),
            rest.substring(indexRight+rightDelimiter.length)
    )
}

private sealed class Partition {
    class Parts(
            val before: String,
            val inner: String,
            val after: String
    ): Partition()
    class Unbreakable(val input: String): Partition()
}
