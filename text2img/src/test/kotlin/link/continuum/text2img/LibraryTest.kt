package link.continuum.text2img

import java.awt.Color
import java.awt.Font
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryTest {
    @Test fun testSomeLibraryMethod() {
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

    @Ignore @Test fun testRun() {
            val bg = ImageIO.read(File("b.jpg"))
            val im = drawPicSay(bg, "hello", 1024, 768, 600,
                    Font(Font.SANS_SERIF, Font.ITALIC,64), color =  Color.RED)
            File("o.png").writeBytes(im)

    }
}
