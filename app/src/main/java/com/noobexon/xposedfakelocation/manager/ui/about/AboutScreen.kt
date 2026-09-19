package com.noobexon.xposedfakelocation.manager.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.BuildConfig
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.App
import com.noobexon.xposedfakelocation.manager.ui.components.BlurredBar
import com.noobexon.xposedfakelocation.manager.ui.components.BlurBackdropBox
import com.noobexon.xposedfakelocation.manager.ui.components.CardDivider
import com.noobexon.xposedfakelocation.manager.ui.components.RemoteImage
import com.noobexon.xposedfakelocation.manager.ui.components.StatusRow
import com.noobexon.xposedfakelocation.manager.ui.components.pageScrollModifiers
import com.noobexon.xposedfakelocation.manager.ui.components.rememberBlurBackdrop
import com.noobexon.xposedfakelocation.manager.ui.theme.StatusSuccess
import com.noobexon.xposedfakelocation.manager.ui.theme.StatusWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * HyperOS "About my device" style screen: hero identity header, live status rows, source link
 * and contributor list — rendered with miuix components and the standard page chrome
 * (blurred collapsing top bar, LazyColumn with scroll wiring).
 */
@Composable
fun AboutScreen(
    navController: NavController,
    viewModel: AboutViewModel = viewModel()
) {
    val contributorsState by viewModel.contributorsState.collectAsStateWithLifecycle()
    val xposedService by App.serviceState.collectAsStateWithLifecycle()
    val isModuleActive = xposedService != null
    val colorScheme = MiuixTheme.colorScheme

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.screen_about),
                    subtitle = stringResource(R.string.screen_about_subtitle),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.cd_navigate_back)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        val top = innerPadding.calculateTopPadding()
        val bottom = innerPadding.calculateBottomPadding()
        val contentPadding = remember(top, bottom) {
            PaddingValues(top = top, start = 0.dp, end = 0.dp, bottom = bottom + 16.dp)
        }
        BlurBackdropBox(backdrop) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(
                        enableScrollEndHaptic = true,
                        showTopAppBar = true,
                        topAppBarScrollBehavior = topAppBarScrollBehavior
                    ),
                contentPadding = contentPadding,
            ) {
                item(key = "hero") {
                    AppHeroSection()
                }

                item(key = "status_title") {
                    SmallTitle(text = stringResource(R.string.about_status_section))
                }
                item(key = "status_card") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        StatusRow(
                            title = stringResource(R.string.about_xposed_title),
                            summary = "libxposed 101+",
                            statusText = stringResource(
                                if (isModuleActive) R.string.about_status_active
                                else R.string.about_status_inactive
                            ),
                            statusColor = if (isModuleActive) StatusSuccess else colorScheme.error,
                        )
                        CardDivider()
                        StatusRow(
                            title = stringResource(R.string.about_refresh_title),
                            summary = stringResource(R.string.about_refresh_subtitle),
                            statusText = stringResource(R.string.about_refresh_status),
                            statusColor = colorScheme.primary,
                        )
                        CardDivider()
                        StatusRow(
                            title = stringResource(R.string.about_shield_title),
                            summary = stringResource(R.string.about_shield_subtitle),
                            statusText = stringResource(R.string.about_shield_status),
                            statusColor = StatusSuccess,
                        )
                        CardDivider()
                        StatusRow(
                            title = stringResource(R.string.about_map_engine_title),
                            summary = stringResource(R.string.about_map_engine_subtitle),
                            statusText = stringResource(R.string.about_map_engine_status),
                            statusColor = StatusWarning,
                        )
                    }
                }

                item(key = "source_title") {
                    SmallTitle(text = stringResource(R.string.about_source_section))
                }
                item(key = "source_card") {
                    val context = LocalContext.current
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        ArrowPreference(
                            title = stringResource(R.string.about_github_repo),
                            summary = "Souitou-iop / MockX",
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, "https://github.com/Souitou-iop/MockX".toUri())
                                )
                            },
                        )
                    }
                }

                item(key = "contrib_title") {
                    SmallTitle(text = stringResource(R.string.about_contrib_section))
                }
                item(key = "contrib_card") {
                    val developer = (contributorsState as? ContributorsUiState.Success)?.developer
                        ?: viewModel.developerFallback
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        ContributorRow(developer, roleRes = R.string.about_maintainer_role)
                        CardDivider()
                        ContributorRow(viewModel.contributorFallback, roleRes = R.string.about_contributor_role)
                    }
                }
                item(key = "contributors_card") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        ContributorsList(
                            state = contributorsState,
                            onRetry = { viewModel.loadContributors(forceRefresh = true) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeroSection() {
    val context = LocalContext.current
    val appIconBitmap by produceState<android.graphics.Bitmap?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(context.packageName).toBitmap(128, 128)
            }.getOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val bitmap = appIconBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
        }

        Text(
            text = stringResource(R.string.app_name),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "v${BuildConfig.VERSION_NAME} · ${stringResource(R.string.about_official_release)}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.primary,
        )

        Text(
            text = stringResource(R.string.about_description),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun ContributorRow(
    contributor: Contributor,
    roleRes: Int = R.string.about_maintainer_role,
) {
    val context = LocalContext.current
    BasicComponent(
        title = contributor.name,
        summary = stringResource(roleRes),
        onClick = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, contributor.githubUrl.toUri())
            )
        },
        startAction = {
            RemoteImage(
                url = contributor.avatarUrl,
                contentDescription = contributor.name,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        },
        endActions = {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(16.dp)
            )
        },
    )
}

@Composable
private fun ContributorsList(
    state: ContributorsUiState,
    onRetry: () -> Unit,
) {
    when (state) {
        is ContributorsUiState.Loading -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(size = 20.dp, strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.about_contributors_loading),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
        is ContributorsUiState.Error -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.about_contributors_error),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.error
                )
                TextButton(
                    text = stringResource(R.string.about_contributors_retry),
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
        is ContributorsUiState.Success -> {
            if (state.contributors.isEmpty()) {
                Text(
                    text = stringResource(R.string.about_contributors_empty),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                state.contributors.forEachIndexed { index, contributor ->
                    ContributorRow(contributor)
                    if (index != state.contributors.lastIndex) {
                        CardDivider()
                    }
                }
            }
        }
    }
}
