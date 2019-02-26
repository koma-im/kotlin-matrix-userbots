package link.continuum.picsay.util

fun partitionString(input: String, chars: List<Char>): Pair<String, String>? {
    val matchChar = { c: Char ->  chars.any {c == it} }
    val pos = input.indexOfFirst(matchChar)
    if (pos < 0) return null
    val prefix = input.substring(0, pos).trimEnd(matchChar)
    val suffix = input.substring(pos+1).trimStart(matchChar)
    return prefix to suffix
}
