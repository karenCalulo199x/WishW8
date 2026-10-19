package com.karencalulo.wishwait.presentation.wins

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.karencalulo.wishwait.presentation.common.descriptionRes
import com.karencalulo.wishwait.presentation.common.titleRes
import com.karencalulo.wishwait.presentation.common.res
import com.karencalulo.wishwait.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karencalulo.wishwait.domain.model.Achievement
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.common.FriendVoice
import com.karencalulo.wishwait.presentation.common.LocalMoney
import com.karencalulo.wishwait.presentation.common.appViewModel
import com.karencalulo.wishwait.presentation.components.DecidedWishRow
import com.karencalulo.wishwait.presentation.components.SectionTitle
import com.karencalulo.wishwait.presentation.components.StatTile
import java.util.Calendar

@Composable
fun WinsRoute(onOpenWish: (String) -> Unit) {
    val vm = appViewModel { WinsViewModel(it.observeStats, it.observeWishes, it.observeGoal) }
    val state by vm.state.collectAsStateWithLifecycle()
    WinsScreen(state, onOpenWish)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinsScreen(state: WinsState, onOpenWish: (String) -> Unit) {
    val money = LocalMoney.current
    val stats = state.stats
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.wins_title)) }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.wins_kept), style = MaterialTheme.typography.labelLarge)
                        Text(money.format(stats.savedTotal), style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.streakDays?.let { HeroStat("🔥", "$it", stringResource(R.string.wins_streak), Modifier.weight(1f)) }
                            HeroStat("🕊️", "${stats.letGoCount}", stringResource(R.string.wins_let_go), Modifier.weight(1f))
                            HeroStat("✅", "${stats.mindfulBuys}", stringResource(R.string.wins_mindful), Modifier.weight(1f))
                        }
                        state.goal?.let { goal ->
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.wins_goal_progress, goal.emoji, goal.name),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(Fmt.percent(goal.progress.toDouble()), style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { goal.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(MaterialTheme.shapes.small),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (goal.isReached) stringResource(R.string.goal_reached_line)
                                else stringResource(R.string.goal_progress_line, money.format(goal.saved), money.format(goal.target)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("⚡", "${stats.impulseBuys}", stringResource(R.string.wins_early), Modifier.weight(1f))
                    StatTile("⏱️", Fmt.hours(res(), stats.hoursSaved), stringResource(R.string.wins_hours), Modifier.weight(1f))
                    StatTile(
                        "📉",
                        state.averageDesireDrop?.let { (if (it >= 0) "−" else "+") + Fmt.number(kotlin.math.abs(it), 1) } ?: "—",
                        stringResource(R.string.wins_desire),
                        Modifier.weight(1f),
                    )
                }
            }

            // The thank-you badge only appears once earned, so it never reads as "pay to complete".
            val badges = Achievement.entries.filter { !it.hiddenUntilUnlocked || it in stats.unlocked }
            item { SectionTitle(stringResource(R.string.wins_badges, stats.unlocked.size, badges.size)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(badges, key = { it.name }) { a ->
                        Badge(a, unlocked = a in stats.unlocked, modifier = Modifier.width(120.dp))
                    }
                }
            }

            if (state.calendar.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.wins_calendar)) }
                item { ActivityCalendarGrid(state.calendar) }
            }

            if (state.decided.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.wins_history)) }
                items(state.decided, key = { it.id }) { wish ->
                    DecidedWishRow(wish, onClick = { onOpenWish(wish.id) })
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.wins_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStat(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$emoji $value", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ActivityCalendarGrid(days: List<DayCell>) {
    Column {
        days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                week.forEach { cell -> DayDot(cell, Modifier.weight(1f)) }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(MaterialTheme.colorScheme.tertiaryContainer, stringResource(R.string.wins_calendar_active))
            LegendDot(MaterialTheme.colorScheme.errorContainer, stringResource(R.string.wins_calendar_missed))
            LegendDot(MaterialTheme.colorScheme.surfaceContainerLow, stringResource(R.string.wins_calendar_none))
        }
    }
}

@Composable
private fun DayDot(cell: DayCell, modifier: Modifier = Modifier) {
    val color = when (cell.status) {
        DayStatus.MINDFUL -> MaterialTheme.colorScheme.tertiaryContainer
        DayStatus.MISSED -> MaterialTheme.colorScheme.errorContainer
        DayStatus.NONE -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val day = remember(cell.dayStart) {
        Calendar.getInstance().apply { timeInMillis = cell.dayStart }.get(Calendar.DAY_OF_MONTH)
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (cell.emoji != null) {
            Text(cell.emoji, style = MaterialTheme.typography.labelMedium)
        } else {
            Text("$day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


@Composable
private fun Badge(a: Achievement, unlocked: Boolean, modifier: Modifier = Modifier) {
    val title = stringResource(a.titleRes)
    val description = stringResource(a.descriptionRes)
    val spoken = stringResource(if (unlocked) R.string.cd_badge_unlocked else R.string.cd_badge_locked, title, description)
    Card(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .alpha(if (unlocked) 1f else 0.62f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (unlocked) a.emoji else "🔒", fontSize = 30.sp)
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
            )
        }
    }
}
