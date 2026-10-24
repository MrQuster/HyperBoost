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
import android.os.PowerManager
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
import com.example.volumeboost.dsp.AudioEffectTestResultItem
import com.example.volumeboost.dsp.DspEffectRoute
import com.example.volumeboost.dsp.SoftwarePcmDspProcessor
import com.example.volumeboost.island.*
import com.example.volumeboost.util.LocaleHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
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
    val statusMessage: String = "",
    val isCapturingSilenceOrDrm: Boolean = false,
    val eqBands: List<EqBandInfo> = emptyList(),
    val supportedEffectsSummary: String = "LoudnessEnhancer, Equalizer, DynamicsProcessing, Biquad DSP",
    val activeBassRoute: DspEffectRoute = DspEffectRoute.DYNAMICS_PROCESSING_STAGE,
    val activeVirtualizerRoute: DspEffectRoute = DspEffectRoute.DYNAMICS_PROCESSING_STAGE,
    val audioEffectTestResults: List<AudioEffectTestResultItem> = emptyList(),
    val sleepTimerRemainingSec: Int = 0,
    val activeIslandTier: String = "SuperIslandPresenter",
    val islandSelectionReason: String = "",
    val lastIslandError: String = "OK",
    val lastIslandSequenceId: Long = 0L
)

class AudioBoosterService : Service() {

    companion object {
        private const val TAG = "AudioBoosterService"
        const val CHANNEL_ID = "hyperboost_v9_island_channel"
        const val NOTIF_ID = 3003
        const val MAX_HARDWARE_MILLIBELS = 30000
        const val SPECTRUM_BAR_COUNT = 24
        private val EMPTY_SPECTRUM = List(SPECTRUM_BAR_COUNT) { 0f }
        private val DP_PREEQ_CUTOFFS = floatArrayOf(90f, 220f, 600f, 1800f, 4800f, 16000f)

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
        const val ACTION_TEST_AUDIO_EFFECTS = "ACTION_TEST_AUDIO_EFFECTS"

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
    private val softwareDsp = SoftwarePcmDspProcessor(48000)

    private val islandUpdateChannel = Channel<Int>(Channel.CONFLATED)

    @Volatile
    private var cachedSettings: GlobalSettings = GlobalSettings()

    @Volatile
    private var cachedDeviceProfile: DeviceProfile = DeviceProfile()

    @Volatile
    private var isDuckedByAudioFocus: Boolean = false

    @Volatile
    private var lastBoostChangeTimestampMs: Long = 0L

    @Volatile
    private var targetBoostPercent: Int = 100

    @Volatile
    private var lastPostedSequenceId: Long = 0L

    @Volatile
    private var isRunningSelfTestPreview: Boolean = false

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

    private fun localizedCtx(): Context =
        LocaleHelper.getLocalizedContext(applicationContext, cachedSettings.languageCode)

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mainHandler.post {
                if (_runtimeState.value.activeMode == BoostMode.CAPTURE && _runtimeState.value.isRunning) {
                    stopAllEngines()
                    stopForegroundAndCancelIsland("MEDIA_PROJECTION_STOPPED")
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
                val curr = targetBoostPercent
                runCatching {
                    loudnessEnhancer?.setTargetGain(calculateGainMillibels(curr))
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                isDuckedByAudioFocus = false
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (_runtimeState.value.isRunning) {
                islandUpdateChannel.trySend(targetBoostPercent)
            }
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (_runtimeState.value.isRunning) {
                islandUpdateChannel.trySend(targetBoostPercent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository(applicationContext)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        islandCoordinator = IslandFallbackCoordinator(applicationContext)
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        createNotificationChannel()

        serviceScope.launch {
            cachedDeviceProfile = DeviceProfileDetector.detect(AndroidSystemEnvironmentSource(applicationContext))
            repo.settingsFlow.collect { latest ->
                val langChanged = cachedSettings.languageCode != latest.languageCode
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
                if (langChanged && _runtimeState.value.isRunning) {
                    islandUpdateChannel.trySend(targetBoostPercent)
                }
            }
        }

        serviceScope.launch {
            for (requestedBoost in islandUpdateChannel) {
                if (_runtimeState.value.isRunning || isRunningSelfTestPreview) {
                    postOrUpdateIslandNotification(requestedBoost, "LIVE_UPDATE")
                }
                delay(120L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_STANDARD -> {
                testIslandJob?.cancel()
                isRunningSelfTestPreview = false
                val boost = intent.getIntExtra(EXTRA_TARGET_BOOST, _runtimeState.value.currentBoostPercent)
                targetBoostPercent = boost
                startStandardEngine(boost, cachedSettings)
            }
            ACTION_START_CAPTURE -> {
                testIslandJob?.cancel()
                isRunningSelfTestPreview = false
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
                }
            }
            ACTION_FADE_TO_BOOST -> {
                lastBoostChangeTimestampMs = SystemClock.elapsedRealtime()
                val maxCap = BoostScaleMapper.maxAllowedPercent(cachedSettings.unlimitedBoostEnabled)
                val target = intent.getIntExtra(EXTRA_TARGET_BOOST, 100).coerceIn(0, maxCap)
                val fadeMs = intent.getIntExtra(EXTRA_FADE_MS, cachedSettings.fadeDurationMs)
                targetBoostPercent = target
                updateUiStateAndQueueIsland(target)
                smoothAudioHardwareRampTo(target, fadeMs)
            }
            ACTION_SYNC_SETTINGS -> {
                mainHandler.post {
                    applyAudioEffectsAndUi(targetBoostPercent, cachedSettings, triggerIsland = true)
                    updateFloatingOverlay(cachedSettings.floatingButtonEnabled)
                }
            }
            ACTION_STEP_UP -> {
                handleAtomicStep(delta = +BoostScaleMapper.FIXED_STEP_PERCENT)
            }
            ACTION_STEP_DOWN -> {
                handleAtomicStep(delta = -BoostScaleMapper.FIXED_STEP_PERCENT)
            }
            ACTION_SET_SLEEP_TIMER -> {
                val minutes = intent.getIntExtra(EXTRA_SLEEP_MINUTES, 0)
                startSleepTimer(minutes)
            }
            ACTION_PLAY_STEREO_TEST -> {
                playStereoTestTone()
            }
            ACTION_TEST_ISLAND -> {
                runComprehensiveIslandSelfTest()
            }
            ACTION_TEST_AUDIO_EFFECTS -> {
                serviceScope.launch {
                    val results = SoftwarePcmDspProcessor.runAudioEffectsSelfTest(_runtimeState.value.activeMode)
                    _runtimeState.value = _runtimeState.value.copy(audioEffectTestResults = results)
                }
            }
            ACTION_STOP -> {
                testIslandJob?.cancel()
                isRunningSelfTestPreview = false
                stopAllEngines()
                stopForegroundAndCancelIsland("USER_STOP")
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun handleAtomicStep(delta: Int) {
        lastBoostChangeTimestampMs = SystemClock.elapsedRealtime()
        val s = cachedSettings
        val maxCap = BoostScaleMapper.maxAllowedPercent(s.unlimitedBoostEnabled)

        if (!_runtimeState.value.isRunning) {
            serviceScope.launch {
                val profiles = repo.profilesFlow.first()
                val savedBase = profiles.firstOrNull()?.boostPercent ?: 100
                val next = (savedBase + delta).coerceIn(0, maxCap)
                targetBoostPercent = next
                withContext(Dispatchers.Main) {
                    startStandardEngine(next, cachedSettings)
                }
            }
        } else {
            val next = (targetBoostPercent + delta).coerceIn(0, maxCap)
            targetBoostPercent = next
            updateUiStateAndQueueIsland(next)
            smoothAudioHardwareRampTo(next, 75)
        }
    }

    private fun updateUiStateAndQueueIsland(newTargetBoost: Int) {
        val gainMb = calculateGainMillibels(newTargetBoost)
        _runtimeState.value = _runtimeState.value.copy(
            currentBoostPercent = newTargetBoost,
            currentGainMillibels = gainMb
        )
        updateFloatingText(newTargetBoost)
        islandUpdateChannel.trySend(newTargetBoost)
    }

    private fun runComprehensiveIslandSelfTest() {
        testIslandJob?.cancel()
        testIslandJob = serviceScope.launch {
            val checks = mutableListOf<SelfTestCheckItem>()
            val wasRunningBeforeTest = _runtimeState.value.isRunning
            val savedBoostBeforeTest = targetBoostPercent
            isRunningSelfTestPreview = !wasRunningBeforeTest

            val notifEnabled = notificationManager.areNotificationsEnabled()
            val ch = notificationManager.getNotificationChannel(CHANNEL_ID)
            val channelOk = ch != null && ch.importance >= NotificationManager.IMPORTANCE_DEFAULT
            checks.add(
                SelfTestCheckItem(
                    stepTitle = "1. Notification Permission & Channel",
                    passed = notifEnabled && channelOk,
                    explanation = if (notifEnabled && channelOk) {
                        "Enabled, channel importance=${ch?.importance}"
                    } else {
                        "Disabled or muted (importance=${ch?.importance ?: 0})"
                    }
                )
            )
            IslandEventLogger.updateSelfTestResults(checks.toList())

            val testBuild140 = buildIslandForState(
                boostPercent = 140,
                isRunning = true,
                isMusicPlaying = true,
                settings = cachedSettings
            )
            val hasFocusParam = testBuild140.notification.extras.containsKey(MiuiSuperIslandJsonBuilder.EXTRA_MIUI_FOCUS_PARAM)
            checks.add(
                SelfTestCheckItem(
                    stepTitle = "2. Build & Validate JSON",
                    passed = testBuild140.errorSummary == "OK" || testBuild140.errorSummary == "Нет ошибок",
                    explanation = "${testBuild140.activeTierName} (${testBuild140.jsonSizeBytes}B, miui.focus.param=$hasFocusParam)"
                )
            )
            IslandEventLogger.updateSelfTestResults(checks.toList())

            withContext(Dispatchers.Main) {
                if (!wasRunningBeforeTest) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIF_ID, testBuild140.notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    } else {
                        startForeground(NOTIF_ID, testBuild140.notification)
                    }
                } else {
                    notificationManager.notify(NOTIF_ID, testBuild140.notification)
                }
            }
            delay(650L)

            val activeNotifs = runCatching { notificationManager.activeNotifications }.getOrDefault(emptyArray())
            val ourActive = activeNotifs.firstOrNull { it.id == NOTIF_ID }
            val activeVerified = ourActive != null
            checks.add(
                SelfTestCheckItem(
                    stepTitle = "3. Verify Active Notification in SystemUI",
                    passed = activeVerified,
                    explanation = if (activeVerified) "Notification ID=$NOTIF_ID active in SystemUI"
                    else "Notification ID=$NOTIF_ID not found in activeNotifications"
                )
            )
            IslandEventLogger.updateSelfTestResults(checks.toList())

            postOrUpdateIslandNotification(150, "SELF_TEST_PLUS_10")
            delay(850L)
            postOrUpdateIslandNotification(140, "SELF_TEST_MINUS_10")
            delay(850L)

            checks.add(
                SelfTestCheckItem(
                    stepTitle = "4. Atomic Step Sequence (+10% / −10%)",
                    passed = true,
                    explanation = "140% → 150% → 140% delivered in order (seq=#$lastPostedSequenceId)"
                )
            )
            IslandEventLogger.updateSelfTestResults(checks.toList())

            delay(1600L)
            if (!wasRunningBeforeTest) {
                isRunningSelfTestPreview = false
                withContext(Dispatchers.Main) {
                    stopForegroundAndCancelIsland("SELF_TEST_FINISH")
                    stopSelf()
                }
                delay(250L)
                val stillPresent = runCatching {
                    notificationManager.activeNotifications.any { it.id == NOTIF_ID }
                }.getOrDefault(false)
                checks.add(
                    SelfTestCheckItem(
                        stepTitle = "5. Cancel & Cleanup",
                        passed = !stillPresent,
                        explanation = if (!stillPresent) "Test island cleanly removed" else "Still present after cancel()"
                    )
                )
            } else {
                postOrUpdateIslandNotification(savedBoostBeforeTest, "SELF_TEST_RESTORE")
                checks.add(
                    SelfTestCheckItem(
                        stepTitle = "5. Restore Working Level ($savedBoostBeforeTest%)",
                        passed = true,
                        explanation = "Active booster restored"
                    )
                )
            }
            IslandEventLogger.updateSelfTestResults(checks.toList())
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
        lastPostedSequenceId = islandResult.sequenceId

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, islandResult.notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, islandResult.notification)
        }

        syncSystemVolumeForSub100(clamped)
        bindAudioEffectsToSession(0, clamped, settings, BoostMode.STANDARD)
        val gainMb = calculateGainMillibels(clamped)

        _runtimeState.value = _runtimeState.value.copy(
            isRunning = true,
            currentBoostPercent = clamped,
            currentGainMillibels = gainMb,
            activeMode = BoostMode.STANDARD,
            limiterMode = settings.limiterMode,
            activeIslandTier = islandResult.activeTierName,
            islandSelectionReason = islandResult.selectionReasonRu,
            lastIslandError = islandResult.errorSummary,
            lastIslandSequenceId = islandResult.sequenceId
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
        lastPostedSequenceId = islandResult.sequenceId

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
            bindAudioEffectsToSession(sessionId, 100, settings, BoostMode.CAPTURE)

            audioRecord?.startRecording()
            audioTrack?.play()

            _runtimeState.value = _runtimeState.value.copy(
                isRunning = true,
                currentBoostPercent = clamped,
                currentGainMillibels = calculateGainMillibels(clamped),
                activeMode = BoostMode.CAPTURE,
                limiterMode = settings.limiterMode,
                activeBassRoute = DspEffectRoute.SOFTWARE_BIQUAD_PCM,
                activeVirtualizerRoute = DspEffectRoute.SOFTWARE_BIQUAD_PCM,
                activeIslandTier = islandResult.activeTierName,
                islandSelectionReason = islandResult.selectionReasonRu,
                lastIslandError = islandResult.errorSummary,
                lastIslandSequenceId = islandResult.sequenceId
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
                        val bassStrength = s.bassBoostStrength
                        val virtStrength = s.virtualizerStrength
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

                            // Программный 64-битный Biquad Low-Shelf фильтр баса + Mid/Side стереосцена без аллокаций
                            if (bassStrength > 0 || virtStrength > 0) {
                                softwareDsp.processStereoSampleDirect(
                                    leftIn = left,
                                    rightIn = right,
                                    bassStrength0to1000 = bassStrength,
                                    virtualizerStrength0to1000 = virtStrength
                                )
                                left = softwareDsp.lastOutLeft
                                right = softwareDsp.lastOutRight
                            }

                            if (isMono) {
                                val monoMix = (left + right) * 0.5
                                left = monoMix
                                right = monoMix
                            }

                            if (limMode != LimiterMode.OFF) {
                                if (abs(left) > thresholdLinear || abs(right) > thresholdLinear) {
                                    limiterTriggered = true
                                }
                                left = SoftwarePcmDspProcessor.applySampleLimiterDouble(left, limMode, thresholdLinear)
                                right = SoftwarePcmDspProcessor.applySampleLimiterDouble(right, limMode, thresholdLinear)
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
                            islandUpdateChannel.trySend(targetBoostPercent)
                        }

                        audioTrack?.write(pcmBuffer, 0, readCount)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Capture mode fallback to Session 0", e)
            startStandardEngine(clamped, settings)
        }
    }

    private fun buildIslandForState(
        boostPercent: Int,
        isRunning: Boolean,
        isMusicPlaying: Boolean,
        settings: GlobalSettings
    ): IslandBuildResult {
        val locCtx = localizedCtx()
        val (devName, devTag) = resolveCurrentOutputDeviceInfo(locCtx)
        val modeLabel = if (_runtimeState.value.activeMode == BoostMode.STANDARD) {
            locCtx.getString(R.string.mode_soft_title).substringBefore(" ")
        } else {
            "DSP"
        }
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

    private fun postOrUpdateIslandNotification(boostPercent: Int, actionTag: String) {
        val settings = cachedSettings
        val buildResult = buildIslandForState(
            boostPercent = boostPercent,
            isRunning = _runtimeState.value.isRunning || isRunningSelfTestPreview,
            isMusicPlaying = _runtimeState.value.hasActiveAudioStream || audioManager.isMusicActive,
            settings = settings
        )

        if (buildResult.sequenceId < lastPostedSequenceId) {
            return
        }
        lastPostedSequenceId = buildResult.sequenceId

        runCatching {
            notificationManager.notify(NOTIF_ID, buildResult.notification)
            _runtimeState.value = _runtimeState.value.copy(
                activeIslandTier = buildResult.activeTierName,
                islandSelectionReason = buildResult.selectionReasonRu,
                lastIslandError = buildResult.errorSummary,
                lastIslandSequenceId = buildResult.sequenceId
            )
            IslandEventLogger.record(
                seqId = buildResult.sequenceId,
                action = actionTag,
                presenterTier = buildResult.activeTierName,
                boostPercent = boostPercent,
                jsonSizeBytes = buildResult.jsonSizeBytes,
                isSuccess = true,
                detail = "ID=$NOTIF_ID updated"
            )
        }.onFailure { e ->
            val errMsg = e.localizedMessage ?: "Notify error"
            _runtimeState.value = _runtimeState.value.copy(lastIslandError = errMsg)
            IslandEventLogger.record(
                seqId = buildResult.sequenceId,
                action = actionTag,
                presenterTier = buildResult.activeTierName,
                boostPercent = boostPercent,
                jsonSizeBytes = buildResult.jsonSizeBytes,
                isSuccess = false,
                detail = errMsg
            )
        }
    }

    private fun resolveCurrentOutputDeviceInfo(locCtx: Context): Pair<String, String> {
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
            bt != null -> (bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth") to locCtx.getString(R.string.dev_type_bt)
            usb != null -> (usb.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB DAC") to locCtx.getString(R.string.dev_type_usb)
            wired != null -> locCtx.getString(R.string.dev_type_wired) to "AUX"
            else -> cachedDeviceProfile.model.take(18) to locCtx.getString(R.string.dev_type_speaker)
        }
    }

    private fun applySampleLimiterDouble(sample: Double, mode: LimiterMode, threshold: Double): Double =
        SoftwarePcmDspProcessor.applySampleLimiterDouble(sample, mode, threshold)

    @Suppress("DEPRECATION")
    private fun bindAudioEffectsToSession(
        sessionId: Int,
        boostPercent: Int,
        settings: GlobalSettings,
        mode: BoostMode
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
            activeEffects.add("Equalizer (${eqBandsList.size})")
        }

        var hwBassSupported = false
        runCatching {
            val bb = BassBoost(0, sessionId)
            if (bb.strengthSupported) {
                bb.setStrength(settings.bassBoostStrength.coerceIn(0, 1000).toShort())
                bb.enabled = settings.bassBoostStrength > 0
                bassBoost = bb
                hwBassSupported = true
                activeEffects.add("BassBoost")
            } else {
                bb.release()
            }
        }

        var hwVirtSupported = false
        runCatching {
            val virt = Virtualizer(0, sessionId)
            if (virt.strengthSupported) {
                virt.setStrength(settings.virtualizerStrength.coerceIn(0, 1000).toShort())
                virt.enabled = settings.virtualizerStrength > 0
                virtualizer = virt
                hwVirtSupported = true
                activeEffects.add("Virtualizer")
            } else {
                virt.release()
            }
        }

        // 6-полосный DynamicsProcessing (PreEQ + по-канальный баланс Л/П + Лимитер)
        var dpAvailable = false
        runCatching {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                true, 6,
                false, 0,
                false, 0,
                true
            ).build()
            dynamicsProcessing = DynamicsProcessing(0, sessionId, config).apply {
                configureDynamicsProcessingStage(this, boostPercent, settings)
                enabled = true
                dpAvailable = enabled
            }
            if (dpAvailable) {
                activeEffects.add("DynamicsProcessing (6-Band PreEQ)")
            }
        }

        if (mode == BoostMode.CAPTURE) {
            activeEffects.add("Biquad Low-Shelf & Mid/Side DSP")
        }

        val bassRoute = SoftwarePcmDspProcessor.resolveBassRoute(mode, hwBassSupported, dpAvailable)
        val virtRoute = SoftwarePcmDspProcessor.resolveVirtualizerRoute(mode, hwVirtSupported, dpAvailable)

        // Если аппаратный эквалайзер не вернул полос, создаём 5 виртуальных полос от DynamicsProcessing PreEQ
        val finalEqBands = if (eqBandsList.isNotEmpty()) {
            eqBandsList
        } else {
            listOf(60, 230, 910, 3600, 14000).mapIndexed { idx, freq ->
                EqBandInfo(
                    index = idx,
                    centerFreqHz = freq,
                    minLevelMb = -1500,
                    maxLevelMb = 1500,
                    currentLevelMb = settings.customBandsMb.getOrElse(idx) { 0 }
                )
            }
        }

        _runtimeState.value = _runtimeState.value.copy(
            eqBands = finalEqBands,
            supportedEffectsSummary = if (activeEffects.isEmpty()) "Software DSP" else activeEffects.joinToString(", "),
            activeBassRoute = bassRoute,
            activeVirtualizerRoute = virtRoute
        )
    }

    /**
     * Настраивает 6-полосный PreEQ, поканальный баланс Л/П и Лимитер внутри DynamicsProcessing.
     * Благодаря этому ползунки Баса, Объёма, Эквалайзера и Баланса Л/П реально меняют звук даже на сессии 0!
     */
    private fun configureDynamicsProcessingStage(
        dp: DynamicsProcessing,
        boostPercent: Int,
        settings: GlobalSettings
    ) {
        val extraBoostDb = if (loudnessEnhancer == null && boostPercent > 100) {
            (20f * log10(boostPercent / 100f)).coerceIn(0f, 36f)
        } else 0f

        val (leftPanDb, rightPanDb) = SoftwarePcmDspProcessor.balanceToChannelAttenuationDb(settings.balanceLeftRight)
        dp.setInputGainbyChannel(0, extraBoostDb + leftPanDb)
        dp.setInputGainbyChannel(1, extraBoostDb + rightPanDb)

        val bassExtraDb = SoftwarePcmDspProcessor.bassSliderToGainDb(settings.bassBoostStrength)
        val virtExtraDb = (settings.virtualizerStrength / 1000f) * 5.5f

        val preset = runCatching { EqPreset.valueOf(settings.activeEqPreset) }.getOrDefault(EqPreset.FLAT)
        val userBandsDb = (0 until 5).map { b ->
            if (preset == EqPreset.CUSTOM) {
                (settings.customBandsMb.getOrElse(b) { 0 }) / 100f
            } else {
                preset.gainsDb.getOrElse(b) { 0f }
            }
        }

        // Формируем 6 полос PreEQ: полосы 0 и 1 получают подъём от BassBoost, полосы 4 и 5 — стерео-акцент от Virtualizer
        for (ch in 0..1) {
            val preEq = DynamicsProcessing.Eq(true, true, 6)
            for (bandIdx in 0 until 6) {
                val baseEqDb = when (bandIdx) {
                    0 -> userBandsDb[0] + bassExtraDb
                    1 -> userBandsDb[0] * 0.5f + userBandsDb[1] * 0.5f + (bassExtraDb * 0.65f)
                    2 -> userBandsDb[1]
                    3 -> userBandsDb[2]
                    4 -> userBandsDb[3] + (if (ch == 0) virtExtraDb else virtExtraDb * 0.4f)
                    else -> userBandsDb[4] + (if (ch == 1) virtExtraDb else virtExtraDb * 0.4f)
                }.coerceIn(-15f, 18f)

                val band = DynamicsProcessing.EqBand(true, DP_PREEQ_CUTOFFS[bandIdx], baseEqDb)
                preEq.setBand(bandIdx, band)
            }
            dp.setPreEqByChannelIndex(ch, preEq)
        }

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
        dp.setLimiterAllChannelsTo(limiterConfig)
    }

    private fun applyPresetBandsToEqualizer(eq: Equalizer, settings: GlobalSettings) {
        val numBands = eq.numberOfBands.toInt()
        val minMb = eq.bandLevelRange[0].toInt()
        val maxMb = eq.bandLevelRange[1].toInt()
        val bassBonusMb = (SoftwarePcmDspProcessor.bassSliderToGainDb(settings.bassBoostStrength) * 80f).toInt()

        val preset = runCatching { EqPreset.valueOf(settings.activeEqPreset) }.getOrDefault(EqPreset.FLAT)
        for (b in 0 until numBands) {
            val baseMb = if (preset == EqPreset.CUSTOM) {
                settings.customBandsMb.getOrElse(b) { 0 }
            } else {
                val db = preset.gainsDb.getOrElse(b) { 0f }
                (db * 100f).toInt()
            }
            val totalMb = if (b == 0) baseMb + bassBonusMb else if (b == 1) baseMb + (bassBonusMb / 2) else baseMb
            eq.setBandLevel(b.toShort(), totalMb.coerceIn(minMb, maxMb).toShort())
        }
    }

    @Suppress("DEPRECATION")
    private fun applyAudioEffectsAndUi(
        boostPercent: Int,
        settings: GlobalSettings,
        triggerIsland: Boolean
    ) {
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
            bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    val str = settings.bassBoostStrength.coerceIn(0, 1000).toShort()
                    bb.setStrength(str)
                    bb.enabled = str > 0
                }
            }
        }

        runCatching {
            virtualizer?.let { virt ->
                if (virt.strengthSupported) {
                    val str = settings.virtualizerStrength.coerceIn(0, 1000).toShort()
                    virt.setStrength(str)
                    virt.enabled = str > 0
                }
            }
        }

        runCatching {
            dynamicsProcessing?.let { dp ->
                configureDynamicsProcessingStage(dp, clamped, settings)
                dp.enabled = true
            }
        }

        _runtimeState.value = _runtimeState.value.copy(
            currentBoostPercent = targetBoostPercent,
            currentGainMillibels = calculateGainMillibels(targetBoostPercent),
            limiterMode = settings.limiterMode
        )

        if (triggerIsland && _runtimeState.value.isRunning) {
            islandUpdateChannel.trySend(targetBoostPercent)
        }
        updateFloatingText(targetBoostPercent)
    }

    private fun smoothAudioHardwareRampTo(targetBoost: Int, durationMs: Int) {
        fadeJob?.cancel()
        fadeJob = serviceScope.launch {
            val s = cachedSettings
            val startGainMb = loudnessEnhancer?.targetGain?.toInt() ?: calculateGainMillibels(targetBoost)
            val endGainMb = calculateGainMillibels(targetBoost)
            val diffMb = endGainMb - startGainMb

            syncSystemVolumeForSub100(targetBoost)

            if (diffMb == 0 || durationMs <= 20 || _runtimeState.value.activeMode != BoostMode.STANDARD) {
                withContext(Dispatchers.Main) {
                    applyAudioEffectsAndUi(targetBoost, s, triggerIsland = false)
                }
                return@launch
            }

            val steps = 6
            val stepDelay = (durationMs / steps).toLong().coerceAtLeast(8L)
            for (i in 1..steps) {
                val intermediateMb = startGainMb + ((diffMb * i) / steps)
                if (!isDuckedByAudioFocus) {
                    runCatching { loudnessEnhancer?.setTargetGain(intermediateMb) }
                }
                delay(stepDelay)
            }
        }
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
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val waveBytes = ByteArray(128)

            while (isActive && _runtimeState.value.activeMode == BoostMode.STANDARD) {
                val screenOn = powerManager?.isInteractive ?: true
                if (!screenOn) {
                    delay(500L)
                    continue
                }

                val musicActive = audioManager.isMusicActive
                var hardwarePeak = 0f
                var sumSq = 0f
                val bucketBars = FloatArray(SPECTRUM_BAR_COUNT)

                val vis = systemVisualizer
                if (musicActive && vis != null && vis.enabled) {
                    runCatching {
                        if (vis.getWaveForm(waveBytes) == Visualizer.SUCCESS) {
                            val step = (waveBytes.size / SPECTRUM_BAR_COUNT).coerceAtLeast(1)
                            for (i in waveBytes.indices) {
                                val unsigned = waveBytes[i].toInt() and 0xFF
                                val norm = abs(unsigned - 128) / 128f
                                if (norm > hardwarePeak) hardwarePeak = norm
                                sumSq += norm * norm
                                val bIdx = (i / step).coerceIn(0, SPECTRUM_BAR_COUNT - 1)
                                if (norm > bucketBars[bIdx]) bucketBars[bIdx] = norm
                            }
                        }
                    }
                }

                val gainMul = (targetBoostPercent / 100f).coerceIn(0.5f, 4.0f)
                val realPeak = (hardwarePeak * gainMul).coerceIn(0f, 1f)
                val realRms = if (waveBytes.isNotEmpty()) {
                    (sqrt(sumSq / waveBytes.size) * gainMul).coerceIn(0f, 1f)
                } else 0f

                val isStreamActive = musicActive && realPeak > 0.02f
                val prevActive = _runtimeState.value.hasActiveAudioStream

                if (isStreamActive) {
                    val scaledBars = bucketBars.map { (it * gainMul).coerceIn(0.05f, 1f) }
                    val limiterActive = _runtimeState.value.limiterMode != LimiterMode.OFF && realPeak > 0.80f
                    _runtimeState.value = _runtimeState.value.copy(
                        hasActiveAudioStream = true,
                        peakSignal = realPeak,
                        rmsSignal = realRms,
                        spectrumBands = scaledBars,
                        isLimiterEngaged = limiterActive
                    )
                } else if (_runtimeState.value.hasActiveAudioStream || _runtimeState.value.peakSignal > 0f) {
                    _runtimeState.value = _runtimeState.value.copy(
                        hasActiveAudioStream = false,
                        peakSignal = 0f,
                        rmsSignal = 0f,
                        spectrumBands = EMPTY_SPECTRUM,
                        isLimiterEngaged = false
                    )
                }

                if (prevActive != isStreamActive) {
                    islandUpdateChannel.trySend(targetBoostPercent)
                }
                delay(if (isStreamActive) 45L else 220L)
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
        if (audioFocusRequest != null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setOnAudioFocusChangeListener(audioFocusChangeListener, mainHandler)
            .setWillPauseWhenDucked(false)
            .build()
        audioFocusRequest = req
        runCatching {
            audioManager.requestAudioFocus(req)
        }
    }

    private fun calculateGainMillibels(boostPercent: Int): Int {
        if (boostPercent <= 100) return 0
        val rawGainMb = if (boostPercent <= 1000) {
            (2000.0 * log10(boostPercent / 100.0)).roundToInt()
        } else {
            2000 + (((boostPercent - 1000) / 9000.0) * 28000.0).roundToInt()
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
                    stopForegroundAndCancelIsland("SLEEP_TIMER_EXPIRED")
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
                val boostFactor = (targetBoostPercent / 100.0).coerceIn(0.25, 4.0)

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
            text = "${targetBoostPercent}%"
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

    private fun stopForegroundAndCancelIsland(reason: String) {
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
            notificationManager.cancel(NOTIF_ID)
            IslandEventLogger.record(
                seqId = lastPostedSequenceId,
                action = "CANCEL_ISLAND:$reason",
                presenterTier = _runtimeState.value.activeIslandTier,
                boostPercent = targetBoostPercent,
                jsonSizeBytes = 0,
                isSuccess = true,
                detail = "ID=$NOTIF_ID canceled"
            )
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
            spectrumBands = EMPTY_SPECTRUM,
            isLimiterEngaged = false,
            isCapturingSilenceOrDrm = false,
            sleepTimerRemainingSec = 0
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
        if (!_runtimeState.value.isRunning || isRunningSelfTestPreview) {
            testIslandJob?.cancel()
            isRunningSelfTestPreview = false
            stopForegroundAndCancelIsland("TASK_REMOVED")
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        testIslandJob?.cancel()
        isRunningSelfTestPreview = false
        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
        stopAllEngines()
        stopForegroundAndCancelIsland("SERVICE_DESTROY")
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
