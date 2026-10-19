package com.example.volumeboost.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.volumeboost.service.AudioBoosterService

class BoosterGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetGlassContent()
        }
    }

    @Composable
    private fun WidgetGlassContent() {
        val state = AudioBoosterService.runtimeState.value
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(ColorProvider(Color(0xE6080C16)))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "HyperBoost",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF00F5D4)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = if (state.isRunning) "${state.currentBoostPercent}%" else "Выкл (${state.currentBoostPercent}%)",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Button(
                    text = "−10%",
                    onClick = actionRunCallback<StepDownWidgetAction>()
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Button(
                    text = "+10%",
                    onClick = actionRunCallback<StepUpWidgetAction>()
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Button(
                    text = if (state.isRunning) "Выкл" else "Вкл",
                    onClick = actionRunCallback<ToggleBoostWidgetAction>()
                )
            }
        }
    }
}

class StepDownWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, AudioBoosterService::class.java).apply {
            action = AudioBoosterService.ACTION_STEP_DOWN
        }
        ContextCompat.startForegroundService(context, intent)
        BoosterGlanceWidget().update(context, glanceId)
    }
}

class StepUpWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, AudioBoosterService::class.java).apply {
            action = AudioBoosterService.ACTION_STEP_UP
        }
        ContextCompat.startForegroundService(context, intent)
        BoosterGlanceWidget().update(context, glanceId)
    }
}

class ToggleBoostWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val running = AudioBoosterService.runtimeState.value.isRunning
        val intent = Intent(context, AudioBoosterService::class.java).apply {
            action = if (running) AudioBoosterService.ACTION_STOP else AudioBoosterService.ACTION_START_STANDARD
        }
        if (running) context.startService(intent) else ContextCompat.startForegroundService(context, intent)
        BoosterGlanceWidget().update(context, glanceId)
    }
}

class BoosterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BoosterGlanceWidget()
}
