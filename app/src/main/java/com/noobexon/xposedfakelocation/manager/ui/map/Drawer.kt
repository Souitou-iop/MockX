package com.noobexon.xposedfakelocation.manager.ui.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.noobexon.xposedfakelocation.BuildConfig
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.ui.navigation.Screen
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.MapAlbum
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

private object DrawerDimensions {
    val SECTION_SPACING = 20.dp
    val ITEM_SPACING = 3.dp
    val ICON_SIZE = 22.dp
    val SECTION_PADDING = 8.dp
    val HEADER_PADDING = 16.dp
    val DRAWER_PADDING = 16.dp
    val ITEM_PADDING = 12.dp
    val ITEM_CORNER_RADIUS = 16.dp
}

/**
 * Navigation content of the map screen's drawer. Rendered inside a Material 3
 * [androidx.compose.material3.ModalDrawerSheet], which owns the modal chrome (scrim, slide-in,
 * drag-to-close, predictive back).
 */
@Composable
fun DrawerContent(
    navController: NavController,
    onCloseDrawer: () -> Unit = {},
    onNavigate: () -> Unit = {}
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val navigateTo: (String) -> Unit = { route ->
        if (route != currentRoute) {
            onNavigate()
            navController.navigate(route) { launchSingleTop = true }
        }
        onCloseDrawer()
    }

    Column(
        modifier = Modifier
            .padding(DrawerDimensions.DRAWER_PADDING)
    ) {
        DrawerHeader()
        Spacer(modifier = Modifier.height(DrawerDimensions.SECTION_SPACING))

        DrawerSectionHeader(stringResource(R.string.drawer_navigation))

        DrawerItem(
            icon = MiuixIcons.MapAlbum,
            label = stringResource(R.string.drawer_map),
            onClick = { navigateTo(Screen.Map.route) },
            isSelected = currentRoute == Screen.Map.route
        )

        DrawerItem(
            icon = MiuixIcons.Favorites,
            label = stringResource(R.string.screen_favorites),
            onClick = { navigateTo(Screen.Favorites.route) },
            isSelected = currentRoute == Screen.Favorites.route
        )

        DrawerItem(
            icon = MiuixIcons.Phone,
            label = stringResource(R.string.screen_target_apps),
            onClick = { navigateTo(Screen.TargetApps.route) },
            isSelected = currentRoute == Screen.TargetApps.route
        )

        DrawerItem(
            icon = MiuixIcons.Settings,
            label = stringResource(R.string.screen_settings),
            onClick = { navigateTo(Screen.Settings.route) },
            isSelected = currentRoute == Screen.Settings.route
        )

        Spacer(modifier = Modifier.height(DrawerDimensions.SECTION_SPACING))
        DrawerSectionHeader(stringResource(R.string.drawer_app_info))

        DrawerItem(
            icon = MiuixIcons.Info,
            label = stringResource(R.string.screen_about),
            onClick = { navigateTo(Screen.About.route) },
            isSelected = currentRoute == Screen.About.route
        )

        Spacer(modifier = Modifier.weight(1f))

        Surface(
            shape = CircleShape,
            color = MiuixTheme.colorScheme.surfaceVariant,
            contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun DrawerHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MiuixIcons.MapAlbum,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.drawer_header_subtitle),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = DrawerDimensions.SECTION_PADDING,
            bottom = 6.dp,
            top = 4.dp
        )
    )
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isSelected: Boolean = false,
) {
    val colorScheme = MiuixTheme.colorScheme
    val backgroundColor = if (isSelected) {
        colorScheme.primary.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }

    val contentColor = if (isSelected) {
        colorScheme.primary
    } else {
        colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DrawerDimensions.ITEM_SPACING)
            .clip(RoundedCornerShape(DrawerDimensions.ITEM_CORNER_RADIUS))
            .clickable(onClick = onClick),
        color = backgroundColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(DrawerDimensions.ICON_SIZE),
                tint = contentColor
            )

            Text(
                text = label,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
