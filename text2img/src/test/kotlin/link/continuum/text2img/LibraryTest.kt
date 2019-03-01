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
            val bg = ImageIO.read(File("cow.jpg"))
            val im = drawPicSay(bg, "hello ".repeat(10), Point(117, 75), 140,
                    Font(Font.SERIF, Font.ITALIC,16), color =  Color.RED,
                    horizontalAlign = HorizontalAlign.CENTER,
                    verticalAlign = VerticalAlign.CENTER,
                    format = Format.JPG
                    )
            File("cowsay.jpg").writeBytes(im)

    }

    @Ignore @Test fun testRun2() {
        val bg = ImageIO.read(File("wolf.jpg"))
        val im = drawPicSay(bg, "hello ".repeat(20), Point(232, 87), 400,
                Font(Font.SERIF, Font.ITALIC,21), color =  Color.BLACK,
                horizontalAlign = HorizontalAlign.CENTER,
                verticalAlign = VerticalAlign.TOP,
                format = Format.JPG
        )
        File("wolfsay.jpg").writeBytes(im)

    }
}
