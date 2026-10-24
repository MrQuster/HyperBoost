package com.example.volumeboost.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.volumeboost.R
import com.example.volumeboost.data.*
import com.example.volumeboost.device.FeatureRegistry
import com.example.volumeboost.device.VendorBackgroundGuide
import com.example.volumeboost.device.VendorSetupAction
import com.example.volumeboost.island.ShizukuIslandBypassHelper
import com.example.volumeboost.ui.components.*
import com.example.volumeboost.ui.theme.AccentPalettes
import com.example.volumeboost.ui.theme.LocalGlassTokens
import com.example.volumeboost.util.AppInfo
import com.example.volumeboost.util.LocaleHelper
import com.example.volumeboost.viewmodel.HomeViewModel
import com.example.volumeboost.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    homeViewModel: HomeViewModel,
    onOpenSystemTab: () -> Unit = {}
) {
    val context = LocalContext.current
    val tokens = LocalGlassTokens.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val deviceProfile by viewModel.deviceProfile.collectAsStateWithLifecycle()
    val currentBoost by homeViewModel.boostPercent.collectAsStateWithLifecycle()

    val isEn = !LocaleHelper.isRussianActive(context, settings.languageCode)
    val systemPlan = remember(deviceProfile, settings.islandRoutingPreference, settings.powerSaveGlass) {
        FeatureRegistry.resolvePlan(deviceProfile, settings.islandRoutingPreference, settings.powerSaveGlass)
    }

    var manualInputText by remember(currentBoost) { mutableStateOf(currentBoost.toString()) }
    var showResetSettingsDialog by remember { mutableStateOf(false) }
    var showClearProfilesDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = viewModel.exportBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                Toast.makeText(context, context.getString(R.string.toast_exported), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
                val ok = viewModel.importBackupJson(json)
                Toast.makeText(
                    context,
                    context.getString(if (ok) R.string.toast_imported_ok else R.string.toast_imported_err),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = tokens.spacingLarge, vertical = tokens.spacingMedium),
        verticalArrangement = Arrangement.spacedBy(tokens.spacingLarge)
    ) {
        // 0. Суперостров (Hyper Island) и Уведомления
        SectionHeader(stringResource(R.string.sec_island))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingToggleRow(
                    title = stringResource(R.string.island_enable_toggle),
                    checked = settings.islandEnabled,
                    onCheckedChange = { v -> viewModel.update { it.copy(islandEnabled = v) } }
                )

                Text(
                    text = stringResource(R.string.island_routing_label),
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IslandRoutingPreference.entries.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { pref ->
                                GlassChip(
                                    label = if (isEn) pref.labelEn else pref.labelRu,
                                    selected = settings.islandRoutingPreference == pref,
                                    onClick = { viewModel.update { it.copy(islandRoutingPreference = pref) } },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.island_style_label),
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IslandStyleMode.entries.forEach { style ->
                        GlassChip(
                            label = if (isEn) style.labelEn else style.labelRu,
                            selected = settings.islandStyle == style,
                            onClick = { viewModel.update { it.copy(islandStyle = style) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.island_color_label),
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IslandColorPreset.entries.forEach { preset ->
                        GlassChip(
                            label = if (isEn) preset.labelEn else preset.labelRu,
                            selected = settings.islandColorPreset == preset,
                            onClick = { viewModel.update { it.copy(islandColorPreset = preset) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                SettingToggleRow(
                    title = stringResource(R.string.island_only_playing),
                    checked = settings.islandOnlyWhenPlaying,
                    onCheckedChange = { v -> viewModel.update { it.copy(islandOnlyWhenPlaying = v) } }
                )

                SettingToggleRow(
                    title = stringResource(R.string.island_lockscreen_actions),
                    checked = settings.islandAllowLockscreenActions,
                    onCheckedChange = { v -> viewModel.update { it.copy(islandAllowLockscreenActions = v) } }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = stringResource(R.string.island_test_btn),
                        icon = Icons.Default.Visibility,
                        isAccent = true,
                        onClick = { viewModel.triggerIslandPreview() },
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = stringResource(R.string.island_open_notif_btn),
                        icon = Icons.Default.NotificationsActive,
                        onClick = {
                            VendorBackgroundGuide.executeActionSafely(
                                context,
                                VendorSetupAction.OPEN_NOTIFICATIONS_AND_ISLAND
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                SettingToggleRow(
                    title = stringResource(R.string.island_shizuku_toggle),
                    checked = settings.islandShizukuBypassEnabled,
                    onCheckedChange = { v -> viewModel.update { it.copy(islandShizukuBypassEnabled = v) } }
                )

                AnimatedVisibility(visible = settings.islandShizukuBypassEnabled) {
                    val commands = remember { ShizukuIslandBypassHelper.buildManualAdbCommands(context.packageName) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = commands.trim(),
                            color = tokens.accentColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 1. Звук, шкала усиления и DSP
        SectionHeader(stringResource(R.string.sec_sound))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(tokens.accentColor.copy(alpha = 0.14f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (isEn) systemPlan.vendorAudioNoteEn else systemPlan.vendorAudioNoteRu,
                        color = tokens.textPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }

                SettingToggleRow(
                    title = stringResource(R.string.sound_unlimited_toggle),
                    checked = settings.unlimitedBoostEnabled,
                    onCheckedChange = { v -> viewModel.update { it.copy(unlimitedBoostEnabled = v) } }
                )

                Text(
                    text = stringResource(R.string.sound_manual_input_label),
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = manualInputText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 5) {
                                manualInputText = input
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = {
                            val maxAllowed = BoostScaleMapper.maxAllowedPercent(settings.unlimitedBoostEnabled)
                            Text("0 .. $maxAllowed%", color = tokens.textSecondary)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = tokens.textPrimary,
                            unfocusedTextColor = tokens.textPrimary,
                            focusedBorderColor = tokens.accentColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = stringResource(R.string.action_apply),
                        isAccent = true,
                        onClick = {
                            val parsed = manualInputText.toIntOrNull() ?: 100
                            if (parsed > BoostScaleMapper.STANDARD_MAX_PERCENT && !settings.unlimitedBoostEnabled) {
                                viewModel.update { it.copy(unlimitedBoostEnabled = true) }
                            }
                            homeViewModel.setBoostPercent(parsed)
                        }
                    )
                }

                Text(
                    text = stringResource(R.string.sound_limiter_mode_label),
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LimiterMode.entries.forEach { mode ->
                        GlassChip(
                            label = if (isEn) mode.labelEn else mode.labelRu,
                            selected = settings.limiterMode == mode,
                            onClick = { viewModel.update { it.copy(limiterMode = mode) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                LabeledGlassSliderRow(
                    title = stringResource(R.string.sound_limiter_thresh),
                    valueText = stringResource(R.string.unit_db, "%.1f".format(settings.limiterThresholdDb)),
                    value = settings.limiterThresholdDb,
                    onValueChange = { v -> viewModel.update { it.copy(limiterThresholdDb = v) } },
                    valueRange = -15f..0f
                )

                LabeledGlassSliderRow(
                    title = stringResource(R.string.sound_limiter_attack),
                    valueText = stringResource(R.string.unit_ms, settings.attackMs),
                    value = settings.attackMs.toFloat(),
                    onValueChange = { v -> viewModel.update { it.copy(attackMs = v.toInt().coerceAtLeast(1)) } },
                    valueRange = 1f..50f
                )

                LabeledGlassSliderRow(
                    title = stringResource(R.string.sound_fade_duration),
                    valueText = stringResource(R.string.unit_ms, settings.fadeDurationMs),
                    value = settings.fadeDurationMs.toFloat(),
                    onValueChange = { v -> viewModel.update { it.copy(fadeDurationMs = v.toInt()) } },
                    valueRange = 20f..1000f
                )
            }
        }

        // 2. Внешний вид и Линза Liquid Glass
        SectionHeader(stringResource(R.string.sec_appearance))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = stringResource(R.string.theme_label),
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                val themes = AppThemeMode.entries
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        themes.take(2).forEach { mode ->
                            GlassChip(
                                label = if (isEn) mode.labelEn else mode.labelRu,
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.update { it.copy(themeMode = mode) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        themes.drop(2).forEach { mode ->
                            GlassChip(
                                label = if (isEn) mode.labelEn else mode.labelRu,
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.update { it.copy(themeMode = mode) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                SettingToggleRow(
                    title = stringResource(R.string.lens_free_drag),
                    checked = settings.freeLensDragEnabled,
                    onCheckedChange = { v -> viewModel.update { it.copy(freeLensDragEnabled = v) } }
                )

                LabeledGlassSliderRow(
                    title = stringResource(R.string.lens_refraction),
                    valueText = "${(settings.lensRefractionStrength * 100).toInt()}%",
                    value = settings.lensRefractionStrength,
                    onValueChange = { v -> viewModel.update { it.copy(lensRefractionStrength = v) } },
                    valueRange = 0f..1f
                )

                SettingToggleRow(
                    title = stringResource(R.string.material_you_toggle),
                    checked = settings.dynamicMaterialYou,
                    onCheckedChange = { v -> viewModel.update { it.copy(dynamicMaterialYou = v) } }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AccentPalettes.PresetColors.forEach { (hex, colorName) ->
                        val isSelected = settings.accentColorHex == hex
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { viewModel.update { it.copy(accentColorHex = hex, dynamicMaterialYou = false) } }
                        )
                    }
                }

                LabeledGlassSliderRow(
                    title = stringResource(R.string.blur_radius_label),
                    valueText = "${settings.blurIntensityDp.toInt()} dp",
                    value = settings.blurIntensityDp,
                    onValueChange = { v -> viewModel.update { it.copy(blurIntensityDp = v) } },
                    valueRange = 20f..90f
                )

                LabeledGlassSliderRow(
                    title = stringResource(R.string.glass_density_label),
                    valueText = "${(settings.glassAlpha * 100).toInt()}%",
                    value = settings.glassAlpha,
                    onValueChange = { v -> viewModel.update { it.copy(glassAlpha = v) } },
                    valueRange = 0.08f..0.38f
                )

                SettingToggleRow(
                    title = stringResource(R.string.parallax_toggle),
                    checked = settings.parallaxEnabled,
                    onCheckedChange = { v -> viewModel.update { it.copy(parallaxEnabled = v) } }
                )

                SettingToggleRow(
                    title = stringResource(R.string.powersave_glass_toggle),
                    checked = settings.powerSaveGlass,
                    onCheckedChange = { v -> viewModel.update { it.copy(powerSaveGlass = v) } }
                )

                Text(
                    text = stringResource(R.string.language_label),
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassChip(
                        label = stringResource(R.string.lang_system),
                        selected = settings.languageCode == "system",
                        onClick = { viewModel.setAppLanguage("system") },
                        modifier = Modifier.weight(1f)
                    )
                    GlassChip(
                        label = stringResource(R.string.lang_ru),
                        selected = settings.languageCode == "ru",
                        onClick = { viewModel.setAppLanguage("ru") },
                        modifier = Modifier.weight(1f)
                    )
                    GlassChip(
                        label = stringResource(R.string.lang_en),
                        selected = settings.languageCode == "en",
                        onClick = { viewModel.setAppLanguage("en") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Поведение
        SectionHeader(stringResource(R.string.sec_behavior))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingToggleRow(
                    title = stringResource(R.string.beh_autostart_bt),
                    checked = settings.autoStartOnBluetooth,
                    onCheckedChange = { v -> viewModel.update { it.copy(autoStartOnBluetooth = v) } }
                )
                SettingToggleRow(
                    title = stringResource(R.string.beh_start_boot),
                    checked = settings.startOnBoot,
                    onCheckedChange = { v -> viewModel.update { it.copy(startOnBoot = v) } }
                )
                SettingToggleRow(
                    title = stringResource(R.string.beh_compact_notif),
                    checked = settings.compactNotification,
                    onCheckedChange = { v -> viewModel.update { it.copy(compactNotification = v) } }
                )
                SettingToggleRow(
                    title = stringResource(R.string.beh_floating_btn),
                    checked = settings.floatingButtonEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !Settings.canDrawOverlays(context)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            viewModel.update { it.copy(floatingButtonEnabled = enabled) }
                        }
                    }
                )
            }
        }

        // 4. Адаптивный мастер настройки фоновой работы
        val vendorGuideTitle = remember(deviceProfile.osFamily, isEn) {
            VendorBackgroundGuide.getTitleForFamily(deviceProfile.osFamily, isEn)
        }
        val vendorSteps = remember(deviceProfile) {
            VendorBackgroundGuide.getStepsForProfile(deviceProfile)
        }

        SectionHeader(vendorGuideTitle)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                vendorSteps.forEachIndexed { idx, step ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (isEn) step.descriptionEn else step.descriptionRu,
                            color = tokens.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        GlassButton(
                            text = if (isEn) step.stepTitleEn else step.stepTitleRu,
                            icon = if (idx == 0) Icons.Default.RocketLaunch else Icons.Default.Shield,
                            isAccent = idx == 0,
                            onClick = {
                                VendorBackgroundGuide.executeActionSafely(context, step.actionType)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // 5. Резервное копирование и сброс
        SectionHeader(stringResource(R.string.sec_data))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = stringResource(R.string.btn_export_json),
                        icon = Icons.Default.UploadFile,
                        onClick = { exportLauncher.launch("hyperboost_backup.json") },
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = stringResource(R.string.btn_import_json),
                        icon = Icons.Default.Download,
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = stringResource(R.string.btn_reset_settings),
                        icon = Icons.Default.RestartAlt,
                        isDanger = true,
                        onClick = { showResetSettingsDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = stringResource(R.string.btn_clear_profiles),
                        icon = Icons.Default.DeleteSweep,
                        isDanger = true,
                        onClick = { showClearProfilesDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 6. Переход на вкладку «Система»
        SectionHeader(stringResource(R.string.sec_system_link))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.sys_link_desc),
                    color = tokens.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                GlassButton(
                    text = stringResource(R.string.btn_open_system_tab),
                    icon = Icons.Default.Memory,
                    isAccent = true,
                    onClick = onOpenSystemTab,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 7. Новая секция: «О приложении и Авторе» (MrQuster)
        SectionHeader(stringResource(R.string.sec_about_author))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(tokens.accentColor.copy(alpha = 0.22f))
                                .border(1.dp, tokens.accentColor.copy(alpha = 0.55f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = AppInfo.AUTHOR_NAME,
                                tint = tokens.accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = AppInfo.AUTHOR_NAME,
                                color = tokens.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.about_author_role),
                                color = tokens.accentColor,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    GlassButton(
                        text = stringResource(R.string.btn_author_github),
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        isAccent = true,
                        onClick = {
                            AppInfo.openUrlSafely(context, AppInfo.GITHUB_PROFILE_URL)
                        }
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${AppInfo.APP_NAME} • " + stringResource(
                            R.string.about_version_fmt,
                            AppInfo.VERSION_NAME,
                            AppInfo.VERSION_CODE
                        ),
                        color = tokens.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(
                            R.string.about_copyright_fmt,
                            AppInfo.COPYRIGHT_YEAR,
                            AppInfo.AUTHOR_NAME
                        ),
                        color = tokens.textSecondary,
                        fontSize = 12.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = stringResource(R.string.btn_source_code),
                        icon = Icons.Default.Code,
                        onClick = { AppInfo.openUrlSafely(context, AppInfo.REPO_URL) },
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = stringResource(R.string.btn_github_releases),
                        icon = Icons.Default.Download,
                        onClick = { AppInfo.openUrlSafely(context, AppInfo.RELEASES_URL) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(136.dp))
    }

    if (showResetSettingsDialog) {
        GlassDialog(
            title = stringResource(R.string.confirm_reset_title),
            onDismiss = { showResetSettingsDialog = false },
            confirmText = stringResource(R.string.btn_confirm_delete),
            isDangerConfirm = true,
            onConfirm = {
                viewModel.resetSettings()
                showResetSettingsDialog = false
                Toast.makeText(context, context.getString(R.string.toast_reset_done), Toast.LENGTH_SHORT).show()
            }
        ) {
            Text(
                text = stringResource(R.string.confirm_reset_msg),
                color = tokens.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }

    if (showClearProfilesDialog) {
        GlassDialog(
            title = stringResource(R.string.confirm_clear_profiles_title),
            onDismiss = { showClearProfilesDialog = false },
            confirmText = stringResource(R.string.btn_confirm_delete),
            isDangerConfirm = true,
            onConfirm = {
                viewModel.clearDeviceProfiles()
                showClearProfilesDialog = false
                Toast.makeText(context, context.getString(R.string.toast_profiles_cleared), Toast.LENGTH_SHORT).show()
            }
        ) {
            Text(
                text = stringResource(R.string.confirm_clear_profiles_msg),
                color = tokens.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val tokens = LocalGlassTokens.current
    Text(
        text = title,
        color = tokens.accentColor,
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold
    )
}

@Composable
private fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val tokens = LocalGlassTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = tokens.minTouchTarget),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = tokens.textPrimary,
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        GlassSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
