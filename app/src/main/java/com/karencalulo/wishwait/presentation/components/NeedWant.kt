package com.karencalulo.wishwait.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karencalulo.wishwait.R
import com.karencalulo.wishwait.domain.model.Reason
import com.karencalulo.wishwait.domain.model.reasonsFor
import com.karencalulo.wishwait.presentation.common.labelRes

/**
 * "Need or want?" — a Material 3 segmented button, then a single-select list of matching reasons.
 * A need must say why (broken / for work), so marking everything a need can't quietly skip the wait.
 */
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            reasonsFor(isNeed).forEach { r ->
                val selected = r in reasons
                Surface(
                    onClick = { onReasonToggle(r) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(r.emoji, fontSize = 18.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(r.labelRes), modifier = Modifier.weight(1f))
                        if (selected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
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
