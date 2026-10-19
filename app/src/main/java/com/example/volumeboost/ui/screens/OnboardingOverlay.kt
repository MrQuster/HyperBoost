package com.example.volumeboost.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volumeboost.ui.components.GlassButton
import com.example.volumeboost.ui.components.GlassCard
import com.example.volumeboost.ui.theme.LocalGlassTokens

@Composable
fun OnboardingOverlay(onFinishOnboarding: () -> Unit) {
    val tokens = LocalGlassTokens.current
    var step by remember { mutableIntStateOf(0) }

    val pages = listOf(
        Triple(
            Icons.Default.VolumeUp,
            "Добро пожаловать в HyperBoost 2.0",
            "Усилитель громкости в эстетике Liquid Glass, созданный специально для Xiaomi 17T Pro и прошивки HyperOS."
        ),
        Triple(
            Icons.Default.GraphicEq,
            "Два звуковых движка + Лимитер",
            "Мягкий режим (Session 0) работает без задержки. А режим «Захват звука» (MediaProjection) обходит любые системные ограничения HyperOS и защищает динамики от хрипа мягким DSP-лимитером."
        ),
        Triple(
            Icons.Default.Headphones,
            "Умные профили устройств",
            "Приложение автоматически распознаёт динамики телефона, Bluetooth-наушники и USB-ЦАП, плавно применяя индивидуальный буст и эквалайзер без щелчков."
        ),
        Triple(
            Icons.Default.Security,
            "Необходимые разрешения",
            "На следующем шаге разрешите уведомления (для управления из шторки), доступ к Bluetooth (для чтения имени наушников) и микрофону/захвату аудио (требование Android для AudioPlaybackCapture)."
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        val current = pages[step]
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Icon(
                    imageVector = current.first,
                    contentDescription = null,
                    tint = tokens.accentColor,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = current.second,
                    color = tokens.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = current.third,
                    color = tokens.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Text(
                    text = "Шаг ${step + 1} из ${pages.size}",
                    color = tokens.accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (step > 0) {
                        GlassButton(text = "Назад", onClick = { step-- })
                    } else {
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    GlassButton(
                        text = if (step < pages.lastIndex) "Далее" else "Начать работу",
                        isAccent = true,
                        onClick = {
                            if (step < pages.lastIndex) step++ else onFinishOnboarding()
                        }
                    )
                }
            }
        }
    }
}
