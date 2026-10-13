package dev.ezaz.manazone.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.ezaz.manazone.ui.theme.DigitStyle

/** Look of the countdown digits. [weight] (thickness) applies to every style. */
enum class ClockStyle(val label: String) {
    /** Soft curved digits in one flat color with no shading. */
    FLAT("Flat"),

    /** The same curved digits, inflated with a lit top edge and a darker underside, like modelling clay. */
    CLAY("Clay"),
    CLASSIC("Classic"),
    MONO("Mono"),
    SEGMENT("Digital"),

    /** Seven-segment display with the unlit segments faintly visible, like an LCD. */
    LCD("LCD");

    val isSegment: Boolean get() = this == SEGMENT || this == LCD || this == CLAY || this == FLAT

    companion object {
        val Default = FLAT

        fun from(name: String?): ClockStyle = entries.firstOrNull { it.name == name } ?: Default
    }
}

/**
 * Draws [text] (digits and colons) centered in the available space, as large as fits within
 * [widthFraction] of the width and [heightFraction] of the height. [contentModifier] is applied to
 * the glyphs themselves (test tags, semantics).
 */
@Composable
fun ClockDisplay(
    text: String,
    style: ClockStyle,
    color: Color,
    weight: Int,
    modifier: Modifier = Modifier.fillMaxSize(),
    widthFraction: Float = 0.88f,
    heightFraction: Float = 0.28f,
    contentModifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (style.isSegment) {
            val units = segmentWidthUnits(text)
            val h = min(maxWidth * widthFraction / units, maxHeight * heightFraction * 0.8f)
            Canvas(contentModifier.size(h * units, h)) {
                when (style) {
                    ClockStyle.CLAY -> drawCurved(text, color, weight, shaded = true)
                    ClockStyle.FLAT -> drawCurved(text, color, weight, shaded = false)
                    else -> drawSegments(text, color, weight, ghost = style == ClockStyle.LCD)
                }
            }
        } else {
            val charWidthEm = if (style == ClockStyle.MONO) 0.62f else 0.56f
            val digitSize: Dp = min(maxWidth * widthFraction / (text.length * charWidthEm), maxHeight * heightFraction)
            val fontSize = with(LocalDensity.current) { digitSize.toSp() }
            Text(
                text,
                style = DigitStyle.copy(
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    color = color,
                    fontWeight = FontWeight(weight),
                    fontFamily = if (style == ClockStyle.MONO) FontFamily.Monospace else FontFamily.SansSerif,
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = contentModifier,
            )
        }
    }
}

// Layout in units of digit height.
private const val DIGIT_W = 0.58f
private const val COLON_W = 0.22f
private const val GAP = 0.16f

private fun segmentWidthUnits(text: String): Float {
    if (text.isEmpty()) return 1f
    return text.sumOf { (if (it == ':') COLON_W else DIGIT_W).toDouble() }.toFloat() + GAP * (text.length - 1)
}

/** Segments a..g as bits: a top, b top-right, c bottom-right, d bottom, e bottom-left, f top-left, g middle. */
private val DIGIT_SEGMENTS = listOf("abcdef", "bc", "abdeg", "abcdg", "bcfg", "acdfg", "acdefg", "abc", "abcdefg", "abcdfg")

private fun DrawScope.drawSegments(text: String, color: Color, weight: Int, ghost: Boolean) {
    val h = size.height
    val t = h * (0.05f + 0.11f * (weight.coerceIn(100, 900) - 100) / 800f)
    val dim = color.copy(alpha = 0.12f)
    var x = 0f
    for (ch in text) {
        if (ch == ':') {
            val cx = x + COLON_W * h / 2
            drawCircle(color, t * 0.6f, Offset(cx, h * 0.32f))
            drawCircle(color, t * 0.6f, Offset(cx, h * 0.68f))
            x += (COLON_W + GAP) * h
        } else {
            val lit = ch.digitToIntOrNull()?.let { DIGIT_SEGMENTS[it] } ?: ""
            drawDigit(x, DIGIT_W * h, h, t, lit, color, if (ghost) dim else null)
            x += (DIGIT_W + GAP) * h
        }
    }
}

private fun DrawScope.drawDigit(x: Float, w: Float, h: Float, t: Float, lit: String, color: Color, ghost: Color?) {
    for ((name, line) in digitSegmentLines(x, w, h, t, t * 0.9f)) {
        val c = if (name in lit) color else ghost ?: continue
        drawLine(c, line.first, line.second, strokeWidth = t, cap = StrokeCap.Round)
    }
}

/** Centre lines of segments a..g for one digit cell; [g] is the inset that leaves room at corners. */
private fun digitSegmentLines(x: Float, w: Float, h: Float, t: Float, g: Float): List<Pair<Char, Pair<Offset, Offset>>> {
    val half = t / 2
    val xl = x + half
    val xr = x + w - half
    val yt = half
    val ym = h / 2
    val yb = h - half
    return listOf(
        'a' to (Offset(xl + g, yt) to Offset(xr - g, yt)),
        'g' to (Offset(xl + g, ym) to Offset(xr - g, ym)),
        'd' to (Offset(xl + g, yb) to Offset(xr - g, yb)),
        'f' to (Offset(xl, yt + g) to Offset(xl, ym - g)),
        'b' to (Offset(xr, yt + g) to Offset(xr, ym - g)),
        'e' to (Offset(xl, ym + g) to Offset(xl, yb - g)),
        'c' to (Offset(xr, ym + g) to Offset(xr, yb - g)),
    )
}

/**
 * Clay digits: fat rounded tubes whose corners fuse into one soft form. Shading is built in layers
 * across the whole text (underside, body, diffuse light, specular) so overlapping tubes read as one
 * piece of clay instead of separate bars.
 */
private fun DrawScope.drawCurved(text: String, color: Color, weight: Int, shaded: Boolean) {
    val h = size.height
    val t = h * (0.10f + 0.07f * (weight.coerceIn(100, 900) - 100) / 800f)
    val shade = lerp(color, Color.Black, 0.5f)
    val lightSoft = lerp(color, Color.White, 0.5f).copy(alpha = 0.11f)
    val specSoft = Color.White.copy(alpha = 0.14f)

    val glyphs = Path()
    val balls = ArrayList<Offset>()
    var x = 0f
    for (ch in text) {
        if (ch == ':') {
            val cx = x + COLON_W * h / 2
            balls += Offset(cx, h * 0.32f)
            balls += Offset(cx, h * 0.68f)
            x += (COLON_W + GAP) * h
        } else {
            ch.digitToIntOrNull()?.let { addClayDigit(glyphs, it, x + t / 2, x + DIGIT_W * h - t / 2, t / 2, h - t / 2) }
            x += (DIGIT_W + GAP) * h
        }
    }

    fun layer(width: Float, dx: Float, dy: Float, c: Color) {
        translate(dx * t, dy * t) {
            drawPath(glyphs, c, style = Stroke(t * width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            for (p in balls) drawCircle(c, t * width * 0.62f, p)
        }
    }
    if (!shaded) {
        layer(1f, 0f, 0f, color)
        return
    }
    layer(1.0f, 0.10f, 0.14f, shade)
    layer(0.94f, 0f, 0f, color)
    // Highlights are stacks of thin translucent layers so they fade out instead of ending in a hard line.
    for (i in 0 until 5) {
        val f = i / 4f
        layer(0.78f - 0.36f * f, -0.10f * (0.4f + 0.6f * f), -0.13f * (0.4f + 0.6f * f), lightSoft)
    }
    for (i in 0 until 4) {
        val f = i / 3f
        layer(0.34f - 0.20f * f, -0.16f, -0.20f, specSoft)
    }
}

/** Adds the centre line of digit [d] to [path], inside the box [xl]..[xr] x [yt]..[yb], as smooth curves. */
private fun addClayDigit(path: Path, d: Int, xl: Float, xr: Float, yt: Float, yb: Float) {
    val w = xr - xl
    val h = yb - yt
    val ym = yt + h / 2
    val cx = xl + w / 2
    when (d) {
        0 -> path.addRoundRect(RoundRect(xl, yt, xr, yb, CornerRadius(w / 2)))
        1 -> {
            path.moveTo(xl + 0.1f * w, yt + 0.2f * h)
            path.lineTo(cx + 0.15f * w, yt)
            path.lineTo(cx + 0.15f * w, yb)
        }
        2 -> {
            path.arcTo(Rect(xl, yt, xr, yt + w), 180f, 215f, true)
            path.lineTo(xl, yb)
            path.lineTo(xr, yb)
        }
        3 -> {
            path.arcTo(Rect(xl + 0.04f * w, yt, xr, ym), 200f, 250f, true)
            path.arcTo(Rect(xl, ym, xr, yb), 270f, 250f, false)
        }
        4 -> {
            path.moveTo(xr - 0.28f * w, yb)
            path.lineTo(xr - 0.28f * w, yt)
            path.lineTo(xl, yt + 0.66f * h)
            path.lineTo(xr, yt + 0.66f * h)
        }
        5 -> {
            path.moveTo(xr, yt)
            path.lineTo(xl + 0.1f * w, yt)
            path.lineTo(xl + 0.05f * w, yt + 0.45f * h)
            path.arcTo(Rect(xl, yt + 0.4f * h, xr, yb), 235f, 285f, false)
        }
        6 -> {
            path.addOval(Rect(xl, yt + 0.4f * h, xr, yb))
            path.moveTo(xr - 0.05f * w, yt + 0.08f * h)
            path.cubicTo(xr - 0.3f * w, yt, xl, yt + 0.2f * h, xl, yt + 0.6f * h)
        }
        7 -> {
            path.moveTo(xl, yt)
            path.lineTo(xr, yt)
            path.lineTo(xl + 0.3f * w, yb)
        }
        8 -> {
            path.addOval(Rect(xl + 0.06f * w, yt, xr - 0.06f * w, ym))
            path.addOval(Rect(xl, ym, xr, yb))
        }
        9 -> {
            path.addOval(Rect(xl, yt, xr, yt + 0.6f * h))
            path.moveTo(xl + 0.05f * w, yb - 0.08f * h)
            path.cubicTo(xl + 0.3f * w, yb, xr, yb - 0.2f * h, xr, yb - 0.6f * h)
        }
    }
}
