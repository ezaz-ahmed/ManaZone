package dev.ezaz.manazone.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ezaz.manazone.R
import dev.ezaz.manazone.presets.DurationInput
import dev.ezaz.manazone.presets.DurationParser
import dev.ezaz.manazone.presets.MAX_PRESETS
import dev.ezaz.manazone.presets.PresetRules
import dev.ezaz.manazone.presets.formatDuration
import dev.ezaz.manazone.ui.MainViewModel
import dev.ezaz.manazone.ui.Route
import dev.ezaz.manazone.feedback.CompletionSound
import dev.ezaz.manazone.ui.focus.ClockDisplay
import dev.ezaz.manazone.ui.focus.ClockStyle
import dev.ezaz.manazone.ui.theme.Mz
import kotlin.math.roundToInt

/** Editor state: null = closed, -1 = adding, otherwise the index being edited. */
private const val ADDING = -1

private val CLOCK_COLORS = listOf(
    0xFF34C48B, 0xFFE6E9E7, 0xFF4FC3F7, 0xFFFFB74D, 0xFFE57373, 0xFFBA68C8, 0xFFFFF176,
).map { it.toInt() }

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val presets by vm.presets.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val dndAccess by vm.dndAccess.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var rowMessage by remember { mutableStateOf<String?>(null) }
    var explainDnd by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    BackHandler { vm.navigate(Route.Home) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.navigate(Route.Home) }) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = Mz.TextSecondary)
            }
            Text("Settings", style = MaterialTheme.typography.headlineSmall, color = Mz.TextPrimary)
        }

        Section("Durations")
        presets.forEachIndexed { index, minutes ->
            PresetRow(
                minutes = minutes,
                canMoveUp = index > 0,
                canMoveDown = index < presets.lastIndex,
                onEdit = { editing = index },
                onMoveUp = { rowMessage = vm.updatePresets(PresetRules.move(presets, index, index - 1)) },
                onMoveDown = { rowMessage = vm.updatePresets(PresetRules.move(presets, index, index + 1)) },
                onRemove = { rowMessage = vm.updatePresets(PresetRules.remove(presets, index)) },
            )
        }
        rowMessage?.let {
            Text(it, color = Mz.Error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
        }
        if (presets.size < MAX_PRESETS) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { rowMessage = null; editing = ADDING }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = Mz.Emerald)
                Spacer(Modifier.size(16.dp))
                Text("Add duration", color = Mz.Emerald, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Section("Clock")
        val clockStyle = ClockStyle.from(settings.clockStyle)
        ClockDisplay(
            text = "12:34",
            style = clockStyle,
            color = Color(settings.clockColor),
            weight = settings.clockWeight,
            modifier = Modifier.fillMaxWidth().height(96.dp),
            widthFraction = 0.7f,
            heightFraction = 0.9f,
            contentModifier = Modifier.testTag("clockPreview"),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClockStyle.entries.forEach { option ->
                val selected = option == clockStyle
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(if (selected) 2.dp else 1.dp, if (selected) Mz.Emerald else Mz.Hairline, RoundedCornerShape(12.dp))
                        .clickable(role = Role.RadioButton, onClickLabel = "Use ${option.label} style") { vm.setClockStyle(option.name) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(option.label, style = MaterialTheme.typography.bodyMedium, color = if (selected) Mz.Emerald else Mz.TextSecondary)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CLOCK_COLORS.forEach { argb ->
                val selected = argb == settings.clockColor
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(if (selected) 3.dp else 1.dp, if (selected) Mz.TextPrimary else Mz.Hairline, CircleShape)
                        .clickable(role = Role.RadioButton, onClickLabel = "Use this color") { vm.setClockColor(argb) },
                )
            }
        }
        Text("Thickness", style = MaterialTheme.typography.bodyLarge, color = Mz.TextPrimary, modifier = Modifier.padding(horizontal = 8.dp))
        Slider(
            value = settings.clockWeight / 100f,
            onValueChange = { vm.setClockWeight(it.roundToInt() * 100) },
            valueRange = 1f..9f,
            steps = 7,
            colors = SliderDefaults.colors(thumbColor = Mz.Emerald, activeTrackColor = Mz.Emerald, inactiveTrackColor = Mz.Hairline),
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Section("Completion")
        ToggleRow("Vibration", "A short double pulse when a session completes", settings.vibrationEnabled, vm::setVibration)
        ToggleRow("Sound", "Plays when a session completes. Follows your ringer and Do Not Disturb settings.", settings.soundEnabled, vm::setSound)
        if (settings.soundEnabled) {
            var volume by remember(settings.soundVolume) { mutableFloatStateOf(settings.soundVolume.toFloat()) }
            Text("Volume", style = MaterialTheme.typography.bodyLarge, color = Mz.TextPrimary, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
            Slider(
                value = volume,
                onValueChange = { volume = it },
                onValueChangeFinished = {
                    vm.setSoundVolume(volume.roundToInt())
                    vm.previewSound(settings.soundId, volume.roundToInt())
                },
                valueRange = 10f..100f,
                colors = SliderDefaults.colors(thumbColor = Mz.Emerald, activeTrackColor = Mz.Emerald, inactiveTrackColor = Mz.Hairline),
                modifier = Modifier.padding(horizontal = 8.dp).testTag("volumeSlider"),
            )
            Text("Tap a sound to choose and hear it", style = MaterialTheme.typography.bodyMedium, color = Mz.TextSecondary, modifier = Modifier.padding(horizontal = 8.dp))
            CompletionSound.all.forEach { sound ->
                val selected = sound.id == settings.soundId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.RadioButton) {
                            vm.setSoundId(sound.id)
                            vm.previewSound(sound.id, settings.soundVolume)
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = Mz.Emerald, unselectedColor = Mz.TextTertiary),
                    )
                    Spacer(Modifier.size(16.dp))
                    Text(sound.name, style = MaterialTheme.typography.bodyLarge, color = if (selected) Mz.TextPrimary else Mz.TextSecondary)
                }
            }
        }

        Section("Do Not Disturb")
        ToggleRow(
            title = "Silence notifications while focusing",
            subtitle = when {
                !settings.dndEnabled -> "Off. Manazone works without this."
                dndAccess -> "On. Access granted."
                else -> "Access was revoked. Sessions will run without Do Not Disturb."
            },
            checked = settings.dndEnabled && dndAccess,
            onChange = { enabled -> if (vm.requestDnd(enabled)) explainDnd = true },
            tag = "dndToggle",
        )

        Section("How sessions work")
        Text(
            """
            Tap a duration to start. The countdown is calculated from the start time, so it stays accurate while you take a call, switch apps, lock the phone or rotate the screen. None of these pause or cancel the session.

            The focus screen shows only the countdown. To end a session early, use the Stop button on the notification; only the time you actually focused is recorded.

            The screen stays awake while the countdown is visible. If you leave Manazone, the session keeps running and the countdown stays in your notifications; return any time from there, the app or a widget.

            If the phone restarts, an unfinished session continues from the time on the clock, and a session that ran out is recorded as completed.
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            color = Mz.TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(32.dp))
    }

    editing?.let { index ->
        DurationDialog(
            title = if (index == ADDING) "Add duration" else "Edit duration",
            initial = presets.getOrNull(index)?.toString() ?: "",
            onDismiss = { editing = null },
            onSave = { minutes ->
                val edit = if (index == ADDING) PresetRules.add(presets, minutes) else PresetRules.edit(presets, index, minutes)
                vm.updatePresets(edit).also { if (it == null) { editing = null; rowMessage = null } }
            },
        )
    }

    if (explainDnd) {
        AlertDialog(
            onDismissRequest = { explainDnd = false; vm.cancelDndRequest() },
            title = { Text("Do Not Disturb access") },
            text = {
                Text(
                    "To silence notifications during a session, Manazone needs Do Not Disturb access. " +
                        "Android will open its settings so you can allow it.\n\n" +
                        "Manazone only turns on priority mode when Do Not Disturb is off, and turns it back off " +
                        "afterwards unless you or another app changed it. Alarms, priority contacts and emergency " +
                        "alerts can still come through, depending on your system settings."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    explainDnd = false
                    try {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    } catch (_: ActivityNotFoundException) {
                        vm.cancelDndRequest()
                    }
                }) { Text("Open settings", color = Mz.Emerald) }
            },
            dismissButton = {
                TextButton(onClick = { explainDnd = false; vm.cancelDndRequest() }) {
                    Text("Not now", color = Mz.TextSecondary)
                }
            },
            containerColor = Mz.Surface,
        )
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(28.dp))
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Mz.TextTertiary,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

@Composable
private fun PresetRow(
    minutes: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val label = formatDuration(minutes)
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = Mz.TextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = "Edit $label", role = Role.Button, onClick = onEdit)
                    .padding(vertical = 16.dp),
            )
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(painterResource(R.drawable.ic_arrow_up), "Move $label up", tint = if (canMoveUp) Mz.TextSecondary else Mz.Hairline)
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(painterResource(R.drawable.ic_arrow_down), "Move $label down", tint = if (canMoveDown) Mz.TextSecondary else Mz.Hairline)
            }
            IconButton(onClick = onEdit) {
                Icon(painterResource(R.drawable.ic_edit), "Edit $label", tint = Mz.TextSecondary)
            }
            IconButton(onClick = onRemove) {
                Icon(painterResource(R.drawable.ic_close), "Remove $label", tint = Mz.TextSecondary)
            }
        }
        HorizontalDivider(color = Mz.Hairline, thickness = 1.dp)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    tag: String = title,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Mz.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Mz.TextSecondary)
        }
        Spacer(Modifier.size(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Mz.Black,
                checkedTrackColor = Mz.Emerald,
                uncheckedThumbColor = Mz.TextTertiary,
                uncheckedTrackColor = Mz.Black,
                uncheckedBorderColor = Mz.Hairline,
            ),
        )
    }
}

/** Text entry for a duration. Validation errors stay visible; input is never silently dropped. */
@Composable
private fun DurationDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (Int) -> String?,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val parsed = DurationParser.parse(text)
    val submit = {
        when (parsed) {
            is DurationInput.Invalid -> error = parsed.message
            is DurationInput.Valid -> error = onSave(parsed.minutes)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    singleLine = true,
                    label = { Text("Duration") },
                    placeholder = { Text("45, 90m, 2h or 1h 30m") },
                    isError = error != null,
                    supportingText = {
                        Text(
                            error ?: (parsed as? DurationInput.Valid)?.let { formatDuration(it.minutes) }
                                ?: "1 minute to 24 hours"
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Mz.Emerald,
                        focusedLabelColor = Mz.Emerald,
                        cursorColor = Mz.Emerald,
                    ),
                    modifier = Modifier.widthIn(min = 240.dp).testTag("durationInput"),
                )
            }
        },
        confirmButton = { TextButton(onClick = submit) { Text("Save", color = Mz.Emerald) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Mz.TextSecondary) } },
        containerColor = Mz.Surface,
    )
}
