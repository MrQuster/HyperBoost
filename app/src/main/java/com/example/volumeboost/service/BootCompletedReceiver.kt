package com.example.volumeboost.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.volumeboost.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val repo = SettingsRepository(context.applicationContext)
                val settings = repo.settingsFlow.first()
                if (settings.startOnBoot) {
                    val srvIntent = Intent(context, AudioBoosterService::class.java).apply {
                        action = AudioBoosterService.ACTION_START_STANDARD
                        putExtra(AudioBoosterService.EXTRA_TARGET_BOOST, 125)
                    }
                    context.startForegroundService(srvIntent)
                }
            }
            pending.finish()
        }
    }
}
