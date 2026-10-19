package com.example.volumeboost.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class BoosterTileService : TileService() {

    private var tileJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        tileJob = CoroutineScope(Dispatchers.Main).launch {
            AudioBoosterService.runtimeState.collect { state ->
                val tile = qsTile ?: return@collect
                tile.state = if (state.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.label = if (state.isRunning) "Буст ${state.currentBoostPercent}%" else "HyperBoost"
                tile.subtitle = if (state.isRunning) "+10% / −10%" else "Выкл"
                tile.updateTile()
            }
        }
    }

    override fun onStopListening() {
        tileJob?.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val state = AudioBoosterService.runtimeState.value
        if (state.isRunning) {
            startService(Intent(this, AudioBoosterService::class.java).apply {
                action = AudioBoosterService.ACTION_STOP
            })
        } else {
            ContextCompat.startForegroundService(
                this,
                Intent(this, AudioBoosterService::class.java).apply {
                    action = AudioBoosterService.ACTION_START_STANDARD
                }
            )
        }
    }
}
