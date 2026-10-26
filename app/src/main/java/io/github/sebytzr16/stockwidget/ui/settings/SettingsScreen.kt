package io.github.sebytzr16.stockwidget.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sebytzr16.stockwidget.BuildConfig
import io.github.sebytzr16.stockwidget.R
import io.github.sebytzr16.stockwidget.core.Currencies
import io.github.sebytzr16.stockwidget.core.Indices
import io.github.sebytzr16.stockwidget.core.Settings
import io.github.sebytzr16.stockwidget.core.ThemeMode
import io.github.sebytzr16.stockwidget.system.Notifier
import io.github.sebytzr16.stockwidget.ui.AppViewModel
import io.github.sebytzr16.stockwidget.ui.components.AppIcon
import io.github.sebytzr16.stockwidget.ui.components.ContentMaxWidth
import io.github.sebytzr16.stockwidget.ui.components.GroupLabel
import io.github.sebytzr16.stockwidget.ui.components.RowDivider
import io.github.sebytzr16.stockwidget.ui.components.Segmented
import io.github.sebytzr16.stockwidget.ui.components.SwitchRow
import io.github.sebytzr16.stockwidget.ui.components.ValueRow
import io.github.sebytzr16.stockwidget.ui.theme.LocalPalette
import java.util.Currency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val user by vm.user.collectAsStateWithLifecycle()
    val s = user.settings
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var canNotify by remember { mutableStateOf(Notifier.canNotify(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canNotify = Notifier.canNotify(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canNotify = it }
    val update: ((Settings) -> Settings) -> Unit = { vm.updateSettings(it) }
    val background = MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(R.drawable.ic_back, stringResource(R.string.back), tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background, scrolledContainerColor = background),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Group(stringResource(R.string.group_appearance)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.theme), style = MaterialTheme.typography.bodyLarge)
                        Segmented(
                            options = listOf(
                                ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                                ThemeMode.LIGHT to stringResource(R.string.theme_light),
                                ThemeMode.DARK to stringResource(R.string.theme_dark),
                            ),
                            selected = s.theme,
                            onSelect = { mode -> update { it.copy(theme = mode) } },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    }
                    RowDivider()
                    SwitchRow(
                        stringResource(R.string.pure_black),
                        stringResource(R.string.pure_black_hint),
                        checked = s.pureBlack,
                        enabled = s.theme != ThemeMode.LIGHT,
                    ) { on -> update { it.copy(pureBlack = on) } }
                    if (Build.VERSION.SDK_INT >= 31) {
                        RowDivider()
                        SwitchRow(stringResource(R.string.wallpaper_colors), stringResource(R.string.wallpaper_colors_hint), s.dynamicColor) { on ->
                            update { it.copy(dynamicColor = on) }
                        }
                    }
                }

                Group(stringResource(R.string.group_market)) {
                    ValueRow(
                        stringResource(R.string.home_currency),
                        stringResource(R.string.home_currency_hint),
                        s.homeCurrency,
                        onClick = { dialog = "currency" },
                    )
                    RowDivider()
                    ValueRow(
                        stringResource(R.string.indices),
                        s.indices.joinToString(", ") { Indices.short(it) ?: it.removePrefix("^") }.ifEmpty { stringResource(R.string.indices_hidden) },
                        null,
                        onClick = { dialog = "indices" },
                    )
                    RowDivider()
                    SwitchRow(stringResource(R.string.extended_hours), stringResource(R.string.extended_hours_hint), s.extendedHours) { on ->
                        update { it.copy(extendedHours = on) }
                    }
                }

                Group(stringResource(R.string.group_refresh)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.refresh_every), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.refresh_every_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Segmented(
                            options = listOf(
                                15 to stringResource(R.string.minutes_15),
                                30 to stringResource(R.string.minutes_30),
                                60 to stringResource(R.string.hour_1),
                            ),
                            selected = s.refreshMinutes,
                            onSelect = { m -> update { it.copy(refreshMinutes = m) } },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    }
                    RowDivider()
                    SwitchRow(stringResource(R.string.only_when_open), stringResource(R.string.only_when_open_hint), s.onlyWhenOpen) { on ->
                        update { it.copy(onlyWhenOpen = on) }
                    }
                }

                Group(stringResource(R.string.group_alerts)) {
                    SwitchRow(stringResource(R.string.price_alerts), stringResource(R.string.price_alerts_hint), s.alertsEnabled) { on ->
                        update { it.copy(alertsEnabled = on) }
                        if (on && !canNotify && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (s.alertsEnabled && !canNotify) {
                        RowDivider()
                        ValueRow(
                            stringResource(R.string.notifications_off),
                            stringResource(R.string.notifications_off_hint),
                            null,
                            icon = R.drawable.ic_bell_off,
                            onClick = { openNotificationSettings(context) },
                        )
                    }
                }

                Group(stringResource(R.string.group_about)) {
                    ValueRow(stringResource(R.string.version), null, BuildConfig.VERSION_NAME, onClick = {})
                    RowDivider()
                    ValueRow(stringResource(R.string.source_code), stringResource(R.string.source_code_hint), null, icon = R.drawable.ic_external) {
                        open(context, context.getString(R.string.source_url))
                    }
                    RowDivider()
                    ValueRow(stringResource(R.string.license), stringResource(R.string.license_hint), null, icon = R.drawable.ic_external) {
                        open(context, context.getString(R.string.source_url) + "/blob/main/LICENSE")
                    }
                    RowDivider()
                    ValueRow(stringResource(R.string.privacy), stringResource(R.string.privacy_hint), null, icon = R.drawable.ic_external) {
                        open(context, context.getString(R.string.source_url) + "/blob/main/PRIVACY.md")
                    }
                }
                Text(
                    stringResource(R.string.disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalPalette.current.text3,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 20.dp),
                )
            }
        }
    }

    when (dialog) {
        "currency" -> CurrencyDialog(
            current = s.homeCurrency,
            onPick = { code -> update { it.copy(homeCurrency = code) }; dialog = null },
            onDismiss = { dialog = null },
        )
        "indices" -> IndicesDialog(
            current = s.indices,
            onSave = { list -> update { it.copy(indices = list) }; dialog = null },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun Group(label: String, content: @Composable ColumnScope.() -> Unit) {
    GroupLabel(label)
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
private fun CurrencyDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (Currencies.HOME_CHOICES.indexOf(current) - 2).coerceAtLeast(0))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_currency)) },
        text = {
            LazyColumn(state = state, modifier = Modifier.heightIn(max = 420.dp)) {
                items(Currencies.HOME_CHOICES, key = { it }) { code ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .selectable(selected = code == current, onClick = { onPick(code) }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = code == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(code, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                runCatching { Currency.getInstance(code).displayName }.getOrDefault(code),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun IndicesDialog(current: List<String>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val picked = remember { mutableStateListOf<String>().apply { addAll(current) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.indices)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.indices_dialog_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(Indices.POPULAR, key = { it.symbol }) { idx ->
                        val on = idx.symbol in picked
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(
                                value = on,
                                role = Role.Checkbox,
                                onValueChange = { if (it) picked.add(idx.symbol) else picked.remove(idx.symbol) },
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Text(idx.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(picked.toList()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }
}
