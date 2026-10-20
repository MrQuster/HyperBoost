package com.example.volumeboost

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.volumeboost.data.AppThemeMode
import com.example.volumeboost.data.BoostMode
import com.example.volumeboost.device.FeatureRegistry
import com.example.volumeboost.device.GlassQualityTier
import com.example.volumeboost.ui.components.*
import com.example.volumeboost.ui.screens.*
import com.example.volumeboost.ui.theme.GlassTokens
import com.example.volumeboost.ui.theme.LocalGlassTokens
import com.example.volumeboost.viewmodel.DevicesViewModel
import com.example.volumeboost.viewmodel.EqualizerViewModel
import com.example.volumeboost.viewmodel.HomeViewModel
import com.example.volumeboost.viewmodel.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

class MainActivity : AppCompatActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private val eqViewModel: EqualizerViewModel by viewModels()
    private val devicesViewModel: DevicesViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
        }
        handleShortcutIntent(intent)

        setContent {
            val context = LocalContext.current
            val globalSettings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val deviceProfile by settingsViewModel.deviceProfile.collectAsStateWithLifecycle()
            val runtime by homeViewModel.runtimeState.collectAsStateWithLifecycle()
            val boostPercent by homeViewModel.boostPercent.collectAsStateWithLifecycle()
            val selectedTab by homeViewModel.selectedTabIndex.collectAsStateWithLifecycle()

            val hazeState = remember { HazeState() }

            val systemPlan = remember(deviceProfile, globalSettings.islandRoutingPreference, globalSettings.powerSaveGlass) {
                FeatureRegistry.resolvePlan(
                    profile = deviceProfile,
                    islandPreference = globalSettings.islandRoutingPreference,
                    powerSaveGlass = globalSettings.powerSaveGlass
                )
            }

            val systemDark = isSystemInDarkTheme()
            val isDark = when (globalSettings.themeMode) {
                AppThemeMode.DARK, AppThemeMode.AMOLED -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> systemDark
            }
            val isAmoled = globalSettings.themeMode == AppThemeMode.AMOLED

            val accent = if (globalSettings.dynamicMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
            } else {
                Color(globalSettings.accentColorHex)
            }

            val useLightweightGlass = systemPlan.glassQualityTier == GlassQualityTier.LIGHTWEIGHT_TRANSLUCENT
            val parallax by rememberSensorParallax(enabled = globalSettings.parallaxEnabled && !useLightweightGlass)

            val tokens = remember(globalSettings, isDark, isAmoled, accent, parallax, useLightweightGlass) {
                GlassTokens(
                    isDark = isDark,
                    isAmoled = isAmoled,
                    baseBackground = when {
                        isAmoled -> Color(0xFF000000)
                        isDark -> Color(0xFF060914)
                        else -> Color(0xFFEBF1F8)
                    },
                    cardBackingColor = when {
                        isAmoled -> Color(0xCC05070E)
                        isDark -> Color(0xBD0A1022)
                        else -> Color(0xD9FFFFFF)
                    },
                    navBarBackingColor = when {
                        isAmoled -> Color(0x9904060C)
                        isDark -> Color(0x8C080D1C)
                        else -> Color(0xB3F1F5F9)
                    },
                    accentColor = accent,
                    textPrimary = if (isDark) Color.White else Color(0xFF0F172A),
                    textSecondary = if (isDark) Color(0xD9E2E8F0) else Color(0xB31E293B),
                    glassSurfaceAlpha = globalSettings.glassAlpha,
                    blurRadius = globalSettings.blurIntensityDp.dp,
                    parallaxOffset = parallax,
                    powerSaveMode = useLightweightGlass
                )
            }

            CompositionLocalProvider(LocalGlassTokens provides tokens) {
                MaterialTheme {
                    var pendingMode by remember { mutableStateOf<BoostMode?>(null) }

                    val projectionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { res ->
                        if (res.resultCode == RESULT_OK && res.data != null) {
                            homeViewModel.startCaptureService(res.resultCode, res.data!!)
                        }
                    }

                    val permissionsLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { grants ->
                        val mode = pendingMode ?: return@rememberLauncherForActivityResult
                        if (mode == BoostMode.CAPTURE) {
                            if (grants[Manifest.permission.RECORD_AUDIO] == true) {
                                val mp = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                projectionLauncher.launch(mp.createScreenCaptureIntent())
                            } else {
                                homeViewModel.startStandardService()
                            }
                        } else {
                            homeViewModel.startStandardService()
                        }
                    }

                    val startEngineRequest: (BoostMode) -> Unit = { mode ->
                        pendingMode = mode
                        val perms = mutableListOf<String>()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            perms.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                        perms.add(Manifest.permission.RECORD_AUDIO)
                        permissionsLauncher.launch(perms.toTypedArray())
                    }

                    val navTabs = listOf(
                        GlassTabItem(stringResource(R.string.nav_home), Icons.Default.Home),
                        GlassTabItem(stringResource(R.string.nav_eq), Icons.Default.Equalizer),
                        GlassTabItem(stringResource(R.string.nav_devices), Icons.Default.Headphones),
                        GlassTabItem(stringResource(R.string.nav_settings), Icons.Default.Settings),
                        GlassTabItem(stringResource(R.string.nav_system), Icons.Default.Memory)
                    )

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isWideScreen = maxWidth >= 600.dp || systemPlan.useSideNavigationRail

                        if (!globalSettings.onboardingCompleted) {
                            DynamicBlobBackground(
                                boostPercent = boostPercent,
                                audioPeakLevel = runtime.peakSignal,
                                animationSpeed = globalSettings.bgAnimationSpeed
                            )
                            OnboardingOverlay(
                                onFinishOnboarding = {
                                    settingsViewModel.update { it.copy(onboardingCompleted = true) }
                                }
                            )
                        } else if (isWideScreen) {
                            DynamicBlobBackground(
                                boostPercent = boostPercent,
                                audioPeakLevel = runtime.peakSignal,
                                animationSpeed = globalSettings.bgAnimationSpeed
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                            ) {
                                GlassSideNavigationRail(
                                    items = navTabs,
                                    selectedIndex = selectedTab,
                                    onSelect = { homeViewModel.selectTab(it) }
                                )
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    when (selectedTab) {
                                        0 -> HomeScreen(homeViewModel, startEngineRequest)
                                        1 -> EqualizerScreen(eqViewModel)
                                        2 -> DevicesScreen(devicesViewModel)
                                        3 -> SettingsScreen(
                                            viewModel = settingsViewModel,
                                            homeViewModel = homeViewModel,
                                            onOpenSystemTab = { homeViewModel.selectTab(4) }
                                        )
                                        4 -> SystemScreen(settingsViewModel)
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Весь прокручиваемый контент помечен как источник размытия .haze(state = hazeState)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .haze(state = hazeState)
                                ) {
                                    DynamicBlobBackground(
                                        boostPercent = boostPercent,
                                        audioPeakLevel = runtime.peakSignal,
                                        animationSpeed = globalSettings.bgAnimationSpeed
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .statusBarsPadding()
                                    ) {
                                        when (selectedTab) {
                                            0 -> HomeScreen(homeViewModel, startEngineRequest)
                                            1 -> EqualizerScreen(eqViewModel)
                                            2 -> DevicesScreen(devicesViewModel)
                                            3 -> SettingsScreen(
                                                viewModel = settingsViewModel,
                                                homeViewModel = homeViewModel,
                                                onOpenSystemTab = { homeViewModel.selectTab(4) }
                                            )
                                            4 -> SystemScreen(settingsViewModel)
                                        }
                                    }
                                }

                                GlassLensTabBar(
                                    items = navTabs,
                                    selectedIndex = selectedTab,
                                    onSelect = { homeViewModel.selectTab(it) },
                                    hazeState = hazeState,
                                    freeLensDragEnabled = globalSettings.freeLensDragEnabled,
                                    refractionStrength = if (systemPlan.glassQualityTier == GlassQualityTier.FULL_AGSL_SHADER) {
                                        globalSettings.lensRefractionStrength
                                    } else 0f,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .navigationBarsPadding()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.action) {
            "io.github.mrquster.hyperboost.SHORTCUT_ON" -> homeViewModel.startStandardService()
            "io.github.mrquster.hyperboost.SHORTCUT_PLUS_10" -> {
                val next = homeViewModel.boostPercent.value + 10
                homeViewModel.setBoostPercent(next)
                homeViewModel.startStandardService(next)
            }
            "io.github.mrquster.hyperboost.SHORTCUT_OFF" -> homeViewModel.stopService()
        }
    }
}
