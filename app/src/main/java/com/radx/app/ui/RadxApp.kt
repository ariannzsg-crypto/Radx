package com.radx.app.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.net.VpnService
import android.text.TextUtils
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radx.app.R
import com.radx.app.data.Profile
import com.radx.app.data.TunnelMode
import com.radx.app.ui.components.GlassBackdrop
import com.radx.app.ui.components.GlassNavBar
import com.radx.app.ui.components.ImportSheet
import com.radx.app.ui.components.LocalGlassEffects
import com.radx.app.ui.components.QrImageDialog
import com.radx.app.qr.QrCodec
import com.radx.app.ui.screens.HomeScreen
import com.radx.app.ui.screens.LogsScreen
import com.radx.app.ui.screens.NodesScreen
import com.radx.app.ui.screens.SettingsScreen
import com.radx.app.ui.theme.RadxTheme
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun RadxApp(viewModel: RadxViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LocalizedContent(state.settings.language) {
        RadxTheme(accent = state.settings.accent, dynamicColor = state.settings.dynamicColor) {
            CompositionLocalProvider(LocalGlassEffects provides state.settings.glassEffects) {
                GlassBackdrop {
                    RadxAppContent(state = state, viewModel = viewModel)
                }
            }
        }
    }
}

/**
 * Applies the language picked in the settings without restarting the activity.
 *
 * The strings and the reading direction come from a localised context, so a Persian
 * interface stays RTL on an English device and the other way round.
 */
@Composable
private fun LocalizedContent(language: String, content: @Composable () -> Unit) {
    if (language == "auto") {
        content()
        return
    }
    val base = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val locale = remember(language) { Locale.forLanguageTag(language) }
    val configuration = remember(language, baseConfiguration) {
        Configuration(baseConfiguration).apply { setLocale(locale) }
    }
    val localized = remember(configuration) { base.createConfigurationContext(configuration) }
    val direction = if (TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides configuration,
        LocalLayoutDirection provides direction,
    ) {
        content()
    }
}

@Composable
private fun RadxAppContent(state: UiState, viewModel: RadxViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Profile?>(null) }
    var qrTarget by remember { mutableStateOf<Profile?>(null) }
    var autoConnectDone by rememberSaveable { mutableStateOf(false) }

    // The whole surface eases in once, instead of popping in with the first frame.
    // Slides follow the reading direction, so the same motion feels right in Persian.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "entrance",
    )

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbarHost.showSnackbar(event.message)
        }
    }

    val vpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.connect()
        } else {
            viewModel.reportVpnDenied()
        }
    }

    val onToggle: () -> Unit = {
        when {
            state.connected || state.busy -> viewModel.disconnect()
            state.settings.tunnelMode == TunnelMode.VPN -> {
                val intent = VpnService.prepare(context)
                if (intent != null) vpnPermission.launch(intent) else viewModel.connect()
            }

            else -> viewModel.connect()
        }
    }

    val showCopied: (String) -> Unit = { message ->
        scope.launch { snackbarHost.showSnackbar(message) }
    }

    val copyText: (String) -> Unit = { text ->
        clipboard.setText(AnnotatedString(text))
        showCopied(context.getString(R.string.copied))
    }

    val shareText: (String) -> Unit = { text ->
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
    }

    // Auto-connect never asks for the VPN permission itself: it only brings the tunnel
    // up when the permission was granted on an earlier run.
    LaunchedEffect(state.settings.autoConnect, state.selectedProfileId, autoConnectDone) {
        if (autoConnectDone || !state.settings.autoConnect) return@LaunchedEffect
        if (!state.coreBundled || state.selectedProfile == null) return@LaunchedEffect
        if (state.connected || state.busy) return@LaunchedEffect
        autoConnectDone = true
        if (state.settings.tunnelMode == TunnelMode.VPN) {
            if (VpnService.prepare(context) == null) viewModel.connect()
        } else {
            viewModel.connect()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            GlassNavBar(
                items = listOf(
                    R.string.tab_home to Icons.Rounded.Home,
                    R.string.tab_nodes to Icons.Rounded.Dns,
                    R.string.tab_logs to Icons.Rounded.ReceiptLong,
                    R.string.tab_settings to Icons.Rounded.Settings,
                ),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.graphicsLayer {
                    alpha = entrance
                    translationY = (1f - entrance) * 48.dp.toPx()
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .graphicsLayer {
                    alpha = entrance
                    translationY = (1f - entrance) * 16.dp.toPx()
                    val scale = 0.985f + 0.015f * entrance
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val forward = targetState > initialState
                    val shift = if (rtl) -1 else 1
                    (
                        fadeIn(tween(220)) +
                            slideInHorizontally(tween(260)) { width ->
                                shift * if (forward) width / 8 else -width / 8
                            }
                        ).togetherWith(
                        fadeOut(tween(150)) +
                            slideOutHorizontally(tween(200)) { width ->
                                -shift * if (forward) width / 10 else -width / 10
                            },
                    )
                },
                label = "tab",
            ) { index ->
                when (index) {
                0 -> HomeScreen(
                    state = state,
                    onToggle = onToggle,
                    onAddConfig = { showImport = true },
                    onOpenNodes = { tab = 1 },
                    onSelectProfile = viewModel::selectProfile,
                    onUpdateProfile = { profile -> viewModel.refreshSubscription(profile.id) },
                    onDeleteProfile = { profile ->
                        if (state.settings.deleteConfirm) {
                            deleteTarget = profile
                        } else {
                            viewModel.deleteProfile(profile.id)
                        }
                    },
                    onCopyProfile = { profile ->
                        copyText(profile.raw.ifBlank { profile.subscriptionUrl.orEmpty() })
                    },
                    onShareProfile = { profile ->
                        shareText(profile.raw.ifBlank { profile.subscriptionUrl.orEmpty() })
                    },
                    onShowQr = { profile -> qrTarget = profile },
                    onSetTunnelMode = { mode ->
                        viewModel.updateSettings { it.copy(tunnelMode = mode) }
                    },
                )

                1 -> NodesScreen(
                    state = state,
                    onSelect = viewModel::selectNode,
                    onPing = viewModel::pingAll,
                    twoColumns = state.settings.twoColumnLayout,
                )

                2 -> LogsScreen(onCopy = copyText)

                else -> SettingsScreen(
                    state = state,
                    onUpdate = viewModel::updateSettings,
                )
                }
            }
        }
    }

    if (showImport) {
        ImportSheet(
            importing = state.importing,
            onDismiss = { showImport = false },
            onImportText = { text, name -> viewModel.importText(text, name) },
            onImportUrl = { url, name -> viewModel.importSubscription(url, name) },
            onImportFile = { uri: Uri ->
                showImport = false
                viewModel.importFile(uri)
            },
        )
    }

    val qr = qrTarget
    if (qr != null) {
        QrImageDialog(
            payload = QrCodec.payload(qr.subscriptionUrl, qr.raw),
            title = stringResource(R.string.qr_title),
            onDismiss = { qrTarget = null },
        )
    }

    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_profile_title)) },
            text = { Text(stringResource(R.string.delete_profile_message, target.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProfile(target.id)
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}
