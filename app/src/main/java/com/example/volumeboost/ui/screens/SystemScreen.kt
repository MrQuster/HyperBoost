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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volumeboost.device.*
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

    val isEn = settings.languageCode == "en"
    val plan = remember(profile, settings.islandRoutingPreference, settings.powerSaveGlass) {
        FeatureRegistry.resolvePlan(profile, settings.islandRoutingPreference, settings.powerSaveGlass)
    }

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
                            text = "Производитель: ${profile.vendor} • Код: ${profile.deviceCodename}",
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
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(tokens.accentColor.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = tokens.accentColor)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BadgePill(
                        text = "${profile.osFamily.displayNameRu} (${profile.osVersion})",
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
                        text = "Уверенность: ${if (isEn) profile.confidence.labelEn else profile.confidence.labelRu}",
                        color = tokens.textSecondary,
                        fontSize = 12.sp
                    )
                    if (profile.isManualOverride) {
                        BadgePill(text = "РУЧНОЙ РЕЖИМ", accent = Color(0xFFFF9E00))
                    }
                }
            }
        }

        // 2. Секция «Система»
        SysSectionTitle("Система и прошивка")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyValueRow("Оболочка ОС", "${profile.osFamily.displayNameRu} (${profile.osVersion})")
                KeyValueRow("Регион прошивки", profile.romRegion)
                KeyValueRow("Версия Android", "${profile.androidRelease} (API ${profile.apiLevel} / Полный ${profile.apiLevelFullFormatted})")
                KeyValueRow("Патч безопасности", profile.securityPatch)
                KeyValueRow("Номер сборки", profile.displayBuildId)
                KeyValueRow(
                    "Статус целостности ROM",
                    if (profile.isLikelyCustomRom) "Признаки кастомной AOSP прошивки" else "Штатная вендорная прошивка"
                )
            }
        }

        // 3. Секция «Железо и экран»
        SysSectionTitle("Аппаратная платформа и дисплей")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            val hw = profile.hardwareSpecs
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyValueRow("Процессор (SoC)", "${hw.socManufacturer} ${hw.socModel} (${hw.hardwareBoard})")
                KeyValueRow("Ядра CPU", "${hw.cpuCores} ядер")
                KeyValueRow(
                    "Оперативная память (ОЗУ)",
                    if (hw.totalRamMb > 0) "Свободно ${hw.availableRamMb} МБ из ${hw.totalRamMb} МБ" else "Неизвестно"
                )
                KeyValueRow("Экран и плотность", "${hw.screenResolutionPx} • ${hw.screenDensityDpi} dpi (${profile.smallestScreenWidthDp}dp)")
                KeyValueRow("Частота и HDR", "${hw.refreshRateHz} Гц • HDR: ${if (hw.hdrSupported) "Поддерживается" else "Нет"}")
                KeyValueRow("Форм-фактор", if (isEn) profile.formFactor.labelEn else profile.formFactor.labelRu)
                KeyValueRow("Вырез камеры (Cutout)", "${profile.cutoutStatus.symbol} ${profile.cutoutStatus.labelRu}")
            }
        }

        // 4. Секция «Аудио тракт»
        SysSectionTitle("Аудиоподсистема и HAL")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            val hw = profile.hardwareSpecs
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyValueRow("Выбранный движок", plan.primaryAudioStrategy.titleRu)
                KeyValueRow("Активные эффекты", runtime.supportedEffectsSummary)
                KeyValueRow("Нативная частота и буфер", "${hw.nativeSampleRateHz} Гц • ${hw.framesPerBuffer} фреймов/буфер")
                KeyValueRow(
                    "Флаги задержки",
                    "Low Latency: ${if (hw.hasLowLatencyAudio) "Да" else "Нет"} • Pro Audio: ${if (hw.hasProAudio) "Да" else "Нет"}"
                )
                KeyValueRow("Активные аудиовыходы", hw.connectedOutputSummary)
            }
        }

        // 5. Секция «Уведомления и Суперостров»
        SysSectionTitle("Уведомления и Суперостров")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyValueRow("Выбранная стратегия", plan.selectedIslandStrategy.titleRu)
                KeyValueRow("Активный презентер", runtime.activeIslandTier.ifBlank { settings.lastActiveIslandTier })
                KeyValueRow("Причина выбора", runtime.islandSelectionReason.ifBlank { plan.islandSelectionReasonRu })
                KeyValueRow("Статус фокусных уведомлений", "${profile.focusNotificationStatus.symbol} ${profile.focusNotificationStatus.labelRu}")
                KeyValueRow("Последняя отправка острова", runtime.lastIslandError.ifBlank { settings.lastIslandError })

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = "Тест острова (5с)",
                        icon = Icons.Default.Visibility,
                        isAccent = true,
                        onClick = { viewModel.triggerIslandPreview() },
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = "Уведомления",
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
            }
        }

        // 6. Секция «Матрица возможностей (Capabilities)»
        SysSectionTitle("Матрица возможностей (${profile.capabilities.size} активно)")
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

        // 7. Секция «Сигналы определения и системные свойства» (сворачиваемая)
        SysSectionTitle("Сигналы детектора и свойства getprop")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSignalsExpanded = !showSignalsExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Совпавших сигналов: ${profile.matchedSignals.size} • Пустых/закрытых свойств: ${profile.unreadableProperties.size}",
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (showSignalsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = tokens.accentColor
                    )
                }

                AnimatedVisibility(visible = showSignalsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Прочитанные сигналы:\n" + profile.matchedSignals.joinToString("\n") { " • $it" },
                            color = tokens.accentColor,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (profile.unreadableProperties.isNotEmpty()) {
                            Text(
                                text = "Свойства с пустым ответом / закрытые SELinux:\n" +
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
        SysSectionTitle("Управление определением и экспорт")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showOverridePicker = !showOverridePicker },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Режим определения ОС",
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
                        contentDescription = null,
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
                        text = "Пересканировать",
                        icon = Icons.Default.Refresh,
                        onClick = {
                            viewModel.rescanDeviceProfile(settings.osOverrideOption)
                            Toast.makeText(context, "Система пересканирована в фоновом потоке", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = "Скопировать отчёт",
                        icon = Icons.Default.ContentCopy,
                        isAccent = true,
                        onClick = {
                            val report = viewModel.buildDiagnosticReport()
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("HyperBoost System Report", report))
                            Toast.makeText(context, "Отчёт скопирован в буфер обмена", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                GlassButton(
                    text = "Поделиться JSON профилем устройства",
                    icon = Icons.Default.Share,
                    onClick = {
                        val json = profile.toJsonString()
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_TEXT, json)
                            putExtra(Intent.EXTRA_TITLE, "DeviceProfile_${profile.model}.json")
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Поделиться JSON"))
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
