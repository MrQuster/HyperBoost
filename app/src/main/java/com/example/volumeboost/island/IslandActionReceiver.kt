package com.example.volumeboost.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.volumeboost.service.AudioBoosterService

class IslandActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TOGGLE = "io.github.mrquster.hyperboost.ISLAND_ACTION_TOGGLE"
        const val ACTION_STEP_MINUS = "io.github.mrquster.hyperboost.ISLAND_ACTION_STEP_MINUS"
        const val ACTION_STEP_PLUS = "io.github.mrquster.hyperboost.ISLAND_ACTION_STEP_PLUS"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val state = AudioBoosterService.runtimeState.value
        val isRunning = state.isRunning

        IslandEventLogger.record(
            seqId = state.lastIslandSequenceId,
            action = "BUTTON_CLICK:${action.substringAfterLast('.')}",
            presenterTier = state.activeIslandTier,
            boostPercent = state.currentBoostPercent,
            jsonSizeBytes = 0,
            isSuccess = true,
            detail = "Получен клик из острова/уведомления (running=$isRunning)"
        )

        when (action) {
            ACTION_TOGGLE -> {
                if (isRunning) {
                    context.startService(Intent(context, AudioBoosterService::class.java).apply {
                        this.action = AudioBoosterService.ACTION_STOP
                    })
                } else {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, AudioBoosterService::class.java).apply {
                            this.action = AudioBoosterService.ACTION_START_STANDARD
                        }
                    )
                }
            }
            ACTION_STEP_MINUS -> {
                dispatchStepToService(context, isRunning, AudioBoosterService.ACTION_STEP_DOWN)
            }
            ACTION_STEP_PLUS -> {
                dispatchStepToService(context, isRunning, AudioBoosterService.ACTION_STEP_UP)
            }
        }
    }

    private fun dispatchStepToService(context: Context, isRunning: Boolean, serviceAction: String) {
        val serviceIntent = Intent(context, AudioBoosterService::class.java).apply {
            action = serviceAction
        }
        if (isRunning) {
            context.startService(serviceIntent)
        } else {
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
