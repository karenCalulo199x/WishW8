package com.karencalulo.wishwait.presentation.settings

import com.karencalulo.wishwait.presentation.components.PaydayChips
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.karencalulo.wishwait.presentation.common.paydayLabel
import android.Manifest
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karencalulo.wishwait.R
import com.karencalulo.wishwait.data.reminder.NotificationHelper
import com.karencalulo.wishwait.domain.model.END_MONTH_SALE_DAY
import com.karencalulo.wishwait.domain.model.MID_MONTH_SALE_DAY
import com.karencalulo.wishwait.domain.model.Strictness
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.common.MoneyFormatter
import com.karencalulo.wishwait.presentation.common.appViewModel
import com.karencalulo.wishwait.presentation.common.descriptionRes
import com.karencalulo.wishwait.presentation.common.labelRes
import com.karencalulo.wishwait.presentation.common.res
import com.karencalulo.wishwait.presentation.components.AmountField
import com.karencalulo.wishwait.presentation.components.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

@Composable
fun SettingsRoute(onBack: () -> Unit, onResetDone: () -> Unit, onOpenTerms: () -> Unit, onOpenTips: () -> Unit) {
    val vm = appViewModel {
        SettingsViewModel(
            SettingsUseCases(
                getProfile = it.getProfile,
                updateProfile = it.updateProfile,
                resetAllData = it.resetAllData,
                observeWishes = it.observeWishes,
                changeCurrency = it.changeCurrency,
                exportBackup = it.exportBackup,
                readBackup = it.readBackup,
                restoreBackup = it.restoreBackup,
                backupStatus = it.backupStatusRepository.status,
                checkDriveBackup = it.checkDriveBackup,
                connectDrive = it.connectDrive,
                backUpToDrive = it.backUpToDrive,
                readDriveBackup = it.readDriveBackup,
                disconnectDrive = it.disconnectDrive,
                markFileBackup = it.markFileBackup,
                setBackupReminder = it.setBackupReminder,
            ),
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = res()
    val driveAuth = remember { (context.applicationContext as com.karencalulo.wishwait.WishWaitApp).container.driveAuth }
    val scope = rememberCoroutineScope()
    // Survives rotation while Google's consent screen is open.
    var drivePurpose by rememberSaveable { mutableStateOf<String?>(null) }
    val driveConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val purpose = drivePurpose?.let { name -> DrivePurpose.entries.firstOrNull { it.name == name } }
        drivePurpose = null
        val auth = if (result.resultCode == Activity.RESULT_OK) driveAuth.resultFrom(result.data) else null
        when {
            purpose == null -> vm.onIntent(SettingsIntent.DriveAuthFailed(cancelled = true))
            auth?.accessToken != null -> vm.onIntent(SettingsIntent.DriveAuthorized(purpose, driveAuth.accountEmail(auth)))
            else -> vm.onIntent(SettingsIntent.DriveAuthFailed(cancelled = result.resultCode != Activity.RESULT_OK))
        }
    }

    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.Message -> snackbar.showSnackbar(effect.text(resources))
                is SettingsEffect.WriteBackup -> {
                    val ok = writeText(context, Uri.parse(effect.uri), effect.content)
                    vm.onIntent(SettingsIntent.ExportResult(ok))
                }
                SettingsEffect.ResetDone -> onResetDone()
                is SettingsEffect.AuthorizeDrive -> scope.launch {
                    val result = driveAuth.authorize()
                    val pending = result?.pendingIntent
                    when {
                        result == null -> vm.onIntent(SettingsIntent.DriveAuthFailed(cancelled = false))
                        result.hasResolution() && pending != null -> {
                            drivePurpose = effect.purpose.name
                            driveConsent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                        }
                        result.accessToken != null ->
                            vm.onIntent(SettingsIntent.DriveAuthorized(effect.purpose, driveAuth.accountEmail(result)))
                        else -> vm.onIntent(SettingsIntent.DriveAuthFailed(cancelled = false))
                    }
                }
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onIntent(SettingsIntent.RemindersToggled(granted))
    }
    val onReminders: (Boolean) -> Unit = { on ->
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationHelper.canNotify(context)) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.onIntent(SettingsIntent.RemindersToggled(on))
        }
    }

    // System file pickers: no storage permission needed.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.onIntent(SettingsIntent.ExportTo(uri.toString()))
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.onIntent(SettingsIntent.BackupFileRead(readText(context, uri)))
        }
    }
    val onExport = {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        exportLauncher.launch("wishwait-backup-$stamp.json")
    }
    val onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }

    SettingsScreen(state, vm::onIntent, onBack, snackbar, onReminders, onExport, onImport, onOpenTerms, onOpenTips)
}

private suspend fun writeText(context: Context, uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
    }.getOrDefault(false)
}

private fun readText(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        // Backups are small; refuse anything absurdly large.
        stream.readBytes().takeIf { it.size < 5_000_000 }?.toString(Charsets.UTF_8)
    }
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    onRemindersToggle: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onOpenTerms: () -> Unit = {},
    onOpenTips: () -> Unit = {},
) {
    val symbol = remember(state.currencyCode) { MoneyFormatter(state.currencyCode).symbol }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
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
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.set_money))
            AmountField(
                value = state.income,
                onValueChange = { onIntent(SettingsIntent.IncomeChanged(it)) },
                label = stringResource(R.string.field_income),
                prefix = symbol,
            )
            AmountField(
                value = state.hours,
                onValueChange = { onIntent(SettingsIntent.HoursChanged(it)) },
                label = stringResource(R.string.field_hours),
                suffix = stringResource(R.string.field_hours_suffix),
                keyboardType = KeyboardType.Number,
            )
            AmountField(
                value = state.savings,
                onValueChange = { onIntent(SettingsIntent.SavingsChanged(it)) },
                label = stringResource(R.string.set_savings),
                prefix = symbol,
                imeAction = ImeAction.Done,
            )
            Button(
                onClick = { onIntent(SettingsIntent.SaveMoney) },
                enabled = state.dirty,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }

            ListItem(
                modifier = Modifier.selectable(
                    selected = false,
                    role = Role.Button,
                    onClick = { onIntent(SettingsIntent.OpenCurrencyPicker) },
                ),
                headlineContent = { Text(stringResource(R.string.set_currency)) },
                supportingContent = { Text(currencyName(state.currencyCode)) },
                trailingContent = { Text(symbol, style = MaterialTheme.typography.titleMedium) },
            )
            ListItem(
                modifier = Modifier.selectable(
                    selected = false,
                    role = Role.Button,
                    onClick = { onIntent(SettingsIntent.OpenPaydayPicker) },
                ),
                headlineContent = { Text(stringResource(R.string.set_payday)) },
                supportingContent = { Text(paydayLabel(res(), state.paydays)) },
            )
            PaydaySaleSwitch(
                title = stringResource(R.string.sale_payday_mid),
                description = stringResource(R.string.set_sale_mid_desc),
                checked = MID_MONTH_SALE_DAY in state.paydaySales,
                onToggle = { onIntent(SettingsIntent.PaydaySaleToggled(MID_MONTH_SALE_DAY, it)) },
            )
            PaydaySaleSwitch(
                title = stringResource(R.string.sale_payday_end),
                description = stringResource(R.string.set_sale_end_desc),
                checked = END_MONTH_SALE_DAY in state.paydaySales,
                onToggle = { onIntent(SettingsIntent.PaydaySaleToggled(END_MONTH_SALE_DAY, it)) },
            )
            Text(
                stringResource(R.string.set_sale_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            HorizontalDivider()

            SectionTitle(stringResource(R.string.set_strictness))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Strictness.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = state.strictness == option,
                        onClick = { onIntent(SettingsIntent.StrictnessSelected(option)) },
                        shape = SegmentedButtonDefaults.itemShape(index, Strictness.entries.size),
                    ) { Text(stringResource(option.labelRes)) }
                }
            }
            Text(
                stringResource(R.string.set_strictness_note, stringResource(state.strictness.descriptionRes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()

            ListItem(
                modifier = Modifier.toggleable(
                    value = state.remindersEnabled,
                    role = Role.Switch,
                    onValueChange = onRemindersToggle,
                ),
                headlineContent = { Text(stringResource(R.string.set_reminders)) },
                supportingContent = { Text(stringResource(R.string.set_reminders_desc)) },
                trailingContent = { Switch(checked = state.remindersEnabled, onCheckedChange = null) },
            )
            HorizontalDivider()

            ListItem(
                modifier = Modifier.toggleable(
                    value = state.fetchLinkDetails,
                    role = Role.Switch,
                    onValueChange = { onIntent(SettingsIntent.FetchLinksToggled(it)) },
                ),
                headlineContent = { Text(stringResource(R.string.set_fetch_links)) },
                supportingContent = { Text(stringResource(R.string.set_fetch_links_desc)) },
                trailingContent = { Switch(checked = state.fetchLinkDetails, onCheckedChange = null) },
            )
            HorizontalDivider()

            SectionTitle(stringResource(R.string.set_backup))
            DriveBackupCard(state, onIntent)
            Text(
                stringResource(R.string.set_backup_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Upload, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.set_export))
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.set_import))
                }
            }
            BackupReminderRow(state, onIntent)
            HorizontalDivider()

            SectionTitle(stringResource(R.string.set_privacy))
            Text(stringResource(R.string.set_privacy_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { onIntent(SettingsIntent.ResetRequested) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.set_erase), color = MaterialTheme.colorScheme.error) }
            HorizontalDivider()

            SectionTitle(stringResource(R.string.set_about))
            ListItem(
                modifier = Modifier.selectable(selected = false, role = Role.Button, onClick = onOpenTips),
                headlineContent = { Text(stringResource(R.string.set_tips)) },
                supportingContent = { Text(stringResource(R.string.set_tips_desc)) },
                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
            )
            ListItem(
                modifier = Modifier.selectable(selected = false, role = Role.Button, onClick = onOpenTerms),
                headlineContent = { Text(stringResource(R.string.set_terms)) },
                supportingContent = { Text(stringResource(R.string.set_terms_desc)) },
                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
            )
            val context = LocalContext.current
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
            }
            if (version.isNotEmpty()) {
                Text(
                    stringResource(R.string.set_version, version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (state.showCurrencyPicker) {
        AlertDialog(
            onDismissRequest = { onIntent(SettingsIntent.DismissDialogs) },
            title = { Text(stringResource(R.string.set_currency)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(currencyOptions(), key = { it }) { code ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = code == state.currencyCode,
                                    role = Role.RadioButton,
                                    onClick = { onIntent(SettingsIntent.CurrencySelected(code)) },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = code == state.currencyCode, onClick = null)
                            Spacer(Modifier.padding(4.dp))
                            Text("$code · ${currencyName(code)}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onIntent(SettingsIntent.DismissDialogs) }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    if (state.showPaydayPicker) {
        PaydayDialog(
            current = state.paydays,
            onSave = { onIntent(SettingsIntent.PaydaysSelected(it)) },
            onDismiss = { onIntent(SettingsIntent.DismissDialogs) },
        )
    }

    val pending = state.pendingCurrency
    if (pending != null) {
        val rateOk = (Fmt.parseAmount(state.conversionRate) ?: 0.0) > 0.0
        AlertDialog(
            onDismissRequest = { onIntent(SettingsIntent.DismissDialogs) },
            title = { Text(stringResource(R.string.currency_title, pending)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.currency_body, state.currencyCode, pending))
                    AmountField(
                        value = state.conversionRate,
                        onValueChange = { onIntent(SettingsIntent.RateChanged(it)) },
                        label = stringResource(R.string.currency_rate, state.currencyCode, pending),
                        imeAction = ImeAction.Done,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { onIntent(SettingsIntent.ConvertCurrency) }, enabled = rateOk) {
                    Text(stringResource(R.string.currency_convert))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(SettingsIntent.RelabelCurrency) }) {
                    Text(stringResource(R.string.currency_relabel))
                }
            },
        )
    }

    DriveDialogs(state, onIntent)

    val restore = state.pendingRestore
    if (restore != null) {
        AlertDialog(
            onDismissRequest = { onIntent(SettingsIntent.DismissDialogs) },
            title = { Text(stringResource(R.string.restore_title)) },
            text = { Text(stringResource(R.string.restore_body, Fmt.date(restore.exportedAt), restore.wishes.size)) },
            confirmButton = {
                Button(onClick = { onIntent(SettingsIntent.RestoreConfirmed) }) { Text(stringResource(R.string.restore_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(SettingsIntent.DismissDialogs) }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (state.showResetConfirm) {
        AlertDialog(
            onDismissRequest = { onIntent(SettingsIntent.DismissDialogs) },
            title = { Text(stringResource(R.string.erase_title)) },
            text = { Text(stringResource(R.string.erase_body)) },
            confirmButton = {
                TextButton(onClick = { onIntent(SettingsIntent.ResetConfirmed) }) {
                    Text(stringResource(R.string.erase_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(SettingsIntent.DismissDialogs) }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun currencyName(code: String): String =
    runCatching { Currency.getInstance(code).getDisplayName(Locale.getDefault()) }.getOrDefault(code)

private fun currencyOptions(): List<String> {
    val device = runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull()
    val common = listOf(
        "PHP", "USD", "EUR", "GBP", "JPY", "SGD", "AUD", "CAD", "INR", "IDR", "MYR", "THB",
        "VND", "KRW", "CNY", "HKD", "TWD", "AED", "SAR", "BRL", "MXN", "ZAR", "NGN", "CHF", "NZD",
    )
    return (listOfNotNull(device) + common).distinct()
}

/** Presets first; "Other days" only if needed. */
@Composable
private fun PaydayDialog(current: List<Int>, onSave: (List<Int>) -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableStateOf(current) }
    val custom = days.isNotEmpty() && com.karencalulo.wishwait.presentation.common.PaydayPreset.matching(days) == null
    var showOther by remember { mutableStateOf(custom) }
    var first by remember { mutableStateOf(if (custom) days.getOrNull(0)?.toString().orEmpty() else "") }
    var second by remember { mutableStateOf(if (custom) days.getOrNull(1)?.toString().orEmpty() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.payday_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.payday_hint), style = MaterialTheme.typography.bodySmall)
                PaydayChips(
                    selected = if (showOther) listOf(-1) else days,
                    onSelect = {
                        days = it
                        showOther = false
                    },
                )
                TextButton(onClick = { showOther = true }) { Text(stringResource(R.string.payday_other)) }
                if (showOther) {
                    AmountField(
                        value = first,
                        onValueChange = { first = it.filter(Char::isDigit).take(2) },
                        label = stringResource(R.string.payday_day_field),
                        keyboardType = KeyboardType.Number,
                    )
                    AmountField(
                        value = second,
                        onValueChange = { second = it.filter(Char::isDigit).take(2) },
                        label = stringResource(R.string.payday_second_field),
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val result = if (showOther) {
                    listOfNotNull(first.toIntOrNull(), second.toIntOrNull()).filter { it in 1..31 }
                } else {
                    days
                }
                onSave(result)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PaydaySaleSwitch(title: String, description: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onToggle),
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}
