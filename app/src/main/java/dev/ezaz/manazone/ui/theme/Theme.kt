package dev.ezaz.manazone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** AMOLED palette: true black everywhere, one restrained emerald accent, quiet greys. */
object Mz {
    val Black = Color(0xFF000000)
    val Emerald = Color(0xFF34C48B)
    val EmeraldDim = Color(0xFF1E7351)
    val EmeraldFaint = Color(0xFF0B1F17)
    val TextPrimary = Color(0xFFE6E9E7)
    val TextSecondary = Color(0xFF8E9390)
    val TextTertiary = Color(0xFF5A5F5C)
    val Hairline = Color(0xFF1E2220)
    val Surface = Color(0xFF0A0B0B)
    val Error = Color(0xFFE57373)
}

private val Colors = darkColorScheme(
    primary = Mz.Emerald,
    onPrimary = Mz.Black,
    primaryContainer = Mz.EmeraldFaint,
    onPrimaryContainer = Mz.Emerald,
    secondary = Mz.EmeraldDim,
    onSecondary = Mz.TextPrimary,
    background = Mz.Black,
    onBackground = Mz.TextPrimary,
    surface = Mz.Black,
    onSurface = Mz.TextPrimary,
    surfaceVariant = Mz.Surface,
    onSurfaceVariant = Mz.TextSecondary,
    surfaceContainer = Mz.Surface,
    surfaceContainerHigh = Color(0xFF111312),
    surfaceContainerHighest = Color(0xFF161817),
    outline = Mz.Hairline,
    outlineVariant = Mz.Hairline,
    error = Mz.Error,
)

/** Tabular figures keep countdown digits from shifting as they change. */
val DigitStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.ExtraLight,
    fontFeatureSettings = "tnum",
    color = Mz.Emerald,
)

private val Type = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 34.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 24.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 2.sp),
)

@Composable
fun ManazoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
