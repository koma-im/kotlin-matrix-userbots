package link.continuum.avecho

import mu.KotlinLogging

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

sealed class Partition {
    class Parts(
            val before: String,
            val inner: String,
            val after: String
    ): Partition()
    class Unbreakable(val input: String): Partition()
}
