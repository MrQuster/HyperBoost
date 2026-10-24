package com.example.volumeboost.island

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.example.volumeboost.MainActivity
import com.example.volumeboost.R
import com.example.volumeboost.util.LocaleHelper
import com.example.volumeboost.data.*
import com.example.volumeboost.device.DeviceProfile
import com.example.volumeboost.device.FeatureRegistry
import com.example.volumeboost.device.IslandStrategy
import com.example.volumeboost.device.TriStateStatus
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

interface IslandPresenter {
    val presenterName: String
    fun isSupported(context: Context): Boolean
    fun buildNotification(
        context: Context,
        channelId: String,
        snapshot: IslandSnapshot,
        intents: IslandPendingIntents
    ): Pair<Notification, Int>
}

object HyperOsEnvironmentDetector {

    fun isXiaomiOrHyperOsDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val isXiaomiBrand = manufacturer.contains("xiaomi") ||
                brand.contains("xiaomi") ||
                brand.contains("redmi") ||
                brand.contains("poco")
        return isXiaomiBrand && getHyperOsVersionString().isNotBlank()
    }

    fun getHyperOsVersionString(): String {
        val osName = readSystemProperty("ro.mi.os.version.name")
        val osCode = readSystemProperty("ro.mi.os.version.code")
        val miuiVer = readSystemProperty("ro.miui.ui.version.name")
        return when {
            osName.isNotBlank() -> "HyperOS $osName (code $osCode)"
            miuiVer.isNotBlank() -> "MIUI / HyperOS ($miuiVer)"
            else -> ""
        }
    }

    /**
     * Честная трёхзначная проверка разрешения фокусных уведомлений:
     * SUPPORTED (если провайдер явно вернул true или persist.sys.feature.island=true),
     * UNSUPPORTED (если уведомления выключены или провайдер вернул false),
     * UNKNOWN (если провайдер закрыт — требуется реальная пробная отправка).
     */
    fun queryFocusPermissionStatus(context: Context): TriStateStatus {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.areNotificationsEnabled()) return TriStateStatus.UNSUPPORTED

        val uri = Uri.parse("content://miui.statusbar.notification.public")
        val extras = Bundle().apply { putString("package", context.packageName) }
        val providerAnswer = runCatching {
            context.contentResolver.call(uri, "canShowFocus", context.packageName, extras)
        }.getOrNull()

        if (providerAnswer != null && providerAnswer.containsKey("canShowFocus")) {
            return if (providerAnswer.getBoolean("canShowFocus")) {
                TriStateStatus.SUPPORTED
            } else {
                TriStateStatus.UNSUPPORTED
            }
        }

        val islandProp = readSystemProperty("persist.sys.feature.island")
        if (islandProp.equals("true", ignoreCase = true) || islandProp == "1") {
            return TriStateStatus.SUPPORTED
        }
        return TriStateStatus.UNKNOWN
    }

    fun canAttemptFocusNotification(context: Context): Boolean {
        return queryFocusPermissionStatus(context) != TriStateStatus.UNSUPPORTED
    }

    fun canPostPromotedLiveUpdates(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return runCatching {
            val method = nm.javaClass.getMethod("canPostPromotedNotifications")
            method.invoke(nm) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun readSystemProperty(key: String): String {
        return runCatching {
            val sp = Class.forName("android.os.SystemProperties")
            val getMethod = sp.getMethod("get", String::class.java, String::class.java)
            (getMethod.invoke(null, key, "") as? String)?.trim().orEmpty()
        }.getOrDefault("")
    }
}

class SuperIslandPresenter : IslandPresenter {
    override val presenterName: String = "SuperIslandPresenter (Xiaomi HyperIsland)"

    override fun isSupported(context: Context): Boolean {
        return HyperOsEnvironmentDetector.isXiaomiOrHyperOsDevice() &&
                HyperOsEnvironmentDetector.canAttemptFocusNotification(context)
    }

    override fun buildNotification(
        context: Context,
        channelId: String,
        snapshot: IslandSnapshot,
        intents: IslandPendingIntents
    ): Pair<Notification, Int> {
        val fraction = BoostScaleMapper.percentToFraction(snapshot.boostPercent, snapshot.unlimitedMode)
        val progress100 = (fraction * 100f).roundToInt().coerceIn(0, 100)
        val parsedColor = runCatching { Color.parseColor(snapshot.accentHex) }.getOrDefault(Color.parseColor("#00F5D4"))

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_boost)
            .setColor(parsedColor)
            .setColorized(true)
            .setContentTitle("HyperBoost • ${snapshot.boostPercent}%")
            .setContentText("${snapshot.deviceName} (${snapshot.deviceTypeTag})")
            .setSubText("${snapshot.boostPercent}%")
            .setContentIntent(intents.openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(0L)
            .setShowWhen(false)
            .setVisibility(
                if (snapshot.allowLockscreenActions) NotificationCompat.VISIBILITY_PUBLIC
                else NotificationCompat.VISIBILITY_SECRET
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setProgress(100, progress100, false)

        attachTwoStepAndToggleButtons(context, builder, snapshot, intents)

        val extras = Bundle()
        val validated = MiuiSuperIslandJsonBuilder.attachMiuiFocusBundles(context, extras, snapshot, intents)
        builder.addExtras(extras)

        return builder.build() to validated.sizeBytes
    }
}

class LiveUpdatePresenter : IslandPresenter {
    override val presenterName: String = "LiveUpdatePresenter (Android 16+ API 36+)"

    override fun isSupported(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= 36 && HyperOsEnvironmentDetector.canPostPromotedLiveUpdates(context)
    }

    override fun buildNotification(
        context: Context,
        channelId: String,
        snapshot: IslandSnapshot,
        intents: IslandPendingIntents
    ): Pair<Notification, Int> {
        val fraction = BoostScaleMapper.percentToFraction(snapshot.boostPercent, snapshot.unlimitedMode)
        val progress100 = (fraction * 100f).roundToInt().coerceIn(0, 100)
        val shortChipText = if (snapshot.styleMode == IslandStyleMode.ICON_ONLY) "" else "${snapshot.boostPercent}%"
        val parsedColor = runCatching { Color.parseColor(snapshot.accentHex) }.getOrDefault(Color.parseColor("#00F5D4"))

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_boost)
            .setColor(parsedColor)
            .setContentTitle("HyperBoost • ${snapshot.boostPercent}%")
            .setContentText("${snapshot.deviceName} (${snapshot.deviceTypeTag})")
            .setSubText(shortChipText)
            .setContentIntent(intents.openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(0L)
            .setShowWhen(false)
            .setVisibility(
                if (snapshot.allowLockscreenActions) NotificationCompat.VISIBILITY_PUBLIC
                else NotificationCompat.VISIBILITY_SECRET
            )
            .setProgress(100, progress100, false)

        val liveExtras = Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
            if (shortChipText.isNotEmpty()) {
                putString("android.shortCriticalText", shortChipText)
            }
            putInt("android.ongoingActivityNoti.style", 1)
            putString("android.ongoingActivityNoti.primaryInfo", "HyperBoost ${snapshot.boostPercent}%")
        }
        builder.addExtras(liveExtras)
        attachTwoStepAndToggleButtons(context, builder, snapshot, intents)

        return builder.build() to 128
    }
}

class ClassicNotificationPresenter : IslandPresenter {
    override val presenterName: String = "ClassicNotificationPresenter"

    override fun isSupported(context: Context): Boolean = true

    override fun buildNotification(
        context: Context,
        channelId: String,
        snapshot: IslandSnapshot,
        intents: IslandPendingIntents
    ): Pair<Notification, Int> {
        val fraction = BoostScaleMapper.percentToFraction(snapshot.boostPercent, snapshot.unlimitedMode)
        val progress100 = (fraction * 100f).roundToInt().coerceIn(0, 100)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_boost)
            .setContentTitle("HyperBoost • ${snapshot.boostPercent}%")
            .setContentText("${snapshot.deviceName} (${snapshot.deviceTypeTag})")
            .setContentIntent(intents.openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(0L)
            .setShowWhen(false)
            .setVisibility(
                if (snapshot.allowLockscreenActions) NotificationCompat.VISIBILITY_PUBLIC
                else NotificationCompat.VISIBILITY_SECRET
            )
            .setProgress(100, progress100, false)

        attachTwoStepAndToggleButtons(context, builder, snapshot, intents)
        return builder.build() to 0
    }
}

private fun attachTwoStepAndToggleButtons(
    context: Context,
    builder: NotificationCompat.Builder,
    snapshot: IslandSnapshot,
    intents: IslandPendingIntents
) {
    val allowButtons = !snapshot.isDeviceLocked || snapshot.allowLockscreenActions
    if (!allowButtons) return

    builder.addAction(
        0,
        context.getString(R.string.island_action_minus_10),
        intents.stepMinus10
    )
    builder.addAction(
        0,
        context.getString(R.string.island_action_plus_10),
        intents.stepPlus10
    )
    builder.addAction(
        0,
        if (snapshot.isRunning) context.getString(R.string.island_action_stop)
        else context.getString(R.string.island_action_start),
        intents.togglePower
    )
}

data class IslandBuildResult(
    val sequenceId: Long,
    val notification: Notification,
    val activeTierName: String,
    val jsonSizeBytes: Int,
    val selectionReasonRu: String,
    val errorSummary: String
)

class IslandFallbackCoordinator(private val context: Context) {

    companion object {
        private val sequenceGenerator = AtomicLong(1L)
    }

    private val superIsland = SuperIslandPresenter()
    private val liveUpdate = LiveUpdatePresenter()
    private val classic = ClassicNotificationPresenter()

    fun buildBestNotification(
        channelId: String,
        deviceProfile: DeviceProfile,
        settings: GlobalSettings,
        isRunning: Boolean,
        isMusicPlaying: Boolean,
        highlightPulse: Boolean,
        boostPercent: Int,
        deviceName: String,
        deviceTypeTag: String,
        modeLabel: String
    ): IslandBuildResult {
        val seqId = sequenceGenerator.getAndIncrement()
        val locCtx = LocaleHelper.getLocalizedContext(context, settings.languageCode)
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguard?.isKeyguardLocked ?: false

        val accentHex = when (settings.islandColorPreset) {
            IslandColorPreset.DYNAMIC_BY_LEVEL -> BoostScaleMapper.dynamicIslandColorHexForBoost(boostPercent)
            IslandColorPreset.THEME_SYNC -> String.format("#%06X", (0xFFFFFFL and settings.accentColorHex))
            else -> settings.islandColorPreset.hexColor
        }

        val snapshot = IslandSnapshot(
            sequenceNumber = seqId,
            isRunning = isRunning,
            isMusicPlaying = isMusicPlaying,
            boostPercent = boostPercent,
            unlimitedMode = settings.unlimitedBoostEnabled,
            deviceName = deviceName,
            deviceTypeTag = deviceTypeTag,
            modeLabel = modeLabel,
            styleMode = if (!settings.islandEnabled) IslandStyleMode.DISABLED else settings.islandStyle,
            accentHex = accentHex,
            highlightPulse = highlightPulse,
            onlyWhenPlaying = settings.islandOnlyWhenPlaying,
            isDeviceLocked = isLocked,
            allowLockscreenActions = settings.islandAllowLockscreenActions
        )

        val intents = createPendingIntents(context)

        if (settings.islandOnlyWhenPlaying && !isMusicPlaying) {
            val (quietNotif, bytes) = classic.buildNotification(locCtx, channelId, snapshot, intents)
            return IslandBuildResult(
                sequenceId = seqId,
                notification = quietNotif,
                activeTierName = classic.presenterName,
                jsonSizeBytes = bytes,
                selectionReasonRu = "Остров свёрнут (музыка на паузе, включён режим «Только при воспроизведении»)",
                errorSummary = "Нет ошибок"
            )
        }

        if (!settings.islandEnabled || settings.islandStyle == IslandStyleMode.DISABLED) {
            val (notif, bytes) = classic.buildNotification(locCtx, channelId, snapshot, intents)
            return IslandBuildResult(
                sequenceId = seqId,
                notification = notif,
                activeTierName = classic.presenterName,
                jsonSizeBytes = bytes,
                selectionReasonRu = "Остров отключён пользователем в настройках",
                errorSummary = "Нет ошибок"
            )
        }

        val plan = FeatureRegistry.resolvePlan(
            profile = deviceProfile,
            islandPreference = settings.islandRoutingPreference,
            powerSaveGlass = settings.powerSaveGlass
        )

        val orderedChain: List<IslandPresenter> = when (plan.selectedIslandStrategy) {
            IslandStrategy.XIAOMI_SUPER_ISLAND -> listOf(superIsland, liveUpdate, classic)
            IslandStrategy.SAMSUNG_NOW_BAR_LIVE,
            IslandStrategy.ANDROID_16_LIVE_UPDATE -> listOf(liveUpdate, superIsland, classic)
            IslandStrategy.CLASSIC_MEDIA_NOTIFICATION -> listOf(classic)
        }

        var lastError = "Нет ошибок"
        for (presenter in orderedChain) {
            if (settings.islandRoutingPreference == IslandRoutingPreference.AUTO && !presenter.isSupported(context)) {
                continue
            }
            val result = runCatching {
                presenter.buildNotification(locCtx, channelId, snapshot, intents)
            }
            if (result.isSuccess) {
                val (notif, bytes) = result.getOrThrow()
                return IslandBuildResult(
                    sequenceId = seqId,
                    notification = notif,
                    activeTierName = presenter.presenterName,
                    jsonSizeBytes = bytes,
                    selectionReasonRu = plan.islandSelectionReasonRu,
                    errorSummary = lastError
                )
            } else {
                lastError = "${presenter.presenterName}: ${result.exceptionOrNull()?.localizedMessage ?: "сбой"}"
            }
        }

        val (fallbackNotif, bytes) = classic.buildNotification(locCtx, channelId, snapshot, intents)
        return IslandBuildResult(
            sequenceId = seqId,
            notification = fallbackNotif,
            activeTierName = classic.presenterName,
            jsonSizeBytes = bytes,
            selectionReasonRu = "Активирован резервный вариант после отказа основного презентера",
            errorSummary = lastError
        )
    }

    private fun createPendingIntents(context: Context): IslandPendingIntents {
        val openApp = PendingIntent.getActivity(
            context,
            200,
            Intent(context, MainActivity::class.java).apply {
                setPackage(context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun explicitBroadcastPi(reqCode: Int, actionStr: String): PendingIntent {
            val intent = Intent().apply {
                component = ComponentName(context, IslandActionReceiver::class.java)
                setPackage(context.packageName)
                action = actionStr
            }
            return PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return IslandPendingIntents(
            openApp = openApp,
            togglePower = explicitBroadcastPi(201, IslandActionReceiver.ACTION_TOGGLE),
            stepMinus10 = explicitBroadcastPi(202, IslandActionReceiver.ACTION_STEP_MINUS),
            stepPlus10 = explicitBroadcastPi(203, IslandActionReceiver.ACTION_STEP_PLUS)
        )
    }
}
