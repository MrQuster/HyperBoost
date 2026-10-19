package com.example.volumeboost.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volumeboost.R
import com.example.volumeboost.data.EqPreset
import com.example.volumeboost.ui.components.*
import com.example.volumeboost.ui.theme.LocalGlassTokens
import com.example.volumeboost.viewmodel.EqualizerViewModel

@Composable
fun EqualizerScreen(viewModel: EqualizerViewModel) {
    val tokens = LocalGlassTokens.current
    val settings by viewModel.settings.collectAsState()
    val customPresets by viewModel.customPresets.collectAsState()
    val runtime by viewModel.runtimeState.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var customPresetName by remember { mutableStateOf("") }

    val defaultFreqs = listOf(60, 230, 910, 3600, 14000)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = tokens.spacingLarge, vertical = tokens.spacingMedium),
        verticalArrangement = Arrangement.spacedBy(tokens.spacingLarge)
    ) {
        // Шапка: заголовок слева с weight(1f), компактная кнопка «Сбросить» справа в одну строку
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.eq_title),
                    color = tokens.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.eq_subtitle),
                    color = tokens.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            GlassButton(
                text = stringResource(R.string.eq_reset),
                icon = Icons.Default.Refresh,
                onClick = { viewModel.resetEqualizer() },
                modifier = Modifier.widthIn(min = 118.dp)
            )
        }

        // Горизонтальная лента пресетов на LazyRow с подсказкой прокрутки
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Пресеты АЧХ",
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = stringResource(R.string.eq_scroll_hint),
                        color = tokens.accentColor,
                        fontSize = 11.sp
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(EqPreset.entries, key = { it.name }) { preset ->
                        val title = if (settings.languageCode == "en") preset.labelEn else preset.labelRu
                        GlassChip(
                            label = title,
                            selected = settings.activeEqPreset == preset.name,
                            onClick = { viewModel.selectBuiltInPreset(preset) }
                        )
                    }
                }

                if (customPresets.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(customPresets, key = { it.name }) { custom ->
                            GlassChip(
                                label = "★ ${custom.name}",
                                selected = false,
                                onClick = { viewModel.applyCustomPreset(custom) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = stringResource(R.string.eq_save_custom),
                        icon = Icons.Default.Add,
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    if (customPresets.isNotEmpty()) {
                        GlassButton(
                            text = stringResource(R.string.eq_delete_custom),
                            icon = Icons.Default.Delete,
                            isDanger = true,
                            onClick = { viewModel.deleteCustomPreset(customPresets.last().name) }
                        )
                    }
                }
            }
        }

        // Вертикальные слайдеры полос эквалайзера (равномерный вес колонок, без наложений)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            val bandsCount = if (runtime.eqBands.isNotEmpty()) runtime.eqBands.size else 5
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until bandsCount) {
                    val bandInfo = runtime.eqBands.getOrNull(i)
                    val minMb = bandInfo?.minLevelMb ?: -1500
                    val maxMb = bandInfo?.maxLevelMb ?: 1500
                    val currentMb = settings.customBandsMb.getOrElse(i) { bandInfo?.currentLevelMb ?: 0 }
                    val freqHz = bandInfo?.centerFreqHz ?: defaultFreqs.getOrElse(i) { 1000 }
                    val freqLabel = if (freqHz >= 1000) "${freqHz / 1000} кГц" else "$freqHz Гц"
                    val dbValue = currentMb / 100f
                    val dbText = if (dbValue >= 0) "+${"%.1f".format(dbValue)}" else "%.1f".format(dbValue)
                    val frac = ((currentMb - minMb).toFloat() / (maxMb - minMb).toFloat()).coerceIn(0f, 1f)

                    GlassVerticalSlider(
                        freqLabel = freqLabel,
                        gainDbText = "${dbText}дБ",
                        fraction = frac,
                        onFractionChange = { newFrac ->
                            val targetMb = (minMb + newFrac * (maxMb - minMb)).toInt()
                            viewModel.updateBandLevelMb(i, targetMb)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Унифицированные ползунки: Бас, Объёмный звук, Баланс, Порог лимитера и Моно
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LabeledGlassSliderRow(
                    title = stringResource(R.string.eq_bass_boost),
                    valueText = "${settings.bassBoostStrength / 10}%",
                    value = settings.bassBoostStrength.toFloat(),
                    onValueChange = { viewModel.setBassBoost(it.toInt()) },
                    valueRange = 0f..1000f
                )

                LabeledGlassSliderRow(
                    title = stringResource(R.string.eq_virtualizer),
                    valueText = "${settings.virtualizerStrength / 10}%",
                    value = settings.virtualizerStrength.toFloat(),
                    onValueChange = { viewModel.setVirtualizer(it.toInt()) },
                    valueRange = 0f..1000f
                )

                LabeledGlassSliderRow(
                    title = stringResource(R.string.eq_balance),
                    valueText = when {
                        settings.balanceLeftRight < -0.05f -> "Лев ${"%.2f".format(settings.balanceLeftRight)}"
                        settings.balanceLeftRight > 0.05f -> "Прав +${"%.2f".format(settings.balanceLeftRight)}"
                        else -> "Центр (0.0)"
                    },
                    value = settings.balanceLeftRight,
                    onValueChange = { viewModel.setStereoBalance(it) },
                    valueRange = -1f..1f
                )

                LabeledGlassSliderRow(
                    title = stringResource(R.string.eq_compressor),
                    valueText = "${"%.1f".format(settings.limiterThresholdDb)} дБ",
                    value = settings.limiterThresholdDb,
                    onValueChange = { viewModel.setCompressorThreshold(it) },
                    valueRange = -15f..0f
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.eq_mono),
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    GlassSwitch(
                        checked = settings.monoEnabled,
                        onCheckedChange = { viewModel.setMonoMode(it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(tokens.screenBottomPadding))
    }

    if (showSaveDialog) {
        GlassDialog(
            title = stringResource(R.string.eq_save_custom),
            onDismiss = { showSaveDialog = false },
            confirmText = stringResource(R.string.action_ok),
            onConfirm = {
                viewModel.saveCurrentAsCustomPreset(customPresetName)
                customPresetName = ""
                showSaveDialog = false
            }
        ) {
            OutlinedTextField(
                value = customPresetName,
                onValueChange = { customPresetName = it },
                label = { Text("Название пресета") },
                singleLine = true
            )
        }
    }
}
