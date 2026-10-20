package com.example.volumeboost.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.volumeboost.device.OsOverrideOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

private val Context.dataStore by preferencesDataStore(name = "hyperboost_v7_prefs")

enum class BoostMode {
    STANDARD,
    CAPTURE
}

enum class LimiterMode(val labelRu: String, val labelEn: String) {
    OFF("Выкл", "Off"),
    SOFT("Мягкий (tanh)", "Soft Knee (tanh)"),
    HARD("Жёсткий (Brickwall)", "Hard Brickwall")
}

enum class AppThemeMode(val labelRu: String, val labelEn: String) {
    DARK("Тёмная", "Dark"),
    LIGHT("Светлая", "Light"),
    AMOLED("AMOLED Чёрная", "AMOLED Pitch Black"),
    SYSTEM("Системная", "System Default")
}

enum class IslandStyleMode(val labelRu: String, val labelEn: String) {
    COMPACT_ICON_LEVEL("Компактный (иконка + %)", "Compact (Icon + %)"),
    ICON_ONLY("Только иконка (узкий)", "Icon Only (Minimal)"),
    DISABLED("Выкл (обычное уведомление)", "Off (Classic Notification)")
}

enum class IslandRoutingPreference(val labelRu: String, val labelEn: String) {
    AUTO("Авто (по системе)", "Auto (System Best)"),
    FORCE_SUPER_ISLAND("Xiaomi Super Island", "Xiaomi Super Island"),
    FORCE_LIVE_UPDATE("Android 16+ Live Update", "Android 16+ Live Update"),
    FORCE_CLASSIC("Обычное уведомление", "Classic Notification")
}

enum class IslandColorPreset(val labelRu: String, val labelEn: String, val hexColor: String) {
    DYNAMIC_BY_LEVEL("Динамический от уровня (Бирюза → Индиго → Золото)", "Dynamic by Gain Level", "#00F5D4"),
    THEME_SYNC("Синхронно с темой приложения", "Sync with App Theme", "#00F5D4"),
    CYBER_MINT("Фиксированный Бирюзовый", "Cyber Mint", "#00F5D4"),
    HYPER_VIOLET("Фиксированный Фиолетовый", "Hyper Violet", "#9B5DE5"),
    SOLAR_GOLD("Фиксированный Тёплый Янтарь", "Solar Gold", "#FF9E00")
}

enum class EqPreset(val labelRu: String, val labelEn: String, val gainsDb: List<Float>) {
    FLAT("Ровный", "Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    VOICE("Голос", "Voice", listOf(-4f, -1f, 5f, 6f, 2f)),
    BASS("Бас", "Bass Boost", listOf(8f, 6f, 0f, -1f, 1f)),
    CINEMA("Кино", "Cinema", listOf(6f, 3f, -1f, 4f, 6f)),
    MUSIC("Музыка", "Music", listOf(4f, 2f, 0f, 3f, 5f)),
    NIGHT("Ночной режим", "Night Mode", listOf(-6f, -2f, 4f, 2f, -3f)),
    TREBLE("Чёткие высокие", "Crisp Treble", listOf(0f, 0f, 2f, 6f, 9f)),
    CUSTOM("Свой пресет", "Custom", listOf(0f, 0f, 0f, 0f, 0f))
}

object BoostScaleMapper {
    const val STANDARD_MAX_PERCENT = 1000
    const val UNLIMITED_TECH_MAX_PERCENT = 10000
    const val FIXED_STEP_PERCENT = 10

    fun maxAllowedPercent(unlimitedMode: Boolean): Int =
        if (unlimitedMode) UNLIMITED_TECH_MAX_PERCENT else STANDARD_MAX_PERCENT

    fun fractionToPercent(fraction: Float, unlimitedMode: Boolean): Int {
        val f = fraction.coerceIn(0f, 1f)
        val q3 = if (unlimitedMode) 1500f else 500f
        val q4 = maxAllowedPercent(unlimitedMode).toFloat()
        val raw = when {
            f <= 0.25f -> (f / 0.25f) * 100f
            f <= 0.50f -> 100f + ((f - 0.25f) / 0.25f) * 100f
            f <= 0.75f -> 200f + ((f - 0.50f) / 0.25f) * (q3 - 200f)
            else -> q3 + ((f - 0.75f) / 0.25f) * (q4 - q3)
        }
        val step = when {
            raw <= 200f -> 5
            raw <= 1000f -> 10
            else -> 50
        }
        return (((raw.roundToInt() + step / 2) / step) * step).coerceIn(0, q4.toInt())
    }

    fun percentToFraction(percent: Int, unlimitedMode: Boolean): Float {
        val q3 = if (unlimitedMode) 1500f else 500f
        val q4 = maxAllowedPercent(unlimitedMode).toFloat()
        val p = percent.toFloat().coerceIn(0f, q4)
        return when {
            p <= 100f -> (p / 100f) * 0.25f
            p <= 200f -> 0.25f + ((p - 100f) / 100f) * 0.25f
            p <= q3 -> 0.50f + ((p - 200f) / (q3 - 200f)) * 0.25f
            else -> 0.75f + ((p - q3) / (q4 - q3).coerceAtLeast(1f)) * 0.25f
        }.coerceIn(0f, 1f)
    }

    fun dynamicIslandColorHexForBoost(boostPercent: Int): String {
        return when {
            boostPercent <= 120 -> "#00F5D4"
            boostPercent <= 200 -> "#00BBF9"
            boostPercent <= 400 -> "#9B5DE5"
            else -> "#FF9E00"
        }
    }
}

data class CustomEqPreset(
    val name: String,
    val bandLevelsMillibels: List<Int>
)

data class DeviceProfile(
    val id: String,
    val customName: String,
    val type: String,
    val boostPercent: Int = 100,
    val eqPresetName: String = EqPreset.FLAT.name,
    val limiterMode: LimiterMode = LimiterMode.OFF,
    val autoStartOnConnect: Boolean = false,
    val isPinned: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

data class GlobalSettings(
    // Звук
    val unlimitedBoostEnabled: Boolean = false,
    val defaultBoostMode: BoostMode = BoostMode.STANDARD,
    val limiterMode: LimiterMode = LimiterMode.OFF,
    val limiterThresholdDb: Float = -1.0f,
    val attackMs: Int = 5,
    val releaseMs: Int = 60,
    val fadeDurationMs: Int = 250,
    // Эквалайзер
    val activeEqPreset: String = EqPreset.FLAT.name,
    val customBandsMb: List<Int> = listOf(0, 0, 0, 0, 0),
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val balanceLeftRight: Float = 0f,
    val monoEnabled: Boolean = false,
    // Внешний вид
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val dynamicMaterialYou: Boolean = false,
    val accentColorHex: Long = 0xFF00F5D4,
    val blurIntensityDp: Float = 60f,
    val glassAlpha: Float = 0.16f,
    val bgAnimationSpeed: Float = 1.0f,
    val parallaxEnabled: Boolean = true,
    val powerSaveGlass: Boolean = false,
    val freeLensDragEnabled: Boolean = true,
    val lensRefractionStrength: Float = 0.65f,
    val languageCode: String = "system",
    // Поведение
    val autoStartOnBluetooth: Boolean = true,
    val startOnBoot: Boolean = false,
    val rememberLastLevel: Boolean = true,
    val floatingButtonEnabled: Boolean = false,
    val compactNotification: Boolean = false,
    val onboardingCompleted: Boolean = false,
    // Суперостров (только −10% и +10%)
    val islandEnabled: Boolean = true,
    val islandRoutingPreference: IslandRoutingPreference = IslandRoutingPreference.AUTO,
    val islandStyle: IslandStyleMode = IslandStyleMode.COMPACT_ICON_LEVEL,
    val islandColorPreset: IslandColorPreset = IslandColorPreset.DYNAMIC_BY_LEVEL,
    val islandOnlyWhenPlaying: Boolean = false,
    val islandAllowLockscreenActions: Boolean = false,
    val islandShizukuBypassEnabled: Boolean = false,
    val lastActiveIslandTier: String = "SuperIslandPresenter",
    val lastIslandError: String = "Нет ошибок",
    // Профиль системы
    val osOverrideOption: OsOverrideOption = OsOverrideOption.AUTO,
    val cachedDeviceProfileJson: String = "",
    val lastScannedAppVersion: Int = 0,
    val lastSystemBuildSignature: String = ""
)

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_JSON_SETTINGS = stringPreferencesKey("global_settings_v7_json")
        private val KEY_JSON_PROFILES = stringPreferencesKey("device_profiles_v7_json")
        private val KEY_JSON_CUSTOM_EQ = stringPreferencesKey("custom_eq_v7_json")
    }

    val settingsFlow: Flow<GlobalSettings> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_JSON_SETTINGS]
        if (raw.isNullOrBlank()) GlobalSettings() else parseSettingsJson(raw)
    }

    val profilesFlow: Flow<List<DeviceProfile>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_JSON_PROFILES]
        val list = if (raw.isNullOrBlank()) defaultProfiles() else parseProfilesJson(raw)
        list.sortedWith(compareByDescending<DeviceProfile> { it.isPinned }.thenByDescending { it.lastSeenTimestamp })
    }

    val customEqPresetsFlow: Flow<List<CustomEqPreset>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_JSON_CUSTOM_EQ]
        if (raw.isNullOrBlank()) emptyList() else parseCustomEqJson(raw)
    }

    suspend fun updateSettings(transform: (GlobalSettings) -> GlobalSettings) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_JSON_SETTINGS]?.let { parseSettingsJson(it) } ?: GlobalSettings()
            val updated = transform(current)
            prefs[KEY_JSON_SETTINGS] = serializeSettingsJson(updated)
        }
    }

    suspend fun upsertDeviceProfile(profile: DeviceProfile) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_JSON_PROFILES]?.let { parseProfilesJson(it) }?.toMutableList()
                ?: defaultProfiles().toMutableList()
            val idx = current.indexOfFirst { it.id == profile.id }
            if (idx >= 0) current[idx] = profile else current.add(profile)
            prefs[KEY_JSON_PROFILES] = serializeProfilesJson(current)
        }
    }

    suspend fun deleteDeviceProfile(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_JSON_PROFILES]?.let { parseProfilesJson(it) }?.toMutableList() ?: return@edit
            current.removeAll { it.id == id && it.id != "SPEAKER" }
            prefs[KEY_JSON_PROFILES] = serializeProfilesJson(current)
        }
    }

    suspend fun clearAllProfiles() {
        context.dataStore.edit { prefs ->
            prefs[KEY_JSON_PROFILES] = serializeProfilesJson(defaultProfiles())
        }
    }

    suspend fun saveCustomEqPreset(preset: CustomEqPreset) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_JSON_CUSTOM_EQ]?.let { parseCustomEqJson(it) }?.toMutableList() ?: mutableListOf()
            current.removeAll { it.name.equals(preset.name, ignoreCase = true) }
            current.add(preset)
            prefs[KEY_JSON_CUSTOM_EQ] = serializeCustomEqJson(current)
        }
    }

    suspend fun deleteCustomEqPreset(name: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_JSON_CUSTOM_EQ]?.let { parseCustomEqJson(it) }?.toMutableList() ?: return@edit
            current.removeAll { it.name == name }
            prefs[KEY_JSON_CUSTOM_EQ] = serializeCustomEqJson(current)
        }
    }

    suspend fun resetAllSettings() {
        context.dataStore.edit { prefs ->
            prefs[KEY_JSON_SETTINGS] = serializeSettingsJson(GlobalSettings(onboardingCompleted = true))
        }
    }

    suspend fun exportFullBackupJson(): String {
        val prefs = context.dataStore.data.first()
        val root = JSONObject()
        root.put("version", 7)
        root.put("settings", JSONObject(prefs[KEY_JSON_SETTINGS] ?: serializeSettingsJson(GlobalSettings())))
        root.put("profiles", JSONArray(prefs[KEY_JSON_PROFILES] ?: serializeProfilesJson(defaultProfiles())))
        root.put("customEq", JSONArray(prefs[KEY_JSON_CUSTOM_EQ] ?: "[]"))
        return root.toString(2)
    }

    suspend fun importFullBackupJson(jsonString: String): Boolean {
        return runCatching {
            val root = JSONObject(jsonString)
            val settingsObj = root.optJSONObject("settings")
            val profilesArr = root.optJSONArray("profiles")
            val eqArr = root.optJSONArray("customEq")
            context.dataStore.edit { prefs ->
                if (settingsObj != null) {
                    val validated = parseSettingsJson(settingsObj.toString())
                    prefs[KEY_JSON_SETTINGS] = serializeSettingsJson(validated)
                }
                if (profilesArr != null) {
                    val validatedProfiles = parseProfilesJson(profilesArr.toString())
                    prefs[KEY_JSON_PROFILES] = serializeProfilesJson(validatedProfiles)
                }
                if (eqArr != null) {
                    val validatedEq = parseCustomEqJson(eqArr.toString())
                    prefs[KEY_JSON_CUSTOM_EQ] = serializeCustomEqJson(validatedEq)
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun defaultProfiles(): List<DeviceProfile> = listOf(
        DeviceProfile(
            id = "SPEAKER",
            customName = "Встроенные динамики",
            type = "SPEAKER",
            boostPercent = 120,
            limiterMode = LimiterMode.OFF,
            isPinned = true
        )
    )

    private fun serializeSettingsJson(s: GlobalSettings): String = JSONObject().apply {
        put("unlimitedBoostEnabled", s.unlimitedBoostEnabled)
        put("defaultBoostMode", s.defaultBoostMode.name)
        put("limiterMode", s.limiterMode.name)
        put("limiterThresholdDb", s.limiterThresholdDb.toDouble())
        put("attackMs", s.attackMs)
        put("releaseMs", s.releaseMs)
        put("fadeDurationMs", s.fadeDurationMs)
        put("activeEqPreset", s.activeEqPreset)
        put("customBandsMb", JSONArray(s.customBandsMb))
        put("bassBoostStrength", s.bassBoostStrength)
        put("virtualizerStrength", s.virtualizerStrength)
        put("balanceLeftRight", s.balanceLeftRight.toDouble())
        put("monoEnabled", s.monoEnabled)
        put("themeMode", s.themeMode.name)
        put("dynamicMaterialYou", s.dynamicMaterialYou)
        put("accentColorHex", s.accentColorHex)
        put("blurIntensityDp", s.blurIntensityDp.toDouble())
        put("glassAlpha", s.glassAlpha.toDouble())
        put("bgAnimationSpeed", s.bgAnimationSpeed.toDouble())
        put("parallaxEnabled", s.parallaxEnabled)
        put("powerSaveGlass", s.powerSaveGlass)
        put("freeLensDragEnabled", s.freeLensDragEnabled)
        put("lensRefractionStrength", s.lensRefractionStrength.toDouble())
        put("languageCode", s.languageCode)
        put("autoStartOnBluetooth", s.autoStartOnBluetooth)
        put("startOnBoot", s.startOnBoot)
        put("rememberLastLevel", s.rememberLastLevel)
        put("floatingButtonEnabled", s.floatingButtonEnabled)
        put("compactNotification", s.compactNotification)
        put("onboardingCompleted", s.onboardingCompleted)
        put("islandEnabled", s.islandEnabled)
        put("islandRoutingPreference", s.islandRoutingPreference.name)
        put("islandStyle", s.islandStyle.name)
        put("islandColorPreset", s.islandColorPreset.name)
        put("islandOnlyWhenPlaying", s.islandOnlyWhenPlaying)
        put("islandAllowLockscreenActions", s.islandAllowLockscreenActions)
        put("islandShizukuBypassEnabled", s.islandShizukuBypassEnabled)
        put("lastActiveIslandTier", s.lastActiveIslandTier)
        put("lastIslandError", s.lastIslandError)
        put("osOverrideOption", s.osOverrideOption.name)
        put("cachedDeviceProfileJson", s.cachedDeviceProfileJson)
        put("lastScannedAppVersion", s.lastScannedAppVersion)
        put("lastSystemBuildSignature", s.lastSystemBuildSignature)
    }.toString()

    private fun parseSettingsJson(raw: String): GlobalSettings = runCatching {
        val o = JSONObject(raw)
        val bandsArr = o.optJSONArray("customBandsMb")
        val bands = if (bandsArr != null && bandsArr.length() > 0) {
            List(bandsArr.length()) { bandsArr.optInt(it, 0).coerceIn(-1500, 1500) }
        } else listOf(0, 0, 0, 0, 0)

        GlobalSettings(
            unlimitedBoostEnabled = o.optBoolean("unlimitedBoostEnabled", false),
            defaultBoostMode = runCatching { BoostMode.valueOf(o.optString("defaultBoostMode", "STANDARD")) }.getOrDefault(BoostMode.STANDARD),
            limiterMode = runCatching { LimiterMode.valueOf(o.optString("limiterMode", "OFF")) }.getOrDefault(LimiterMode.OFF),
            limiterThresholdDb = o.optDouble("limiterThresholdDb", -1.0).toFloat().coerceIn(-15f, 0f),
            attackMs = o.optInt("attackMs", 5).coerceIn(1, 200),
            releaseMs = o.optInt("releaseMs", 60).coerceIn(10, 1000),
            fadeDurationMs = o.optInt("fadeDurationMs", 250).coerceIn(20, 2000),
            activeEqPreset = o.optString("activeEqPreset", EqPreset.FLAT.name),
            customBandsMb = bands,
            bassBoostStrength = o.optInt("bassBoostStrength", 0).coerceIn(0, 1000),
            virtualizerStrength = o.optInt("virtualizerStrength", 0).coerceIn(0, 1000),
            balanceLeftRight = o.optDouble("balanceLeftRight", 0.0).toFloat().coerceIn(-1f, 1f),
            monoEnabled = o.optBoolean("monoEnabled", false),
            themeMode = runCatching { AppThemeMode.valueOf(o.optString("themeMode", "DARK")) }.getOrDefault(AppThemeMode.DARK),
            dynamicMaterialYou = o.optBoolean("dynamicMaterialYou", false),
            accentColorHex = o.optLong("accentColorHex", 0xFF00F5D4),
            blurIntensityDp = o.optDouble("blurIntensityDp", 60.0).toFloat().coerceIn(10f, 100f),
            glassAlpha = o.optDouble("glassAlpha", 0.16).toFloat().coerceIn(0.05f, 0.50f),
            bgAnimationSpeed = o.optDouble("bgAnimationSpeed", 1.0).toFloat().coerceIn(0.2f, 3.0f),
            parallaxEnabled = o.optBoolean("parallaxEnabled", true),
            powerSaveGlass = o.optBoolean("powerSaveGlass", false),
            freeLensDragEnabled = o.optBoolean("freeLensDragEnabled", true),
            lensRefractionStrength = o.optDouble("lensRefractionStrength", 0.65).toFloat().coerceIn(0f, 1f),
            languageCode = o.optString("languageCode", "system").let { if (it in listOf("system", "ru", "en")) it else "system" },
            autoStartOnBluetooth = o.optBoolean("autoStartOnBluetooth", true),
            startOnBoot = o.optBoolean("startOnBoot", false),
            rememberLastLevel = o.optBoolean("rememberLastLevel", true),
            floatingButtonEnabled = o.optBoolean("floatingButtonEnabled", false),
            compactNotification = o.optBoolean("compactNotification", false),
            onboardingCompleted = o.optBoolean("onboardingCompleted", false),
            islandEnabled = o.optBoolean("islandEnabled", true),
            islandRoutingPreference = runCatching { IslandRoutingPreference.valueOf(o.optString("islandRoutingPreference", "AUTO")) }
                .getOrDefault(IslandRoutingPreference.AUTO),
            islandStyle = runCatching { IslandStyleMode.valueOf(o.optString("islandStyle", "COMPACT_ICON_LEVEL")) }
                .getOrDefault(IslandStyleMode.COMPACT_ICON_LEVEL),
            islandColorPreset = runCatching { IslandColorPreset.valueOf(o.optString("islandColorPreset", "DYNAMIC_BY_LEVEL")) }
                .getOrDefault(IslandColorPreset.DYNAMIC_BY_LEVEL),
            islandOnlyWhenPlaying = o.optBoolean("islandOnlyWhenPlaying", false),
            islandAllowLockscreenActions = o.optBoolean("islandAllowLockscreenActions", false),
            islandShizukuBypassEnabled = o.optBoolean("islandShizukuBypassEnabled", false),
            lastActiveIslandTier = o.optString("lastActiveIslandTier", "SuperIslandPresenter"),
            lastIslandError = o.optString("lastIslandError", "Нет ошибок"),
            osOverrideOption = runCatching { OsOverrideOption.valueOf(o.optString("osOverrideOption", "AUTO")) }
                .getOrDefault(OsOverrideOption.AUTO),
            cachedDeviceProfileJson = o.optString("cachedDeviceProfileJson", ""),
            lastScannedAppVersion = o.optInt("lastScannedAppVersion", 0),
            lastSystemBuildSignature = o.optString("lastSystemBuildSignature", "")
        )
    }.getOrDefault(GlobalSettings())

    private fun serializeProfilesJson(list: List<DeviceProfile>): String {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("customName", p.customName)
                put("type", p.type)
                put("boostPercent", p.boostPercent.coerceIn(0, BoostScaleMapper.UNLIMITED_TECH_MAX_PERCENT))
                put("eqPresetName", p.eqPresetName)
                put("limiterMode", p.limiterMode.name)
                put("autoStartOnConnect", p.autoStartOnConnect)
                put("isPinned", p.isPinned)
                put("lastSeenTimestamp", p.lastSeenTimestamp)
            })
        }
        return arr.toString()
    }

    private fun parseProfilesJson(raw: String): List<DeviceProfile> = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            DeviceProfile(
                id = o.getString("id"),
                customName = o.getString("customName").take(45),
                type = o.optString("type", "SPEAKER"),
                boostPercent = o.optInt("boostPercent", 100).coerceIn(0, BoostScaleMapper.UNLIMITED_TECH_MAX_PERCENT),
                eqPresetName = o.optString("eqPresetName", EqPreset.FLAT.name),
                limiterMode = runCatching { LimiterMode.valueOf(o.optString("limiterMode", "OFF")) }.getOrDefault(LimiterMode.OFF),
                autoStartOnConnect = o.optBoolean("autoStartOnConnect", false),
                isPinned = o.optBoolean("isPinned", false),
                lastSeenTimestamp = o.optLong("lastSeenTimestamp", System.currentTimeMillis())
            )
        }
    }.getOrDefault(defaultProfiles())

    private fun serializeCustomEqJson(list: List<CustomEqPreset>): String {
        val arr = JSONArray()
        list.forEach { eq ->
            arr.put(JSONObject().apply {
                put("name", eq.name)
                put("bands", JSONArray(eq.bandLevelsMillibels))
            })
        }
        return arr.toString()
    }

    private fun parseCustomEqJson(raw: String): List<CustomEqPreset> = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val bArr = o.getJSONArray("bands")
            CustomEqPreset(
                name = o.getString("name").take(30),
                bandLevelsMillibels = List(bArr.length()) { bArr.getInt(it).coerceIn(-1500, 1500) }
            )
        }
    }.getOrDefault(emptyList())
}
