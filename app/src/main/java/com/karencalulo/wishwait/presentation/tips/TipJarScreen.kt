package com.karencalulo.wishwait.presentation.tips

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karencalulo.wishwait.R
import com.karencalulo.wishwait.domain.model.TipOption
import com.karencalulo.wishwait.domain.model.TipSize
import com.karencalulo.wishwait.presentation.common.FriendVoice
import com.karencalulo.wishwait.presentation.common.appViewModel
import com.karencalulo.wishwait.presentation.common.res
import com.karencalulo.wishwait.presentation.components.ConfettiOverlay
import com.karencalulo.wishwait.presentation.components.EmojiAvatar
import com.karencalulo.wishwait.presentation.components.FriendBubble

@Composable
fun TipJarRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as com.karencalulo.wishwait.WishWaitApp).container }
    val vm = appViewModel { TipJarViewModel(it.tipJar, it.observeProfile) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = res()
    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                is TipJarEffect.OpenPayment -> {
                    val activity = context.findActivity()
                    val opened = activity != null && container.tipJar.launch(activity, effect.size)
                    if (!opened) vm.onIntent(TipJarIntent.LaunchFailed(effect.size))
                }
                is TipJarEffect.Message -> snackbar.showSnackbar(effect.text(resources))
            }
        }
    }
    TipJarScreen(state, vm::onIntent, onBack, snackbar)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipJarScreen(
    state: TipJarState,
    onIntent: (TipJarIntent) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.tip_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EmojiAvatar(
                        FriendVoice.AVATAR,
                        size = 72.dp,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                    Text(
                        stringResource(R.string.tip_headline),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.tip_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                // Ollie applies his own rule to himself: a tip is a want.
                FriendBubble(
                    headline = stringResource(R.string.tip_rule_title),
                    message = stringResource(R.string.tip_rule_body),
                )

                if (state.tipsGiven > 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            pluralStringResource(R.plurals.tip_given, state.tipsGiven, state.tipsGiven),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                when {
                    state.isLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.unavailable -> Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.tip_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        OutlinedButton(onClick = { onIntent(TipJarIntent.Retry) }) { Text(stringResource(R.string.tip_retry)) }
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.options.forEach { option -> TipCard(option, onTip = { onIntent(TipJarIntent.Tip(option.size)) }) }
                    }
                }

                Text(
                    stringResource(R.string.tip_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        if (state.celebrate) {
            ConfettiOverlay(trigger = 1)
            AlertDialog(
                onDismissRequest = { onIntent(TipJarIntent.CelebrationDone) },
                icon = { Text("🦉💚", fontSize = 40.sp, modifier = Modifier.clearAndSetSemantics { }) },
                title = { Text(stringResource(R.string.tip_thanks_title), textAlign = TextAlign.Center) },
                text = { Text(stringResource(R.string.tip_thanks_body), textAlign = TextAlign.Center) },
                confirmButton = {
                    Button(onClick = { onIntent(TipJarIntent.CelebrationDone) }) { Text(stringResource(R.string.tip_thanks_done)) }
                },
            )
        }
    }
}

@Composable
private fun TipCard(option: TipOption, onTip: () -> Unit) {
    val title = stringResource(option.size.titleRes)
    val subtitle = stringResource(option.size.subtitleRes)
    val buttonLabel = stringResource(R.string.tip_button_cd, title, option.price)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(option.size.emoji, fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onTip,
                modifier = Modifier.semantics { contentDescription = buttonLabel },
            ) { Text(option.price) }
        }
    }
}

private val TipSize.titleRes: Int
    get() = when (this) {
        TipSize.SMALL -> R.string.tip_small_title
        TipSize.COFFEE -> R.string.tip_coffee_title
        TipSize.LUNCH -> R.string.tip_lunch_title
    }

private val TipSize.subtitleRes: Int
    get() = when (this) {
        TipSize.SMALL -> R.string.tip_small_sub
        TipSize.COFFEE -> R.string.tip_coffee_sub
        TipSize.LUNCH -> R.string.tip_lunch_sub
    }
