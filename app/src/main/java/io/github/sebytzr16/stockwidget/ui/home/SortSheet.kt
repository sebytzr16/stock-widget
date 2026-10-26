package io.github.sebytzr16.stockwidget.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.sebytzr16.stockwidget.R
import io.github.sebytzr16.stockwidget.core.SortMode
import io.github.sebytzr16.stockwidget.ui.theme.LocalPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(current: SortMode, onPick: (SortMode) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LocalPalette.current.sheet,
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp).selectableGroup()) {
            Text(stringResource(R.string.sort_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
            SortMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth().height(56.dp)
                        .selectable(selected = mode == current, onClick = { onPick(mode) }, role = Role.RadioButton),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == current, onClick = null)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(sortOption(mode)), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(
                stringResource(R.string.sort_pinned_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun sortOption(mode: SortMode): Int = when (mode) {
    SortMode.CUSTOM -> R.string.sort_custom
    SortMode.NAME -> R.string.sort_option_name
    SortMode.CHANGE -> R.string.sort_option_change
    SortMode.PRICE -> R.string.sort_price
    SortMode.VALUE -> R.string.sort_option_value
}
