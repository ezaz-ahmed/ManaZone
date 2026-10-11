package dev.ezaz.manazone.presets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {

    private fun ok(edit: PresetEdit) = (edit as PresetEdit.Ok).presets
    private fun valid(text: String) = (DurationParser.parse(text) as DurationInput.Valid).minutes
    private fun invalid(text: String) = DurationParser.parse(text) is DurationInput.Invalid

    @Test
    fun `defaults are 5 20 25 30 60`() {
        assertEquals(listOf(5, 20, 25, 30, 60), DEFAULT_PRESETS)
    }

    @Test
    fun `parses common duration formats`() {
        assertEquals(45, valid("45"))
        assertEquals(45, valid(" 45 min "))
        assertEquals(90, valid("90m"))
        assertEquals(120, valid("2h"))
        assertEquals(120, valid("2 hr"))
        assertEquals(90, valid("1h 30m"))
        assertEquals(90, valid("1h30"))
        assertEquals(90, valid("1:30"))
        assertEquals(1, valid("1"))
        assertEquals(1440, valid("24h"))
        assertEquals(1440, valid("1440"))
    }

    @Test
    fun `rejects invalid and out of range input with a message`() {
        listOf("", "  ", "abc", "0", "1441", "25h", "1:75", "-5", "1.5h").forEach {
            assertTrue("expected '$it' to be rejected", invalid(it))
        }
        val message = (DurationParser.parse("0") as DurationInput.Invalid).message
        assertTrue(message.isNotBlank())
    }

    @Test
    fun `add appends valid durations and rejects duplicates`() {
        assertEquals(DEFAULT_PRESETS + 45, ok(PresetRules.add(DEFAULT_PRESETS, 45)))
        assertTrue(PresetRules.add(DEFAULT_PRESETS, 25) is PresetEdit.Rejected)
        assertTrue(PresetRules.add(DEFAULT_PRESETS, 0) is PresetEdit.Rejected)
        assertTrue(PresetRules.add(DEFAULT_PRESETS, 1441) is PresetEdit.Rejected)
    }

    @Test
    fun `add enforces the preset limit`() {
        val full = (1..MAX_PRESETS).toList()
        assertTrue(PresetRules.add(full, 100) is PresetEdit.Rejected)
    }

    @Test
    fun `edit replaces in place and rejects collisions with other presets`() {
        assertEquals(listOf(5, 20, 45, 30, 60), ok(PresetRules.edit(DEFAULT_PRESETS, 2, 45)))
        assertEquals(DEFAULT_PRESETS, ok(PresetRules.edit(DEFAULT_PRESETS, 2, 25))) // unchanged value is fine
        assertTrue(PresetRules.edit(DEFAULT_PRESETS, 2, 60) is PresetEdit.Rejected)
        assertTrue(PresetRules.edit(DEFAULT_PRESETS, 9, 45) is PresetEdit.Rejected)
    }

    @Test
    fun `remove keeps at least one preset`() {
        assertEquals(listOf(5, 25, 30, 60), ok(PresetRules.remove(DEFAULT_PRESETS, 1)))
        assertTrue(PresetRules.remove(listOf(25), 0) is PresetEdit.Rejected)
    }

    @Test
    fun `move reorders presets`() {
        assertEquals(listOf(20, 5, 25, 30, 60), ok(PresetRules.move(DEFAULT_PRESETS, 1, 0)))
        assertEquals(listOf(5, 20, 25, 60, 30), ok(PresetRules.move(DEFAULT_PRESETS, 3, 4)))
        assertEquals(DEFAULT_PRESETS, ok(PresetRules.move(DEFAULT_PRESETS, 0, -1)))
    }

    @Test
    fun `sanitize repairs corrupted persisted presets`() {
        assertEquals(listOf(25, 90), PresetRules.sanitize(listOf(25, 0, 25, 90, 5000)))
        assertEquals(DEFAULT_PRESETS, PresetRules.sanitize(emptyList()))
    }

    @Test
    fun `formats durations for display`() {
        assertEquals("25 min", formatDuration(25))
        assertEquals("2 h", formatDuration(120))
        assertEquals("1 h 30 min", formatDuration(90))
        assertEquals("25" to "min", compactDuration(25))
        assertEquals("90" to "min", compactDuration(90))
        assertEquals("2" to "hr", compactDuration(120))
        assertEquals("2:15" to "hr", compactDuration(135))
    }
}
