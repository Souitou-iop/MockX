package com.noobexon.xposedfakelocation.manager.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.FavoriteLocation
import com.noobexon.xposedfakelocation.manager.ui.components.AppDialog
import com.noobexon.xposedfakelocation.manager.ui.components.BlurredBar
import com.noobexon.xposedfakelocation.manager.ui.components.BlurBackdropBox
import com.noobexon.xposedfakelocation.manager.ui.components.pageScrollModifiers
import com.noobexon.xposedfakelocation.manager.ui.components.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Composable
fun FavoritesScreen(
    navController: NavController,
    onFavoriteSelected: (FavoriteLocation) -> Unit,
    favoritesViewModel: FavoritesViewModel = viewModel()
) {
    val favorites by favoritesViewModel.favorites.collectAsStateWithLifecycle()

    FavoritesContent(
        favorites = favorites,
        onFavoriteClick = { favorite ->
            onFavoriteSelected(favorite)
            navController.navigateUp()
        },
        onDelete = { favorite -> favoritesViewModel.removeFavorite(favorite) },
        onEdit = { old, new -> favoritesViewModel.updateFavorite(old, new) },
        onNavigateUp = { navController.navigateUp() },
    )
}

@Composable
private fun FavoritesContent(
    favorites: List<FavoriteLocation>,
    onFavoriteClick: (FavoriteLocation) -> Unit,
    onDelete: (FavoriteLocation) -> Unit,
    onEdit: (old: FavoriteLocation, new: FavoriteLocation) -> Unit,
    onNavigateUp: () -> Unit,
) {
    var deletePending by remember { mutableStateOf<FavoriteLocation?>(null) }
    var editPending by remember { mutableStateOf<FavoriteLocation?>(null) }

    deletePending?.let { favorite ->
        DeleteConfirmationDialog(
            favoriteName = favorite.name,
            onConfirm = {
                onDelete(favorite)
                deletePending = null
            },
            onDismiss = { deletePending = null },
        )
    }

    editPending?.let { favorite ->
        EditFavoriteDialog(
            favorite = favorite,
            onSave = { updated ->
                onEdit(favorite, updated)
                editPending = null
            },
            onDismiss = { editPending = null },
        )
    }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val colorScheme = MiuixTheme.colorScheme
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.screen_favorites),
                    subtitle = stringResource(R.string.screen_favorites_subtitle),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        val top = innerPadding.calculateTopPadding()
        val bottom = innerPadding.calculateBottomPadding()
        BlurBackdropBox(backdrop) {
            if (favorites.isEmpty()) {
                FavoritesEmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = top)
                        .padding(horizontal = 32.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .pageScrollModifiers(
                            enableScrollEndHaptic = true,
                            showTopAppBar = true,
                            topAppBarScrollBehavior = topAppBarScrollBehavior
                        ),
                    contentPadding = PaddingValues(
                        top = top + 4.dp,
                        bottom = bottom + 16.dp
                    )
                ) {
                    // Rows are independent lazy items, but the section background is painted
                    // continuously so the whole list reads as one card (same style as the
                    // target apps list).
                    itemsIndexed(
                        items = favorites,
                        key = { _, favorite -> "${favorite.name}_${favorite.latitude}_${favorite.longitude}" },
                        contentType = { _, _ -> "favorite" }
                    ) { index, favorite ->
                        val listSize = favorites.size
                        val rowShape = when {
                            listSize == 1 -> RoundedCornerShape(16.dp)
                            index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                            index == listSize - 1 -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = if (index == listSize - 1) 12.dp else 0.dp)
                                .fillMaxWidth()
                                .background(color = colorScheme.surfaceContainer, shape = rowShape)
                        ) {
                            FavoriteRow(
                                favorite = favorite,
                                onClick = { onFavoriteClick(favorite) },
                                onEditClick = { editPending = favorite },
                                onDeleteClick = { deletePending = favorite },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesEmptyState(modifier: Modifier = Modifier) {
    val colorScheme = MiuixTheme.colorScheme
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MiuixIcons.Favorites,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = colorScheme.primary
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.favorites_empty),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.favorites_empty_description),
            color = colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Single favorite row inside the shared card surface; the card background and corner radii are
 * painted by the item container, not here.
 */
@Composable
private fun FavoriteRow(
    favorite: FavoriteLocation,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = colorScheme.primary.copy(alpha = 0.12f),
            contentColor = colorScheme.primary,
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = MiuixIcons.Location,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = favorite.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (favorite.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = favorite.description,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = String.format(Locale.US, "%.5f, %.5f", favorite.latitude, favorite.longitude),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onEditClick) {
            Icon(
                imageVector = MiuixIcons.Edit,
                contentDescription = stringResource(R.string.cd_edit_named_item, favorite.name),
                tint = colorScheme.onSurfaceVariantActions,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(onClick = onDeleteClick) {
            Icon(
                imageVector = MiuixIcons.Delete,
                contentDescription = stringResource(R.string.cd_delete_named_item, favorite.name),
                tint = colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    favoriteName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = stringResource(R.string.favorites_delete_title),
        onDismissRequest = onDismiss,
        confirmText = stringResource(R.string.favorites_delete_confirm),
        onConfirm = onConfirm,
        confirmDestructive = true,
        dismissText = stringResource(R.string.action_cancel),
    ) {
        Text(
            text = stringResource(R.string.favorites_delete_message, favoriteName),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
private fun EditFavoriteDialog(
    favorite: FavoriteLocation,
    onSave: (FavoriteLocation) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(favorite.name) }
    var description by remember { mutableStateOf(favorite.description) }
    var latitudeText by remember { mutableStateOf(favorite.latitude.toString()) }
    var longitudeText by remember { mutableStateOf(favorite.longitude.toString()) }
    var nameError by remember { mutableStateOf(false) }
    var latError by remember { mutableStateOf(false) }
    var lonError by remember { mutableStateOf(false) }

    AppDialog(
        title = stringResource(R.string.favorites_edit_title),
        onDismissRequest = onDismiss,
        confirmText = stringResource(R.string.action_save),
        onConfirm = {
            val lat = latitudeText.toDoubleOrNull()
            val lon = longitudeText.toDoubleOrNull()
            val validName = name.isNotBlank()
            nameError = !validName
            latError = lat == null
            lonError = lon == null
            if (validName && lat != null && lon != null) {
                onSave(
                    favorite.copy(
                        name = name.trim(),
                        description = description.trim(),
                        latitude = lat,
                        longitude = lon,
                    )
                )
            }
        },
        dismissText = stringResource(R.string.action_cancel),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = it.isBlank()
                },
                label = stringResource(R.string.field_name),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = description,
                onValueChange = { description = it },
                label = stringResource(R.string.field_description),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = latitudeText,
                onValueChange = {
                    latitudeText = it
                    latError = it.toDoubleOrNull() == null
                },
                label = stringResource(R.string.field_latitude),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = longitudeText,
                onValueChange = {
                    longitudeText = it
                    lonError = it.toDoubleOrNull() == null
                },
                label = stringResource(R.string.field_longitude),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
