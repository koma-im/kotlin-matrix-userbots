package link.continuum.text2img

enum class HorizontalAlign {
    LEFT,
    CENTER
}

enum class VerticalAlign {
    TOP,
    CENTER
}


class Point (
        val x: Int,
        val y: Int
)

class Rect(
        val topLeft: Point,
        val bottomRight: Point
)

/**
 * calculate position of rectangle based on alignment
 */
fun posRect(
        anchor: Point,
        width: Int,
        height: Int,
        vAlign: VerticalAlign,
        hAlign: HorizontalAlign
): Rect {
    val left = when(hAlign) {
        HorizontalAlign.LEFT -> anchor.x
        HorizontalAlign.CENTER -> anchor.x - width /2
    }
    val top = when (vAlign) {
        VerticalAlign.TOP -> anchor.y
        VerticalAlign.CENTER -> anchor.y - height / 2
    }
    return Rect(Point( left, top), Point(left + width, top + height))
}
