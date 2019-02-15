package link.continuum.avecho

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class TextKtTest {

    @Test
    fun unescapeUnicode() {
        assertEquals("\\u{-1}", unescapeUnicode("\\u{-1}"))
        assertEquals("\\u{}", unescapeUnicode("\\u{}"))
        assertEquals("\\u{g}", unescapeUnicode("\\u{g}"))
        assertEquals("\n", unescapeUnicode("\\u{a}"))
        assertEquals(" ", unescapeUnicode("\\u{20}"))
        assertEquals("\u0000", unescapeUnicode("\\u{0}"))
        assertEquals("❤", unescapeUnicode("\\u{2764}"))
        assertEquals("☺", unescapeUnicode("\\u{263a}"))
        assertEquals("☺❤", unescapeUnicode("\\u{263a}\\u{2764}"))

    }
}
