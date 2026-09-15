package com.noobexon.xposedfakelocation.manager.ui.map

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixDialog
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixDialogButton

/**
 * HyperOS / Miuix Styled Dialog that lets the user jump to an arbitrary coordinate.
 */
@Composable
fun GoToPointDialog(
    latitude: String,
    longitude: String,
    @StringRes latitudeErrorRes: Int?,
    @StringRes longitudeErrorRes: Int?,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    MiuixDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.map_go_to_point),
        confirmButton = {
            MiuixDialogButton(
                text = stringResource(R.string.action_go),
                isPrimary = true,
                onClick = onConfirm
            )
        },
        dismissButton = {
            MiuixDialogButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismissRequest
            )
        }
    ) {
        Column {
            CoordinateInputField(
                value = latitude,
                onValueChange = onLatitudeChange,
                label = stringResource(R.string.field_latitude),
                errorRes = latitudeErrorRes,
            )
            Spacer(modifier = Modifier.height(10.dp))
            CoordinateInputField(
                value = longitude,
                onValueChange = onLongitudeChange,
                label = stringResource(R.string.field_longitude),
                errorRes = longitudeErrorRes,
            )
        }
    }
}

/**
 * HyperOS / Miuix Styled Dialog that lets the user save the current spoof location as a named favourite.
 */
@Composable
fun AddToFavoritesDialog(
    name: String,
    description: String,
    latitude: String,
    longitude: String,
    @StringRes nameErrorRes: Int?,
    @StringRes latitudeErrorRes: Int?,
    @StringRes longitudeErrorRes: Int?,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    MiuixDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.map_add_to_favorites),
        confirmButton = {
            MiuixDialogButton(
                text = stringResource(R.string.action_add),
                isPrimary = true,
                onClick = onConfirm
            )
        },
        dismissButton = {
            MiuixDialogButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismissRequest
            )
        }
    ) {
        Column {
            CoordinateInputField(
                value = name,
                onValueChange = onNameChange,
                label = stringResource(R.string.field_name),
                errorRes = nameErrorRes,
            )
            Spacer(modifier = Modifier.height(10.dp))
            CoordinateInputField(
                value = description,
                onValueChange = onDescriptionChange,
                label = stringResource(R.string.field_description),
                errorRes = null,
            )
            Spacer(modifier = Modifier.height(10.dp))
            CoordinateInputField(
                value = latitude,
                onValueChange = onLatitudeChange,
                label = stringResource(R.string.field_latitude),
                errorRes = latitudeErrorRes,
                keyboardType = KeyboardType.Number,
            )
            Spacer(modifier = Modifier.height(10.dp))
            CoordinateInputField(
                value = longitude,
                onValueChange = onLongitudeChange,
                label = stringResource(R.string.field_longitude),
                errorRes = longitudeErrorRes,
                keyboardType = KeyboardType.Number,
            )
        }
    }
}

/**
 * A single labelled [OutlinedTextField] with squircle corners and inline error annotations.
 */
@Composable
private fun CoordinateInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    @StringRes errorRes: Int?,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Unspecified,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = errorRes != null,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth()
    )
    if (errorRes != null) {
        Text(
            text = stringResource(errorRes),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
