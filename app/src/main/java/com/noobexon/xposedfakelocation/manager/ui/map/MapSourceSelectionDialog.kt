package com.noobexon.xposedfakelocation.manager.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * HyperOS / Miuix Official RadioButton Single-choice map source picker dialog.
 * Powered by compose-miuix-ui WindowDialog and RadioButtonPreference.
 */
@Composable
fun MapSourceSelectionDialog(
    selectedSource: MapSourceOption,
    onSourceSelected: (MapSourceOption) -> Unit,
    onDismiss: () -> Unit
) {
    WindowDialog(
        show = true,
        title = stringResource(R.string.setting_map_source_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                MapSourceOption.entries.forEach { option ->
                    val selected = option == selectedSource
                    RadioButtonPreference(
                        title = stringResource(option.labelRes),
                        selected = selected,
                        onClick = {
                            onSourceSelected(option)
                            onDismiss()
                        }
                    )
                }
            }

            TextButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors()
            )
        }
    }
}
