package com.example.volumeboost.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.volumeboost.data.BoostMode
import com.example.volumeboost.data.BoostScaleMapper
import com.example.volumeboost.data.CustomEqPreset
import com.example.volumeboost.data.EqPreset
import com.example.volumeboost.data.GlobalSettings
import com.example.volumeboost.data.SettingsRepository
import com.example.volumeboost.device.AndroidSystemEnvironmentSource
import com.example.volumeboost.device.DeviceProfile
import com.example.volumeboost.device.DeviceProfileDetector
import com.example.volumeboost.device.FeatureRegistry
import com.example.volumeboost.device.OsOverrideOption
import com.example.volumeboost.service.AudioBoosterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CURRENT_APP_VERSION_CODE = 6

data class ConnectedAudioDeviceInfo(
    val id: String,
    val name: String,
    val type: String,
    val codecAndLatencyHint: String
)

@OptIn(FlowPreview::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    val repo = SettingsRepository(context)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val settings = repo.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, GlobalSettings())
    val runtimeState = AudioBoosterService.runtimeState

    // 5 вкладок нижней навигации: 0=Главная, 1=Эквалайзер, 2=Устройства, 3=Система, 4=Настройки
    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex = _selectedTabIndex.asStateFlow()

    fun selectTab(index: Int) {
        val clamped = index.coerceIn(0, 4)
        if (_selectedTabIndex.value != clamped) {
            _selectedTabIndex.value = clamped
        }
    }

    private val _connectedDevice = MutableStateFlow(
        ConnectedAudioDeviceInfo("SPEAKER", "Встроенные динамики", "SPEAKER", "Встроенный стерео ЦАП • 48 кГц")
    )
    val connectedDevice = _connectedDevice.asStateFlow()

    private val _boostPercent = MutableStateFlow(100)
    val boostPercent = _boostPercent.asStateFlow()

    private val persistBoostEvents = MutableSharedFlow<Int>(extraBufferCapacity = 16)

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            detectAndApplyDeviceProfile(fromHardwareCallback = true)
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            detectAndApplyDeviceProfile(fromHardwareCallback = true)
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        detectAndApplyDeviceProfile(fromHardwareCallback = false)

        // Синхронизируем ползунок и профиль устройства при нажатии −10% / +10% из Суперострова или виджета
        viewModelScope.launch {
            runtimeState.collect { rt ->
                if (rt.isRunning && _boostPercent.value != rt.currentBoostPercent) {
                    _boostPercent.value = rt.currentBoostPercent
                    persistBoostEvents.tryEmit(rt.currentBoostPercent)
                }
            }
        }

        viewModelScope.launch {
            persistBoostEvents
                .debounce(250L)
                .collectLatest { clampedValue ->
                    val currentDev = _connectedDevice.value
                    val profiles = repo.profilesFlow.first()
                    val currentProfile = profiles.firstOrNull { it.id == currentDev.id }
                        ?: com.example.volumeboost.data.DeviceProfile(
                            id = currentDev.id,
                            customName = currentDev.name,
                            type = currentDev.type
                        )
                    repo.upsertDeviceProfile(
                        currentProfile.copy(
                            boostPercent = clampedValue,
                            lastSeenTimestamp = System.currentTimeMillis()
                        )
                    )
                }
        }
    }

    fun detectAndApplyDeviceProfile(fromHardwareCallback: Boolean) {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
        val usb = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        val wired = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }

        val speakerLabel = "Динамики ${Build.MODEL ?: "устройства"}"
        val detected = when {
            bt != null -> {
                val productName = bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth Аудио"
                val sr = bt.sampleRates.maxOrNull() ?: 48000
                ConnectedAudioDeviceInfo(
                    id = "BT_${productName.uppercase()}",
                    name = productName,
                    type = "BLUETOOTH",
                    codecAndLatencyHint = "Bluetooth A2DP / BLE • ${sr / 1000} кГц • Задержка ~120 мс"
                )
            }
            usb != null -> {
                val productName = usb.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB-C ЦАП"
                ConnectedAudioDeviceInfo(
                    id = "USB_${productName.uppercase()}",
                    name = productName,
                    type = "USB",
                    codecAndLatencyHint = "Цифровой USB Hi-Res • Задержка <15 мс"
                )
            }
            wired != null -> {
                ConnectedAudioDeviceInfo(
                    id = "WIRED_HEADSET",
                    name = "Проводные наушники",
                    type = "WIRED",
                    codecAndLatencyHint = "Аналоговая линия • Задержка 0 мс"
                )
            }
            else -> {
                ConnectedAudioDeviceInfo(
                    id = "SPEAKER",
                    name = speakerLabel,
                    type = "SPEAKER",
                    codecAndLatencyHint = "Встроенные стереодинамики • 48 кГц"
                )
            }
        }

        _connectedDevice.value = detected

        viewModelScope.launch {
            val global = repo.settingsFlow.first()
            val profiles = repo.profilesFlow.first()
            val existing = profiles.firstOrNull { it.id == detected.id }
            val profileToUse = existing ?: com.example.volumeboost.data.DeviceProfile(
                id = detected.id,
                customName = detected.name,
                type = detected.type,
                boostPercent = if (detected.type == "BLUETOOTH") 130 else 115,
                autoStartOnConnect = detected.type == "BLUETOOTH" && global.autoStartOnBluetooth
            ).also { repo.upsertDeviceProfile(it) }

            _boostPercent.value = profileToUse.boostPercent

            if (runtimeState.value.isRunning) {
                context.startService(Intent(context, AudioBoosterService::class.java).apply {
                    action = AudioBoosterService.ACTION_FADE_TO_BOOST
                    putExtra(AudioBoosterService.EXTRA_TARGET_BOOST, profileToUse.boostPercent)
                    putExtra(AudioBoosterService.EXTRA_FADE_MS, global.fadeDurationMs)
                })
            } else if (fromHardwareCallback &&
                global.onboardingCompleted &&
                (profileToUse.autoStartOnConnect || (detected.type == "BLUETOOTH" && global.autoStartOnBluetooth))
            ) {
                runCatching { startStandardService(profileToUse.boostPercent) }
            }
        }
    }

    fun setBoostPercent(target: Int) {
        val maxCap = BoostScaleMapper.maxAllowedPercent(settings.value.unlimitedBoostEnabled)
        val clamped = target.coerceIn(0, maxCap)
        _boostPercent.value = clamped
        persistBoostEvents.tryEmit(clamped)

        if (runtimeState.value.isRunning) {
            context.startService(Intent(context, AudioBoosterService::class.java).apply {
                action = AudioBoosterService.ACTION_FADE_TO_BOOST
                putExtra(AudioBoosterService.EXTRA_TARGET_BOOST, clamped)
                putExtra(AudioBoosterService.EXTRA_FADE_MS, 65)
            })
        }
    }

    fun selectBoostMode(mode: BoostMode) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(defaultBoostMode = mode) }
            if (runtimeState.value.isRunning) {
                stopService()
            }
        }
    }

    fun startStandardService(boost: Int = _boostPercent.value) {
        context.startForegroundService(Intent(context, AudioBoosterService::class.java).apply {
            action = AudioBoosterService.ACTION_START_STANDARD
            putExtra(AudioBoosterService.EXTRA_TARGET_BOOST, boost)
        })
    }

    fun startCaptureService(resultCode: Int, data: Intent) {
        context.startForegroundService(Intent(context, AudioBoosterService::class.java).apply {
            action = AudioBoosterService.ACTION_START_CAPTURE
            putExtra(AudioBoosterService.EXTRA_TARGET_BOOST, _boostPercent.value)
            putExtra(AudioBoosterService.EXTRA_RESULT_CODE, resultCode)
            putExtra(AudioBoosterService.EXTRA_RESULT_DATA, data)
        })
    }

    fun stopService() {
        context.startService(Intent(context, AudioBoosterService::class.java).apply {
            action = AudioBoosterService.ACTION_STOP
        })
    }

    fun setSleepTimerMinutes(minutes: Int) {
        context.startService(Intent(context, AudioBoosterService::class.java).apply {
            action = AudioBoosterService.ACTION_SET_SLEEP_TIMER
            putExtra(AudioBoosterService.EXTRA_SLEEP_MINUTES, minutes)
        })
    }

    fun triggerStereoTest() {
        context.startService(Intent(context, AudioBoosterService::class.java).apply {
            action = AudioBoosterService.ACTION_PLAY_STEREO_TEST
        })
    }

    override fun onCleared() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        super.onCleared()
    }
}

class EqualizerViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val repo = SettingsRepository(context)

    val settings = repo.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, GlobalSettings())
    val customPresets = repo.customEqPresetsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val runtimeState = AudioBoosterService.runtimeState

    private fun syncService() {
        if (runtimeState.value.isRunning) {
            context.startService(Intent(context, AudioBoosterService::class.java).apply {
                action = AudioBoosterService.ACTION_SYNC_SETTINGS
            })
        }
    }

    fun selectBuiltInPreset(preset: EqPreset) {
        viewModelScope.launch {
            val mbList = preset.gainsDb.map { (it * 100f).toInt() }
            repo.updateSettings { it.copy(activeEqPreset = preset.name, customBandsMb = mbList) }
            syncService()
        }
    }

    fun applyCustomPreset(preset: CustomEqPreset) {
        viewModelScope.launch {
            repo.updateSettings {
                it.copy(activeEqPreset = EqPreset.CUSTOM.name, customBandsMb = preset.bandLevelsMillibels)
            }
            syncService()
        }
    }

    fun updateBandLevelMb(bandIndex: Int, levelMb: Int) {
        viewModelScope.launch {
            repo.updateSettings { current ->
                val mutable = current.customBandsMb.toMutableList()
                while (mutable.size <= bandIndex) mutable.add(0)
                mutable[bandIndex] = levelMb
                current.copy(activeEqPreset = EqPreset.CUSTOM.name, customBandsMb = mutable)
            }
            syncService()
        }
    }

    fun saveCurrentAsCustomPreset(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val currentBands = settings.value.customBandsMb
            repo.saveCustomEqPreset(CustomEqPreset(name.trim(), currentBands))
        }
    }

    fun deleteCustomPreset(name: String) {
        viewModelScope.launch {
            repo.deleteCustomEqPreset(name)
        }
    }

    fun setBassBoost(strength: Int) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(bassBoostStrength = strength.coerceIn(0, 1000)) }
            syncService()
        }
    }

    fun setVirtualizer(strength: Int) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(virtualizerStrength = strength.coerceIn(0, 1000)) }
            syncService()
        }
    }

    fun setStereoBalance(balance: Float) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(balanceLeftRight = balance.coerceIn(-1f, 1f)) }
            syncService()
        }
    }

    fun setMonoMode(enabled: Boolean) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(monoEnabled = enabled) }
            syncService()
        }
    }

    fun setCompressorThreshold(thresholdDb: Float) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(limiterThresholdDb = thresholdDb.coerceIn(-15f, 0f)) }
            syncService()
        }
    }

    fun resetEqualizer() {
        viewModelScope.launch {
            repo.updateSettings {
                it.copy(
                    activeEqPreset = EqPreset.FLAT.name,
                    customBandsMb = listOf(0, 0, 0, 0, 0),
                    bassBoostStrength = 0,
                    virtualizerStrength = 0,
                    balanceLeftRight = 0f,
                    monoEnabled = false
                )
            }
            syncService()
        }
    }
}

class DevicesViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val repo = SettingsRepository(context)

    val profiles = repo.profilesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings = repo.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, GlobalSettings())

    fun renameProfile(id: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val target = profiles.value.firstOrNull { it.id == id } ?: return@launch
            repo.upsertDeviceProfile(target.copy(customName = newName.trim()))
        }
    }

    fun togglePin(id: String) {
        viewModelScope.launch {
            val target = profiles.value.firstOrNull { it.id == id } ?: return@launch
            repo.upsertDeviceProfile(target.copy(isPinned = !target.isPinned))
        }
    }

    fun toggleAutoStart(id: String) {
        viewModelScope.launch {
            val target = profiles.value.firstOrNull { it.id == id } ?: return@launch
            repo.upsertDeviceProfile(target.copy(autoStartOnConnect = !target.autoStartOnConnect))
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            repo.deleteDeviceProfile(id)
        }
    }
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val repo = SettingsRepository(context)

    val settings = repo.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, GlobalSettings())
    val runtimeState = AudioBoosterService.runtimeState

    private val _deviceProfile = MutableStateFlow(DeviceProfile())
    val deviceProfile = _deviceProfile.asStateFlow()

    private val _isScanningProfile = MutableStateFlow(false)
    val isScanningProfile = _isScanningProfile.asStateFlow()

    init {
        viewModelScope.launch {
            val s = repo.settingsFlow.first()
            val envSource = AndroidSystemEnvironmentSource(context)
            val currentSignature = DeviceProfileDetector.computeBuildSignature(envSource)
            val cached = DeviceProfile.fromJsonString(s.cachedDeviceProfileJson)

            if (cached == null ||
                s.lastScannedAppVersion < CURRENT_APP_VERSION_CODE ||
                s.lastSystemBuildSignature != currentSignature
            ) {
                rescanDeviceProfile(s.osOverrideOption)
            } else {
                _deviceProfile.value = cached
            }
        }
    }

    fun rescanDeviceProfile(overrideOption: OsOverrideOption = settings.value.osOverrideOption) {
        viewModelScope.launch {
            _isScanningProfile.value = true
            val fresh = withContext(Dispatchers.Default) {
                DeviceProfileDetector.detect(
                    AndroidSystemEnvironmentSource(context),
                    overrideOption
                )
            }
            _deviceProfile.value = fresh
            repo.updateSettings {
                it.copy(
                    osOverrideOption = overrideOption,
                    cachedDeviceProfileJson = fresh.toJsonString(),
                    lastScannedAppVersion = CURRENT_APP_VERSION_CODE,
                    lastSystemBuildSignature = fresh.systemBuildSignature
                )
            }
            _isScanningProfile.value = false
            if (runtimeState.value.isRunning) {
                context.startService(Intent(context, AudioBoosterService::class.java).apply {
                    action = AudioBoosterService.ACTION_SYNC_SETTINGS
                })
            }
        }
    }

    fun update(transform: (GlobalSettings) -> GlobalSettings) {
        viewModelScope.launch {
            repo.updateSettings(transform)
            context.startService(Intent(context, AudioBoosterService::class.java).apply {
                action = AudioBoosterService.ACTION_SYNC_SETTINGS
            })
        }
    }

    fun triggerIslandPreview() {
        context.startForegroundService(Intent(context, AudioBoosterService::class.java).apply {
            action = AudioBoosterService.ACTION_TEST_ISLAND
        })
    }

    fun setAppLanguage(langCode: String) {
        update { it.copy(languageCode = langCode) }
        val localeList = LocaleListCompat.forLanguageTags(langCode)
        AppCompatDelegate.setApplicationLocales(localeList)
    }



    fun resetSettings() {
        viewModelScope.launch {
            repo.resetAllSettings()
            rescanDeviceProfile(OsOverrideOption.AUTO)
        }
    }

    fun clearDeviceProfiles() {
        viewModelScope.launch {
            repo.clearAllProfiles()
        }
    }

    suspend fun exportBackupJson(): String = repo.exportFullBackupJson()

    suspend fun importBackupJson(json: String): Boolean = repo.importFullBackupJson(json)

    fun buildDiagnosticReport(): String {
        val s = settings.value
        val r = runtimeState.value
        val p = _deviceProfile.value
        val hw = p.hardwareSpecs
        val plan = FeatureRegistry.resolvePlan(p, s.islandRoutingPreference, s.powerSaveGlass)
        val maxCap = BoostScaleMapper.maxAllowedPercent(s.unlimitedBoostEnabled)
        val modeRu = if (r.activeMode == BoostMode.STANDARD) "Мягкий (Session 0)" else "Захват звука (MediaProjection)"
        val activePresenter = r.activeIslandTier.ifBlank { s.lastActiveIslandTier }
        val lastErr = r.lastIslandError.ifBlank { s.lastIslandError }

        return buildString {
            appendLine("=== Профиль устройства (HyperBoost v6.0) ===")
            appendLine("Устройство: ${p.brand} ${p.model} (производитель: ${p.vendor}, код: ${p.deviceCodename})")
            appendLine("Оболочка: ${p.osFamily.displayNameRu} (${p.osVersion}) • Регион: ${p.romRegion}")
            appendLine("Android: ${p.androidRelease} • API ${p.apiLevel} (Полный: ${p.apiLevelFullFormatted}) • Патч: ${p.securityPatch}")
            appendLine("Сборка: ${p.displayBuildId} • Кастомный ROM: ${if (p.isLikelyCustomRom) "Да" else "Нет"}")
            appendLine("Уверенность: ${p.confidence.labelRu}")
            appendLine()
            appendLine("=== Железо и Аудио HAL ===")
            appendLine("SoC: ${hw.socManufacturer} ${hw.socModel} (${hw.cpuCores} ядер) • ОЗУ: ${hw.availableRamMb}/${hw.totalRamMb} МБ")
            appendLine("Дисплей: ${hw.screenResolutionPx} (${hw.screenDensityDpi} dpi, ${hw.refreshRateHz} Гц) • Вырез: ${p.cutoutStatus.labelRu}")
            appendLine("Аудио HAL: ${hw.nativeSampleRateHz} Гц, буфер ${hw.framesPerBuffer} фр. • LowLatency: ${hw.hasLowLatencyAudio}")
            appendLine("Метод усиления: $modeRu (${r.currentBoostPercent}% / потолок $maxCap%)")
            appendLine("Эффекты: ${r.supportedEffectsSummary}")
            appendLine()
            appendLine("=== Суперостров и Уведомления ===")
            appendLine("Стратегия: ${plan.selectedIslandStrategy.titleRu}")
            appendLine("Активный презентер: $activePresenter")
            appendLine("Фокусные уведомления: ${p.focusNotificationStatus.labelRu}")
            appendLine("Последний статус: $lastErr")
            appendLine("Сигналы (${p.matchedSignals.size}): ${p.matchedSignals.joinToString(", ")}")
        }
    }
}
