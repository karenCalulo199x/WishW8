package com.karencalulo.wishwait.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.karencalulo.wishwait.R
import com.karencalulo.wishwait.domain.model.Category
import com.karencalulo.wishwait.domain.model.Reason
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.common.labelRes
import com.karencalulo.wishwait.presentation.components.MoneyField
import com.karencalulo.wishwait.presentation.components.NeedWantPicker
import com.karencalulo.wishwait.domain.model.reasonsAfterSwitch

/** Fix a typo, a price change, the category, or need vs. want. Cooldown and history stay untouched. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditWishDialog(
    wish: Wish,
    onSave: (name: String, price: Double, category: Category, note: String, reasons: Set<Reason>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(wish.name) }
    var price by remember { mutableStateOf(Fmt.plain(wish.price)) }
    var category by remember { mutableStateOf(wish.category) }
    var note by remember { mutableStateOf(wish.note) }
    var isNeed by remember { mutableStateOf(wish.isNeed) }
    var reasons by remember { mutableStateOf(reasonsAfterSwitch(wish.reasons, wish.isNeed)) }
    val parsed = Fmt.parseAmount(price)
    val needWithoutReason = isNeed && reasons.none { it.isNeed }
    val valid = name.isNotBlank() && parsed != null && parsed > 0 && !needWithoutReason

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text(stringResource(R.string.field_what)) },
                    isError = name.isBlank(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                MoneyField(
                    value = price,
                    onValueChange = { price = Fmt.sanitizeAmount(it) },
                    label = stringResource(R.string.field_price),
                    isError = parsed == null || parsed <= 0,
                    supportingText = stringResource(R.string.edit_note_cooldown),
                )
                Text(stringResource(R.string.add_category), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text("${c.emoji} ${stringResource(c.labelRes)}") },
                        )
                    }
                }
                Text(stringResource(R.string.kind_title), style = MaterialTheme.typography.titleSmall)
                NeedWantPicker(
                    isNeed = isNeed,
                    reasons = reasons,
                    onKindChange = { need ->
                        isNeed = need
                        reasons = reasonsAfterSwitch(reasons, need)
                    },
                    onReasonToggle = { r -> reasons = if (r in reasons) reasons - r else reasons + r },
                    showError = needWithoutReason,
                )
                if (isNeed != wish.isNeed) {
                    Text(
                        stringResource(R.string.edit_kind_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(280) },
                    label = { Text(stringResource(R.string.field_note)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (parsed != null) onSave(name, parsed, category, note, keptReasons(reasons, isNeed)) },
                enabled = valid,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Saved reasons always match the choice, so the Need/Want label and Ollie's rules never disagree. */
internal fun keptReasons(picked: Set<Reason>, isNeed: Boolean): Set<Reason> = picked.filter { it.isNeed == isNeed }.toSet()
