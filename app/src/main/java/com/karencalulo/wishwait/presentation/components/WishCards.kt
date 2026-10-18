package com.karencalulo.wishwait.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import com.karencalulo.wishwait.presentation.common.Countdown
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.common.LocalMoney
import com.karencalulo.wishwait.presentation.common.res
import com.karencalulo.wishwait.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription

@Composable
fun WaitingWishCard(wish: Wish, now: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val money = LocalMoney.current
    val countdown = Countdown.of(wish.remainingMillis(now))
    val r = res()
    val left = countdown.short(r)
    val description = stringResource(R.string.cd_wish_waiting, wish.name, money.format(wish.price), left) +
        ", " + stringResource(if (wish.isNeed) R.string.tag_need else R.string.tag_want)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiAvatar(wish.category.emoji)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    wish.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        money.format(wish.price),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    KindTag(wish.isNeed)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.HourglassTop,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        left,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            CountdownRing(progress = wish.progress(now), size = 56.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (countdown.days > 0) "${countdown.days}" else "${countdown.hours}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(if (countdown.days > 0) R.string.unit_days_short else R.string.unit_hours_short),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun ReadyWishCard(wish: Wish, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val money = LocalMoney.current
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiAvatar(wish.category.emoji, container = MaterialTheme.colorScheme.surface)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.card_ready_label), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    KindTag(wish.isNeed)
                }
                Text(
                    wish.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.card_ready_body, money.format(wish.price), Fmt.days(res(), wish.cooldownDays)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = stringResource(R.string.card_decide))
        }
    }
}

@Composable
fun DecidedWishRow(wish: Wish, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val money = LocalMoney.current
    val (label, color) = when {
        wish.status == WishStatus.LET_GO ->
            stringResource(R.string.row_let_go, money.format(wish.price)) to MaterialTheme.colorScheme.primary
        wish.boughtEarly -> stringResource(R.string.row_bought_early) to MaterialTheme.colorScheme.tertiary
        else -> stringResource(R.string.row_bought_mindfully) to MaterialTheme.colorScheme.secondary
    }
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = { EmojiAvatar(wish.category.emoji, size = 40.dp) },
        headlineContent = { Text(wish.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.row_with_date, label, Fmt.date(wish.decidedAt ?: wish.createdAt)),
                    color = color,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                KindTag(wish.isNeed)
            }
        },
        trailingContent = {
            Text(
                if (wish.status == WishStatus.LET_GO) "🕊️" else if (wish.gladRating == true) "💚" else if (wish.gladRating == false) "😕" else "🛍️",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clearAndSetSemantics { },
            )
        },
    )
}
