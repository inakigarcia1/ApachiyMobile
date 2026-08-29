package com.nuvio.app

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.nuvio.app.core.account.InactiveSubscriptionNotifier
import com.nuvio.app.core.auth.DeviceLimitNotifier
import com.nuvio.app.core.ui.DeviceLimitDialog
import com.nuvio.app.core.ui.InactiveSubscriptionDialog
import com.nuvio.app.core.ui.NativeProfileSwitcherController
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.core.ui.ProtocolRelativeImageUrlMapper
import com.nuvio.app.core.ui.configurePlatformImageLoader
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.navigation.AppRoute
import com.nuvio.app.navigation.TabsRoute

fun disposeRoute(route: AppRoute) {
    disposeRouteResources(route)
}

@OptIn(ExperimentalCoilApi::class)
@Composable
@Preview
fun App(
    initialTab: AppScreenTab = AppScreenTab.Home,
    initialRoute: AppRoute = TabsRoute,
    useNativeNavigation: Boolean = false,
    useNativeTabBar: Boolean = false,
    useTabletFloatingTabBar: Boolean = false,
    ownsAppRuntime: Boolean = true,
    bypassAppGate: Boolean = false,
    onNavigate: ((AppRoute, launchSingleTop: Boolean) -> Unit)? = null,
    onGoBack: (() -> Unit)? = null,
    onReplace: ((AppRoute) -> Unit)? = null,
    onActivate: ((AppScreenTab) -> Unit)? = null,
    onAppReady: ((Boolean) -> Unit)? = null,
    onTabTitles: ((home: String, search: String, library: String, profile: String, switchProfile: String, addProfile: String) -> Unit)? = null,
    nativeProfileSwitcherController: NativeProfileSwitcherController? = null,
    appGateController: AppGateController? = null,
) {
    AppEnvironment {
        AppGate(
            initialTab = initialTab,
            initialRoute = initialRoute,
            useNativeNavigation = useNativeNavigation,
            useNativeTabBar = useNativeTabBar,
            useTabletFloatingTabBar = useTabletFloatingTabBar,
            ownsAppRuntime = ownsAppRuntime,
            bypassAppGate = bypassAppGate,
            renderMainContent = true,
            onNavigate = onNavigate,
            onGoBack = onGoBack,
            onReplace = onReplace,
            onActivate = onActivate,
            onAppReady = onAppReady,
            onMainContentMountChanged = null,
            onMainContentVisibleChanged = null,
            onTabTitles = onTabTitles,
            nativeProfileSwitcherController = nativeProfileSwitcherController,
            appGateController = appGateController,
        )
    }
}

@Composable
internal fun AppEnvironment(content: @Composable () -> Unit) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .components {
                add(SvgDecoder.Factory())
                add(ProtocolRelativeImageUrlMapper())
            }
            .configurePlatformImageLoader()
            .build()
    }
    val selectedTheme by remember {
        ThemeSettingsRepository.ensureLoaded()
        ThemeSettingsRepository.selectedTheme
    }.collectAsStateWithLifecycle()
    val amoledEnabled by remember {
        ThemeSettingsRepository.amoledEnabled
    }.collectAsStateWithLifecycle()

    NuvioTheme(appTheme = selectedTheme, amoled = amoledEnabled) {
        var showDeviceLimitDialog by remember { mutableStateOf(false) }
        var showInactiveSubscriptionDialog by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            DeviceLimitNotifier.events.collect { showDeviceLimitDialog = true }
        }
        LaunchedEffect(Unit) {
            InactiveSubscriptionNotifier.events.collect { showInactiveSubscriptionDialog = true }
        }
        Box {
            content()
            if (showDeviceLimitDialog) {
                DeviceLimitDialog(onDismiss = { showDeviceLimitDialog = false })
            }
            if (showInactiveSubscriptionDialog) {
                InactiveSubscriptionDialog(onDismiss = { showInactiveSubscriptionDialog = false })
            }
        }
    }
}
