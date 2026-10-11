package dev.ezaz.manazone.presets

import dev.ezaz.manazone.session.SessionManager

val DEFAULT_PRESETS = listOf(5, 20, 25, 30, 60)
const val MAX_PRESETS = 12

sealed interface DurationInput {
    data class Valid(val minutes: Int) : DurationInput
    data class Invalid(val message: String) : DurationInput
}

/**
 * Parses whole-minute durations typed by the user. Accepts `45`, `90m`, `2h`, `1h 30m`, `1h30`
 * and `1:30` (hours:minutes). Range is 1 minute through 24 hours.
 */
object DurationParser {
    private val hoursMinutes = Regex("""^(\d{1,2})\s*h(?:\s*(\d{1,4})\s*m?)?$""")
    private val colon = Regex("""^(\d{1,2}):(\d{2})$""")
    private val minutesOnly = Regex("""^(\d{1,4})\s*m?$""")

    fun parse(text: String): DurationInput {
        val input = text.trim().lowercase().replace("min", "m").replace("hr", "h")
        if (input.isEmpty()) return DurationInput.Invalid("Enter a duration")
        val minutes = hoursMinutes.matchEntire(input)?.let { m ->
            m.groupValues[1].toInt() * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
        } ?: colon.matchEntire(input)?.let { m ->
            val mins = m.groupValues[2].toInt()
            if (mins >= 60) return DurationInput.Invalid("Minutes after ':' must be below 60")
            m.groupValues[1].toInt() * 60 + mins
        } ?: minutesOnly.matchEntire(input)?.groupValues?.get(1)?.toInt()
            ?: return DurationInput.Invalid("Use minutes (45), hours (2h) or both (1h 30m)")
        return validateRange(minutes)
    }

    fun validateRange(minutes: Int): DurationInput = when {
        minutes < SessionManager.MIN_MINUTES -> DurationInput.Invalid("Duration must be at least 1 minute")
        minutes > SessionManager.MAX_MINUTES -> DurationInput.Invalid("Duration can be at most 24 hours")
        else -> DurationInput.Valid(minutes)
    }
}

sealed interface PresetEdit {
    data class Ok(val presets: List<Int>) : PresetEdit
    data class Rejected(val message: String) : PresetEdit
}

/** Pure list operations for the preset editor, with validation. */
object PresetRules {
    fun add(presets: List<Int>, minutes: Int): PresetEdit {
        rangeError(minutes)?.let { return PresetEdit.Rejected(it) }
        if (minutes in presets) return PresetEdit.Rejected("${formatDuration(minutes)} is already a preset")
        if (presets.size >= MAX_PRESETS) return PresetEdit.Rejected("You can keep up to $MAX_PRESETS presets")
        return PresetEdit.Ok(presets + minutes)
    }

    fun edit(presets: List<Int>, index: Int, minutes: Int): PresetEdit {
        if (index !in presets.indices) return PresetEdit.Rejected("That preset no longer exists")
        rangeError(minutes)?.let { return PresetEdit.Rejected(it) }
        if (presets.withIndex().any { (i, v) -> i != index && v == minutes }) {
            return PresetEdit.Rejected("${formatDuration(minutes)} is already a preset")
        }
        return PresetEdit.Ok(presets.toMutableList().also { it[index] = minutes })
    }

    fun remove(presets: List<Int>, index: Int): PresetEdit {
        if (index !in presets.indices) return PresetEdit.Rejected("That preset no longer exists")
        if (presets.size <= 1) return PresetEdit.Rejected("Keep at least one duration")
        return PresetEdit.Ok(presets.toMutableList().also { it.removeAt(index) })
    }

    fun move(presets: List<Int>, from: Int, to: Int): PresetEdit {
        if (from !in presets.indices || to !in presets.indices) return PresetEdit.Ok(presets)
        return PresetEdit.Ok(presets.toMutableList().also { it.add(to, it.removeAt(from)) })
    }

    /** Sanitizes persisted data: drops invalid and duplicate values, never returns an empty list. */
    fun sanitize(presets: List<Int>): List<Int> =
        presets.filter { rangeError(it) == null }.distinct().take(MAX_PRESETS).ifEmpty { DEFAULT_PRESETS }

    private fun rangeError(minutes: Int) =
        (DurationParser.validateRange(minutes) as? DurationInput.Invalid)?.message
}

/** "25 min", "1 h", "1 h 30 min". */
fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

/** Compact two-part label for presets and widgets, e.g. ("25", "min"), ("2", "hr"), ("1:30", "hr"). */
fun compactDuration(minutes: Int): Pair<String, String> = when {
    minutes < 60 -> "$minutes" to "min"
    minutes % 60 == 0 -> "${minutes / 60}" to "hr"
    minutes < 100 -> "$minutes" to "min"
    else -> "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}" to "hr"
}
