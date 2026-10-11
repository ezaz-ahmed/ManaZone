package dev.ezaz.manazone.ui

import dev.ezaz.manazone.feedback.CompletionFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {
    @Test
    fun `countdown rounds seconds up so zero means finished`() {
        assertEquals("25:00", formatCountdown(25 * 60_000L))
        assertEquals("24:59", formatCountdown(24 * 60_000L + 59_000))
        assertEquals("00:01", formatCountdown(1))
        assertEquals("00:00", formatCountdown(0))
        assertEquals("1:00:00", formatCountdown(60 * 60_000L))
        assertEquals("24:00:00", formatCountdown(24 * 60 * 60_000L))
    }

    @Test
    fun `focused time is shown in whole minutes, rounded down`() {
        assertEquals("0 min", formatFocused(59_999))
        assertEquals("9 min", formatFocused(9 * 60_000L + 59_000))
        assertEquals("1 h", formatFocused(60 * 60_000L))
        assertEquals("1 h 25 min", formatFocused(85 * 60_000L))
    }

    @Test
    fun `ding-dong is short, non-silent and does not clip`() {
        val pcm = CompletionFeedback.synthesizeDingDong()
        assertTrue(pcm.size in 20_000..60_000)
        val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue(peak in 2_000 until Short.MAX_VALUE.toInt())
    }
}
