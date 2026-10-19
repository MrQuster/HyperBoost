package com.example.volumeboost.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.volumeboost.R
import com.example.volumeboost.data.BoostScaleMapper
import com.example.volumeboost.ui.theme.LocalGlassTokens
import kotlin.math.*

@Composable
fun rememberSensorParallax(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val rawOffset = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(enabled) {
        if (!enabled) {
            rawOffset.value = Offset.Zero
            return@DisposableEffect onDispose {}
        }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                val x = (-event.values[0] * 3.8f).coerceIn(-28f, 28f)
                val y = (event.values[1] * 3.8f).coerceIn(-28f, 28f)
                rawOffset.value = Offset(x, y)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val animX by animateFloatAsState(
        targetValue = rawOffset.value.x,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "px"
    )
    val animY by animateFloatAsState(
        targetValue = rawOffset.value.y,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "py"
    )
    return remember(animX, animY) { derivedStateOf { Offset(animX, animY) } }
}

@Composable
fun DynamicBlobBackground(
    boostPercent: Int,
    audioPeakLevel: Float,
    animationSpeed: Float
) {
    val tokens = LocalGlassTokens.current
    val infiniteTransition = rememberInfiniteTransition(label = "blobs")

    val duration = ((10000 / animationSpeed.coerceAtLeast(0.2f)).toInt()).coerceIn(3000, 28000)
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blobPhase"
    )

    val blob1Color by animateColorAsState(
        targetValue = when {
            boostPercent <= 150 -> tokens.accentColor
            boostPercent <= 400 -> Color(0xFF6930C3)
            else -> Color(0xFF0096C7)
        },
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "b1"
    )

    val blob2Color by animateColorAsState(
        targetValue = when {
            boostPercent <= 150 -> tokens.secondaryAccent
            boostPercent <= 400 -> Color(0xFF3A0CA3)
            else -> Color(0xFF7209B7)
        },
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "b2"
    )

    val effectiveBlur = if (tokens.powerSaveMode) 28.dp else tokens.blurRadius

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.baseBackground)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .blur(effectiveBlur)
        ) {
            val w = size.width
            val h = size.height
            val px = tokens.parallaxOffset.x
            val py = tokens.parallaxOffset.y
            val boostFactor = (ln((boostPercent.coerceAtLeast(100) / 100f).toDouble()).toFloat() * 0.08f).coerceIn(0f, 0.25f)
            val reactivePulse = 1f + (audioPeakLevel * 0.18f) + boostFactor

            // Сниженная яркость пятен для соблюдения контраста WCAG AA (>4.5:1)
            val alphaBase = when {
                tokens.isAmoled -> 0.20f
                tokens.isDark -> 0.26f
                else -> 0.22f
            }

            drawCircle(
                color = blob1Color.copy(alpha = alphaBase),
                radius = w * 0.44f * reactivePulse,
                center = Offset(
                    x = w * (0.25f + 0.10f * cos(phase)) + px * 1.8f,
                    y = h * (0.20f + 0.08f * sin(phase)) + py * 1.8f
                )
            )

            drawCircle(
                color = blob2Color.copy(alpha = alphaBase * 0.9f),
                radius = w * 0.48f * reactivePulse,
                center = Offset(
                    x = w * (0.76f + 0.10f * sin(phase * 0.8f)) - px * 1.5f,
                    y = h * (0.72f + 0.08f * cos(phase * 0.8f)) - py * 1.5f
                )
            )
        }

        // Тонкий выравнивающий слой затемнения для безупречной читаемости белого текста
        if (tokens.isDark && !tokens.isAmoled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x40040711))
            )
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = LocalGlassTokens.current.cardCornerRadius,
    tintColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = LocalGlassTokens.current
    val shape = RoundedCornerShape(cornerRadius)
    val px = tokens.parallaxOffset.x
    val py = tokens.parallaxOffset.y
    val alpha = tokens.glassSurfaceAlpha

    val baseTint = tintColor ?: Color.White
    val surfaceColors = if (tokens.isDark) {
        listOf(
            baseTint.copy(alpha = (alpha + 0.06f).coerceAtMost(0.38f)),
            Color.White.copy(alpha = (alpha * 0.40f).coerceAtLeast(0.05f)),
            baseTint.copy(alpha = (alpha * 0.65f).coerceAtMost(0.28f))
        )
    } else {
        listOf(
            Color.White.copy(alpha = 0.88f),
            Color.White.copy(alpha = 0.68f),
            Color.White.copy(alpha = 0.78f)
        )
    }

    val borderColors = if (tokens.isDark) {
        listOf(
            Color.White.copy(alpha = 0.55f),
            tokens.accentColor.copy(alpha = 0.25f),
            Color.White.copy(alpha = 0.10f)
        )
    } else {
        listOf(
            Color.White.copy(alpha = 0.95f),
            tokens.accentColor.copy(alpha = 0.40f),
            Color.White.copy(alpha = 0.40f)
        )
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = if (tokens.powerSaveMode) 6.dp else tokens.shadowElevation,
                shape = shape,
                ambientColor = tokens.accentColor.copy(alpha = 0.18f),
                spotColor = Color.Black.copy(alpha = 0.50f)
            )
            .clip(shape)
            .background(tokens.cardBackingColor)
            .background(
                brush = Brush.linearGradient(
                    colors = surfaceColors,
                    start = Offset(px * 3f, py * 3f),
                    end = Offset(850f - px * 3f, 850f - py * 3f)
                )
            )
            .border(
                width = tokens.borderStrokeWidth,
                brush = Brush.linearGradient(
                    colors = borderColors,
                    start = Offset(0f + px * 2f, 0f + py * 2f),
                    end = Offset(700f, 700f)
                ),
                shape = shape
            )
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isAccent: Boolean = false,
    isDanger: Boolean = false
) {
    val tokens = LocalGlassTokens.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "btnScale"
    )

    val shape = RoundedCornerShape(22.dp)
    val bgBrush = when {
        isDanger -> Brush.linearGradient(
            listOf(
                tokens.dangerColor.copy(alpha = 0.38f),
                tokens.dangerColor.copy(alpha = 0.18f)
            )
        )
        isAccent -> Brush.linearGradient(
            listOf(
                tokens.accentColor.copy(alpha = 0.38f),
                tokens.secondaryAccent.copy(alpha = 0.22f)
            )
        )
        else -> Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (tokens.isDark) 0.22f else 0.85f),
                Color.White.copy(alpha = if (tokens.isDark) 0.12f else 0.65f)
            )
        )
    }

    val borderStrokeColor = when {
        isDanger -> tokens.dangerColor.copy(alpha = 0.80f)
        isAccent -> tokens.accentColor.copy(alpha = 0.80f)
        else -> Color.White.copy(alpha = 0.45f)
    }

    Row(
        modifier = modifier
            .heightIn(min = 46.dp)
            .scale(scale)
            .clip(shape)
            .background(bgBrush)
            .border(width = 1.1.dp, color = borderStrokeColor, shape = shape)
            .clickable(interactionSource = interactionSource, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    isDanger -> Color(0xFFFF8FA3)
                    isAccent -> tokens.accentColor
                    else -> tokens.textPrimary
                },
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalGlassTokens.current
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.03f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "chipScale"
    )
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .heightIn(min = 38.dp)
            .scale(scale)
            .clip(shape)
            .background(
                if (selected) tokens.accentColor.copy(alpha = 0.34f)
                else Color.White.copy(alpha = if (tokens.isDark) 0.12f else 0.65f)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) tokens.accentColor else Color.White.copy(alpha = 0.32f),
                shape = shape
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = tokens.textPrimary,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val tokens = LocalGlassTokens.current
    val haptics = LocalHapticFeedback.current
    val thumbProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "switchThumb"
    )

    Box(
        modifier = Modifier
            .width(54.dp)
            .height(30.dp)
            .clip(CircleShape)
            .background(
                if (checked) tokens.accentColor.copy(alpha = 0.40f)
                else Color.White.copy(alpha = if (tokens.isDark) 0.16f else 0.55f)
            )
            .border(
                width = 1.2.dp,
                color = if (checked) tokens.accentColor else Color.White.copy(alpha = 0.38f),
                shape = CircleShape
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = (22.dp * thumbProgress))
                .size(22.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            if (checked) tokens.accentColor else Color.LightGray
                        )
                    )
                )
        )
    }
}

@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier
) {
    val tokens = LocalGlassTokens.current
    val haptics = LocalHapticFeedback.current
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    var isDragging by remember { mutableStateOf(false) }

    val dropletScale by animateFloatAsState(
        targetValue = if (isDragging) 1.25f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "dropScale"
    )

    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    val newFrac = (offset.x / size.width).coerceIn(0f, 1f)
                    val newVal = valueRange.start + newFrac * span
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnValueChange(newVal)
                }
            }
            .pointerInput(valueRange) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false }
                ) { change, _ ->
                    change.consume()
                    val newFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                    val newVal = valueRange.start + newFrac * span
                    currentOnValueChange(newVal)
                }
            }
    ) {
        val trackHeight = 12.dp.toPx()
        val cy = size.height / 2f
        val thumbX = (size.width * fraction).coerceIn(14.dp.toPx(), size.width - 14.dp.toPx())

        drawRoundRect(
            color = Color.White.copy(alpha = if (tokens.isDark) 0.16f else 0.55f),
            topLeft = Offset(0f, cy - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )

        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(tokens.secondaryAccent, tokens.accentColor)
            ),
            topLeft = Offset(0f, cy - trackHeight / 2f),
            size = Size(thumbX, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )

        val radius = 12.dp.toPx() * dropletScale
        drawCircle(
            color = tokens.accentColor.copy(alpha = 0.35f),
            radius = radius * 1.35f,
            center = Offset(thumbX, cy)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, tokens.accentColor),
                center = Offset(thumbX - radius * 0.3f, cy - radius * 0.3f),
                radius = radius * 1.2f
            ),
            radius = radius,
            center = Offset(thumbX, cy)
        )
        drawCircle(
            color = Color.White,
            radius = radius,
            center = Offset(thumbX, cy),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

/**
 * Унифицированный горизонтальный ползунок с ровной шапкой (название слева, бейдж значения справа)
 */
@Composable
fun LabeledGlassSliderRow(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    val tokens = LocalGlassTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = tokens.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(tokens.accentColor.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = valueText,
                    color = tokens.accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
        GlassSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

/**
 * Вертикальный слайдер полосы эквалайзера без залипаний (с rememberUpdatedState и поддержкой тапа)
 */
@Composable
fun GlassVerticalSlider(
    freqLabel: String,
    gainDbText: String,
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalGlassTokens.current
    val haptics = LocalHapticFeedback.current
    val currentFraction by rememberUpdatedState(fraction)
    val currentOnFractionChange by rememberUpdatedState(onFractionChange)

    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = gainDbText,
            color = tokens.accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        Canvas(
            modifier = Modifier
                .width(42.dp)
                .height(160.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val newFrac = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnFractionChange(newFrac)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val newFrac = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
                        if (abs(newFrac - currentFraction) > 0.03f) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        currentOnFractionChange(newFrac)
                    }
                }
        ) {
            val trackW = 12.dp.toPx()
            val cx = size.width / 2f
            val clamped = fraction.coerceIn(0f, 1f)
            val pad = 12.dp.toPx()
            val usableH = size.height - pad * 2f
            val thumbY = pad + usableH * (1f - clamped)

            // Центральная нулевая риска (0 dB)
            drawLine(
                color = Color.White.copy(alpha = 0.28f),
                start = Offset(cx - 12.dp.toPx(), size.height / 2f),
                end = Offset(cx + 12.dp.toPx(), size.height / 2f),
                strokeWidth = 1.2.dp.toPx()
            )

            drawRoundRect(
                color = Color.White.copy(alpha = if (tokens.isDark) 0.16f else 0.55f),
                topLeft = Offset(cx - trackW / 2f, pad),
                size = Size(trackW, usableH),
                cornerRadius = CornerRadius(trackW / 2f)
            )

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(tokens.accentColor, tokens.secondaryAccent)
                ),
                topLeft = Offset(cx - trackW / 2f, thumbY),
                size = Size(trackW, (size.height - pad) - thumbY),
                cornerRadius = CornerRadius(trackW / 2f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, tokens.accentColor),
                    center = Offset(cx - 3f, thumbY - 3f),
                    radius = 14.dp.toPx()
                ),
                radius = 10.5.dp.toPx(),
                center = Offset(cx, thumbY)
            )
            drawCircle(
                color = Color.White,
                radius = 10.5.dp.toPx(),
                center = Offset(cx, thumbY),
                style = Stroke(width = 1.4.dp.toPx())
            )
        }

        Text(
            text = freqLabel,
            color = tokens.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * Круговой регулятор усиления, синхронизированный с линейным слайдером по единой шкале BoostScaleMapper,
 * с опорными рисками и крупным русским статусом в центре.
 */
@Composable
fun CircularLiquidDial(
    boostPercent: Int,
    unlimitedMode: Boolean,
    isRunning: Boolean,
    audioPeak: Float,
    onBoostChange: (Int) -> Unit,
    onTogglePower: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalGlassTokens.current
    val haptics = LocalHapticFeedback.current
    val currentOnBoostChange by rememberUpdatedState(onBoostChange)
    val reusableWavePath = remember { Path() }

    val infiniteTransition = rememberInfiniteTransition(label = "dialWave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "wavePhase"
    )

    val dialColor by animateColorAsState(
        targetValue = if (isRunning) tokens.accentColor else Color(0xFF7B8794),
        label = "dialColor"
    )

    val fillRatio = remember(boostPercent, unlimitedMode) {
        BoostScaleMapper.percentToFraction(boostPercent, unlimitedMode)
    }

    Box(
        modifier = modifier
            .size(256.dp)
            .pointerInput(unlimitedMode) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = change.position.x - center.x
                    val dy = change.position.y - center.y
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (angle < 0f) angle += 360f
                    val shifted = (angle - 135f + 360f) % 360f
                    if (shifted <= 285f) {
                        val frac = (shifted / 270f).coerceIn(0f, 1f)
                        val mappedPercent = BoostScaleMapper.fractionToPercent(frac, unlimitedMode)
                        if (mappedPercent != boostPercent) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnBoostChange(mappedPercent)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 15.dp.toPx()
            val radius = (size.minDimension - stroke * 2.6f) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (tokens.isDark) 0.14f else 0.70f),
                        dialColor.copy(alpha = 0.10f)
                    )
                ),
                radius = radius - 14.dp.toPx(),
                center = center
            )

            // Живая жидкая волна внутри сферы
            val innerRadius = radius - 16.dp.toPx()
            reusableWavePath.reset()
            val waterTopY = center.y + innerRadius - (2f * innerRadius * fillRatio)
            val waveAmp = (4.dp.toPx() + audioPeak * 14.dp.toPx())

            reusableWavePath.moveTo(center.x - innerRadius, center.y + innerRadius)
            var x = center.x - innerRadius
            while (x <= center.x + innerRadius) {
                val normX = (x - (center.x - innerRadius)) / (2f * innerRadius)
                val y = waterTopY + sin(normX * 2f * PI + wavePhase).toFloat() * waveAmp
                reusableWavePath.lineTo(x, y)
                x += 8f
            }
            reusableWavePath.lineTo(center.x + innerRadius, center.y + innerRadius)
            reusableWavePath.close()

            drawPath(
                path = reusableWavePath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        dialColor.copy(alpha = 0.26f),
                        tokens.secondaryAccent.copy(alpha = 0.08f)
                    )
                )
            )

            // Опорные риски шкалы вдоль дуги (0%, 100% на 0.25, 200% на 0.50, 500% на 0.75, MAX на 1.0)
            val tickFractions = listOf(0f, 0.25f, 0.50f, 0.75f, 1.0f)
            tickFractions.forEach { tf ->
                val tickRad = Math.toRadians((135f + 270f * tf).toDouble())
                val innerR = radius + stroke * 0.65f
                val outerR = radius + stroke * 1.15f
                drawLine(
                    color = if (fillRatio >= tf) tokens.accentColor else Color.White.copy(alpha = 0.35f),
                    start = Offset(
                        center.x + innerR * cos(tickRad).toFloat(),
                        center.y + innerR * sin(tickRad).toFloat()
                    ),
                    end = Offset(
                        center.x + outerR * cos(tickRad).toFloat(),
                        center.y + outerR * sin(tickRad).toFloat()
                    ),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Фоновая дуга (270 градусов)
            drawArc(
                color = Color.White.copy(alpha = 0.16f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // Активная неоновая дуга
            val sweep = 270f * fillRatio
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(tokens.secondaryAccent, dialColor, Color(0xFF9B5DE5), tokens.secondaryAccent)
                ),
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // Стеклянная капля-бегунок на дуге
            val thumbAngleRad = Math.toRadians((135f + sweep).toDouble())
            val thumbCenter = Offset(
                x = center.x + radius * cos(thumbAngleRad).toFloat(),
                y = center.y + radius * sin(thumbAngleRad).toFloat()
            )
            drawCircle(
                color = Color.White,
                radius = 11.dp.toPx(),
                center = thumbCenter
            )
            drawCircle(
                color = dialColor,
                radius = 7.dp.toPx(),
                center = thumbCenter
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(CircleShape)
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTogglePower()
                }
                .padding(16.dp)
        ) {
            Text(
                text = "$boostPercent%",
                color = tokens.textPrimary,
                fontSize = if (boostPercent >= 1000) 36.sp else 42.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isRunning) tokens.accentColor.copy(alpha = 0.24f)
                        else Color.White.copy(alpha = 0.14f)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isRunning) stringResource(R.string.dial_status_on)
                    else stringResource(R.string.dial_status_off),
                    color = if (isRunning) tokens.accentColor else tokens.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

data class GlassTabItem(
    val title: String,
    val icon: ImageVector
)

@Composable
fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    isDangerConfirm: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalGlassTokens.current
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            cornerRadius = 28.dp,
            tintColor = tokens.baseBackground
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = title,
                    color = tokens.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                content()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    GlassButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
                    Spacer(modifier = Modifier.width(10.dp))
                    GlassButton(
                        text = confirmText,
                        onClick = onConfirm,
                        isAccent = !isDangerConfirm,
                        isDanger = isDangerConfirm
                    )
                }
            }
        }
    }
}
