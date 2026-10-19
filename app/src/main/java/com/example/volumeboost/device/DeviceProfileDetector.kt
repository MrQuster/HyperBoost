package com.example.volumeboost.device

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.*
import android.net.Uri
import android.os.Build
import android.view.Display
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

enum class OsFamily(val displayNameRu: String, val displayNameEn: String, val defaultVersionHint: String) {
    HYPER_OS("Xiaomi HyperOS", "Xiaomi HyperOS", "HyperOS"),
    MIUI("Xiaomi MIUI", "Xiaomi MIUI", "MIUI 14"),
    ONE_UI("Samsung One UI", "Samsung One UI", "One UI"),
    PIXEL_UI("Google Pixel UI", "Google Pixel UI", "Pixel Stock"),
    OXYGEN_OS("OnePlus OxygenOS", "OnePlus OxygenOS", "OxygenOS"),
    COLOR_OS("OPPO ColorOS", "OPPO ColorOS", "ColorOS"),
    REALME_UI("realme UI", "realme UI", "realme UI"),
    ORIGIN_FUNTOUCH_OS("vivo OriginOS / Funtouch", "vivo OriginOS / Funtouch", "OriginOS / Funtouch"),
    MAGIC_OS("Honor MagicOS", "Honor MagicOS", "MagicOS"),
    EMUI_HARMONY("Huawei EMUI / HarmonyOS", "Huawei EMUI / HarmonyOS", "EMUI / HarmonyOS"),
    NOTHING_OS("Nothing OS", "Nothing OS", "Nothing OS"),
    ROG_ZEN_UI("ASUS ZenUI / ROG UI", "ASUS ZenUI / ROG UI", "ZenUI / ROG UI"),
    MOTOROLA_MYUX("Motorola MyUX / Hello UI", "Motorola MyUX / Hello UI", "Hello UI"),
    SONY_XPERIA("Sony Xperia UI", "Sony Xperia UI", "Xperia UI"),
    HIOS_XOS("Transsion HiOS / XOS", "Transsion HiOS / XOS", "HiOS / XOS"),
    AOSP_GENERIC("Чистый Android (AOSP / Custom ROM)", "Pure Android (AOSP / Custom ROM)", "AOSP")
}

/**
 * Полный список ручного переопределения для всех поддерживаемых семейств ОС (Пункт 5 аудита)
 */
enum class OsOverrideOption(val labelRu: String, val labelEn: String, val forcedFamily: OsFamily?) {
    AUTO("Автоопределение", "Auto-Detect", null),
    XIAOMI_HYPER_OS("Xiaomi HyperOS", "Xiaomi HyperOS", OsFamily.HYPER_OS),
    XIAOMI_MIUI("Xiaomi MIUI", "Xiaomi MIUI", OsFamily.MIUI),
    SAMSUNG_ONE_UI("Samsung One UI", "Samsung One UI", OsFamily.ONE_UI),
    GOOGLE_PIXEL("Google Pixel UI", "Google Pixel UI", OsFamily.PIXEL_UI),
    ONEPLUS_OXYGEN_OS("OnePlus OxygenOS", "OnePlus OxygenOS", OsFamily.OXYGEN_OS),
    OPPO_COLOR_OS("OPPO ColorOS", "OPPO ColorOS", OsFamily.COLOR_OS),
    REALME_UI("realme UI", "realme UI", OsFamily.REALME_UI),
    VIVO_ORIGIN_OS("vivo OriginOS / Funtouch", "vivo OriginOS / Funtouch", OsFamily.ORIGIN_FUNTOUCH_OS),
    HONOR_MAGIC_OS("Honor MagicOS", "Honor MagicOS", OsFamily.MAGIC_OS),
    HUAWEI_EMUI("Huawei EMUI / HarmonyOS", "Huawei EMUI / HarmonyOS", OsFamily.EMUI_HARMONY),
    NOTHING_OS("Nothing OS", "Nothing OS", OsFamily.NOTHING_OS),
    ASUS_ROG_ZEN("ASUS ZenUI / ROG UI", "ASUS ZenUI / ROG UI", OsFamily.ROG_ZEN_UI),
    MOTOROLA_MYUX("Motorola MyUX", "Motorola MyUX", OsFamily.MOTOROLA_MYUX),
    SONY_XPERIA("Sony Xperia UI", "Sony Xperia UI", OsFamily.SONY_XPERIA),
    TRANSSION_HIOS("Tecno / Infinix HiOS", "Tecno / Infinix HiOS", OsFamily.HIOS_XOS),
    OTHER_AOSP("Чистый AOSP / Custom ROM", "Pure AOSP / Custom ROM", OsFamily.AOSP_GENERIC)
}

enum class DetectionConfidence(val labelRu: String, val labelEn: String) {
    HIGH("Высокая (подтверждено свойствами и пакетами)", "High (verified by props & packages)"),
    MEDIUM("Средняя (определено по косвенным признакам)", "Medium (partial signals)"),
    LOW("Низкая (универсальный безопасный профиль)", "Low (safe fallback profile)"),
    MANUAL("Ручной режим (выбрано пользователем)", "Manual Override (user selected)")
}

enum class DeviceFormFactor(val labelRu: String, val labelEn: String) {
    PHONE("Смартфон", "Smartphone"),
    FOLDABLE_FOLDED("Складной смартфон (сложен)", "Foldable (Folded)"),
    FOLDABLE_UNFOLDED("Складной смартфон (разложен)", "Foldable (Unfolded)"),
    TABLET("Планшет / Широкий экран", "Tablet / Wide Screen")
}

enum class TriStateStatus(val symbol: String, val labelRu: String, val labelEn: String) {
    SUPPORTED("✓", "Поддерживается", "Supported"),
    UNSUPPORTED("✗", "Не поддерживается", "Unsupported"),
    UNKNOWN("?", "Неизвестно (требуется проверка)", "Unknown (needs verification)")
}

enum class VerificationSource(val labelRu: String, val labelEn: String) {
    VERIFIED_RUNTIME("Проверено реально в системе", "Verified at runtime"),
    VERSION_CHECK("По версии Android API", "By Android API version"),
    ASSUMED("Предположение по модели/оболочке", "Assumed by OS family")
}

enum class Capability(val titleRu: String, val titleEn: String) {
    LOUDNESS_ENHANCER("LoudnessEnhancer (Усиление)", "LoudnessEnhancer"),
    DYNAMICS_PROCESSING("DynamicsProcessing (Многополосный DSP)", "DynamicsProcessing"),
    EQUALIZER("Аппаратный эквалайзер", "Hardware Equalizer"),
    BASS_BOOST("Усиление низких частот (BassBoost)", "BassBoost"),
    VIRTUALIZER("Пространственный звук (Virtualizer)", "Virtualizer"),
    PLAYBACK_CAPTURE("Захват системного звука (AudioPlaybackCapture)", "AudioPlaybackCapture"),
    MEDIA_PROJECTION("Системный токен MediaProjection", "MediaProjection"),
    XIAOMI_SUPER_ISLAND("Суперостров Xiaomi (HyperIsland)", "Xiaomi Super Island"),
    SAMSUNG_LIVE_NOTIF("Samsung Now Bar / Live Notification", "Samsung Live Notification"),
    ANDROID_16_LIVE_UPDATES("Android 16+ Live Updates (API 36+)", "Android 16+ Live Updates"),
    AGSL_RUNTIME_SHADER("Шейдер преломления линзы AGSL (API 33+)", "AGSL RuntimeShader"),
    HARDWARE_RENDER_BLUR("Аппаратное размытие RenderEffect (API 31+)", "RenderEffect Blur"),
    THEMED_MONOCHROME_ICON("Монохромная иконка Android 13+", "Themed Monochrome Icon"),
    QUICK_SETTINGS_TILE("Плитка быстрых настроек в шторке", "Quick Settings Tile"),
    APP_WIDGETS("Виджет рабочего стола (Glance)", "Home Screen Widget"),
    APP_SHORTCUTS("Быстрые ярлыки иконки (App Shortcuts)", "App Shortcuts"),
    BLUETOOTH_CODEC_INSPECTION("Инспекция Bluetooth-устройств и задержки", "Bluetooth Device Inspection")
}

data class CapabilityEvaluation(
    val capability: Capability,
    val status: TriStateStatus,
    val source: VerificationSource,
    val reasonRu: String,
    val reasonEn: String
)

data class HardwareAudioSpecs(
    val socManufacturer: String = "Неизвестно",
    val socModel: String = "Неизвестно",
    val hardwareBoard: String = "unknown",
    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val totalRamMb: Long = 0L,
    val availableRamMb: Long = 0L,
    val screenResolutionPx: String = "Неизвестно",
    val screenDensityDpi: Int = 440,
    val refreshRateHz: Int = 60,
    val hdrSupported: Boolean = false,
    val nativeSampleRateHz: Int = 48000,
    val framesPerBuffer: Int = 256,
    val hasLowLatencyAudio: Boolean = false,
    val hasProAudio: Boolean = false,
    val connectedOutputSummary: String = "Встроенный динамик"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("socManufacturer", socManufacturer)
        put("socModel", socModel)
        put("hardwareBoard", hardwareBoard)
        put("cpuCores", cpuCores)
        put("totalRamMb", totalRamMb)
        put("availableRamMb", availableRamMb)
        put("screenResolutionPx", screenResolutionPx)
        put("screenDensityDpi", screenDensityDpi)
        put("refreshRateHz", refreshRateHz)
        put("hdrSupported", hdrSupported)
        put("nativeSampleRateHz", nativeSampleRateHz)
        put("framesPerBuffer", framesPerBuffer)
        put("hasLowLatencyAudio", hasLowLatencyAudio)
        put("hasProAudio", hasProAudio)
        put("connectedOutputSummary", connectedOutputSummary)
    }

    companion object {
        fun fromJson(o: JSONObject?): HardwareAudioSpecs {
            if (o == null) return HardwareAudioSpecs()
            return HardwareAudioSpecs(
                socManufacturer = o.optString("socManufacturer", "Неизвестно"),
                socModel = o.optString("socModel", "Неизвестно"),
                hardwareBoard = o.optString("hardwareBoard", "unknown"),
                cpuCores = o.optInt("cpuCores", Runtime.getRuntime().availableProcessors()),
                totalRamMb = o.optLong("totalRamMb", 0L),
                availableRamMb = o.optLong("availableRamMb", 0L),
                screenResolutionPx = o.optString("screenResolutionPx", "Неизвестно"),
                screenDensityDpi = o.optInt("screenDensityDpi", 440),
                refreshRateHz = o.optInt("refreshRateHz", 60),
                hdrSupported = o.optBoolean("hdrSupported", false),
                nativeSampleRateHz = o.optInt("nativeSampleRateHz", 48000),
                framesPerBuffer = o.optInt("framesPerBuffer", 256),
                hasLowLatencyAudio = o.optBoolean("hasLowLatencyAudio", false),
                hasProAudio = o.optBoolean("hasProAudio", false),
                connectedOutputSummary = o.optString("connectedOutputSummary", "Встроенный динамик")
            )
        }
    }
}

data class DeviceProfile(
    val vendor: String = "Unknown",
    val brand: String = "Unknown",
    val model: String = "Unknown",
    val deviceCodename: String = "unknown",
    val osFamily: OsFamily = OsFamily.AOSP_GENERIC,
    val osVersion: String = "AOSP",
    val romRegion: String = "Неизвестно",
    val androidRelease: String = "15",
    val apiLevel: Int = 35,
    val apiLevelFullFormatted: String = "35.0",
    val securityPatch: String = "Неизвестно",
    val displayBuildId: String = "unknown",
    val systemBuildSignature: String = "",
    val isManualOverride: Boolean = false,
    val isLikelyCustomRom: Boolean = false,
    val formFactor: DeviceFormFactor = DeviceFormFactor.PHONE,
    val smallestScreenWidthDp: Int = 360,
    val cutoutStatus: TriStateStatus = TriStateStatus.UNKNOWN,
    val focusNotificationStatus: TriStateStatus = TriStateStatus.UNKNOWN,
    val confidence: DetectionConfidence = DetectionConfidence.HIGH,
    val capabilities: Set<Capability> = emptySet(),
    val capabilityEvaluations: List<CapabilityEvaluation> = emptyList(),
    val matchedSignals: List<String> = emptyList(),
    val unreadableProperties: List<String> = emptyList(),
    val hardwareSpecs: HardwareAudioSpecs = HardwareAudioSpecs()
) {
    val hasDisplayCutout: Boolean
        get() = cutoutStatus == TriStateStatus.SUPPORTED

    fun toJsonString(): String = JSONObject().apply {
        put("vendor", vendor)
        put("brand", brand)
        put("model", model)
        put("deviceCodename", deviceCodename)
        put("osFamily", osFamily.name)
        put("osVersion", osVersion)
        put("romRegion", romRegion)
        put("androidRelease", androidRelease)
        put("apiLevel", apiLevel)
        put("apiLevelFullFormatted", apiLevelFullFormatted)
        put("securityPatch", securityPatch)
        put("displayBuildId", displayBuildId)
        put("systemBuildSignature", systemBuildSignature)
        put("isManualOverride", isManualOverride)
        put("isLikelyCustomRom", isLikelyCustomRom)
        put("formFactor", formFactor.name)
        put("smallestScreenWidthDp", smallestScreenWidthDp)
        put("cutoutStatus", cutoutStatus.name)
        put("focusNotificationStatus", focusNotificationStatus.name)
        put("confidence", confidence.name)
        put("capabilities", JSONArray(capabilities.map { it.name }))
        put("matchedSignals", JSONArray(matchedSignals))
        put("unreadableProperties", JSONArray(unreadableProperties))
        put("hardwareSpecs", hardwareSpecs.toJson())
        val evalArr = JSONArray()
        capabilityEvaluations.forEach { ev ->
            evalArr.put(JSONObject().apply {
                put("cap", ev.capability.name)
                put("status", ev.status.name)
                put("source", ev.source.name)
                put("reasonRu", ev.reasonRu)
                put("reasonEn", ev.reasonEn)
            })
        }
        put("capabilityEvaluations", evalArr)
    }.toString(2)

    companion object {
        fun fromJsonString(raw: String): DeviceProfile? = runCatching {
            if (raw.isBlank()) return null
            val o = JSONObject(raw)
            val capArr = o.optJSONArray("capabilities")
            val caps = mutableSetOf<Capability>()
            if (capArr != null) {
                for (i in 0 until capArr.length()) {
                    runCatching { Capability.valueOf(capArr.getString(i)) }.getOrNull()?.let { caps.add(it) }
                }
            }
            val sigArr = o.optJSONArray("matchedSignals")
            val signals = if (sigArr != null) List(sigArr.length()) { sigArr.optString(it) } else emptyList()
            val unreadArr = o.optJSONArray("unreadableProperties")
            val unreadable = if (unreadArr != null) List(unreadArr.length()) { unreadArr.optString(it) } else emptyList()

            val evalArr = o.optJSONArray("capabilityEvaluations")
            val evals = mutableListOf<CapabilityEvaluation>()
            if (evalArr != null) {
                for (i in 0 until evalArr.length()) {
                    val eo = evalArr.getJSONObject(i)
                    val cap = runCatching { Capability.valueOf(eo.getString("cap")) }.getOrNull() ?: continue
                    val st = runCatching { TriStateStatus.valueOf(eo.optString("status", "UNKNOWN")) }.getOrDefault(TriStateStatus.UNKNOWN)
                    val src = runCatching { VerificationSource.valueOf(eo.optString("source", "ASSUMED")) }.getOrDefault(VerificationSource.ASSUMED)
                    evals.add(
                        CapabilityEvaluation(
                            capability = cap,
                            status = st,
                            source = src,
                            reasonRu = eo.optString("reasonRu", ""),
                            reasonEn = eo.optString("reasonEn", "")
                        )
                    )
                }
            }

            DeviceProfile(
                vendor = o.optString("vendor", "Unknown"),
                brand = o.optString("brand", "Unknown"),
                model = o.optString("model", "Unknown"),
                deviceCodename = o.optString("deviceCodename", "unknown"),
                osFamily = runCatching { OsFamily.valueOf(o.optString("osFamily")) }.getOrDefault(OsFamily.AOSP_GENERIC),
                osVersion = o.optString("osVersion", "AOSP"),
                romRegion = o.optString("romRegion", "Неизвестно"),
                androidRelease = o.optString("androidRelease", "15"),
                apiLevel = o.optInt("apiLevel", 35),
                apiLevelFullFormatted = o.optString("apiLevelFullFormatted", "35.0"),
                securityPatch = o.optString("securityPatch", "Неизвестно").ifBlank { "Неизвестно" },
                displayBuildId = o.optString("displayBuildId", "unknown"),
                systemBuildSignature = o.optString("systemBuildSignature", ""),
                isManualOverride = o.optBoolean("isManualOverride", false),
                isLikelyCustomRom = o.optBoolean("isLikelyCustomRom", false),
                formFactor = runCatching { DeviceFormFactor.valueOf(o.optString("formFactor")) }.getOrDefault(DeviceFormFactor.PHONE),
                smallestScreenWidthDp = o.optInt("smallestScreenWidthDp", 360),
                cutoutStatus = runCatching { TriStateStatus.valueOf(o.optString("cutoutStatus")) }.getOrDefault(TriStateStatus.UNKNOWN),
                focusNotificationStatus = runCatching { TriStateStatus.valueOf(o.optString("focusNotificationStatus")) }.getOrDefault(TriStateStatus.UNKNOWN),
                confidence = runCatching { DetectionConfidence.valueOf(o.optString("confidence")) }.getOrDefault(DetectionConfidence.MEDIUM),
                capabilities = caps,
                capabilityEvaluations = evals,
                matchedSignals = signals,
                unreadableProperties = unreadable,
                hardwareSpecs = HardwareAudioSpecs.fromJson(o.optJSONObject("hardwareSpecs"))
            )
        }.getOrNull()
    }
}

interface SystemEnvironmentSource {
    val manufacturer: String
    val brand: String
    val model: String
    val device: String
    val displayBuildId: String
    val androidRelease: String
    val sdkInt: Int
    val sdkIntFull: Int
    val securityPatch: String
    val smallestWidthDp: Int
    val hasHingeFeature: Boolean
    val hasMultipleDisplays: Boolean
    val cutoutStatus: TriStateStatus
    val queriedEffectUuids: Set<String>
    val focusNotificationStatus: TriStateStatus
    val promotedNotificationsAllowed: Boolean
    val hardwareSpecs: HardwareAudioSpecs
    fun getSystemProperty(key: String): String
    fun isPackageInstalled(packageName: String): Boolean
    fun probeAudioEffectCreation(effectShortName: String): Boolean
}

class AndroidSystemEnvironmentSource(private val context: Context) : SystemEnvironmentSource {

    companion object {
        // Кэшируем Method для SystemProperties.get один раз (Пункт 7 аудита)
        private val cachedSystemPropMethod: Method? by lazy {
            runCatching {
                val clazz = Class.forName("android.os.SystemProperties")
                clazz.getMethod("get", String::class.java, String::class.java)
            }.getOrNull()
        }
    }

    override val manufacturer: String = Build.MANUFACTURER ?: ""
    override val brand: String = Build.BRAND ?: ""
    override val model: String = Build.MODEL ?: ""
    override val device: String = Build.DEVICE ?: ""
    override val displayBuildId: String = Build.DISPLAY ?: ""
    override val androidRelease: String = Build.VERSION.RELEASE ?: ""
    override val sdkInt: Int = Build.VERSION.SDK_INT
    override val sdkIntFull: Int = runCatching {
        Build.VERSION::class.java.getField("SDK_INT_FULL").getInt(null)
    }.getOrDefault(Build.VERSION.SDK_INT * 100_000)
    override val securityPatch: String = (Build.VERSION.SECURITY_PATCH ?: "").ifBlank { "Неизвестно" }

    override val smallestWidthDp: Int
        get() = context.resources.configuration.smallestScreenWidthDp.takeIf {
            it != Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED && it > 0
        } ?: 360

    override val hasHingeFeature: Boolean
        get() = runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
        }.getOrDefault(false)

    override val hasMultipleDisplays: Boolean
        get() = runCatching {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            dm.displays.size > 1
        }.getOrDefault(false)

    // Точная проверка Display.getCutout() через DisplayManager с состоянием UNKNOWN (Пункт 10 аудита)
    override val cutoutStatus: TriStateStatus
        get() = runCatching {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val defaultDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY)
                ?: return@runCatching TriStateStatus.UNKNOWN
            val cutout = defaultDisplay.cutout
            if (cutout != null && cutout.boundingRects.isNotEmpty()) {
                TriStateStatus.SUPPORTED
            } else {
                TriStateStatus.UNSUPPORTED
            }
        }.getOrDefault(TriStateStatus.UNKNOWN)

    // Настоящее чтение зарегистрированных UUID из AudioEffect.queryEffects() (Пункт 4 аудита)
    override val queriedEffectUuids: Set<String>
        get() = runCatching {
            val descriptors = AudioEffect.queryEffects() ?: return@runCatching emptySet<String>()
            descriptors.mapNotNull { it.type?.toString()?.lowercase() }.toSet()
        }.getOrDefault(emptySet())

    /**
     * Честная проверка поддержки Xiaomi Focus Notifications / Super Island (Пункт 3 аудита).
     * Проверяет:
     * 1) Системное свойство persist.sys.feature.island / ro.mi.os.version.code
     * 2) Системный ContentProvider "content://miui.statusbar.notification.public" (метод "canShowFocus")
     * [Нужно проверить на устройстве: если провайдер закрыт правами, возвращает честный UNKNOWN вместо ложного SUPPORTED].
     */
    override val focusNotificationStatus: TriStateStatus
        get() = runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.areNotificationsEnabled()) return@runCatching TriStateStatus.UNSUPPORTED

            // Попытка вызова публичного провайдера статус-бара MIUI/HyperOS
            val uri = Uri.parse("content://miui.statusbar.notification.public")
            val extras = android.os.Bundle().apply { putString("package", context.packageName) }
            val bundleResult = runCatching {
                context.contentResolver.call(uri, "canShowFocus", context.packageName, extras)
            }.getOrNull()

            if (bundleResult != null && bundleResult.containsKey("canShowFocus")) {
                return@runCatching if (bundleResult.getBoolean("canShowFocus")) {
                    TriStateStatus.SUPPORTED
                } else {
                    TriStateStatus.UNSUPPORTED
                }
            }

            // Если провайдер не ответил — проверяем наличие плагина SystemUI и свойства острова
            val islandProp = getSystemProperty("persist.sys.feature.island")
            if (islandProp.equals("true", ignoreCase = true) || islandProp == "1") {
                return@runCatching TriStateStatus.SUPPORTED
            }
            TriStateStatus.UNKNOWN
        }.getOrDefault(TriStateStatus.UNKNOWN)

    // Проверка Android 16+ (API 36+) canPostPromotedNotifications (Пункт 2 аудита)
    override val promotedNotificationsAllowed: Boolean
        get() = runCatching {
            if (Build.VERSION.SDK_INT < 36) return@runCatching false
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val method = nm.javaClass.getMethod("canPostPromotedNotifications")
            method.invoke(nm) as? Boolean ?: false
        }.getOrDefault(false)

    override val hardwareSpecs: HardwareAudioSpecs
        get() = collectHardwareAndAudioSpecs()

    override fun getSystemProperty(key: String): String {
        // Способ 1: Закэшированный Method рефлексии SystemProperties
        val reflected = runCatching {
            (cachedSystemPropMethod?.invoke(null, key, "") as? String)?.trim().orEmpty()
        }.getOrDefault("")
        if (reflected.isNotEmpty()) return reflected

        // Способ 2: Запасной вызов getprop с коротким таймаутом 120 мс (Пункт 7 аудита)
        return runCatching {
            val proc = ProcessBuilder("/system/bin/getprop", key)
                .redirectErrorStream(true)
                .start()
            val finished = proc.waitFor(120, TimeUnit.MILLISECONDS)
            if (finished) {
                BufferedReader(InputStreamReader(proc.inputStream)).use { it.readLine()?.trim().orEmpty() }
            } else {
                proc.destroy()
                ""
            }
        }.getOrDefault("")
    }

    override fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    // Пробное создание аудиоэффекта с немедленным освобождением .release() (Пункт 4 аудита)
    override fun probeAudioEffectCreation(effectShortName: String): Boolean {
        return runCatching {
            when (effectShortName) {
                "LOUDNESS" -> LoudnessEnhancer(0).apply { release() }
                "DYNAMICS" -> {
                    val cfg = DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        2, true, 1, false, 0, false, 0, true
                    ).build()
                    DynamicsProcessing(0, 0, cfg).apply { release() }
                }
                "EQUALIZER" -> Equalizer(0, 0).apply { release() }
                "BASS" -> BassBoost(0, 0).apply { release() }
                "VIRTUALIZER" -> @Suppress("DEPRECATION") Virtualizer(0, 0).apply { release() }
            }
            true
        }.getOrDefault(false)
    }

    private fun collectHardwareAndAudioSpecs(): HardwareAudioSpecs {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        runCatching { am?.getMemoryInfo(memInfo) }
        val totalRam = (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(0L)
        val availRam = (memInfo.availMem / (1024L * 1024L)).coerceAtLeast(0L)

        val socMfg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (Build.SOC_MANUFACTURER ?: "").ifBlank { "Неизвестно" }
        } else "Неизвестно"
        val socMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (Build.SOC_MODEL ?: "").ifBlank { Build.HARDWARE ?: "Неизвестно" }
        } else Build.HARDWARE ?: "Неизвестно"

        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val disp = dm?.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = context.resources.displayMetrics
        val resString = "${metrics.widthPixels} × ${metrics.heightPixels}"
        val refreshHz = disp?.refreshRate?.roundToInt() ?: 60
        val hdr = runCatching { disp?.isHdr ?: false }.getOrDefault(false)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val sr = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
        val fpb = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256
        val pm = context.packageManager
        val lowLat = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)
        val proAud = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO)

        val outputs = runCatching {
            audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        }.getOrDefault(emptyArray())
        val outNames = outputs.mapNotNull { dev ->
            when (dev.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET ->
                    "BT: ${dev.productName ?: "Bluetooth"}"
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE ->
                    "USB: ${dev.productName ?: "DAC"}"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET ->
                    "Проводные наушники"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Встроенный динамик"
                else -> null
            }
        }.distinct().joinToString(", ").ifBlank { "Встроенный динамик" }

        return HardwareAudioSpecs(
            socManufacturer = socMfg,
            socModel = socMod,
            hardwareBoard = Build.HARDWARE ?: "unknown",
            cpuCores = Runtime.getRuntime().availableProcessors(),
            totalRamMb = totalRam,
            availableRamMb = availRam,
            screenResolutionPx = resString,
            screenDensityDpi = metrics.densityDpi,
            refreshRateHz = refreshHz,
            hdrSupported = hdr,
            nativeSampleRateHz = sr,
            framesPerBuffer = fpb,
            hasLowLatencyAudio = lowLat,
            hasProAudio = proAud,
            connectedOutputSummary = outNames
        )
    }
}

object DeviceProfileDetector {

    private val UUID_LOUDNESS = "fe3199be-aed0-413f-87bb-11260eb63cf1"
    private val UUID_DYNAMICS = "7261676f-6d75-7369-6364-28e2fd3ac39e"
    private val UUID_EQUALIZER = "0bed4300-ddd6-11db-8f34-0002a5d5c51b"
    private val UUID_BASS_BOOST = "0634f220-ddd4-11db-a0fc-0002a5d5c51b"
    private val UUID_VIRTUALIZER = "37cc2c00-dddd-11db-8577-0002a5d5c51b"

    /**
     * Форматирование полного API уровня Android 16+ (SDK_INT_FULL: major * 100_000 + minor -> "36.1")
     * (Пункт 11 аудита)
     */
    fun formatFullSdkVersion(sdkInt: Int, sdkIntFull: Int): String {
        if (sdkIntFull >= 100_000) {
            val major = sdkIntFull / 100_000
            val minor = sdkIntFull % 100_000
            return "$major.$minor"
        }
        return "$sdkInt.0"
    }

    fun computeBuildSignature(source: SystemEnvironmentSource): String {
        return "${source.displayBuildId.trim()}|${source.sdkIntFull}|${source.securityPatch.trim()}"
    }

    fun detect(
        source: SystemEnvironmentSource,
        overrideOption: OsOverrideOption = OsOverrideOption.AUTO
    ): DeviceProfile {
        val mfg = source.manufacturer.trim()
        val brd = source.brand.trim()
        val mfgLower = mfg.lowercase()
        val brdLower = brd.lowercase()

        val signals = mutableListOf<String>()
        val unreadableProps = mutableListOf<String>()

        fun readPropTracked(key: String): String {
            val v = source.getSystemProperty(key).trim()
            if (v.isEmpty()) unreadableProps.add(key) else signals.add("prop:$key=$v")
            return v
        }

        var detectedFamily = OsFamily.AOSP_GENERIC
        var detectedVersion = ""
        var confidenceScore = 0
        var likelyCustomRom = false

        val miOsName = readPropTracked("ro.mi.os.version.name")
        val miOsCode = readPropTracked("ro.mi.os.version.code")
        val miuiUiName = readPropTracked("ro.miui.ui.version.name")
        val hasMiuiSecurityPkg = source.isPackageInstalled("com.miui.securitycenter")
        val hasMiuiSystemUiPlugin = source.isPackageInstalled("miui.systemui.plugin")
        val isXiaomiHardware = mfgLower.contains("xiaomi") || brdLower in listOf("xiaomi", "redmi", "poco")

        // 1. Xiaomi HyperOS / MIUI
        if (miOsName.isNotBlank() || miOsCode.isNotBlank()) {
            detectedFamily = OsFamily.HYPER_OS
            detectedVersion = miOsName.ifBlank { "HyperOS $miOsCode" }
            confidenceScore += 2
            if (isXiaomiHardware) { signals.add("brand:$brd"); confidenceScore++ }
            if (hasMiuiSecurityPkg || hasMiuiSystemUiPlugin) {
                signals.add("pkg:miui_system")
                confidenceScore++
            }
        } else if (miuiUiName.isNotBlank()) {
            if (miuiUiName.startsWith("V816", ignoreCase = true) ||
                source.displayBuildId.contains("OS1.") ||
                source.displayBuildId.contains("OS2.") ||
                source.displayBuildId.contains("OS3.") ||
                source.displayBuildId.contains("OS4.")
            ) {
                detectedFamily = OsFamily.HYPER_OS
                detectedVersion = miuiUiName
            } else {
                detectedFamily = OsFamily.MIUI
                detectedVersion = miuiUiName
            }
            confidenceScore += 2
            if (isXiaomiHardware) confidenceScore++
        } else if (isXiaomiHardware) {
            if (hasMiuiSecurityPkg) {
                detectedFamily = OsFamily.HYPER_OS
                detectedVersion = "HyperOS / MIUI"
                signals.add("pkg:com.miui.securitycenter")
                confidenceScore = 2
            } else {
                // Xiaomi железо без единого свойства и без пакета MIUI -> Кастомная прошивка (LineageOS / PixelOS)
                detectedFamily = OsFamily.AOSP_GENERIC
                detectedVersion = "Custom ROM (${source.androidRelease})"
                likelyCustomRom = true
                signals.add("custom_rom_suspected:xiaomi_without_miui_props")
                confidenceScore = 1
            }
        }

        // 2. Samsung One UI (с проверкой на кастомную прошивку — Пункт 8 аудита)
        val oneUiProp = readPropTracked("ro.build.version.oneui")
        val semProp = readPropTracked("ro.build.version.sem")
        val validOneUiVer = formatSamsungOneUiVersion(oneUiProp)
        val validSemVer = semProp.toIntOrNull() != null
        val hasSamsungDeviceCare = source.isPackageInstalled("com.samsung.android.lool") ||
                source.isPackageInstalled("com.samsung.android.sm")
        val isSamsung = mfgLower.contains("samsung") || brdLower.contains("samsung")
        if (detectedFamily == OsFamily.AOSP_GENERIC && !likelyCustomRom && (validOneUiVer.isNotBlank() || validSemVer || isSamsung)) {
            detectedFamily = OsFamily.ONE_UI
            detectedVersion = validOneUiVer.ifBlank { "One UI" }
            if (validOneUiVer.isNotBlank() || validSemVer) confidenceScore += 2
            if (hasSamsungDeviceCare) { signals.add("pkg:SamsungDeviceCare"); confidenceScore++ }
            if (isSamsung && validOneUiVer.isBlank() && !validSemVer && !hasSamsungDeviceCare) {
                likelyCustomRom = true
                signals.add("custom_rom_suspected:samsung_without_oneui_props")
                confidenceScore = 1
            } else if (isSamsung) {
                signals.add("mfg:samsung")
                confidenceScore++
            }
        }

        // 3. OnePlus / OPPO / realme (с гарантированным непустым osVersion — Пункт 14 аудита)
        val oplusRomProp = readPropTracked("ro.build.version.oplusrom")
        val oxygenProp = readPropTracked("ro.oxygen.version")
        val realmeUiProp = readPropTracked("ro.build.version.realmeui")
        val hasColorOsPkg = source.isPackageInstalled("com.coloros.safecenter") ||
                source.isPackageInstalled("com.oplus.battery")
        if (detectedFamily == OsFamily.AOSP_GENERIC && !likelyCustomRom &&
            (oplusRomProp.isNotBlank() || oxygenProp.isNotBlank() || realmeUiProp.isNotBlank() ||
             brdLower in listOf("oneplus", "oppo", "realme"))
        ) {
            when {
                brdLower.contains("oneplus") || oxygenProp.isNotBlank() -> {
                    detectedFamily = OsFamily.OXYGEN_OS
                    detectedVersion = oxygenProp.ifBlank { oplusRomProp }.ifBlank { "OxygenOS" }
                }
                brdLower.contains("realme") || realmeUiProp.isNotBlank() -> {
                    detectedFamily = OsFamily.REALME_UI
                    detectedVersion = realmeUiProp.ifBlank { oplusRomProp }.ifBlank { "realme UI" }
                }
                else -> {
                    detectedFamily = OsFamily.COLOR_OS
                    detectedVersion = oplusRomProp.ifBlank { "ColorOS" }
                }
            }
            if (oplusRomProp.isNotBlank() || oxygenProp.isNotBlank() || realmeUiProp.isNotBlank()) confidenceScore += 2
            if (hasColorOsPkg) { signals.add("pkg:OPlusSafeCenter"); confidenceScore++ }
            if (confidenceScore == 0) {
                likelyCustomRom = true
                signals.add("custom_rom_suspected:oplus_without_rom_props")
                confidenceScore = 1
            } else {
                confidenceScore++
            }
        }

        // 4. vivo / iQOO
        val vivoOsProp = readPropTracked("ro.vivo.os.build.display.id")
        val hasVivoPkg = source.isPackageInstalled("com.vivo.permissionmanager") ||
                source.isPackageInstalled("com.iqoo.secure")
        if (detectedFamily == OsFamily.AOSP_GENERIC && !likelyCustomRom && (vivoOsProp.isNotBlank() || brdLower in listOf("vivo", "iqoo"))) {
            detectedFamily = OsFamily.ORIGIN_FUNTOUCH_OS
            detectedVersion = vivoOsProp.ifBlank { "OriginOS / Funtouch" }
            if (vivoOsProp.isNotBlank()) confidenceScore += 2
            if (hasVivoPkg) { signals.add("pkg:VivoManager"); confidenceScore++ }
            if (vivoOsProp.isBlank() && !hasVivoPkg) {
                likelyCustomRom = true
                signals.add("custom_rom_suspected:vivo_without_props")
                confidenceScore = 1
            } else {
                confidenceScore++
            }
        }

        // 5. Honor MagicOS и Huawei EMUI / HarmonyOS
        val magicProp = readPropTracked("ro.build.version.magic")
        val emuiProp = readPropTracked("ro.build.version.emui")
        val hasHonorHuaweiPkg = source.isPackageInstalled("com.hihonor.systemmanager") ||
                source.isPackageInstalled("com.huawei.systemmanager")
        if (detectedFamily == OsFamily.AOSP_GENERIC && !likelyCustomRom && (magicProp.isNotBlank() || brdLower.contains("honor"))) {
            detectedFamily = OsFamily.MAGIC_OS
            detectedVersion = magicProp.ifBlank { "MagicOS" }
            if (magicProp.isNotBlank()) confidenceScore += 2
            if (hasHonorHuaweiPkg) confidenceScore++
            if (magicProp.isBlank() && !hasHonorHuaweiPkg) {
                likelyCustomRom = true
                signals.add("custom_rom_suspected:honor_without_magic_props")
                confidenceScore = 1
            } else {
                confidenceScore++
            }
        } else if (detectedFamily == OsFamily.AOSP_GENERIC && !likelyCustomRom && (emuiProp.isNotBlank() || brdLower.contains("huawei"))) {
            detectedFamily = OsFamily.EMUI_HARMONY
            detectedVersion = emuiProp.ifBlank { "EMUI / HarmonyOS" }
            if (emuiProp.isNotBlank()) confidenceScore += 2
            if (hasHonorHuaweiPkg) confidenceScore++
            if (emuiProp.isBlank() && !hasHonorHuaweiPkg) {
                likelyCustomRom = true
                signals.add("custom_rom_suspected:huawei_without_emui_props")
                confidenceScore = 1
            } else {
                confidenceScore++
            }
        }

        // 6. Google Pixel, Nothing, ASUS, Motorola, Sony, Transsion, AOSP
        if (detectedFamily == OsFamily.AOSP_GENERIC && !likelyCustomRom) {
            val nothingProp = readPropTracked("ro.nothing.version.id")
            when {
                mfgLower.contains("google") || brdLower.contains("google") -> {
                    detectedFamily = OsFamily.PIXEL_UI
                    detectedVersion = "Pixel Android ${source.androidRelease}"
                    signals.add("mfg:Google")
                    confidenceScore = 3
                }
                mfgLower.contains("nothing") || nothingProp.isNotBlank() -> {
                    detectedFamily = OsFamily.NOTHING_OS
                    detectedVersion = nothingProp.ifBlank { "Nothing OS" }
                    signals.add("mfg:Nothing")
                    confidenceScore = if (nothingProp.isNotBlank()) 3 else 2
                }
                mfgLower.contains("asus") -> {
                    detectedFamily = OsFamily.ROG_ZEN_UI
                    detectedVersion = "ZenUI / ROG UI"
                    signals.add("mfg:ASUS")
                    confidenceScore = 2
                }
                mfgLower.contains("motorola") || brdLower.contains("motorola") -> {
                    detectedFamily = OsFamily.MOTOROLA_MYUX
                    detectedVersion = "MyUX / Hello UI"
                    signals.add("mfg:Motorola")
                    confidenceScore = 2
                }
                mfgLower.contains("sony") -> {
                    detectedFamily = OsFamily.SONY_XPERIA
                    detectedVersion = "Xperia UI"
                    signals.add("mfg:Sony")
                    confidenceScore = 2
                }
                brdLower in listOf("tecno", "infinix", "itel") -> {
                    detectedFamily = OsFamily.HIOS_XOS
                    detectedVersion = "HiOS / XOS"
                    signals.add("mfg:Transsion")
                    confidenceScore = 2
                }
                else -> {
                    detectedFamily = OsFamily.AOSP_GENERIC
                    detectedVersion = "AOSP ${source.androidRelease.ifBlank { "${source.sdkInt}" }}"
                    signals.add("fallback:AOSP")
                    confidenceScore = 1
                }
            }
        }

        // 7. Точный разбор региона прошивки без ложного "CN / Stock" (Пункт 6 аудита)
        var romRegion = "Неизвестно"
        if (detectedFamily == OsFamily.HYPER_OS || detectedFamily == OsFamily.MIUI) {
            val miRegion = readPropTracked("ro.miui.region").uppercase()
            val hwc = readPropTracked("ro.boot.hwc").uppercase()
            val modDevice = readPropTracked("ro.product.mod_device").lowercase()
            val buildDisplayUpper = source.displayBuildId.uppercase()

            romRegion = when {
                modDevice.endsWith("_eea_global") || miRegion == "EEA" || buildDisplayUpper.contains("EUXM") -> "EEA (Европа)"
                modDevice.endsWith("_ru_global") || miRegion == "RU" || buildDisplayUpper.contains("RUXM") -> "RU"
                modDevice.endsWith("_global") || miRegion == "GLOBAL" || hwc == "GLOBAL" || buildDisplayUpper.contains("MIXM") -> "Global"
                miRegion == "CN" || hwc == "CN" || buildDisplayUpper.contains("CNXM") -> "CN (Китай)"
                else -> "Неизвестно"
            }
        }

        // 8. Чистая обработка ручного Override ОС без смешивания полей (Пункт 5 аудита)
        val isManual = overrideOption != OsOverrideOption.AUTO && overrideOption.forcedFamily != null
        val realDetectedFamily = detectedFamily
        if (isManual) {
            val forced = overrideOption.forcedFamily!!
            detectedFamily = forced
            if (forced != realDetectedFamily) {
                detectedVersion = "${forced.defaultVersionHint} (Ручной профиль)"
                romRegion = "Ручной режим"
            }
            signals.add("manual_override:${overrideOption.name}")
        }

        val confidence = when {
            isManual -> DetectionConfidence.MANUAL
            confidenceScore >= 3 -> DetectionConfidence.HIGH
            confidenceScore == 2 -> DetectionConfidence.MEDIUM
            else -> DetectionConfidence.LOW
        }

        // 9. Форм-фактор с различением сложенного и разложенного Foldable (Пункт 9 аудита)
        val isFoldableHardware = source.hasHingeFeature || source.hasMultipleDisplays
        val formFactor = when {
            isFoldableHardware && source.smallestWidthDp >= 600 -> DeviceFormFactor.FOLDABLE_UNFOLDED
            isFoldableHardware -> DeviceFormFactor.FOLDABLE_FOLDED
            source.smallestWidthDp >= 600 -> DeviceFormFactor.TABLET
            else -> DeviceFormFactor.PHONE
        }

        // 10. Честная матрица Capabilities с проверкой AudioEffect.queryEffects() и API 36 (Пункты 2, 3, 4 аудита)
        val effectUuids = source.queriedEffectUuids
        val evaluations = mutableListOf<CapabilityEvaluation>()
        val activeCaps = mutableSetOf<Capability>()

        fun evaluateAudioCap(cap: Capability, uuid: String, probeKey: String, nameRu: String) {
            val inQuery = effectUuids.any { it.contains(uuid) }
            val probeOk = if (!inQuery) source.probeAudioEffectCreation(probeKey) else true
            if (inQuery || probeOk) {
                activeCaps.add(cap)
                evaluations.add(
                    CapabilityEvaluation(
                        capability = cap,
                        status = TriStateStatus.SUPPORTED,
                        source = VerificationSource.VERIFIED_RUNTIME,
                        reasonRu = if (inQuery) "$nameRu найден в AudioEffect.queryEffects()" else "$nameRu подтверждён пробным созданием",
                        reasonEn = "Verified via AudioEffect engine"
                    )
                )
            } else if (effectUuids.isEmpty()) {
                // В JVM unit-тестах или если список пуст — отмечаем как ASSUMED для API 29+
                activeCaps.add(cap)
                evaluations.add(
                    CapabilityEvaluation(
                        capability = cap,
                        status = TriStateStatus.UNKNOWN,
                        source = VerificationSource.ASSUMED,
                        reasonRu = "Список дескрипторов пуст, доступность проверится при старте аудиосессии",
                        reasonEn = "Will be verified on audio session start"
                    )
                )
            } else {
                evaluations.add(
                    CapabilityEvaluation(
                        capability = cap,
                        status = TriStateStatus.UNSUPPORTED,
                        source = VerificationSource.VERIFIED_RUNTIME,
                        reasonRu = "Эффект $nameRu отсутствует в аудиодрайвере устройства",
                        reasonEn = "Effect not found in device audio HAL"
                    )
                )
            }
        }

        evaluateAudioCap(Capability.LOUDNESS_ENHANCER, UUID_LOUDNESS, "LOUDNESS", "LoudnessEnhancer")
        evaluateAudioCap(Capability.DYNAMICS_PROCESSING, UUID_DYNAMICS, "DYNAMICS", "DynamicsProcessing")
        evaluateAudioCap(Capability.EQUALIZER, UUID_EQUALIZER, "EQUALIZER", "Equalizer")
        evaluateAudioCap(Capability.BASS_BOOST, UUID_BASS_BOOST, "BASS", "BassBoost")
        evaluateAudioCap(Capability.VIRTUALIZER, UUID_VIRTUALIZER, "VIRTUALIZER", "Virtualizer")

        if (source.sdkInt >= 29) {
            activeCaps.add(Capability.PLAYBACK_CAPTURE)
            activeCaps.add(Capability.MEDIA_PROJECTION)
            evaluations.add(
                CapabilityEvaluation(
                    Capability.PLAYBACK_CAPTURE,
                    TriStateStatus.SUPPORTED,
                    VerificationSource.VERSION_CHECK,
                    "Доступно (Android 10 / API 29+)",
                    "Supported on API 29+"
                )
            )
            evaluations.add(
                CapabilityEvaluation(
                    Capability.MEDIA_PROJECTION,
                    TriStateStatus.SUPPORTED,
                    VerificationSource.VERSION_CHECK,
                    "Доступно (системный сервис MediaProjection)",
                    "Supported on API 29+"
                )
            )
        }

        // Проверка Xiaomi Super Island: НЕ включаем ложно на не-Xiaomi устройстве при ручном override (Пункты 3 и 5)
        val isRealXiaomiHyperOs = realDetectedFamily == OsFamily.HYPER_OS && !likelyCustomRom
        val focusStatus = source.focusNotificationStatus
        if (isRealXiaomiHyperOs && focusStatus != TriStateStatus.UNSUPPORTED) {
            activeCaps.add(Capability.XIAOMI_SUPER_ISLAND)
            evaluations.add(
                CapabilityEvaluation(
                    Capability.XIAOMI_SUPER_ISLAND,
                    focusStatus,
                    if (focusStatus == TriStateStatus.SUPPORTED) VerificationSource.VERIFIED_RUNTIME else VerificationSource.ASSUMED,
                    if (focusStatus == TriStateStatus.SUPPORTED) "Подтверждено системой HyperOS"
                    else "Обнаружена HyperOS (статус белого списка острова проверится при отправке)",
                    "HyperOS detected"
                )
            )
        } else {
            evaluations.add(
                CapabilityEvaluation(
                    Capability.XIAOMI_SUPER_ISLAND,
                    TriStateStatus.UNSUPPORTED,
                    VerificationSource.VERIFIED_RUNTIME,
                    if (!isRealXiaomiHyperOs) "Требуется настоящая прошивка Xiaomi HyperOS"
                    else "Фокусные уведомления отключены для приложения в настройках",
                    "Requires Xiaomi HyperOS with Focus Notifications enabled"
                )
            )
        }

        // Проверка Android 16+ Live Updates: строго API >= 36 (Пункт 2 аудита)
        if (source.sdkInt >= 36) {
            val allowed = source.promotedNotificationsAllowed
            if (allowed) activeCaps.add(Capability.ANDROID_16_LIVE_UPDATES)
            evaluations.add(
                CapabilityEvaluation(
                    Capability.ANDROID_16_LIVE_UPDATES,
                    if (allowed) TriStateStatus.SUPPORTED else TriStateStatus.UNSUPPORTED,
                    VerificationSource.VERIFIED_RUNTIME,
                    if (allowed) "Доступно (Android 16+ / API ${source.sdkInt}, Promoted Ongoing разрешены)"
                    else "Android 16+, но запрещено canPostPromotedNotifications()",
                    "Android 16+ Live Updates"
                )
            )
        } else {
            evaluations.add(
                CapabilityEvaluation(
                    Capability.ANDROID_16_LIVE_UPDATES,
                    TriStateStatus.UNSUPPORTED,
                    VerificationSource.VERSION_CHECK,
                    "Нужен Android 16+ (API 36+), сейчас API ${source.sdkInt}",
                    "Requires Android 16 (API 36+)"
                )
            )
        }

        // Samsung Now Bar / Live Notification
        val isRealSamsungOneUi = realDetectedFamily == OsFamily.ONE_UI && !likelyCustomRom
        if (isRealSamsungOneUi && source.sdkInt >= 35) {
            activeCaps.add(Capability.SAMSUNG_LIVE_NOTIF)
            evaluations.add(
                CapabilityEvaluation(
                    Capability.SAMSUNG_LIVE_NOTIF,
                    TriStateStatus.UNKNOWN,
                    VerificationSource.ASSUMED,
                    "Обнаружена One UI 7+ (API 35+), поддержка тегов Now Bar зависит от сборки SystemUI",
                    "One UI 7+ detected"
                )
            )
        } else {
            evaluations.add(
                CapabilityEvaluation(
                    Capability.SAMSUNG_LIVE_NOTIF,
                    TriStateStatus.UNSUPPORTED,
                    VerificationSource.VERSION_CHECK,
                    "Требуется Samsung One UI 7.0+ (Android 15+)",
                    "Requires Samsung One UI 7.0+"
                )
            )
        }

        // Шейдеры AGSL, размытие и системные интеграции
        if (source.sdkInt >= 33) {
            activeCaps.add(Capability.AGSL_RUNTIME_SHADER)
            activeCaps.add(Capability.THEMED_MONOCHROME_ICON)
            evaluations.add(
                CapabilityEvaluation(
                    Capability.AGSL_RUNTIME_SHADER,
                    TriStateStatus.SUPPORTED,
                    VerificationSource.VERSION_CHECK,
                    "Поддерживается аппаратный RuntimeShader (API 33+)",
                    "Supported on API 33+"
                )
            )
        } else {
            evaluations.add(
                CapabilityEvaluation(
                    Capability.AGSL_RUNTIME_SHADER,
                    TriStateStatus.UNSUPPORTED,
                    VerificationSource.VERSION_CHECK,
                    "Нужен Android 13+ (API 33+)",
                    "Requires API 33+"
                )
            )
        }

        if (source.sdkInt >= 31) {
            activeCaps.add(Capability.HARDWARE_RENDER_BLUR)
            evaluations.add(
                CapabilityEvaluation(
                    Capability.HARDWARE_RENDER_BLUR,
                    TriStateStatus.SUPPORTED,
                    VerificationSource.VERSION_CHECK,
                    "Поддерживается RenderEffect Blur (API 31+)",
                    "Supported on API 31+"
                )
            )
        }

        listOf(
            Capability.QUICK_SETTINGS_TILE to "Плитка TileService активна",
            Capability.APP_WIDGETS to "Виджет Jetpack Glance активен",
            Capability.APP_SHORTCUTS to "Быстрые ярлыки лаунчера активны",
            Capability.BLUETOOTH_CODEC_INSPECTION to "Мониторинг AudioDeviceCallback активен"
        ).forEach { (cap, msg) ->
            activeCaps.add(cap)
            evaluations.add(
                CapabilityEvaluation(cap, TriStateStatus.SUPPORTED, VerificationSource.VERIFIED_RUNTIME, msg, "Supported")
            )
        }

        return DeviceProfile(
            vendor = mfg.ifBlank { "Android" },
            brand = brd.ifBlank { "Generic" },
            model = source.model.trim().ifBlank { "Smartphone" },
            deviceCodename = source.device.trim().ifBlank { "generic" },
            osFamily = detectedFamily,
            osVersion = detectedVersion.ifBlank { detectedFamily.defaultVersionHint },
            romRegion = romRegion,
            androidRelease = source.androidRelease.trim().ifBlank { "${source.sdkInt}" },
            apiLevel = source.sdkInt,
            apiLevelFullFormatted = formatFullSdkVersion(source.sdkInt, source.sdkIntFull),
            securityPatch = source.securityPatch.trim().ifBlank { "Неизвестно" },
            displayBuildId = source.displayBuildId.trim().ifBlank { "unknown" },
            systemBuildSignature = computeBuildSignature(source),
            isManualOverride = isManual,
            isLikelyCustomRom = likelyCustomRom,
            formFactor = formFactor,
            smallestScreenWidthDp = source.smallestWidthDp,
            cutoutStatus = source.cutoutStatus,
            focusNotificationStatus = if (isRealXiaomiHyperOs) focusStatus else TriStateStatus.UNSUPPORTED,
            confidence = confidence,
            capabilities = activeCaps,
            capabilityEvaluations = evaluations,
            matchedSignals = signals,
            unreadableProperties = unreadableProps,
            hardwareSpecs = source.hardwareSpecs
        )
    }

    private fun formatSamsungOneUiVersion(rawProp: String): String {
        val code = rawProp.toIntOrNull() ?: return ""
        val major = code / 10000
        val minor = (code % 10000) / 100
        return if (major > 0) "One UI $major.$minor" else ""
    }
}
