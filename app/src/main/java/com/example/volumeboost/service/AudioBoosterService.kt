package com.example.volumeboost.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.*
import android.media.audiofx.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.volumeboost.MainActivity
import com.example.volumeboost.R
import com.example.volumeboost.data.*
import com.example.volumeboost.device.AndroidSystemEnvironmentSource
import com.example.volumeboost.device.DeviceProfile
import com.example.volumeboost.device.DeviceProfileDetector
import com.example.volumeboost.island.IslandBuildResult
import com.example.volumeboost.island.IslandFallbackCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

data class EqBandInfo(
    val index: Int,
    val centerFreqHz: Int,
    val minLevelMb: Int,
    val maxLevelMb: Int,
    val currentLevelMb: Int
)

data class ServiceRuntimeState(
    val isRunning: Boolean = false,
    val currentBoostPercent: Int = 100,
    val currentGainMillibels: Int = 0,
    val activeMode: BoostMode = BoostMode.STANDARD,
    val limiterMode: LimiterMode = LimiterMode.OFF,
    val isLimiterEngaged: Boolean = false,
    val hasActiveAudioStream: Boolean = false,
    val peakSignal: Float = 0f,
    val rmsSignal: Float = 0f,
    val spectrumBands: List<Float> = List(24) { 0f },
    val statusMessage: String = "Усилитель в режиме ожидания",
    val isCapturingSilenceOrDrm: Boolean = false,
    val eqBands: List<EqBandInfo> = emptyList(),
    val supportedEffectsSummary: String = "LoudnessEnhancer, Equalizer, BassBoost, Virtualizer, DynamicsProcessing",
    val sleepTimerRemainingSec: Int = 0,
    val activeIslandTier: String = "SuperIslandPresenter",
    val islandSelectionReason: String = "Автовыбор по профилю системы",
    val lastIslandError: String = "Нет ошибок"
)

@OptIn(FlowPreview::class)
class AudioBoosterService : Service() {

    companion object {
        private const val TAG = "AudioBoosterService"
        const val CHANNEL_ID = "hyperboost_v7_island_channel"
        const val NOTIF_ID = 3003
        const val MAX_HARDWARE_MILLIBELS = 30000
        const val SPECTRUM_BAR_COUNT = 24

        const val ACTION_START_STANDARD = "ACTION_START_STANDARD"
        const val ACTION_START_CAPTURE = "ACTION_START_CAPTURE"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_SYNC_SETTINGS = "ACTION_SYNC_SETTINGS"
        const val ACTION_FADE_TO_BOOST = "ACTION_FADE_TO_BOOST"
        const val ACTION_STEP_UP = "ACTION_STEP_UP"
        const val ACTION_STEP_DOWN = "ACTION_STEP_DOWN"
        const val ACTION_SET_SLEEP_TIMER = "ACTION_SET_SLEEP_TIMER"
        const val ACTION_PLAY_STEREO_TEST = "ACTION_PLAY_STEREO_TEST"
        const val ACTION_TEST_ISLAND = "ACTION_TEST_ISLAND"

        const val EXTRA_TARGET_BOOST = "EXTRA_TARGET_BOOST"
        const val EXTRA_FADE_MS = "EXTRA_FADE_MS"
        const val EXTRA_SLEEP_MINUTES = "EXTRA_SLEEP_MINUTES"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"

        private val _runtimeState = MutableStateFlow(ServiceRuntimeState())
        val runtimeState = _runtimeState.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var repo: SettingsRepository
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var islandCoordinator: IslandFallbackCoordinator

    private val islandUpdateRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    @Volatile
    private var cachedSettings: GlobalSettings = GlobalSettings()

    @Volatile
    private var cachedDeviceProfile: DeviceProfile = DeviceProfile()

    @Volatile
    private var isDuckedByAudioFocus: Boolean = false

    @Volatile
    private var lastBoostChangeTimestampMs: Long = 0L

    // Атомарная цель усиления, исключающая потерю быстрых нажатий −10% / +10% во время fade
    @Volatile
    private var targetBoostPercent: Int = 100

    private var savedInitialSystemVolume: Int? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    @Suppress("DEPRECATION")
    private var virtualizer: Virtualizer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var systemVisualizer: Visualizer? = null

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var captureJob: Job? = null
    private var fadeJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var visualizerLoopJob: Job? = null
    private var testIslandJob: Job? = null

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mainHandler.post {
                if (_runtimeState.value.activeMode == BoostMode.CAPTURE && _runtimeState.value.isRunning) {
                    stopAllEngines()
                    stopForegroundAndCancelIsland()
                    stopSelf()
                }
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                isDuckedByAudioFocus = true
                runCatching { loudnessEnhancer?.setTargetGain(0) }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                isDuckedByAudioFocus = false
                val curr = _runtimeState.value.currentBoostPercent
                runCatching {
                    loudnessEnhancer?.setTargetGain(calculateGainMillibels(curr))
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                isDuckedByAudioFocus = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository(applicationContext)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        islandCoordinator = IslandFallbackCoordinator(applicationContext)
        createNotificationChannel()

        serviceScope.launch {
            cachedDeviceProfile = DeviceProfileDetector.detect(AndroidSystemEnvironmentSource(applicationContext))
            repo.settingsFlow.collect { latest ->
                cachedSettings = latest
                val fromCache = DeviceProfile.fromJsonString(latest.cachedDeviceProfileJson)
                cachedDeviceProfile = if (fromCache != null && latest.osOverrideOption.forcedFamily == null) {
                    fromCache
                } else {
                    DeviceProfileDetector.detect(
                        AndroidSystemEnvironmentSource(applicationContext),
                        latest.osOverrideOption
                    )
                }
            }
        }

        // Дебаунс обновлений Суперострова (не чаще ~6 раз в секунду)
        serviceScope.launch {
            islandUpdateRequests
                .sample(160L)
                .collect {
                    if (_runtimeState.value.isRunning) {
                        postOrUpdateIslandNotification(targetBoostPercent)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_STANDARD -> {
                testIslandJob?.cancel()
                val boost = intent.getIntExtra(EXTRA_TARGET_BOOST, _runtimeState.value.currentBoostPercent)
                targetBoostPercent = boost
                startStandardEngine(boost, cachedSettings)
            }
            ACTION_START_CAPTURE -> {
                testIslandJob?.cancel()
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                val boost = intent.getIntExtra(EXTRA_TARGET_BOOST, _runtimeState.value.currentBoostPercent)
                targetBoostPercent = boost
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    startCaptureEngine(resultCode, resultData, boost, cachedSettings)
                } else {
                    _runtimeState.value = _runtimeState.value.copy(
                        statusMessage = getString(R.string.status_idle)
                    )
                }
            }
            ACTION_FADE_TO_BOOST -> {
                lastBoostChangeTimestampMs = SystemClock.elapsedRealtime()
                val maxCap = BoostScaleMapper.maxAllowedPercent(cachedSettings.unlimitedBoostEnabled)
                val target = intent.getIntExtra(EXTRA_TARGET_BOOST, 100).coerceIn(0, maxCap)
                val fadeMs = intent.getIntExtra(EXTRA_FADE_MS, cachedSettings.fadeDurationMs)
                targetBoostPercent = target
                smoothFadeBoostTo(target, fadeMs)
            }
            ACTION_SYNC_SETTINGS -> {
                applyAllParameters(targetBoostPercent, cachedSettings)
                updateFloatingOverlay(cachedSettings.floatingButtonEnabled)
                islandUpdateRequests.tryEmit(Unit)
            }
            ACTION_STEP_UP -> {
                lastBoostChangeTimestampMs = SystemClock.elapsedRealtime()
                val s = cachedSettings
                val maxCap = BoostScaleMapper.maxAllowedPercent(s.unlimitedBoostEnabled)
                val base = if (_runtimeState.value.isRunning) targetBoostPercent else _runtimeState.value.currentBoostPercent
                val next = (base + BoostScaleMapper.FIXED_STEP_PERCENT).coerceAtMost(maxCap)
                targetBoostPercent = next
                if (!_runtimeState.value.isRunning) {
                    startStandardEngine(next, s)
                } else {
                    smoothFadeBoostTo(next, 80)
                    islandUpdateRequests.tryEmit(Unit)
                }
            }
            ACTION_STEP_DOWN -> {
                lastBoostChangeTimestampMs = SystemClock.elapsedRealtime()
                val s = cachedSettings
                val base = if (_runtimeState.value.isRunning) targetBoostPercent else _runtimeState.value.currentBoostPercent
                val prev = (base - BoostScaleMapper.FIXED_STEP_PERCENT).coerceAtLeast(0)
                targetBoostPercent = prev
                if (!_runtimeState.value.isRunning) {
                    startStandardEngine(prev, s)
                } else {
                    smoothFadeBoostTo(prev, 80)
                    islandUpdateRequests.tryEmit(Unit)
                }
            }
            ACTION_SET_SLEEP_TIMER -> {
                val minutes = intent.getIntExtra(EXTRA_SLEEP_MINUTES, 0)
                startSleepTimer(minutes)
            }
            ACTION_PLAY_STEREO_TEST -> {
                playStereoTestTone()
            }
            ACTION_TEST_ISLAND -> {
                runFiveSecondIslandPreview()
            }
            ACTION_STOP -> {
                testIslandJob?.cancel()
                stopAllEngines()
                stopForegroundAndCancelIsland()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun runFiveSecondIslandPreview() {
        lastBoostChangeTimestampMs = SystemClock.elapsedRealtime()
        if (_runtimeState.value.isRunning) {
            postOrUpdateIslandNotification(targetBoostPercent)
            return
        }
        testIslandJob?.cancel()
        testIslandJob = serviceScope.launch {
            val buildResult = buildIslandForState(
                boostPercent = 150,
                isRunning = true,
                isMusicPlaying = true,
                settings = cachedSettings
            )
            withContext(Dispatchers.Main) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIF_ID, buildResult.notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIF_ID, buildResult.notification)
                }
            }
            delay(5000L)
            if (!_runtimeState.value.isRunning) {
                withContext(Dispatchers.Main) {
                    stopForegroundAndCancelIsland()
                    stopSelf()
                }
            }
        }
    }

    private fun startStandardEngine(boostPercent: Int, settings: GlobalSettings) {
        stopAllEngines()
        saveSystemVolumeBaseline()
        requestTransientAudioFocusMonitor()

        val maxCap = BoostScaleMapper.maxAllowedPercent(settings.unlimitedBoostEnabled)
        val clamped = boostPercent.coerceIn(0, maxCap)
        targetBoostPercent = clamped

        val islandResult = buildIslandForState(
            boostPercent = clamped,
            isRunning = true,
            isMusicPlaying = audioManager.isMusicActive,
            settings = settings
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, islandResult.notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, islandResult.notification)
        }

        syncSystemVolumeForSub100(clamped)
        bindAudioEffectsToSession(0, clamped, settings)
        val gainMb = calculateGainMillibels(clamped)

        _runtimeState.value = _runtimeState.value.copy(
            isRunning = true,
            currentBoostPercent = clamped,
            currentGainMillibels = gainMb,
            activeMode = BoostMode.STANDARD,
            limiterMode = settings.limiterMode,
            statusMessage = getString(R.string.status_active_soft, gainMb / 100f),
            activeIslandTier = islandResult.activeTierName,
            islandSelectionReason = islandResult.selectionReasonRu,
            lastIslandError = islandResult.errorSummary
        )

        startSession0Analyzer()
        updateFloatingOverlay(settings.floatingButtonEnabled)
    }

    @SuppressLint("MissingPermission")
    private fun startCaptureEngine(
        resultCode: Int,
        resultData: Intent,
        boostPercent: Int,
        settings: GlobalSettings
    ) {
        stopAllEngines()
        saveSystemVolumeBaseline()
        requestTransientAudioFocusMonitor()

        val maxCap = BoostScaleMapper.maxAllowedPercent(settings.unlimitedBoostEnabled)
        val clamped = boostPercent.coerceIn(0, maxCap)
        targetBoostPercent = clamped

        val islandResult = buildIslandForState(
            boostPercent = clamped,
            isRunning = true,
            isMusicPlaying = true,
            settings = settings
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, islandResult.notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, islandResult.notification)
        }

        try {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mpManager.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("MediaProjection null")

            projection.registerCallback(mediaProjectionCallback, mainHandler)
            mediaProjection = projection

            val sampleRate = 48000
            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()

            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            audioRecord = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBuf * 2)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val sessionId = audioTrack!!.audioSessionId
            bindAudioEffectsToSession(sessionId, 100, settings)

            audioRecord?.startRecording()
            audioTrack?.play()

            _runtimeState.value = _runtimeState.value.copy(
                isRunning = true,
                currentBoostPercent = clamped,
                currentGainMillibels = calculateGainMillibels(clamped),
                activeMode = BoostMode.CAPTURE,
                limiterMode = settings.limiterMode,
                statusMessage = getString(R.string.status_active_capture, clamped / 100f),
                activeIslandTier = islandResult.activeTierName,
                islandSelectionReason = islandResult.selectionReasonRu,
                lastIslandError = islandResult.errorSummary
            )

            updateFloatingOverlay(settings.floatingButtonEnabled)

            captureJob = serviceScope.launch {
                val pcmBuffer = ShortArray(minBuf / 2)
                var silentFrames = 0
                var smoothedGain = (clamped / 100.0).coerceIn(0.0, 100.0)
                val barBuckets = FloatArray(SPECTRUM_BAR_COUNT)

                while (isActive) {
                    val readCount = audioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: -1
                    if (readCount > 0) {
                        val s = cachedSettings
                        val desiredBoost = if (isDuckedByAudioFocus) 100 else targetBoostPercent
                        val targetGain = (desiredBoost / 100.0).coerceIn(0.0, 100.0)

                        val balance = s.balanceLeftRight.toDouble().coerceIn(-1.0, 1.0)
                        val leftBal = if (balance > 0.0) 1.0 - balance else 1.0
                        val rightBal = if (balance < 0.0) 1.0 + balance else 1.0
                        val isMono = s.monoEnabled
                        val limMode = s.limiterMode
                        val thresholdLinear = (32767.0 * 10.0.pow(s.limiterThresholdDb.toDouble() / 20.0))
                            .coerceIn(4000.0, 32760.0)

                        var maxSamplePeak = 0.0
                        var sumSquares = 0.0
                        var limiterTriggered = false
                        barBuckets.fill(0f)
                        val samplesPerBar = (readCount / SPECTRUM_BAR_COUNT).coerceAtLeast(2)

                        var i = 0
                        while (i < readCount - 1) {
                            smoothedGain += (targetGain - smoothedGain) * 0.0015

                            var left = pcmBuffer[i].toDouble() * smoothedGain * leftBal
                            var right = pcmBuffer[i + 1].toDouble() * smoothedGain * rightBal

                            if (isMono) {
                                val monoMix = (left + right) * 0.5
                                left = monoMix
                                right = monoMix
                            }

                            if (limMode != LimiterMode.OFF) {
                                if (abs(left) > thresholdLinear || abs(right) > thresholdLinear) {
                                    limiterTriggered = true
                                }
                                left = applySampleLimiterDouble(left, limMode, thresholdLinear)
                                right = applySampleLimiterDouble(right, limMode, thresholdLinear)
                            }

                            if (!left.isFinite()) left = 0.0
                            if (!right.isFinite()) right = 0.0

                            left = left.coerceIn(-32768.0, 32767.0)
                            right = right.coerceIn(-32768.0, 32767.0)

                            val pairPeak = max(abs(left), abs(right))
                            if (pairPeak > maxSamplePeak) maxSamplePeak = pairPeak
                            sumSquares += (left * left + right * right) * 0.5

                            val barIdx = (i / samplesPerBar).coerceIn(0, SPECTRUM_BAR_COUNT - 1)
                            val normAbs = (pairPeak / 32767.0).toFloat()
                            if (normAbs > barBuckets[barIdx]) {
                                barBuckets[barIdx] = normAbs
                            }

                            pcmBuffer[i] = left.toInt().toShort()
                            pcmBuffer[i + 1] = right.toInt().toShort()
                            i += 2
                        }

                        if (maxSamplePeak < 15.0) silentFrames++ else silentFrames = 0
                        val normalizedPeak = (maxSamplePeak / 32767.0).toFloat().coerceIn(0f, 1f)
                        val rms = (sqrt(sumSquares / (readCount / 2.0)) / 32767.0).toFloat().coerceIn(0f, 1f)
                        val streamPlaying = maxSamplePeak >= 15.0

                        val prevPlaying = _runtimeState.value.hasActiveAudioStream
                        _runtimeState.value = _runtimeState.value.copy(
                            hasActiveAudioStream = streamPlaying,
                            peakSignal = normalizedPeak,
                            rmsSignal = rms,
                            spectrumBands = barBuckets.toList(),
                            isLimiterEngaged = limiterTriggered,
                            isCapturingSilenceOrDrm = silentFrames > 130
                        )

                        if (prevPlaying != streamPlaying) {
                            islandUpdateRequests.tryEmit(Unit)
                        }

                        audioTrack?.write(pcmBuffer, 0, readCount)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Сбой режима захвата, переключаюсь на Session 0", e)
            startStandardEngine(clamped, settings)
        }
    }

    private fun buildIslandForState(
        boostPercent: Int,
        isRunning: Boolean,
        isMusicPlaying: Boolean,
        settings: GlobalSettings
    ): IslandBuildResult {
        val (devName, devTag) = resolveCurrentOutputDeviceInfo()
        val modeLabel = if (_runtimeState.value.activeMode == BoostMode.STANDARD) "Мягкий" else "Захват DSP"
        val pulseActive = (SystemClock.elapsedRealtime() - lastBoostChangeTimestampMs) < 1600L

        val res = islandCoordinator.buildBestNotification(
            channelId = CHANNEL_ID,
            deviceProfile = cachedDeviceProfile,
            settings = settings,
            isRunning = isRunning,
            isMusicPlaying = isMusicPlaying,
            highlightPulse = pulseActive,
            boostPercent = boostPercent,
            deviceName = devName,
            deviceTypeTag = devTag,
            modeLabel = modeLabel
        )

        serviceScope.launch {
            if (settings.lastActiveIslandTier != res.activeTierName ||
                settings.lastIslandError != res.errorSummary
            ) {
                repo.updateSettings {
                    it.copy(
                        lastActiveIslandTier = res.activeTierName,
                        lastIslandError = res.errorSummary
                    )
                }
            }
        }
        return res
    }

    private fun postOrUpdateIslandNotification(boostPercent: Int) {
        val settings = cachedSettings
        val buildResult = buildIslandForState(
            boostPercent = boostPercent,
            isRunning = _runtimeState.value.isRunning,
            isMusicPlaying = _runtimeState.value.hasActiveAudioStream || audioManager.isMusicActive,
            settings = settings
        )
        runCatching {
            notificationManager.notify(NOTIF_ID, buildResult.notification)
            _runtimeState.value = _runtimeState.value.copy(
                activeIslandTier = buildResult.activeTierName,
                islandSelectionReason = buildResult.selectionReasonRu,
                lastIslandError = buildResult.errorSummary
            )
        }.onFailure { e ->
            _runtimeState.value = _runtimeState.value.copy(
                lastIslandError = e.localizedMessage ?: "Ошибка отправки уведомления"
            )
        }
    }

    private fun resolveCurrentOutputDeviceInfo(): Pair<String, String> {
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
        return when {
            bt != null -> (bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth") to "Bluetooth"
            usb != null -> (usb.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB-ЦАП") to "USB"
            wired != null -> "Проводные наушники" to "AUX"
            else -> cachedDeviceProfile.model.take(18) to "Динамик"
        }
    }

    private fun applySampleLimiterDouble(sample: Double, mode: LimiterMode, threshold: Double): Double {
        return when (mode) {
            LimiterMode.OFF -> sample
            LimiterMode.HARD -> sample.coerceIn(-threshold, threshold)
            LimiterMode.SOFT -> {
                val knee = threshold * 0.75
                if (abs(sample) <= knee) {
                    sample
                } else {
                    val norm = sample / 32768.0
                    tanh(norm) * 32767.0
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun bindAudioEffectsToSession(
        sessionId: Int,
        boostPercent: Int,
        settings: GlobalSettings
    ) {
        releaseAudioEffects()
        val activeEffects = mutableListOf<String>()

        runCatching {
            val le = LoudnessEnhancer(sessionId)
            le.setTargetGain(calculateGainMillibels(boostPercent))
            le.enabled = true
            if (le.enabled) {
                loudnessEnhancer = le
                activeEffects.add("LoudnessEnhancer")
            } else {
                le.release()
            }
        }

        val eqBandsList = mutableListOf<EqBandInfo>()
        runCatching {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = true
                val numBands = numberOfBands.toInt()
                val range = bandLevelRange
                applyPresetBandsToEqualizer(this, settings)
                for (b in 0 until numBands) {
                    eqBandsList.add(
                        EqBandInfo(
                            index = b,
                            centerFreqHz = getCenterFreq(b.toShort()) / 1000,
                            minLevelMb = range[0].toInt(),
                            maxLevelMb = range[1].toInt(),
                            currentLevelMb = getBandLevel(b.toShort()).toInt()
                        )
                    )
                }
            }
            activeEffects.add("Equalizer (${eqBandsList.size} полос)")
        }

        runCatching {
            bassBoost = BassBoost(0, sessionId).apply {
                enabled = true
                if (strengthSupported) setStrength(settings.bassBoostStrength.coerceIn(0, 1000).toShort())
            }
            activeEffects.add("BassBoost")
        }

        runCatching {
            virtualizer = Virtualizer(0, sessionId).apply {
                enabled = true
                if (strengthSupported) setStrength(settings.virtualizerStrength.coerceIn(0, 1000).toShort())
            }
            activeEffects.add("Virtualizer")
        }

        runCatching {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                true, 1,
                false, 0,
                false, 0,
                true
            ).build()
            dynamicsProcessing = DynamicsProcessing(0, sessionId, config).apply {
                val extraInputGainDb = if (loudnessEnhancer == null && boostPercent > 100) {
                    (20f * log10(boostPercent / 100f)).coerceIn(0f, 36f)
                } else 0f
                setInputGainAllChannelsTo(extraInputGainDb)

                val limiterConfig = DynamicsProcessing.Limiter(
                    true,
                    settings.limiterMode != LimiterMode.OFF,
                    0,
                    settings.attackMs.toFloat().coerceAtLeast(1f),
                    settings.releaseMs.toFloat().coerceAtLeast(10f),
                    10f,
                    settings.limiterThresholdDb,
                    0f
                )
                setLimiterAllChannelsTo(limiterConfig)
                enabled = settings.limiterMode != LimiterMode.OFF || extraInputGainDb > 0f
            }
            activeEffects.add("DynamicsProcessing")
        }

        _runtimeState.value = _runtimeState.value.copy(
            eqBands = eqBandsList,
            supportedEffectsSummary = if (activeEffects.isEmpty()) "Нет аппаратных эффектов" else activeEffects.joinToString(", ")
        )
    }

    private fun applyPresetBandsToEqualizer(eq: Equalizer, settings: GlobalSettings) {
        val numBands = eq.numberOfBands.toInt()
        val minMb = eq.bandLevelRange[0].toInt()
        val maxMb = eq.bandLevelRange[1].toInt()

        val preset = runCatching { EqPreset.valueOf(settings.activeEqPreset) }.getOrDefault(EqPreset.FLAT)
        for (b in 0 until numBands) {
            val targetMb = if (preset == EqPreset.CUSTOM) {
                settings.customBandsMb.getOrElse(b) { 0 }
            } else {
                val db = preset.gainsDb.getOrElse(b) { 0f }
                (db * 100f).toInt()
            }
            eq.setBandLevel(b.toShort(), targetMb.coerceIn(minMb, maxMb).toShort())
        }
    }

    @Suppress("DEPRECATION")
    private fun applyAllParameters(boostPercent: Int, settings: GlobalSettings) {
        val maxCap = BoostScaleMapper.maxAllowedPercent(settings.unlimitedBoostEnabled)
        val clamped = boostPercent.coerceIn(0, maxCap)

        syncSystemVolumeForSub100(clamped)

        val gainMb = calculateGainMillibels(clamped)
        if (_runtimeState.value.activeMode == BoostMode.STANDARD && !isDuckedByAudioFocus) {
            runCatching {
                loudnessEnhancer?.setTargetGain(gainMb)
            }
        }

        runCatching {
            equalizer?.let { eq ->
                applyPresetBandsToEqualizer(eq, settings)
                val updatedBands = (0 until eq.numberOfBands).map { b ->
                    EqBandInfo(
                        index = b,
                        centerFreqHz = eq.getCenterFreq(b.toShort()) / 1000,
                        minLevelMb = eq.bandLevelRange[0].toInt(),
                        maxLevelMb = eq.bandLevelRange[1].toInt(),
                        currentLevelMb = eq.getBandLevel(b.toShort()).toInt()
                    )
                }
                _runtimeState.value = _runtimeState.value.copy(eqBands = updatedBands)
            }
        }
        runCatching {
            bassBoost?.let { if (it.strengthSupported) it.setStrength(settings.bassBoostStrength.coerceIn(0, 1000).toShort()) }
        }
        runCatching {
            virtualizer?.let { if (it.strengthSupported) it.setStrength(settings.virtualizerStrength.coerceIn(0, 1000).toShort()) }
        }
        runCatching {
            dynamicsProcessing?.let { dp ->
                val extraInputGainDb = if (loudnessEnhancer == null && clamped > 100) {
                    (20f * log10(clamped / 100f)).coerceIn(0f, 36f)
                } else 0f
                dp.setInputGainAllChannelsTo(extraInputGainDb)

                val lim = DynamicsProcessing.Limiter(
                    true,
                    settings.limiterMode != LimiterMode.OFF,
                    0,
                    settings.attackMs.toFloat().coerceAtLeast(1f),
                    settings.releaseMs.toFloat().coerceAtLeast(10f),
                    10f,
                    settings.limiterThresholdDb,
                    0f
                )
                dp.setLimiterAllChannelsTo(lim)
                dp.enabled = settings.limiterMode != LimiterMode.OFF || extraInputGainDb > 0f
            }
        }

        val cleanStatus = if (!_runtimeState.value.isRunning) {
            getString(R.string.status_idle)
        } else if (_runtimeState.value.activeMode == BoostMode.STANDARD) {
            getString(R.string.status_active_soft, gainMb / 100f)
        } else {
            getString(R.string.status_active_capture, clamped / 100f)
        }

        _runtimeState.value = _runtimeState.value.copy(
            currentBoostPercent = clamped,
            currentGainMillibels = gainMb,
            limiterMode = settings.limiterMode,
            statusMessage = cleanStatus
        )

        if (_runtimeState.value.isRunning) {
            islandUpdateRequests.tryEmit(Unit)
        }
        updateFloatingText(clamped)
    }

    private fun startSession0Analyzer() {
        visualizerLoopJob?.cancel()
        runCatching { systemVisualizer?.release() }
        systemVisualizer = null

        val hasRecordPerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasRecordPerm) {
            runCatching {
                systemVisualizer = Visualizer(0).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[0]
                    enabled = true
                }
            }
        }

        visualizerLoopJob = serviceScope.launch {
            val waveBytes = ByteArray(128)
            var phase = 0f
            while (isActive && _runtimeState.value.activeMode == BoostMode.STANDARD) {
                val musicActive = audioManager.isMusicActive
                val boost = _runtimeState.value.currentBoostPercent
                val boostFactor = (ln((boost.coerceAtLeast(60) / 50.0)).toFloat() * 0.34f).coerceIn(0.15f, 1.0f)

                var hardwarePeak = 0f
                val vis = systemVisualizer
                if (vis != null && vis.enabled) {
                    runCatching {
                        if (vis.getWaveForm(waveBytes) == Visualizer.SUCCESS) {
                            var maxDiff = 0
                            for (b in waveBytes) {
                                val unsigned = b.toInt() and 0xFF
                                val centered = abs(unsigned - 128)
                                if (centered > maxDiff) maxDiff = centered
                            }
                            hardwarePeak = (maxDiff / 128f).coerceIn(0f, 1f)
                        }
                    }
                }

                val isStreamActive = musicActive || hardwarePeak > 0.04f
                val prevActive = _runtimeState.value.hasActiveAudioStream

                if (isStreamActive) {
                    phase += 0.38f
                    val basePeak = if (hardwarePeak > 0.05f) {
                        (hardwarePeak * (boost / 100f)).coerceIn(0.10f, 1.0f)
                    } else {
                        (boostFactor + (sin(phase) * 0.12f)).coerceIn(0.12f, 0.96f)
                    }

                    val bars = List(SPECTRUM_BAR_COUNT) { idx ->
                        val freqCurve = sin((idx + 1f) / SPECTRUM_BAR_COUNT * PI).toFloat()
                        val wave = abs(sin(phase + idx * 0.45f))
                        (basePeak * (0.45f + 0.55f * freqCurve * wave)).coerceIn(0.06f, 1.0f)
                    }
                    val rms = (bars.sum() / SPECTRUM_BAR_COUNT).coerceIn(0.05f, 0.95f)
                    val limiterActive = _runtimeState.value.limiterMode != LimiterMode.OFF && basePeak > 0.78f

                    _runtimeState.value = _runtimeState.value.copy(
                        hasActiveAudioStream = true,
                        peakSignal = basePeak,
                        rmsSignal = rms,
                        spectrumBands = bars,
                        isLimiterEngaged = limiterActive
                    )
                } else {
                    _runtimeState.value = _runtimeState.value.copy(
                        hasActiveAudioStream = false,
                        peakSignal = 0f,
                        rmsSignal = 0f,
                        spectrumBands = List(SPECTRUM_BAR_COUNT) { 0f },
                        isLimiterEngaged = false
                    )
                }

                if (prevActive != isStreamActive) {
                    islandUpdateRequests.tryEmit(Unit)
                }
                delay(75L)
            }
        }
    }

    private fun saveSystemVolumeBaseline() {
        if (savedInitialSystemVolume == null) {
            savedInitialSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
    }

    private fun syncSystemVolumeForSub100(boostPercent: Int) {
        val maxSys = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (boostPercent < 100) {
            val targetVol = ((boostPercent / 100f) * maxSys).roundToInt().coerceIn(0, maxSys)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
        } else {
            savedInitialSystemVolume?.let { initial ->
                if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) < initial) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, initial, 0)
                }
            }
        }
    }

    private fun restoreSystemVolumeBaseline() {
        savedInitialSystemVolume?.let { initial ->
            runCatching {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, initial, 0)
            }
        }
        savedInitialSystemVolume = null
    }

    private fun requestTransientAudioFocusMonitor() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setOnAudioFocusChangeListener(audioFocusChangeListener, mainHandler)
            .setWillPauseWhenDucked(false)
            .build()
        audioFocusRequest = req
    }

    private fun smoothFadeBoostTo(targetBoost: Int, durationMs: Int) {
        fadeJob?.cancel()
        fadeJob = serviceScope.launch {
            val s = cachedSettings
            val startBoost = _runtimeState.value.currentBoostPercent
            val diff = targetBoost - startBoost
            if (diff == 0 || durationMs <= 20) {
                withContext(Dispatchers.Main) { applyAllParameters(targetBoost, s) }
                return@launch
            }
            val steps = 8
            val stepDelay = (durationMs / steps).toLong().coerceAtLeast(8L)
            for (i in 1..steps) {
                val intermediate = startBoost + ((diff * i) / steps)
                withContext(Dispatchers.Main) { applyAllParameters(intermediate, s) }
                delay(stepDelay)
            }
        }
    }

    private fun calculateGainMillibels(boostPercent: Int): Int {
        if (boostPercent <= 100) return 0
        val rawGainMb = if (boostPercent <= 1000) {
            ((boostPercent - 100) * 16.666).roundToInt()
        } else {
            15000 + (((boostPercent - 1000) / 9000.0) * 15000.0).roundToInt()
        }
        return rawGainMb.coerceIn(0, MAX_HARDWARE_MILLIBELS)
    }

    private fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _runtimeState.value = _runtimeState.value.copy(sleepTimerRemainingSec = 0)
            return
        }
        sleepTimerJob = serviceScope.launch {
            var remaining = minutes * 60
            while (remaining > 0 && isActive) {
                _runtimeState.value = _runtimeState.value.copy(sleepTimerRemainingSec = remaining)
                delay(1000L)
                remaining--
            }
            if (remaining <= 0) {
                withContext(Dispatchers.Main) {
                    stopAllEngines()
                    stopForegroundAndCancelIsland()
                    stopSelf()
                }
            }
        }
    }

    private fun playStereoTestTone() {
        serviceScope.launch {
            runCatching {
                val sampleRate = 44100
                val durationSec = 2
                val totalSamples = sampleRate * durationSec
                val stereoBuf = ShortArray(totalSamples * 2)
                val boostFactor = (_runtimeState.value.currentBoostPercent / 100.0).coerceIn(0.25, 4.0)

                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val env = sin(PI * t / durationSec)
                    val leftVal = if (t < 1.0) sin(2.0 * PI * 440.0 * t) * env else 0.0
                    val rightVal = if (t >= 1.0) sin(2.0 * PI * 660.0 * t) * env else 0.0

                    stereoBuf[i * 2] = (leftVal * 12000.0 * boostFactor).coerceIn(-32767.0, 32767.0).toInt().toShort()
                    stereoBuf[i * 2 + 1] = (rightVal * 12000.0 * boostFactor).coerceIn(-32767.0, 32767.0).toInt().toShort()
                }

                val testTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(stereoBuf.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                testTrack.write(stereoBuf, 0, stereoBuf.size)
                testTrack.play()
                delay(2200L)
                testTrack.stop()
                testTrack.release()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun updateFloatingOverlay(enabled: Boolean) {
        if (!enabled || !Settings.canDrawOverlays(this) || !_runtimeState.value.isRunning) {
            removeFloatingOverlay()
            return
        }
        if (floatingView != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bubble = TextView(this).apply {
            text = "${_runtimeState.value.currentBoostPercent}%"
            setTextColor(AndroidColor.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(28, 20, 28, 20)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AndroidColor.argb(215, 10, 18, 38))
                setStroke(3, AndroidColor.rgb(0, 245, 212))
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 350
        }

        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - touchX).toInt()
                        params.y = initialY + (event.rawY - touchY).toInt()
                        windowManager?.updateViewLayout(v, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (abs(event.rawX - touchX) < 12 && abs(event.rawY - touchY) < 12) {
                            val openIntent = Intent(this@AudioBoosterService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(openIntent)
                        }
                        return true
                    }
                }
                return false
            }
        })

        runCatching {
            windowManager?.addView(bubble, params)
            floatingView = bubble
        }
    }

    private fun updateFloatingText(boostPercent: Int) {
        (floatingView as? TextView)?.text = "$boostPercent%"
    }

    private fun removeFloatingOverlay() {
        floatingView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        floatingView = null
    }

    @Suppress("DEPRECATION")
    private fun releaseAudioEffects() {
        runCatching { systemVisualizer?.enabled = false; systemVisualizer?.release() }
        runCatching { loudnessEnhancer?.enabled = false; loudnessEnhancer?.release() }
        runCatching { equalizer?.enabled = false; equalizer?.release() }
        runCatching { bassBoost?.enabled = false; bassBoost?.release() }
        runCatching { virtualizer?.enabled = false; virtualizer?.release() }
        runCatching { dynamicsProcessing?.enabled = false; dynamicsProcessing?.release() }
        systemVisualizer = null
        loudnessEnhancer = null
        equalizer = null
        bassBoost = null
        virtualizer = null
        dynamicsProcessing = null
    }

    private fun stopForegroundAndCancelIsland() {
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
            notificationManager.cancel(NOTIF_ID)
        }
    }

    private fun stopAllEngines() {
        captureJob?.cancel()
        visualizerLoopJob?.cancel()
        sleepTimerJob?.cancel()
        fadeJob?.cancel()
        releaseAudioEffects()
        removeFloatingOverlay()
        restoreSystemVolumeBaseline()

        audioFocusRequest?.let { req ->
            runCatching { audioManager.abandonAudioFocusRequest(req) }
        }
        audioFocusRequest = null
        isDuckedByAudioFocus = false

        runCatching { audioRecord?.stop(); audioRecord?.release() }
        runCatching { audioTrack?.stop(); audioTrack?.release() }
        runCatching {
            mediaProjection?.unregisterCallback(mediaProjectionCallback)
            mediaProjection?.stop()
        }
        audioRecord = null
        audioTrack = null
        mediaProjection = null

        _runtimeState.value = _runtimeState.value.copy(
            isRunning = false,
            hasActiveAudioStream = false,
            peakSignal = 0f,
            rmsSignal = 0f,
            spectrumBands = List(SPECTRUM_BAR_COUNT) { 0f },
            isLimiterEngaged = false,
            isCapturingSilenceOrDrm = false,
            sleepTimerRemainingSec = 0,
            statusMessage = getString(R.string.status_idle)
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!_runtimeState.value.isRunning) {
            stopForegroundAndCancelIsland()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        testIslandJob?.cancel()
        stopAllEngines()
        stopForegroundAndCancelIsland()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
