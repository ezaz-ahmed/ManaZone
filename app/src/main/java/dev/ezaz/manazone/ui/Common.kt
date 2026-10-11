package dev.ezaz.manazone.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.ezaz.manazone.ui.theme.Mz

/** Countdown text: `mm:ss` below an hour, `h:mm:ss` above. Seconds round up so 0:00 means done. */
fun formatCountdown(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis + 999) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Focused time for display, rounded down to whole minutes: "0 min", "9 min", "1 h 25 min". */
fun formatFocused(millis: Long): String {
    val minutes = millis / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Mz.TextSecondary,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 120.dp, minHeight = 52.dp),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, Mz.Hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
    ) {
        Text(text)
    }
}
