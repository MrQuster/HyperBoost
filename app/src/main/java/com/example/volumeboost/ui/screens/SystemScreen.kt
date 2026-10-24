package com.example.volumeboost.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volumeboost.R
import com.example.volumeboost.device.*
import com.example.volumeboost.island.IslandEventLogger
import com.example.volumeboost.ui.components.GlassButton
import com.example.volumeboost.ui.components.GlassCard
import com.example.volumeboost.ui.components.GlassChip
import com.example.volumeboost.ui.theme.LocalGlassTokens
import com.example.volumeboost.viewmodel.SettingsViewModel

@Composable
fun SystemScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val tokens = LocalGlassTokens.current
    val settings by viewModel.settings.collectAsState()
    val profile by viewModel.deviceProfile.collectAsState()
    val isScanning by viewModel.isScanningProfile.collectAsState()
    val runtime by viewModel.runtimeState.collectAsState()
    val islandLogs by IslandEventLogger.entriesFlow.collectAsState()
    val selfTestItems by IslandEventLogger.selfTestResults.collectAsState()

    val isEn = !com.example.volumeboost.util.LocaleHelper.isRussianActive(context, settings.languageCode)
    val isRu = !isEn
    val plan = remember(profile, settings.islandRoutingPreference, settings.powerSaveGlass) {
        FeatureRegistry.resolvePlan(profile, settings.islandRoutingPreference, settings.powerSaveGlass)
    }

    var showIslandLogExpanded by remember { mutableStateOf(false) }
    var showSignalsExpanded by remember { mutableStateOf(false) }
    var showOverridePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = tokens.spacingLarge, vertical = tokens.spacingMedium),
        verticalArrangement = Arrangement.spacedBy(tokens.spacingLarge)
    ) {
        // 1. Шапка «Моё устройство»
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 30.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${profile.brand} ${profile.model}",
                            color = tokens.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.sys_manufacturer_codename_fmt, profile.vendor, profile.deviceCodename),
                            color = tokens.textSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                    if (isScanning) {
                        CircularProgressIndicator(
                            color = tokens.accentColor,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(26.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(tokens.accentColor.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = stringResource(R.string.nav_system), tint = tokens.accentColor)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BadgePill(
                        text = "${if (isEn) profile.osFamily.displayNameEn else profile.osFamily.displayNameRu} (${profile.osVersion})",
                        accent = tokens.accentColor
                    )
                    BadgePill(
                        text = "Android ${profile.androidRelease} · API ${profile.apiLevelFullFormatted}",
                        accent = tokens.secondaryAccent
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sys_confidence_fmt, if (isEn) profile.confidence.labelEn else profile.confidence.labelRu),
                        color = tokens.textSecondary,
                        fontSize = 12.sp
                    )
                    if (profile.isManualOverride) {
                        BadgePill(text = stringResource(R.string.sys_manual_badge), accent = Color(0xFFFF9E00))
                    }
                }
            }
        }

        // 2. Секция «Система»
        SysSectionTitle(stringResource(R.string.sys_sec_os))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyValueRow(stringResource(R.string.sys_os_family), "${if (isEn) profile.osFamily.displayNameEn else profile.osFamily.displayNameRu} (${profile.osVersion})")
                KeyValueRow(stringResource(R.string.sys_rom_region), profile.romRegion)
                KeyValueRow(stringResource(R.string.sys_android_ver), "${profile.androidRelease} (API ${profile.apiLevel} / ${profile.apiLevelFullFormatted})")
                KeyValueRow(stringResource(R.string.sys_sec_patch), profile.securityPatch)
                KeyValueRow(stringResource(R.string.sys_build_id), profile.displayBuildId)
                KeyValueRow(
                    stringResource(R.string.sys_rom_integrity),
                    if (profile.isLikelyCustomRom) stringResource(R.string.sys_rom_custom) else stringResource(R.string.sys_rom_stock)
                )
            }
        }

        // 3. Секция «Железо и экран»
        SysSectionTitle(stringResource(R.string.sys_sec_hw))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            val hw = profile.hardwareSpecs
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyValueRow(stringResource(R.string.sys_soc), "${hw.socManufacturer} ${hw.socModel} (${hw.hardwareBoard})")
                KeyValueRow(stringResource(R.string.sys_cpu_cores), stringResource(R.string.sys_cpu_cores_fmt, hw.cpuCores))
                KeyValueRow(
                    stringResource(R.string.sys_ram),
                    if (hw.totalRamMb > 0) stringResource(R.string.sys_ram_fmt, hw.availableRamMb, hw.totalRamMb) else stringResource(R.string.unknown)
                )
                KeyValueRow(stringResource(R.string.sys_screen), "${hw.screenResolutionPx} • ${hw.screenDensityDpi} dpi (${profile.smallestScreenWidthDp}dp)")
                KeyValueRow(
                    stringResource(R.string.sys_refresh_hdr),
                    "${hw.refreshRateHz} Hz • HDR: ${if (hw.hdrSupported) stringResource(R.string.sys_hdr_supported) else stringResource(R.string.sys_hdr_unsupported)}"
                )
                KeyValueRow(stringResource(R.string.sys_form_factor), if (isEn) profile.formFactor.labelEn else profile.formFactor.labelRu)
                KeyValueRow(stringResource(R.string.sys_cutout), "${profile.cutoutStatus.symbol} ${if (isEn) profile.cutoutStatus.labelEn else profile.cutoutStatus.labelRu}")
            }
        }

        // 4. Секция «Аудио тракт, маршруты ползунков и самотест эффектов»
        SysSectionTitle(androidx.compose.ui.res.stringResource(com.example.volumeboost.R.string.sys_sec_audio))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            val hw = profile.hardwareSpecs
            val bassRouteName = if (isEn) runtime.activeBassRoute.labelEn else runtime.activeBassRoute.labelRu
            val virtRouteName = if (isEn) runtime.activeVirtualizerRoute.labelEn else runtime.activeVirtualizerRoute.labelRu
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyValueRow(
                    androidx.compose.ui.res.stringResource(com.example.volumeboost.R.string.sys_audio_engine),
                    if (isEn) plan.primaryAudioStrategy.titleEn else plan.primaryAudioStrategy.titleRu
                )
                KeyValueRow(
                    androidx.compose.ui.res.stringResource(com.example.volumeboost.R.string.sys_audio_effects),
                    runtime.supportedEffectsSummary
                )
                KeyValueRow(
                    androidx.compose.ui.res.stringResource(com.example.volumeboost.R.string.sys_audio_routes),
                    "Bass: $bassRouteName • Surround: $virtRouteName"
                )
                KeyValueRow(
                    androidx.compose.ui.res.stringResource(com.example.volumeboost.R.string.sys_audio_hal),
                    "${hw.nativeSampleRateHz} Hz • ${hw.framesPerBuffer} frames/buffer"
                )
                KeyValueRow(
                    androidx.compose.ui.res.stringResource(com.example.volumeboost.R.string.sys_audio_outputs),
                    hw.connectedOutputSummary
                )

                GlassButton(
                    text = androidx.compose.ui.res.stringResource(com.example.volumeboost.R.string.sys_btn_test_effects),
                    icon = Icons.Default.GraphicEq,
                    isAccent = true,
                    onClick = { viewModel.triggerAudioEffectsTest() },
                    modifier = Modifier.fillMaxWidth()
                )

                if (runtime.audioEffectTestResults.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.42f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.volumeboost.R.string.sys_effects_test_header),
                            color = tokens.accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        runtime.audioEffectTestResults.forEach { item ->
                            val sym = if (item.passed) "✓" else "✗"
                            val col = if (item.passed) tokens.accentColor else tokens.dangerColor
                            Text(
                                text = "$sym ${item.effectName}",
                                color = col,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                            Text(
                                text = if (isEn) item.detailEn else item.detailRu,
                                color = tokens.textSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
        }

        // 5. Секция «Уведомления, Суперостров и Журнал самопроверки»
        SysSectionTitle(stringResource(R.string.sys_sec_island))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KeyValueRow(stringResource(R.string.sys_island_strategy), if (isEn) plan.selectedIslandStrategy.titleEn else plan.selectedIslandStrategy.titleRu)
                KeyValueRow(stringResource(R.string.sys_island_presenter), runtime.activeIslandTier.ifBlank { settings.lastActiveIslandTier })
                KeyValueRow(stringResource(R.string.sys_island_reason), runtime.islandSelectionReason.ifBlank { plan.islandSelectionReasonRu })
                KeyValueRow(stringResource(R.string.sys_island_focus_status), "${profile.focusNotificationStatus.symbol} ${if (isEn) profile.focusNotificationStatus.labelEn else profile.focusNotificationStatus.labelRu}")
                KeyValueRow(stringResource(R.string.sys_island_last_pkt), "Seq #${runtime.lastIslandSequenceId} • ${runtime.lastIslandError.ifBlank { settings.lastIslandError }}")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = stringResource(R.string.sys_btn_island_selftest),
                        icon = Icons.AutoMirrored.Filled.FactCheck,
                        isAccent = true,
                        onClick = {
                            viewModel.triggerIslandPreview()
                            Toast.makeText(context, context.getString(R.string.sys_toast_selftest_started), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = stringResource(R.string.sys_btn_notifications),
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

                // Вывод результатов 5-шагового самотеста ✓ / ✗
                if (selfTestItems.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.42f))
                            .border(1.dp, tokens.accentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.sys_island_selftest_header),
                            color = tokens.accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        selfTestItems.forEach { item ->
                            val iconSymbol = if (item.passed) "✓" else "✗"
                            val itemColor = if (item.passed) tokens.accentColor else tokens.dangerColor
                            Column {
                                Text(
                                    text = "$iconSymbol ${item.stepTitle}",
                                    color = itemColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                                Text(
                                    text = item.explanation,
                                    color = tokens.textSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }

                // Кольцевой буфер последних 100 событий острова
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { showIslandLogExpanded = !showIslandLogExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.sys_island_log_title_fmt, islandLogs.size),
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                    Icon(
                        imageVector = if (showIslandLogExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.sys_island_log_title_fmt, islandLogs.size),
                        tint = tokens.accentColor
                    )
                }

                AnimatedVisibility(visible = showIslandLogExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.50f))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = if (islandLogs.isEmpty()) stringResource(R.string.sys_island_log_empty)
                                else islandLogs.take(25).joinToString("\n") { e ->
                                    "${e.formattedTime} ${if (e.isSuccess) "✓" else "✗"} [#${e.seqId}] ${e.action} (${e.boostPercent}%, ${e.jsonSizeBytes}B): ${e.detail}"
                                },
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GlassButton(
                                text = stringResource(R.string.sys_btn_copy_log),
                                icon = Icons.Default.ContentCopy,
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Island Log", IslandEventLogger.formatAsPlainText()))
                                    Toast.makeText(context, context.getString(R.string.sys_toast_log_copied), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            GlassButton(
                                text = stringResource(R.string.sys_btn_clear),
                                icon = Icons.Default.DeleteSweep,
                                isDanger = true,
                                onClick = { IslandEventLogger.clear() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 6. Секция «Матрица возможностей (Capabilities)»
        SysSectionTitle(stringResource(R.string.sys_sec_caps_fmt, profile.capabilities.size))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                profile.capabilityEvaluations.forEachIndexed { idx, ev ->
                    if (idx > 0) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val badgeColor = when (ev.status) {
                            TriStateStatus.SUPPORTED -> tokens.accentColor
                            TriStateStatus.UNKNOWN -> Color(0xFFFFBE0B)
                            TriStateStatus.UNSUPPORTED -> Color(0xFF94A3B8)
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(badgeColor.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ev.status.symbol,
                                color = badgeColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isEn) ev.capability.titleEn else ev.capability.titleRu,
                                color = tokens.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${if (isEn) ev.reasonEn else ev.reasonRu} • [${if (isEn) ev.source.labelEn else ev.source.labelRu}]",
                                color = tokens.textSecondary,
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // 7. Секция «Сигналы определения и системные свойства»
        SysSectionTitle(stringResource(R.string.sys_sec_signals))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { showSignalsExpanded = !showSignalsExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.sys_signals_summary_fmt, profile.matchedSignals.size, profile.unreadableProperties.size),
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (showSignalsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.sys_sec_signals),
                        tint = tokens.accentColor
                    )
                }

                AnimatedVisibility(visible = showSignalsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.sys_signals_read) + profile.matchedSignals.joinToString("\n") { " • $it" },
                            color = tokens.accentColor,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (profile.unreadableProperties.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.sys_props_empty) +
                                    profile.unreadableProperties.joinToString(", "),
                                color = tokens.textSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // 8. Секция «Управление профилем и экспорт отчёта»
        SysSectionTitle(stringResource(R.string.sys_sec_manage))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { showOverridePicker = !showOverridePicker },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sys_override_mode_label),
                            color = tokens.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isEn) settings.osOverrideOption.labelEn else settings.osOverrideOption.labelRu,
                            color = tokens.accentColor,
                            fontSize = 12.5.sp
                        )
                    }
                    Icon(
                        imageVector = if (showOverridePicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.sys_override_mode_label),
                        tint = tokens.textSecondary
                    )
                }

                AnimatedVisibility(visible = showOverridePicker) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OsOverrideOption.entries.forEach { option ->
                            GlassChip(
                                label = if (isEn) option.labelEn else option.labelRu,
                                selected = settings.osOverrideOption == option,
                                onClick = {
                                    viewModel.rescanDeviceProfile(option)
                                    showOverridePicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = stringResource(R.string.sys_btn_rescan),
                        icon = Icons.Default.Refresh,
                        onClick = {
                            viewModel.rescanDeviceProfile(settings.osOverrideOption)
                            Toast.makeText(context, context.getString(R.string.sys_toast_rescanned), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = stringResource(R.string.sys_btn_copy_report),
                        icon = Icons.Default.ContentCopy,
                        isAccent = true,
                        onClick = {
                            val report = viewModel.buildDiagnosticReport()
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("HyperBoost System Report", report))
                            Toast.makeText(context, context.getString(R.string.sys_toast_report_copied), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                GlassButton(
                    text = stringResource(R.string.sys_btn_share_json),
                    icon = Icons.Default.Share,
                    onClick = {
                        val json = profile.toJsonString()
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_TEXT, json)
                            putExtra(Intent.EXTRA_TITLE, "DeviceProfile_${profile.model}.json")
                        }
                        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.sys_share_json_chooser)))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(tokens.screenBottomPadding))
    }
}

@Composable
private fun SysSectionTitle(title: String) {
    val tokens = LocalGlassTokens.current
    Text(
        text = title,
        color = tokens.accentColor,
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold
    )
}

@Composable
private fun BadgePill(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.20f))
            .border(1.dp, accent.copy(alpha = 0.60f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    val tokens = LocalGlassTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = tokens.textSecondary,
            fontSize = 11.5.sp
        )
        Text(
            text = value,
            color = tokens.textPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
