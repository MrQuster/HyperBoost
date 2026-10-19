package com.example.volumeboost.device

import com.example.volumeboost.data.IslandRoutingPreference

enum class AudioEngineStrategy(val titleRu: String, val titleEn: String) {
    SESSION_0_LOUDNESS_AND_DYNAMICS(
        "Системный LoudnessEnhancer + DynamicsProcessing (Session 0)",
        "System LoudnessEnhancer + DynamicsProcessing (Session 0)"
    ),
    MEDIA_PROJECTION_PCM_DSP(
        "Прямой захват потока AudioPlaybackCapture + 64-bit DSP",
        "Direct AudioPlaybackCapture + 64-bit DSP"
    )
}

enum class IslandStrategy(val titleRu: String, val titleEn: String) {
    XIAOMI_SUPER_ISLAND("Xiaomi Super Island (miui.focus.param)", "Xiaomi Super Island (miui.focus.param)"),
    SAMSUNG_NOW_BAR_LIVE("Samsung Now Bar / Live Notification", "Samsung Now Bar / Live Notification"),
    ANDROID_16_LIVE_UPDATE("Android 16+ Live Updates (API 36+)", "Android 16+ Live Updates (API 36+)"),
    CLASSIC_MEDIA_NOTIFICATION("Классическое уведомление со ступенями и ползунком", "Classic Foreground Notification")
}

enum class GlassQualityTier(val titleRu: String, val titleEn: String) {
    FULL_AGSL_SHADER("Полный Liquid Glass (AGSL-шейдер преломления + Blur)", "Full Liquid Glass (AGSL Shader + Blur)"),
    STANDARD_RENDER_BLUR("Стандартное стекло (RenderEffect Blur без AGSL)", "Standard Glass (RenderEffect Blur)"),
    LIGHTWEIGHT_TRANSLUCENT("Облегчённое стекло (высокий FPS / Экономия батареи)", "Lightweight Translucent Glass")
}

data class ResolvedSystemPlan(
    val primaryAudioStrategy: AudioEngineStrategy,
    val fallbackAudioStrategy: AudioEngineStrategy,
    val selectedIslandStrategy: IslandStrategy,
    val islandSelectionReasonRu: String,
    val glassQualityTier: GlassQualityTier,
    val useSideNavigationRail: Boolean,
    val vendorAudioNoteRu: String,
    val vendorAudioNoteEn: String
)

object FeatureRegistry {

    fun resolvePlan(
        profile: DeviceProfile,
        islandPreference: IslandRoutingPreference = IslandRoutingPreference.AUTO,
        powerSaveGlass: Boolean = false
    ): ResolvedSystemPlan {
        val (islandStrategy, reasonRu) = when (islandPreference) {
            IslandRoutingPreference.FORCE_CLASSIC -> {
                IslandStrategy.CLASSIC_MEDIA_NOTIFICATION to "Выбрано вручную: Классическое уведомление"
            }
            IslandRoutingPreference.FORCE_SUPER_ISLAND -> {
                IslandStrategy.XIAOMI_SUPER_ISLAND to "Принудительно включён Xiaomi Super Island"
            }
            IslandRoutingPreference.FORCE_LIVE_UPDATE -> {
                IslandStrategy.ANDROID_16_LIVE_UPDATE to "Принудительно включён Android 16+ Live Update"
            }
            IslandRoutingPreference.AUTO -> {
                when {
                    Capability.XIAOMI_SUPER_ISLAND in profile.capabilities &&
                        profile.confidence != DetectionConfidence.LOW -> {
                        IslandStrategy.XIAOMI_SUPER_ISLAND to
                            "Автовыбор: обнаружен ${profile.osFamily.displayNameRu} (${profile.osVersion})"
                    }
                    Capability.ANDROID_16_LIVE_UPDATES in profile.capabilities -> {
                        IslandStrategy.ANDROID_16_LIVE_UPDATE to
                            "Автовыбор: Android API ${profile.apiLevel} (${profile.apiLevelFullFormatted}) поддерживает Live Updates"
                    }
                    Capability.SAMSUNG_LIVE_NOTIF in profile.capabilities -> {
                        IslandStrategy.SAMSUNG_NOW_BAR_LIVE to
                            "Автовыбор: обнаружен Samsung ${profile.osVersion} (Now Bar / Live Notification)"
                    }
                    else -> {
                        IslandStrategy.CLASSIC_MEDIA_NOTIFICATION to
                            "Автовыбор: универсальное уведомление с кнопками ступеней и ползунком (${profile.osFamily.displayNameRu})"
                    }
                }
            }
        }

        val glassTier = when {
            powerSaveGlass || profile.confidence == DetectionConfidence.LOW -> GlassQualityTier.LIGHTWEIGHT_TRANSLUCENT
            Capability.AGSL_RUNTIME_SHADER in profile.capabilities -> GlassQualityTier.FULL_AGSL_SHADER
            Capability.HARDWARE_RENDER_BLUR in profile.capabilities -> GlassQualityTier.STANDARD_RENDER_BLUR
            else -> GlassQualityTier.LIGHTWEIGHT_TRANSLUCENT
        }

        val useSideRail = profile.formFactor == DeviceFormFactor.FOLDABLE_UNFOLDED ||
                profile.formFactor == DeviceFormFactor.TABLET ||
                profile.smallestScreenWidthDp >= 600

        val (noteRu, noteEn) = when (profile.osFamily) {
            OsFamily.HYPER_OS, OsFamily.MIUI -> {
                "На Xiaomi (${profile.osVersion}) при активном Dolby Atmos глобальная сессия 0 может ограничиваться микшером. Если буст в «Мягком режиме» слабый, переключитесь на «Захват звука»." to
                "On Xiaomi (${profile.osVersion}) Dolby Atmos may limit global session 0. Switch to Audio Capture if soft mode feels weak."
            }
            OsFamily.ONE_UI -> {
                "На Samsung (${profile.osVersion}) встроенный лимитер Dolby Atmos / SoundAlive может сжимать пики выше 150%. Для максимальной мощности используйте режим «Захват звука»." to
                "On Samsung (${profile.osVersion}) Dolby Atmos / SoundAlive may compress peaks. Use Audio Capture for full gain."
            }
            OsFamily.OXYGEN_OS, OsFamily.COLOR_OS, OsFamily.REALME_UI -> {
                "На ${profile.osFamily.displayNameRu} функция OPlus Real Sound / Dirac может перехватывать сессию 0. В случае отсутствия эффекта включите «Захват звука»." to
                "On ${profile.osFamily.displayNameEn} Real Sound / Dirac may intercept session 0. Enable Audio Capture if needed."
            }
            else -> {
                "На ${profile.osFamily.displayNameRu} доступны как системный тракт Session 0 без задержки, так и глубокий 64-битный DSP-захват звука." to
                "Both zero-latency Session 0 and 64-bit DSP Audio Capture are supported on ${profile.osFamily.displayNameEn}."
            }
        }

        return ResolvedSystemPlan(
            primaryAudioStrategy = if (Capability.LOUDNESS_ENHANCER in profile.capabilities || Capability.DYNAMICS_PROCESSING in profile.capabilities) {
                AudioEngineStrategy.SESSION_0_LOUDNESS_AND_DYNAMICS
            } else {
                AudioEngineStrategy.MEDIA_PROJECTION_PCM_DSP
            },
            fallbackAudioStrategy = AudioEngineStrategy.MEDIA_PROJECTION_PCM_DSP,
            selectedIslandStrategy = islandStrategy,
            islandSelectionReasonRu = reasonRu,
            glassQualityTier = glassTier,
            useSideNavigationRail = useSideRail,
            vendorAudioNoteRu = noteRu,
            vendorAudioNoteEn = noteEn
        )
    }
}
