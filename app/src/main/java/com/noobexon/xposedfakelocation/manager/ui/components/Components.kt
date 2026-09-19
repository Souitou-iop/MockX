package com.noobexon.xposedfakelocation.manager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

// region Page chrome (HyperLyrics-Enhanced style)

/**
 * Remembers the backdrop used to blur the top app bar over scrolling content.
 * Returns null on devices without runtime shader support; callers must fall back to an
 * opaque surface-colored bar.
 */
@Composable
fun rememberBlurBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/**
 * Top bar container: live-blurs the content scrolling underneath, matching the HyperOS
 * large-title look. Falls back to a plain surface bar when blur is unsupported.
 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = if (blurEnabled && backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.8f)),
                    ),
                ),
            )
        } else {
            Modifier
        },
    ) {
        content()
    }
}

/**
 * Standard scroll wiring for page content: over-scroll glow, scroll-end haptics, and the
 * nested-scroll connection that drives the top app bar's collapse/expand.
 */
fun Modifier.pageScrollModifiers(
    enableScrollEndHaptic: Boolean = true,
    showTopAppBar: Boolean = true,
    topAppBarScrollBehavior: ScrollBehavior,
): Modifier = this
    .then(if (enableScrollEndHaptic) Modifier.scrollEndHaptic() else Modifier)
    .overScrollVertical()
    .then(if (showTopAppBar) Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection) else Modifier)
    .fillMaxHeight()

/** Content Box that feeds the top bar's blur backdrop. */
@Composable
fun BlurBackdropBox(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = if (backdrop != null) modifier.layerBackdrop(backdrop) else modifier) {
        content()
    }
}

// endregion

// region Search

/**
 * HyperOS capsule search field built on miuix [InputField], with the default leading search
 * icon and trailing clear button.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = {},
        expanded = false,
        onExpandedChange = {},
        label = label,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
    )
}

// endregion

// region Buttons & chips

/**
 * Filled pill action button in the HyperOS style, built on the miuix [Button] so press
 * feedback and colors follow the active theme (including Monet).
 */
@Composable
fun PillActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isPrimary: Boolean = true,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = when {
        isDestructive -> ButtonDefaults.buttonColors(
            color = MiuixTheme.colorScheme.error,
            contentColor = MiuixTheme.colorScheme.onError,
        )
        isPrimary -> ButtonDefaults.buttonColorsPrimary()
        else -> ButtonDefaults.buttonColors()
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Small status chip for counters and states (e.g. "3 selected").
 */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    activeColor: Color = MiuixTheme.colorScheme.primary,
) {
    val colorScheme = MiuixTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) activeColor.copy(alpha = 0.12f) else colorScheme.outline.copy(alpha = 0.12f),
        contentColor = if (isActive) activeColor else colorScheme.onSurfaceVariantSummary,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// endregion

// region Map overlays

/**
 * Frosted panel background: samples [backdrop] through miuix texture blur clipped to the panel
 * capsule, falling back to a translucent surface when blur is unavailable. [tintAlpha] controls
 * how much surface color is blended over the blurred content — lower is more transparent.
 */
@Composable
private fun PanelSurface(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    modifier: Modifier = Modifier,
    tintAlpha: Float = 0.5f,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    val blurActive = blurEnabled && backdrop != null
    Surface(
        modifier = modifier.then(
            if (blurActive && backdrop != null) {
                Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = shape,
                    blurRadius = 25f,
                    colors = BlurColors(
                        blendColors = listOf(
                            BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = tintAlpha)),
                        ),
                    ),
                )
            } else {
                Modifier
            }
        ),
        color = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = shape,
    ) {
        content()
    }
}

/**
 * HyperOS floating capsule panel used as the map screen's top status island.
 */
@Composable
fun FloatingPanel(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    PanelSurface(backdrop = backdrop, blurEnabled = blurEnabled, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            content = content,
        )
    }
}

/**
 * HyperOS floating bottom dashboard panel used for map controls.
 */
@Composable
fun BottomControlPanel(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    PanelSurface(backdrop = backdrop, blurEnabled = blurEnabled, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

// endregion

// region Misc

/**
 * Inset hairline divider between rows inside a grouped card.
 */
@Composable
fun CardDivider(
    modifier: Modifier = Modifier,
    startPadding: Dp = 16.dp,
    endPadding: Dp = 16.dp,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startPadding, end = endPadding),
        color = MiuixTheme.colorScheme.dividerLine,
    )
}

/**
 * Centered circular loading indicator.
 */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier = modifier)
}

/**
 * A labelled list row with trailing status text, rendered with miuix [BasicComponent].
 */
@Composable
fun StatusRow(
    title: String,
    summary: String,
    statusText: String,
    modifier: Modifier = Modifier,
    statusColor: Color = MiuixTheme.colorScheme.primary,
) {
    BasicComponent(
        modifier = modifier,
        title = title,
        summary = summary,
        endActions = {
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
    )
}

// endregion

// region Dialogs

/**
 * miuix dialog with the standard HyperOS button row. Buttons render only when the
 * corresponding text is provided; each fills half the width as in system dialogs.
 */
@Composable
fun AppDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmDestructive: Boolean = false,
    dismissText: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    WindowDialog(
        show = true,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()

            if (confirmText != null || dismissText != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dismissText != null) {
                        TextButton(
                            text = dismissText,
                            onClick = onDismissRequest,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColors(),
                        )
                    }
                    if (dismissText != null && confirmText != null) {
                        Spacer(modifier = Modifier.width(20.dp))
                    }
                    if (confirmText != null) {
                        TextButton(
                            text = confirmText,
                            onClick = { onConfirm?.invoke() },
                            modifier = Modifier.weight(1f),
                            colors = if (confirmDestructive) {
                                ButtonDefaults.textButtonColors(
                                    color = MiuixTheme.colorScheme.error,
                                    textColor = MiuixTheme.colorScheme.onError,
                                )
                            } else {
                                ButtonDefaults.textButtonColorsPrimary()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Text input dialog in the standard HyperOS layout: miuix [TextField] plus the confirm/dismiss
 * button row.
 */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmText: String,
    dismissText: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    var inputValue by remember { mutableStateOf(initialValue) }

    AppDialog(
        title = title,
        onDismissRequest = onDismissRequest,
        confirmText = confirmText,
        onConfirm = {
            onConfirm(inputValue.trim())
            onDismissRequest()
        },
        dismissText = dismissText,
        modifier = modifier,
    ) {
        TextField(
            value = inputValue,
            onValueChange = { inputValue = it },
            label = label,
            useLabelAsPlaceholder = true,
            singleLine = singleLine,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }
}

// endregion
