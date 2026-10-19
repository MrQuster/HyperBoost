package com.example.volumeboost.ui.components

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volumeboost.ui.theme.LocalGlassTokens
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.*

private const val LENS_REFRACTION_AGSL = """
    uniform shader content;
    uniform float2 lensCenter;
    uniform float2 lensRadius;
    uniform float refractionStrength;
    uniform float isDragging;

    half4 main(float2 coord) {
        float2 delta = coord - lensCenter;
        float2 norm = delta / max(lensRadius, float2(1.0, 1.0));
        float distSq = dot(norm, norm);

        if (distSq < 1.0) {
            float z = sqrt(1.0 - distSq);
            float mag = 1.0 - (refractionStrength * (0.16 + 0.12 * isDragging) * z);
            float2 refractedCoord = lensCenter + delta * mag;

            float2 chromaShift = delta * (distSq * 0.03 * refractionStrength);
            half r = content.eval(refractedCoord - chromaShift).r;
            half g = content.eval(refractedCoord).g;
            half b = content.eval(refractedCoord + chromaShift).b;
            half a = content.eval(refractedCoord).a;

            float rim = smoothstep(0.68, 1.0, distSq) * (0.14 + 0.10 * isDragging);
            return half4(r + rim, g + rim, b + rim * 1.12, a);
        }
        return content.eval(coord);
    }
"""

@Composable
fun GlassLensTabBar(
    items: List<GlassTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    freeLensDragEnabled: Boolean = false,
    refractionStrength: Float = 0.65f,
    borderStrokeWidth: Dp = 1.4.dp
) {
    val tokens = LocalGlassTokens.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current

    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)

    val lensX = remember { Animatable(0f) }
    var isDraggingLens by remember { mutableStateOf(false) }
    var horizontalVelocity by remember { mutableFloatStateOf(0f) }
    var isInitialized by remember { mutableStateOf(false) }

    val dragScale by animateFloatAsState(
        targetValue = if (isDraggingLens) 1.06f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lensDragScale"
    )

    val velocityStretch = (abs(horizontalVelocity) / 3600f).coerceIn(0f, 0.12f)
    val stretchX by animateFloatAsState(
        targetValue = dragScale * (1f + velocityStretch),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "stretchX"
    )
    val stretchY by animateFloatAsState(
        targetValue = dragScale * (1f - velocityStretch * 0.30f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "stretchY"
    )

    val agslShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(LENS_REFRACTION_AGSL) }.getOrNull()
        } else null
    }

    // Внешний контейнер с защитным градиентным затемнением (Scrim) снизу,
    // чтобы карточки при прокрутке никогда не читались сквозь панель и под ней
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        tokens.baseBackground.copy(alpha = 0.85f),
                        tokens.baseBackground
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(22.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(if (tokens.isDark) Color(0xF5070B14) else Color(0xF7F1F5F9))
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            tokens.accentColor.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.14f)
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
        ) {
            val barWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val barHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val tabCount = items.size.coerceAtLeast(1)
            val tabWidthPx = barWidthPx / tabCount
            val padPx = with(density) { 4.dp.toPx() }
            val lensWidthPx = (tabWidthPx - padPx * 1.5f).coerceAtLeast(40f)
            val lensHeightPx = (barHeightPx - padPx * 2f).coerceAtLeast(36f)

            LaunchedEffect(selectedIndex, barWidthPx, isDraggingLens) {
                val targetCenterX = (selectedIndex.coerceIn(0, tabCount - 1) + 0.5f) * tabWidthPx
                if (!isInitialized) {
                    lensX.snapTo(targetCenterX)
                    isInitialized = true
                } else if (!isDraggingLens) {
                    lensX.animateTo(
                        targetValue = targetCenterX,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(tabCount, barWidthPx) {
                        val touchSlop = viewConfig.touchSlop
                        val longPressTimeout = viewConfig.longPressTimeoutMillis

                        coroutineScope {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downTime = down.uptimeMillis
                                val startPos = down.position
                                val velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(downTime, startPos)

                                var dragActivated = false
                                var activeTabDuringDrag = currentSelectedIndex
                                var pointerId = down.id
                                var lastPosition = startPos

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                        ?: event.changes.firstOrNull()
                                    if (change == null || !change.pressed) {
                                        break
                                    }

                                    pointerId = change.id
                                    lastPosition = change.position
                                    velocityTracker.addPosition(change.uptimeMillis, lastPosition)

                                    val totalMove = (lastPosition - startPos).getDistance()
                                    val elapsed = change.uptimeMillis - downTime

                                    if (!dragActivated && (totalMove > touchSlop || elapsed >= longPressTimeout)) {
                                        dragActivated = true
                                        isDraggingLens = true
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }

                                    if (dragActivated) {
                                        val delta = change.positionChange()
                                        change.consume()
                                        horizontalVelocity = delta.x * 50f

                                        val minCenter = tabWidthPx * 0.5f
                                        val maxCenter = barWidthPx - tabWidthPx * 0.5f
                                        val clampedX = lastPosition.x.coerceIn(minCenter, maxCenter)

                                        launch {
                                            lensX.snapTo(clampedX)
                                        }

                                        val candidateTab = ((clampedX / tabWidthPx).toInt()).coerceIn(0, tabCount - 1)
                                        if (candidateTab != activeTabDuringDrag) {
                                            val candidateCenter = (candidateTab + 0.5f) * tabWidthPx
                                            if (abs(clampedX - candidateCenter) <= tabWidthPx * 0.38f) {
                                                activeTabDuringDrag = candidateTab
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                currentOnSelect(candidateTab)
                                            }
                                        }
                                    }
                                }

                                if (!dragActivated) {
                                    val tappedTab = ((lastPosition.x / tabWidthPx).toInt()).coerceIn(0, tabCount - 1)
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (tappedTab != currentSelectedIndex) {
                                        currentOnSelect(tappedTab)
                                    }
                                    val targetCenterX = (tappedTab + 0.5f) * tabWidthPx
                                    launch {
                                        lensX.animateTo(
                                            targetValue = targetCenterX,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                } else {
                                    val velX = velocityTracker.calculateVelocity().x
                                    horizontalVelocity = 0f
                                    isDraggingLens = false
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                    val projectedX = (lensX.value + velX * 0.08f)
                                        .coerceIn(tabWidthPx * 0.5f, barWidthPx - tabWidthPx * 0.5f)
                                    val finalTab = ((projectedX / tabWidthPx).toInt()).coerceIn(0, tabCount - 1)

                                    if (finalTab != currentSelectedIndex) {
                                        currentOnSelect(finalTab)
                                    }

                                    val finalCenterX = (finalTab + 0.5f) * tabWidthPx
                                    launch {
                                        lensX.animateTo(
                                            targetValue = finalCenterX,
                                            initialVelocity = velX * 0.20f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
            ) {
                // Слой иконок и подписей
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                agslShader != null &&
                                !tokens.powerSaveMode &&
                                refractionStrength > 0.02f
                            ) {
                                agslShader.setFloatUniform("lensCenter", lensX.value, barHeightPx * 0.5f)
                                agslShader.setFloatUniform(
                                    "lensRadius",
                                    (lensWidthPx * 0.5f) * stretchX,
                                    (lensHeightPx * 0.5f) * stretchY
                                )
                                agslShader.setFloatUniform("refractionStrength", refractionStrength)
                                agslShader.setFloatUniform("isDragging", if (isDraggingLens) 1f else 0f)
                                renderEffect = AndroidRenderEffect
                                    .createRuntimeShaderEffect(agslShader, "content")
                                    .asComposeRenderEffect()
                            } else {
                                renderEffect = null
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        val tabCenter = (index + 0.5f) * tabWidthPx
                        val distFromLens = abs(lensX.value - tabCenter)
                        val proximity = (1f - (distFromLens / tabWidthPx)).coerceIn(0f, 1f)
                        val isHighlighted = proximity > 0.42f

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = if (isHighlighted) tokens.accentColor else tokens.textSecondary,
                                modifier = Modifier.size(if (isHighlighted) 20.dp else 22.dp)
                            )
                            if (items.size < 5 || isHighlighted) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.title,
                                    color = if (isHighlighted) tokens.textPrimary else tokens.textSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Слой линзы, строго центрированный по вертикали и зажатый внутри границ панели
                val lensWidthDp = with(density) { lensWidthPx.toDp() }
                val lensHeightDp = with(density) { lensHeightPx.toDp() }

                Canvas(
                    modifier = Modifier
                        .offset {
                            val minLeft = padPx
                            val maxLeft = (barWidthPx - lensWidthPx - padPx).coerceAtLeast(minLeft)
                            val clampedLeft = (lensX.value - lensWidthPx * 0.5f).coerceIn(minLeft, maxLeft)
                            val centeredTop = (barHeightPx - lensHeightPx) * 0.5f
                            IntOffset(
                                x = clampedLeft.roundToInt(),
                                y = centeredTop.roundToInt()
                            )
                        }
                        .size(lensWidthDp, lensHeightDp)
                        .graphicsLayer {
                            scaleX = stretchX
                            scaleY = stretchY
                        }
                ) {
                    val corner = CornerRadius(size.height / 2f, size.height / 2f)
                    val px = tokens.parallaxOffset.x

                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                tokens.accentColor.copy(alpha = if (isDraggingLens) 0.36f else 0.24f),
                                tokens.secondaryAccent.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f + px * 0.4f, size.height * 0.5f),
                            radius = size.maxDimension * 0.75f
                        ),
                        cornerRadius = corner
                    )

                    val bodyAlpha = if (isDraggingLens) 0.18f else 0.22f
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = bodyAlpha + 0.10f),
                                tokens.accentColor.copy(alpha = bodyAlpha * 0.65f),
                                Color.White.copy(alpha = bodyAlpha * 0.30f)
                            )
                        ),
                        cornerRadius = corner
                    )

                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.95f),
                                tokens.accentColor.copy(alpha = 0.85f),
                                Color.White.copy(alpha = 0.25f)
                            )
                        ),
                        cornerRadius = corner,
                        style = Stroke(width = borderStrokeWidth.toPx())
                    )
                }
            }
        }
    }
}

@Composable
fun GlassSideNavigationRail(
    items: List<GlassTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalGlassTokens.current
    val haptics = LocalHapticFeedback.current

    GlassCard(
        modifier = modifier
            .fillMaxHeight()
            .width(96.dp)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        cornerRadius = 30.dp,
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (isSelected) tokens.accentColor.copy(alpha = 0.28f)
                            else Color.Transparent
                        )
                        .border(
                            width = if (isSelected) 1.3.dp else 0.dp,
                            color = if (isSelected) tokens.accentColor else Color.Transparent,
                            shape = RoundedCornerShape(22.dp)
                        )
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = if (isSelected) tokens.accentColor else tokens.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.title,
                        color = if (isSelected) tokens.textPrimary else tokens.textSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
