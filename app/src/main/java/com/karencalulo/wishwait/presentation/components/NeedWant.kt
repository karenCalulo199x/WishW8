package com.karencalulo.wishwait.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karencalulo.wishwait.R
import com.karencalulo.wishwait.domain.model.Reason
import com.karencalulo.wishwait.domain.model.reasonsFor
import com.karencalulo.wishwait.presentation.common.labelRes

/**
 * "Need or want?" — a Material 3 segmented button, then the matching reason chips.
 * A need must say why (broken / for work), so marking everything a need can't quietly skip the wait.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NeedWantPicker(
    isNeed: Boolean,
    reasons: Set<Reason>,
    onKindChange: (Boolean) -> Unit,
    onReasonToggle: (Reason) -> Unit,
    showError: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !isNeed,
                onClick = { onKindChange(false) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(stringResource(R.string.kind_want)) }
            SegmentedButton(
                selected = isNeed,
                onClick = { onKindChange(true) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(stringResource(R.string.kind_need)) }
        }
        Text(
            stringResource(if (isNeed) R.string.kind_need_hint else R.string.kind_want_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            reasonsFor(isNeed).forEach { r ->
                FilterChip(
                    selected = r in reasons,
                    onClick = { onReasonToggle(r) },
                    label = { Text("${r.emoji} ${stringResource(r.labelRes)}") },
                )
            }
        }
        if (showError) {
            Text(
                stringResource(R.string.kind_need_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Small "Need" / "Want" tag for wish cards. */
@Composable
fun KindTag(isNeed: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (isNeed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isNeed) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            stringResource(if (isNeed) R.string.tag_need else R.string.tag_want),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
