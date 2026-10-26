package io.github.sebytzr16.stockwidget.ui.detail

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.sebytzr16.stockwidget.R
import io.github.sebytzr16.stockwidget.core.AlertKind
import io.github.sebytzr16.stockwidget.core.Currencies
import io.github.sebytzr16.stockwidget.core.Fmt
import io.github.sebytzr16.stockwidget.core.Holding
import io.github.sebytzr16.stockwidget.core.Quote
import io.github.sebytzr16.stockwidget.system.ShareCard
import io.github.sebytzr16.stockwidget.ui.DetailUi
import io.github.sebytzr16.stockwidget.ui.components.AppIcon
import io.github.sebytzr16.stockwidget.ui.components.Segmented
import io.github.sebytzr16.stockwidget.ui.theme.LocalPalette
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToLong

/** Shares and average cost for a stock; saving an empty form removes the position. */
@Composable
fun HoldingSheet(d: DetailUi, q: Quote, onSave: (Holding?) -> Unit, onDismiss: () -> Unit) {
    val existing = d.holding
    var shares by rememberSaveable { mutableStateOf(existing?.let { Fmt.plain(it.shares) }.orEmpty()) }
    var cost by rememberSaveable { mutableStateOf(existing?.let { Fmt.plain(it.avgCost) }.orEmpty()) }
    val sharesValue = Fmt.parse(shares)?.takeIf { it > 0.0 }
    val costValue = Fmt.parse(cost)?.takeIf { it > 0.0 }
    val unit = Currencies.unit(q.currency)

    Sheet(onDismiss) {
        SheetTitle(stringResource(R.string.your_position), d.subtitle)
        OutlinedTextField(
            value = shares,
            onValueChange = { shares = it },
            label = { Text(if (q.isCrypto) stringResource(R.string.field_amount, d.unitLabel) else stringResource(R.string.field_shares)) },
            isError = shares.isNotBlank() && sharesValue == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = cost,
            onValueChange = { cost = it },
            label = { Text(stringResource(if (q.isCrypto) R.string.field_avg_cost_coin else R.string.field_avg_cost)) },
            suffix = { Text(currencyLabel(q.currency)) },
            isError = cost.isNotBlank() && costValue == null,
            supportingText = { Text(stringResource(R.string.field_avg_cost_hint, Fmt.price(q.price, q.currency, q.priceHint))) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (sharesValue != null) {
            Text(
                stringResource(R.string.position_value_now, Fmt.money(sharesValue * q.price * unit.factor, unit.major)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (existing != null) {
                TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.remove_position), color = MaterialTheme.colorScheme.error) }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(
                    onClick = { onSave(Holding(sharesValue!!, costValue!!)) },
                    enabled = sharesValue != null && costValue != null,
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

/** A new price alert: above or below a price, or a daily move of a given percentage. */
@Composable
fun AlertSheet(q: Quote, onCreate: (AlertKind, Double) -> Unit, onDismiss: () -> Unit) {
    var kind by rememberSaveable { mutableStateOf(AlertKind.ABOVE) }
    val decimals = Fmt.priceDecimals(q.price, q.priceHint)
    var above by rememberSaveable { mutableStateOf(Fmt.plain(rounded(q.price * 1.05, decimals))) }
    var below by rememberSaveable { mutableStateOf(Fmt.plain(rounded(q.price * 0.95, decimals))) }
    var move by rememberSaveable { mutableStateOf(Fmt.plain(5.0)) }
    val text = when (kind) {
        AlertKind.ABOVE -> above
        AlertKind.BELOW -> below
        AlertKind.MOVE -> move
    }
    val value = Fmt.parse(text)
    val error = when {
        value == null || value <= 0.0 -> if (text.isBlank()) null else stringResource(R.string.alert_error_number)
        kind == AlertKind.ABOVE && value <= q.price -> stringResource(R.string.alert_error_above, q.displaySymbol)
        kind == AlertKind.BELOW && value >= q.price -> stringResource(R.string.alert_error_below, q.displaySymbol)
        kind == AlertKind.MOVE && value > 100.0 -> stringResource(R.string.alert_error_move)
        else -> null
    }
    val valid = value != null && value > 0.0 && error == null

    Sheet(onDismiss) {
        SheetTitle(
            stringResource(R.string.new_alert),
            stringResource(R.string.alert_now, q.displaySymbol, Fmt.price(q.price, q.currency, q.priceHint)),
        )
        Segmented(
            options = listOf(
                AlertKind.ABOVE to stringResource(R.string.alert_kind_above),
                AlertKind.BELOW to stringResource(R.string.alert_kind_below),
                AlertKind.MOVE to stringResource(R.string.alert_kind_move),
            ),
            selected = kind,
            onSelect = { kind = it },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = text,
            onValueChange = {
                when (kind) {
                    AlertKind.ABOVE -> above = it
                    AlertKind.BELOW -> below = it
                    AlertKind.MOVE -> move = it
                }
            },
            label = { Text(stringResource(if (kind == AlertKind.MOVE) R.string.field_move else R.string.field_price)) },
            suffix = { Text(if (kind == AlertKind.MOVE) "%" else currencyLabel(q.currency)) },
            isError = error != null,
            supportingText = {
                Text(
                    error ?: when (kind) {
                        AlertKind.MOVE -> stringResource(R.string.alert_hint_move, q.displaySymbol)
                        else -> stringResource(R.string.alert_hint_price)
                    }
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            Button(onClick = { onCreate(kind, value!!) }, enabled = valid, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.create_alert))
            }
        }
    }
}

/** Share as an image card or text, or copy the text. */
@Composable
fun ShareSheet(q: Quote, onCopied: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = LocalPalette.current.dark
    Sheet(onDismiss) {
        SheetTitle(stringResource(R.string.share_title, q.displaySymbol), ShareCard.text(context, q))
        ShareOption(R.drawable.ic_image, stringResource(R.string.share_image), stringResource(R.string.share_image_hint)) {
            scope.launch {
                runCatching { ShareCard.shareImage(context, q, q.intraday, dark) }
                onDismiss()
            }
        }
        ShareOption(R.drawable.ic_share, stringResource(R.string.share_as_text), null) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ShareCard.text(context, q))
            runCatching { context.startActivity(Intent.createChooser(send, null)) }
            onDismiss()
        }
        ShareOption(R.drawable.ic_copy, stringResource(R.string.copy_text), null) {
            ShareCard.copyText(context, q)
            onCopied()
            onDismiss()
        }
    }
}

@Composable
private fun ShareOption(@DrawableRes icon: Int, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIcon(icon, null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LocalPalette.current.sheet,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp), content = content)
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
    )
}

/** The unit a price field is typed in: the quote currency, with London pence shown as GBX. */
private fun currencyLabel(code: String): String = when (code) {
    "GBp" -> "GBX"
    "ZAc" -> "ZAC"
    else -> code
}

private fun rounded(value: Double, decimals: Int): Double {
    val f = 10.0.pow(decimals.coerceIn(0, 10))
    return (value * f).roundToLong() / f
}
