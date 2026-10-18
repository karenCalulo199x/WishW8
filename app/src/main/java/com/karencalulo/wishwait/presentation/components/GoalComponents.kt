package com.karencalulo.wishwait.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karencalulo.wishwait.R
import com.karencalulo.wishwait.domain.model.SavingsGoal
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.common.LocalMoney

/** The one goal, on the Home screen. Tap the card to edit; one button to add money. */
@Composable
fun GoalCard(
    goal: SavingsGoal?,
    onSetGoal: () -> Unit,
    onAddMoney: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val money = LocalMoney.current
    if (goal == null) {
        Card(
            onClick = onSetGoal,
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🎯", fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics { })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.goal_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.goal_empty_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    Card(
        onClick = onSetGoal,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (goal.isReached) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(goal.emoji, fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics { })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.goal_saving_for), style = MaterialTheme.typography.labelMedium)
                    Text(goal.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
                Text(
                    Fmt.percent(goal.progress.toDouble()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { goal.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(MaterialTheme.shapes.small),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (goal.isReached) stringResource(R.string.goal_reached_line)
                    else stringResource(R.string.goal_progress_line, money.format(goal.saved), money.format(goal.target)),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (goal.isReached) {
                    TextButton(onClick = onSetGoal) { Text(stringResource(R.string.goal_new)) }
                } else {
                    FilledTonalButton(onClick = onAddMoney) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.goal_add_money))
                    }
                }
            }
        }
    }
}

/** Confetti + a big "you did it". */
@Composable
fun GoalReachedDialog(goalName: String, onDone: () -> Unit) {
    ConfettiOverlay(trigger = 1)
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Text("🎯", fontSize = 40.sp, modifier = Modifier.clearAndSetSemantics { }) },
        title = { Text(stringResource(R.string.goal_reached_title)) },
        text = { Text(stringResource(R.string.goal_reached_body, goalName)) },
        confirmButton = { Button(onClick = onDone) { Text(stringResource(R.string.action_yay)) } },
    )
}

/** Name + emoji + amount. That's all a goal needs. */
@Composable
fun GoalEditorDialog(
    goal: SavingsGoal?,
    onSave: (name: String, emoji: String, target: Double) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val editing = goal?.takeUnless { it.isReached }
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var emoji by remember { mutableStateOf(editing?.emoji ?: SavingsGoal.SUGGESTED_EMOJIS.first()) }
    var amount by remember { mutableStateOf(editing?.target?.let { Fmt.plain(it) } ?: "") }
    val parsed = Fmt.parseAmount(amount)
    val valid = name.isNotBlank() && parsed != null && parsed > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (editing == null) R.string.goal_dialog_new else R.string.goal_dialog_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SavingsGoal.SUGGESTED_EMOJIS.forEach { e ->
                        FilterChip(selected = emoji == e, onClick = { emoji = e }, label = { Text(e, fontSize = 18.sp) })
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.goal_field_name)) },
                    placeholder = { Text(stringResource(R.string.goal_field_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                MoneyField(
                    value = amount,
                    onValueChange = { amount = Fmt.sanitizeAmount(it) },
                    label = stringResource(R.string.goal_field_amount),
                )
                if (editing != null && editing.saved > 0) {
                    Text(
                        stringResource(R.string.goal_keeps_saved, LocalMoney.current.format(editing.saved)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (parsed != null) onSave(name, emoji, parsed) }, enabled = valid) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (editing != null) {
                    TextButton(onClick = onRemove) {
                        Text(stringResource(R.string.goal_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
fun AddMoneyDialog(
    goal: SavingsGoal,
    onAdd: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    val parsed = Fmt.parseAmount(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(goal.emoji, fontSize = 32.sp, modifier = Modifier.clearAndSetSemantics { }) },
        title = { Text(stringResource(R.string.goal_add_to, goal.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(
                    value = amount,
                    onValueChange = { amount = Fmt.sanitizeAmount(it) },
                    label = stringResource(R.string.goal_add_amount),
                )
                Text(
                    stringResource(R.string.goal_add_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (parsed != null) onAdd(parsed) }, enabled = parsed != null && parsed > 0) {
                Text(stringResource(R.string.goal_add_money))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** One-tap pay schedules + "Set later". */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PaydayChips(selected: List<Int>, onSelect: (List<Int>) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.karencalulo.wishwait.presentation.common.PaydayPreset.entries.forEach { preset ->
            FilterChip(
                selected = selected == preset.days,
                onClick = { onSelect(preset.days) },
                label = { Text(stringResource(preset.labelRes)) },
            )
        }
        FilterChip(
            selected = selected.isEmpty(),
            onClick = { onSelect(emptyList()) },
            label = { Text(stringResource(R.string.payday_later)) },
        )
    }
}
