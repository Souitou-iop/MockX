package com.noobexon.xposedfakelocation.manager.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R

/**
 * Single-choice map source picker dialog. Displays each [MapSourceOption] as a radio row.
 * Selecting a row immediately applies the map source.
 *
 * @param selectedSource Currently active option, pre-selected in the list.
 * @param onSourceSelected Invoked with the chosen [MapSourceOption]; dialog is then dismissed.
 * @param onDismiss Invoked when dismissed without selection.
 */
@Composable
fun MapSourceSelectionDialog(
    selectedSource: MapSourceOption,
    onSourceSelected: (MapSourceOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_map_source_title)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                MapSourceOption.entries.forEach { option ->
                    val selected = option == selectedSource
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSourceSelected(option) }
                            )
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
