package com.karencalulo.wishwait.presentation.wins

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.karencalulo.wishwait.presentation.common.descriptionRes
import com.karencalulo.wishwait.presentation.common.titleRes
import com.karencalulo.wishwait.presentation.common.res
import com.karencalulo.wishwait.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

@Composable
fun WinsRoute(onOpenWish: (String) -> Unit) {
    val vm = appViewModel { WinsViewModel(it.observeStats, it.observeWishes) }
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
                        Spacer(Modifier.height(4.dp))
                        Text(
                            winsLine(stats.letGoCount, stats.mindfulBuys, stats.impulseBuys),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("🕊️", "${stats.letGoCount}", stringResource(R.string.wins_let_go), Modifier.weight(1f))
                    StatTile("✅", "${stats.mindfulBuys}", stringResource(R.string.wins_mindful), Modifier.weight(1f))
                    StatTile("⚡", "${stats.impulseBuys}", stringResource(R.string.wins_early), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("⏱️", Fmt.hours(res(), stats.hoursSaved), stringResource(R.string.wins_hours), Modifier.weight(1f))
                    StatTile(
                        "📉",
                        state.averageDesireDrop?.let { (if (it >= 0) "−" else "+") + Fmt.number(kotlin.math.abs(it), 1) } ?: "—",
                        stringResource(R.string.wins_desire),
                        Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("💚", "${stats.lovedCount}", stringResource(R.string.wins_loved), Modifier.weight(1f))
                    StatTile("😕", "${stats.regrettedCount}", stringResource(R.string.wins_regretted), Modifier.weight(1f))
                }
            }

            // The thank-you badge only appears once earned, so it never reads as "pay to complete".
            val badges = Achievement.entries.filter { !it.hiddenUntilUnlocked || it in stats.unlocked }
            item { SectionTitle(stringResource(R.string.wins_badges, stats.unlocked.size, badges.size)) }
            items(badges.chunked(2)) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { a -> Badge(a, unlocked = a in stats.unlocked, modifier = Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
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
private fun winsLine(letGo: Int, mindful: Int, impulse: Int): String {
    val total = letGo + mindful + impulse
    return if (total == 0) {
        stringResource(R.string.wins_line_none)
    } else {
        stringResource(R.string.wins_line, letGo + mindful, total)
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
                .alpha(if (unlocked) 1f else 0.45f),
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
