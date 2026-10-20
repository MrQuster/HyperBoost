package com.example.volumeboost.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.volumeboost.R
import com.example.volumeboost.data.EqPreset
import com.example.volumeboost.dsp.SoftwarePcmDspProcessor
import com.example.volumeboost.ui.components.*
import com.example.volumeboost.ui.theme.LocalGlassTokens
import com.example.volumeboost.util.LocaleHelper
import com.example.volumeboost.viewmodel.EqualizerViewModel
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(viewModel: EqualizerViewModel) {
    val context = LocalContext.current
    val tokens = LocalGlassTokens.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val customPresets by viewModel.customPresets.collectAsStateWithLifecycle()
    val runtime by viewModel.runtimeState.collectAsStateWithLifecycle()

    val isRu = remember(settings.languageCode) {
        LocaleHelper.isRussianActive(context, settings.languageCode)
    }

    var showSaveDialog by remember { mutableStateOf(false) }
    var customNameInput by remember { mutableStateOf("") }

    val defaultFreqs = listOf(60, 230, 910, 3600, 14000)
    val bandCount = 5

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = tokens.spacingLarge, vertical = tokens.spacingMedium),
        verticalArrangement = Arrangement.spacedBy(tokens.spacingLarge)
    ) {
        // Шапка: Заголовок и кнопка Сбросить
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.eq_title),
                    color = tokens.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.eq_subtitle),
                    color = tokens.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            GlassButton(
                text = stringResource(R.string.eq_reset),
                icon = Icons.Default.Refresh,
                onClick = { viewModel.resetEqualizer() }
            )
        }

        // Карточка пресетов
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.eq_presets_header),
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = stringResource(R.string.eq_scroll_hint),
                        color = tokens.accentColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EqPreset.entries.filter { it != EqPreset.CUSTOM }.forEach { preset ->
                        GlassChip(
                            label = if (isRu) preset.labelRu else preset.labelEn,
                            selected = settings.activeEqPreset == preset.name,
                            onClick = { viewModel.selectBuiltInPreset(preset) }
                        )
                    }
                    customPresets.forEach { custom ->
                        GlassChip(
                            label = "★ ${custom.name}",
                            selected = settings.activeEqPreset == custom.name,
                            onClick = { viewModel.applyCustomPreset(custom) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = stringResource(R.string.eq_save_custom),
                        icon = Icons.Default.Add,
                        isAccent = true,
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    if (customPresets.isNotEmpty()) {
                        GlassButton(
                            text = stringResource(R.string.eq_delete_custom),
                            icon = Icons.Default.Delete,
                            isDanger = true,
                            onClick = { viewModel.deleteCustomPreset(customPresets.last().name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 5-полосный вертикальный эквалайзер
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until bandCount) {
                    val bandInfo = runtime.eqBands.getOrNull(i)
                    val minMb = bandInfo?.minLevelMb ?: -1500
                    val maxMb = bandInfo?.maxLevelMb ?: 1500
                    val currMb = settings.customBandsMb.getOrElse(i) { bandInfo?.currentLevelMb ?: 0 }
                    val freqHz = bandInfo?.centerFreqHz ?: defaultFreqs.getOrElse(i) { 1000 }

                    val span = (maxMb - minMb).coerceAtLeast(1)
                    val fraction = ((currMb - minMb).toFloat() / span).coerceIn(0f, 1f)
                    val dbVal = currMb / 100f
                    val dbText = if (dbVal >= 0f) "+${"%.1f".format(dbVal)}" else "%.1f".format(dbVal)
                    val freqText = if (freqHz >= 1000) {
                        stringResource(R.string.unit_khz, freqHz / 1000)
                    } else {
                        stringResource(R.string.unit_hz, freqHz)
                    }

                    GlassVerticalSlider(
                        freqLabel = freqText,
                        gainDbText = stringResource(R.string.unit_db, dbText),
                        fraction = fraction,
                        onFractionChange = { newFrac ->
                            val newMb = (minMb + newFrac * span).roundToInt()
                            viewModel.updateBandLevelMb(i, newMb)
                        }
                    )
                }
            }
        }

        // Ползунки Усиления баса, Объёмного звука, Баланса Л/П, Моно и Лимитера
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val bassGainDb = SoftwarePcmDspProcessor.bassSliderToGainDb(settings.bassBoostStrength)
                LabeledGlassSliderRow(
                    title = stringResource(R.string.eq_bass_boost),
                    valueText = "${settings.bassBoostStrength / 10}% (+${"%.1f".format(bassGainDb)} dB)",
                    value = settings.bassBoostStrength.toFloat(),
                    onValueChange = { viewModel.setBassBoost(it.roundToInt()) },
                    valueRange = 0f..1000f
                )

                LabeledGlassSliderRow(
                    title = stringResource(R.string.eq_virtualizer),
                    valueText = "${settings.virtualizerStrength / 10}%",
                    value = settings.virtualizerStrength.toFloat(),
                    onValueChange = { viewModel.setVirtualizer(it.roundToInt()) },
                    valueRange = 0f..1000f
                )

                val balText = when {
                    settings.balanceLeftRight < -0.04f ->
                        stringResource(R.string.eq_pan_left_fmt, "%.2f".format(settings.balanceLeftRight))
                    settings.balanceLeftRight > 0.04f ->
                        stringResource(R.string.eq_pan_right_fmt, "%.2f".format(settings.balanceLeftRight))
                    else -> stringResource(R.string.eq_pan_center)
                }
                LabeledGlassSliderRow(
                    title = stringResource(R.string.eq_balance),
                    valueText = balText,
                    value = settings.balanceLeftRight,
                    onValueChange = { viewModel.setStereoBalance(it) },
                    valueRange = -1f..1f
                )

                LabeledGlassSliderRow(
                    title = stringResource(R.string.eq_compressor),
                    valueText = stringResource(R.string.unit_db, "%.1f".format(settings.limiterThresholdDb)),
                    value = settings.limiterThresholdDb,
                    onValueChange = { viewModel.setCompressorThreshold(it) },
                    valueRange = -15f..0f
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = tokens.minTouchTarget),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.eq_mono),
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f).padding(end = 10.dp)
                    )
                    GlassSwitch(
                        checked = settings.monoEnabled,
                        onCheckedChange = { viewModel.setMonoMode(it) }
                    )
                }

                val routeName = if (isRu) runtime.activeBassRoute.labelRu else runtime.activeBassRoute.labelEn
                Text(
                    text = stringResource(R.string.dsp_route_active_fmt, routeName),
                    color = tokens.accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(136.dp))
    }

    if (showSaveDialog) {
        GlassDialog(
            title = stringResource(R.string.eq_save_custom),
            onDismiss = { showSaveDialog = false },
            confirmText = stringResource(R.string.action_ok),
            onConfirm = {
                viewModel.saveCurrentAsCustomPreset(customNameInput)
                customNameInput = ""
                showSaveDialog = false
            }
        ) {
            OutlinedTextField(
                value = customNameInput,
                onValueChange = { customNameInput = it },
                label = { Text(stringResource(R.string.eq_preset_name_hint), color = tokens.textSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = tokens.textPrimary,
                    unfocusedTextColor = tokens.textPrimary,
                    focusedBorderColor = tokens.accentColor,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
