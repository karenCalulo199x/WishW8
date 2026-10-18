package com.karencalulo.wishwait.presentation.home

import com.karencalulo.wishwait.domain.model.SpendingContext
import com.karencalulo.wishwait.domain.model.WishStatus
import com.karencalulo.wishwait.domain.model.CutoffCalculator
import com.karencalulo.wishwait.domain.model.CutoffSpending
import com.karencalulo.wishwait.domain.model.SpendingCalendar
import com.karencalulo.wishwait.presentation.components.GoalReachedDialog
import com.karencalulo.wishwait.presentation.components.AddMoneyDialog
import com.karencalulo.wishwait.presentation.components.GoalEditorDialog
import com.karencalulo.wishwait.presentation.components.GoalCard
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.karencalulo.wishwait.presentation.common.displayName
import com.karencalulo.wishwait.presentation.common.res
import com.karencalulo.wishwait.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.common.FriendVoice
import com.karencalulo.wishwait.presentation.common.LocalMoney
import com.karencalulo.wishwait.presentation.common.appViewModel
import com.karencalulo.wishwait.presentation.common.rememberNow
import com.karencalulo.wishwait.presentation.components.DecidedWishRow
import com.karencalulo.wishwait.presentation.components.EmojiAvatar
import com.karencalulo.wishwait.presentation.components.ReadyWishCard
import com.karencalulo.wishwait.presentation.components.SectionTitle
import com.karencalulo.wishwait.presentation.components.WaitingWishCard

@Composable
fun HomeRoute(
    onAddWish: () -> Unit,
    onOpenWish: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = appViewModel {
        HomeViewModel(
            it.observeWishes,
            it.observeStats,
            it.observeProfile,
            it.observeGoal,
            it.setGoal,
            it.addToGoal,
            it.removeGoal,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    HomeScreen(state, vm::onIntent, onAddWish, onOpenWish, onOpenSettings)
    GoalDialogs(state, vm::onIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onAddWish: () -> Unit,
    onOpenWish: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val now = rememberNow()
    // Payday & sale-day awareness (recomputed once a minute).
    val minute = now / 60_000L
    val timing = remember(minute, state.profile.paydays, state.profile.paydaySales) {
        SpendingCalendar.context(now, state.profile.paydays, paydaySales = state.profile.paydaySales)
    }
    val bannerText = timingBannerText(timing)
    val keptSincePayday = remember(minute, state.profile.paydays, state.wishes) {
        SpendingCalendar.lastPayday(now, state.profile.paydays)?.let { since ->
            state.wishes.filter { it.status == WishStatus.LET_GO && (it.decidedAt ?: 0L) >= since }.sumOf { it.price }
        }
    }
    // Wants bought through WishWait this cutoff, against the strictness line.
    val cutoff = remember(minute, state.profile, state.wishes) { CutoffCalculator.of(state.wishes, state.profile, now) }
    val listState = rememberLazyListState()
    val expandedFab by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val active = state.active
    val ready = active.filter { it.isReady(now) }
    val waiting = active.filterNot { it.isReady(now) }
    val decided = if (state.showAllHistory) state.decided else state.decided.take(HISTORY_PREVIEW)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddWish,
                expanded = expandedFab,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_add)) },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") { HeroCard(state, keptSincePayday, cutoff) }
            bannerText?.let { text ->
                item(key = "timing") { TimingBanner(text) }
            }
            item(key = "goal") {
                GoalCard(
                    goal = state.goal,
                    onSetGoal = { onIntent(HomeIntent.OpenGoalEditor) },
                    onAddMoney = { onIntent(HomeIntent.OpenAddMoney) },
                )
            }

            if (active.isEmpty() && state.decided.isEmpty()) {
                item(key = "empty") { EmptyState(onAddWish) }
            }

            if (ready.isNotEmpty()) {
                item(key = "ready_title") { SectionTitle(stringResource(R.string.home_section_ready)) }
                items(ready, key = { "r_${it.id}" }) { wish ->
                    ReadyWishCard(wish, onClick = { onOpenWish(wish.id) }, modifier = Modifier.animateItem())
                }
            }

            if (waiting.isNotEmpty()) {
                item(key = "waiting_title") { SectionTitle(stringResource(R.string.home_section_waiting)) }
                items(waiting, key = { "w_${it.id}" }) { wish ->
                    WaitingWishCard(wish, now, onClick = { onOpenWish(wish.id) }, modifier = Modifier.animateItem())
                }
            }

            if (state.decided.isNotEmpty()) {
                item(key = "decided_title") {
                    SectionTitle(stringResource(R.string.home_section_decided)) {
                        if (state.decided.size > HISTORY_PREVIEW) {
                            TextButton(onClick = { onIntent(HomeIntent.ToggleHistory) }) {
                                Text(if (state.showAllHistory) stringResource(R.string.home_show_less) else stringResource(R.string.home_see_all, state.decided.size))
                            }
                        }
                    }
                }
                items(decided, key = { "d_${it.id}" }) { wish ->
                    DecidedWishRow(wish, onClick = { onOpenWish(wish.id) }, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(state: HomeState, keptSincePayday: Double?, cutoff: CutoffSpending) {
    val money = LocalMoney.current
    val stats = state.stats
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.primaryContainer) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer)),
                )
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(FriendVoice.AVATAR, fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics { })
                    Spacer(Modifier.width(8.dp))
                    Text(
                        greeting(stats.waitingCount, stats.letGoCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.home_kept), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
                Text(
                    money.format(stats.savedTotal),
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.onPrimaryContainer,
                )
                if (keptSincePayday != null) {
                    Text(
                        stringResource(R.string.home_since_payday, money.format(keptSincePayday)),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onPrimaryContainer,
                    )
                }
                if (cutoff.hasPay && cutoff.spent > 0) {
                    val over = cutoff.spent > cutoff.line + 0.005
                    Text(
                        if (over) {
                            stringResource(
                                if (cutoff.byMonth) R.string.home_cutoff_spent_over_month else R.string.home_cutoff_spent_over,
                                money.format(cutoff.spent),
                                Fmt.percent(cutoff.share),
                                money.format(cutoff.cutoffPay),
                                Fmt.percent(cutoff.lineShare),
                            )
                        } else {
                            stringResource(
                                if (cutoff.byMonth) R.string.home_cutoff_spent_month else R.string.home_cutoff_spent,
                                money.format(cutoff.spent),
                                Fmt.percent(cutoff.share),
                                money.format(cutoff.cutoffPay),
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimaryContainer,
                    )
                }
                if (stats.hoursSaved > 0) {
                    Text(
                        stringResource(R.string.home_hours_kept, Fmt.hours(res(), stats.hoursSaved)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeroPill(stringResource(R.string.home_pill_on_hold, stats.waitingCount))
                    if (stats.onHoldTotal > 0) HeroPill(money.format(stats.onHoldTotal))
                    HeroPill(stringResource(R.string.home_pill_let_go, stats.letGoCount))
                }
            }
        }
    }
}

@Composable
private fun HeroPill(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun greeting(waiting: Int, letGo: Int): String = when {
    waiting == 0 && letGo == 0 -> stringResource(R.string.greet_empty)
    waiting == 1 -> stringResource(R.string.greet_one)
    waiting > 1 -> stringResource(R.string.greet_many, waiting)
    else -> stringResource(R.string.greet_clear)
}

@Composable
private fun EmptyState(onAddWish: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmojiAvatar("🛒", size = 88.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAddWish) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.home_empty_cta))
        }
    }
}

private const val HISTORY_PREVIEW = 5

@Composable
private fun GoalDialogs(state: HomeState, onIntent: (HomeIntent) -> Unit) {
    if (state.showGoalEditor) {
        GoalEditorDialog(
            goal = state.goal,
            onSave = { name, emoji, target -> onIntent(HomeIntent.SaveGoal(name, emoji, target)) },
            onRemove = { onIntent(HomeIntent.RemoveGoal) },
            onDismiss = { onIntent(HomeIntent.DismissDialogs) },
        )
    }
    val goal = state.goal
    if (state.showAddMoney && goal != null) {
        AddMoneyDialog(
            goal = goal,
            onAdd = { onIntent(HomeIntent.AddMoney(it)) },
            onDismiss = { onIntent(HomeIntent.DismissDialogs) },
        )
    }
    if (state.goalJustReached && goal != null) {
        GoalReachedDialog(goal.name, onDone = { onIntent(HomeIntent.DismissDialogs) })
    }
}

/** Sale today > sale tomorrow > payday. At most one banner, so it stays calm. */
@Composable
private fun timingBannerText(timing: SpendingContext): String? = when {
    timing.saleToday != null -> stringResource(R.string.home_banner_sale_today, timing.saleToday.displayName(res()))
    timing.saleTomorrow != null -> stringResource(R.string.home_banner_sale_tomorrow, timing.saleTomorrow.displayName(res()))
    timing.inPaydayWindow -> stringResource(R.string.home_banner_payday)
    else -> null
}

@Composable
private fun TimingBanner(text: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}
