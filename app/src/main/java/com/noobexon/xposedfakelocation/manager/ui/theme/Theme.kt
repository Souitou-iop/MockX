package com.noobexon.xposedfakelocation.manager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

private val MiuixDarkColorScheme = darkColorScheme(
    primary = MiuixBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003875),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF8A93A6),
    onSecondary = Color.White,
    background = MiuixDarkBackground,
    onBackground = MiuixDarkTextPrimary,
    surface = MiuixDarkCard,
    onSurface = MiuixDarkTextPrimary,
    surfaceVariant = MiuixDarkCardSecondary,
    onSurfaceVariant = MiuixDarkTextSecondary,
    outline = Color(0xFF38383A),
    outlineVariant = MiuixDarkDivider
)

private val MiuixLightColorScheme = lightColorScheme(
    primary = MiuixBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F3FF),
    onPrimaryContainer = Color(0xFF004899),
    secondary = Color(0xFF6B7280),
    onSecondary = Color.White,
    background = MiuixLightBackground,
    onBackground = MiuixLightTextPrimary,
    surface = MiuixLightCard,
    onSurface = MiuixLightTextPrimary,
    surfaceVariant = MiuixLightCardSecondary,
    onSurfaceVariant = MiuixLightTextSecondary,
    outline = Color(0xFFE5E5EA),
    outlineVariant = MiuixLightDivider
)

@Composable
fun XposedFakeLocationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MiuixDarkColorScheme else MiuixLightColorScheme
    val miuixColors = if (darkTheme) miuixDarkColorScheme() else miuixLightColorScheme()
    val navEventOwner = rememberNavigationEventDispatcherOwner(parent = null)

    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides navEventOwner
    ) {
        MiuixTheme(
            colors = miuixColors
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = Typography,
                content = content
            )
        }
    }
}
