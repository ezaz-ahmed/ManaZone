package dev.ezaz.manazone.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    CLASSIC("Classic"),
    MONO("Mono"),
    SEGMENT("Digital"),

    /** Seven-segment display with the unlit segments faintly visible, like an LCD. */
    LCD("LCD");

    val isSegment: Boolean get() = this == SEGMENT || this == LCD

    companion object {
        fun from(name: String): ClockStyle = entries.firstOrNull { it.name == name } ?: CLASSIC
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
                drawSegments(text, color, weight, ghost = style == ClockStyle.LCD)
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
    val half = t / 2
    val g = t * 0.9f
    val xl = x + half
    val xr = x + w - half
    val yt = half
    val ym = h / 2
    val yb = h - half
    fun seg(name: Char, a: Offset, b: Offset) {
        val on = name in lit
        val c = if (on) color else ghost ?: return
        drawLine(c, a, b, strokeWidth = t, cap = StrokeCap.Round)
    }
    seg('a', Offset(xl + g, yt), Offset(xr - g, yt))
    seg('g', Offset(xl + g, ym), Offset(xr - g, ym))
    seg('d', Offset(xl + g, yb), Offset(xr - g, yb))
    seg('f', Offset(xl, yt + g), Offset(xl, ym - g))
    seg('b', Offset(xr, yt + g), Offset(xr, ym - g))
    seg('e', Offset(xl, ym + g), Offset(xl, yb - g))
    seg('c', Offset(xr, ym + g), Offset(xr, yb - g))
}
