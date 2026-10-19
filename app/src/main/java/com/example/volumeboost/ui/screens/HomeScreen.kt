package com.example.volumeboost.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volumeboost.R
import com.example.volumeboost.data.BoostMode
import com.example.volumeboost.data.BoostScaleMapper
import com.example.volumeboost.ui.components.*
import com.example.volumeboost.ui.theme.LocalGlassTokens
import com.example.volumeboost.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onRequestStartEngine: (BoostMode) -> Unit
) {
    val tokens = LocalGlassTokens.current
    val settings by viewModel.settings.collectAsState()
    val runtime by viewModel.runtimeState.collectAsState()
    val device by viewModel.connectedDevice.collectAsState()
    val boostPercent by viewModel.boostPercent.collectAsState()

    val unlimited = settings.unlimitedBoostEnabled
    val sliderFraction = remember(boostPercent, unlimited) {
        BoostScaleMapper.percentToFraction(boostPercent, unlimited)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = tokens.spacingLarge, vertical = tokens.spacingMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tokens.spacingLarge)
    ) {
        // 1. Карточка текущего аудиовыхода (полный перенос текста без обрезки)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val devIcon = when (device.type) {
                    "BLUETOOTH" -> Icons.Default.Headphones
                    "USB", "WIRED" -> Icons.Default.Cable
                    else -> Icons.Default.Speaker
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(tokens.accentColor.copy(alpha = 0.24f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(devIcon, contentDescription = null, tint = tokens.accentColor)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = device.codecAndLatencyHint,
                        color = tokens.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 3
                    )
                }
            }
        }

        // 2. Круговой регулятор и синхронизированный нелинейный слайдер
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 30.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularLiquidDial(
                    boostPercent = boostPercent,
                    unlimitedMode = unlimited,
                    isRunning = runtime.isRunning,
                    audioPeak = runtime.peakSignal,
                    onBoostChange = { viewModel.setBoostPercent(it) },
                    onTogglePower = {
                        if (runtime.isRunning) viewModel.stopService()
                        else onRequestStartEngine(settings.defaultBoostMode)
                    }
                )

                Text(
                    text = runtime.statusMessage,
                    color = tokens.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    GlassSlider(
                        value = sliderFraction,
                        onValueChange = { frac ->
                            val mapped = BoostScaleMapper.fractionToPercent(frac, unlimited)
                            viewModel.setBoostPercent(mapped)
                        },
                        valueRange = 0f..1f
                    )
                    val midHighLabel = if (unlimited) "1400%" else "450%"
                    val maxLabel = if (unlimited) "10000%" else "1000%"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("0%", "100%", "200%", midHighLabel, maxLabel).forEach { tickText ->
                            Text(
                                text = tickText,
                                color = tokens.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Горизонтально прокручиваемый ряд пресетов, который не обрезается на любых размерах шрифта
                val presets = if (unlimited) {
                    listOf(100, 150, 300, 1000, 2500)
                } else {
                    listOf(100, 150, 200, 500, 1000)
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(presets, key = { it }) { preset ->
                        val isSelected = boostPercent == preset
                        GlassChip(
                            label = if (isSelected) "✓ $preset%" else "$preset%",
                            selected = isSelected,
                            onClick = { viewModel.setBoostPercent(preset) },
                            modifier = Modifier.widthIn(min = 76.dp)
                        )
                    }
                }
            }
        }

        // 3. Карточка уровня сигнала и спектра в реальном времени (Peak / RMS)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.signal_meter),
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(tokens.accentColor.copy(alpha = 0.20f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (runtime.isLimiterEngaged) stringResource(R.string.limiter_active_badge)
                            else stringResource(R.string.limiter_idle_badge),
                            color = tokens.accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                RealTimeSignalBarsCanvas(
                    bands = runtime.spectrumBands,
                    hasActiveStream = runtime.hasActiveAudioStream,
                    peakSignal = runtime.peakSignal,
                    rmsSignal = runtime.rmsSignal
                )

                AnimatedVisibility(visible = runtime.isCapturingSilenceOrDrm) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = tokens.accentColor)
                        Text(
                            text = stringResource(R.string.drm_warning),
                            color = tokens.textPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 4. Переключатель режима: Мягкий (Системный) vs Захват звука (HyperOS Fix)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple(
                        BoostMode.STANDARD,
                        stringResource(R.string.mode_soft_title),
                        stringResource(R.string.mode_soft_desc)
                    ),
                    Triple(
                        BoostMode.CAPTURE,
                        stringResource(R.string.mode_capture_title),
                        stringResource(R.string.mode_capture_desc)
                    )
                ).forEach { (mode, title, desc) ->
                    val selected = settings.defaultBoostMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (selected) tokens.accentColor.copy(alpha = 0.22f)
                                else Color.Transparent
                            )
                            .clickable { viewModel.selectBoostMode(mode) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(title, color = tokens.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(desc, color = tokens.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        GlassSwitch(checked = selected, onCheckedChange = { viewModel.selectBoostMode(mode) })
                    }
                }
            }
        }

        // 5. Таймер автоотключения и проверка стерео
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.sleep_timer_title),
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (runtime.sleepTimerRemainingSec > 0) {
                        Text(
                            text = "${runtime.sleepTimerRemainingSec / 60} мин ${runtime.sleepTimerRemainingSec % 60} с",
                            color = tokens.accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0 to "Выкл", 15 to "15м", 30 to "30м", 60 to "60м").forEach { (mins, label) ->
                        GlassChip(
                            label = label,
                            selected = (mins == 0 && runtime.sleepTimerRemainingSec == 0),
                            onClick = { viewModel.setSleepTimerMinutes(mins) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                GlassButton(
                    text = stringResource(R.string.test_sound_btn),
                    icon = Icons.Default.GraphicEq,
                    onClick = { viewModel.triggerStereoTest() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(tokens.screenBottomPadding))
    }
}

@Composable
private fun RealTimeSignalBarsCanvas(
    bands: List<Float>,
    hasActiveStream: Boolean,
    peakSignal: Float,
    rmsSignal: Float
) {
    val tokens = LocalGlassTokens.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.28f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val count = bands.size.coerceAtLeast(1)
                val spacing = 4.dp.toPx()
                val barW = ((size.width - spacing * (count - 1)) / count).coerceAtLeast(3f)

                for (i in 0 until count) {
                    val level = bands[i].coerceIn(0.06f, 1f)
                    val barH = size.height * level
                    val x = i * (barW + spacing)

                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.08f),
                        topLeft = Offset(x, 0f),
                        size = Size(barW, size.height),
                        cornerRadius = CornerRadius(barW / 2f)
                    )

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(tokens.accentColor, tokens.secondaryAccent)
                        ),
                        topLeft = Offset(x, size.height - barH),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW / 2f)
                    )
                }
            }

            if (!hasActiveStream) {
                Text(
                    text = stringResource(R.string.signal_no_audio),
                    color = tokens.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "RMS: ${(rmsSignal * 100).toInt()}%",
                color = tokens.textSecondary,
                fontSize = 11.sp
            )
            Text(
                text = "Пик: ${(peakSignal * 100).toInt()}%",
                color = tokens.accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
