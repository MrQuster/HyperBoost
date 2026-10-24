package com.example.volumeboost.island

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import com.example.volumeboost.R
import com.example.volumeboost.data.BoostScaleMapper
import com.example.volumeboost.data.IslandStyleMode
import org.json.JSONObject
import kotlin.math.roundToInt

data class IslandSnapshot(
    val sequenceNumber: Long,
    val isRunning: Boolean,
    val isMusicPlaying: Boolean,
    val boostPercent: Int,
    val unlimitedMode: Boolean,
    val deviceName: String,
    val deviceTypeTag: String,
    val modeLabel: String,
    val styleMode: IslandStyleMode,
    val accentHex: String,
    val highlightPulse: Boolean,
    val onlyWhenPlaying: Boolean,
    val isDeviceLocked: Boolean,
    val allowLockscreenActions: Boolean
)

data class ValidatedIslandJson(
    val jsonString: String,
    val sizeBytes: Int,
    val isValid: Boolean,
    val validationNote: String
)

/**
 * Построитель и валидатор JSON "miui.focus.param" для Xiaomi Super Island (HyperIsland).
 * ПРИМЕЧАНИЕ: Точный набор поддерживаемых полей param_island зависит от версии плагина miui.systemui.plugin
 * (при необходимости сверьте с документацией Xiaomi Mi Push -> Focus Notifications).
 */
object MiuiSuperIslandJsonBuilder {

    const val EXTRA_MIUI_FOCUS_PARAM = "miui.focus.param"
    const val EXTRA_MIUI_FOCUS_PICS = "miui.focus.pics"
    const val EXTRA_MIUI_FOCUS_ACTIONS = "miui.focus.actions"
    private const val MAX_JSON_BYTES = 4096

    private const val PIC_KEY_ICON = "miui.focus.pic_boost_icon"
    private const val PIC_KEY_STATE = "miui.focus.pic_boost_state"

    private const val COLOR_ISLAND_BG = "#070B14"
    private const val COLOR_PULSE_HIGHLIGHT = "#FFFFFF"
    private const val COLOR_SUBTEXT_HEX = "#B8C4D9"
    private const val COLOR_IDLE_HEX = "#64748B"

    fun buildAndValidateFocusParamJson(snapshot: IslandSnapshot, context: Context? = null): ValidatedIslandJson {
        return runCatching {
            val fraction = BoostScaleMapper.percentToFraction(snapshot.boostPercent, snapshot.unlimitedMode)
            val progress100 = (fraction * 100f).roundToInt().coerceIn(0, 100)
            val activeColor = when {
                !snapshot.isRunning -> COLOR_IDLE_HEX
                snapshot.highlightPulse -> COLOR_PULSE_HIGHLIGHT
                else -> snapshot.accentHex
            }

            // Ограничиваем длину имени устройства, чтобы избежать обрезки и переполнения буфера острова
            val defaultSpeaker = context?.getString(R.string.dev_type_speaker) ?: "Speaker"
            val safeDeviceName = snapshot.deviceName
                .replace("\n", " ")
                .trim()
                .take(26)
                .ifEmpty { defaultSpeaker }

            val shortLevelText = if (snapshot.styleMode == IslandStyleMode.ICON_ONLY) {
                ""
            } else {
                "${snapshot.boostPercent}%"
            }

            val playStateLabel = when {
                !snapshot.isRunning -> context?.getString(R.string.island_state_off) ?: "OFF"
                snapshot.isMusicPlaying -> context?.getString(R.string.island_state_playing) ?: "PLAYING"
                else -> context?.getString(R.string.island_state_idle) ?: "STANDBY"
            }
            val expandedSubtitle = "$safeDeviceName (${snapshot.deviceTypeTag}) • $playStateLabel"

            val paramIsland = JSONObject().apply {
                put("islandProperty", 1)
                put("islandOrder", true)
                put("enableFloat", false)
                put("reopen", false)
                put("updatable", true)
                put("sequence", snapshot.sequenceNumber)
                put("aodShow", snapshot.allowLockscreenActions)
                put("bgColor", COLOR_ISLAND_BG)
                put("accentColor", activeColor)

                put("bigIslandArea", JSONObject().apply {
                    put("imageTextInfoLeft", JSONObject().apply {
                        put("type", 1)
                        put("pic", PIC_KEY_ICON)
                        put("color", activeColor)
                    })
                    if (snapshot.styleMode == IslandStyleMode.COMPACT_ICON_LEVEL) {
                        put("sameWidthDigitInfo", JSONObject().apply {
                            put("digit", shortLevelText)
                            put("color", activeColor)
                            put("showRingProgress", true)
                            put("progress", progress100)
                        })
                    }
                })

                put("smallIslandArea", JSONObject().apply {
                    put("pic", PIC_KEY_ICON)
                })
            }

            val paramV2 = JSONObject().apply {
                put("protocol", 1)
                put("business", "volume_booster_hyperboost")
                put("template", 1)
                put("ticker", "HyperBoost: ${snapshot.boostPercent}%")
                put("aodTitle", "HyperBoost ${snapshot.boostPercent}%")
                put("aodPic", PIC_KEY_ICON)
                put("enableFloat", false)
                put("updatable", true)
                put("sequence", snapshot.sequenceNumber)

                put("baseInfo", JSONObject().apply {
                    put("type", 2)
                    put("title", "HyperBoost • ${snapshot.boostPercent}%")
                    put("colorTitle", "#FFFFFF")
                    put("content", expandedSubtitle)
                    put("colorContent", COLOR_SUBTEXT_HEX)
                    put("subContent", if (snapshot.isRunning) snapshot.modeLabel else "ВЫКЛ")
                    put("colorSubContent", activeColor)
                    put("bgColor", COLOR_ISLAND_BG)
                })

                put("progressInfo", JSONObject().apply {
                    put("progress", progress100)
                    put("colorProgress", snapshot.accentHex)
                    put("colorProgressEnd", activeColor)
                    put("colorBg", "#26FFFFFF")
                })

                put("chatInfo", JSONObject().apply {
                    put("picProfile", PIC_KEY_ICON)
                    put("title", "HyperBoost • ${snapshot.boostPercent}%")
                    put("colorTitle", "#FFFFFF")
                    put("content", expandedSubtitle)
                    put("colorContent", COLOR_SUBTEXT_HEX)
                })

                put("param_island", paramIsland)
            }

            val rootJson = JSONObject().apply {
                put("param_v2", paramV2)
            }.toString()

            val utf8Bytes = rootJson.toByteArray(Charsets.UTF_8).size
            val hasRequiredKeys = rootJson.contains("\"param_v2\"") &&
                    rootJson.contains("\"param_island\"") &&
                    rootJson.contains("\"baseInfo\"")
            val sizeOk = utf8Bytes in 64..MAX_JSON_BYTES

            ValidatedIslandJson(
                jsonString = rootJson,
                sizeBytes = utf8Bytes,
                isValid = hasRequiredKeys && sizeOk,
                validationNote = if (hasRequiredKeys && sizeOk) "VALID_OK (${utf8Bytes}B)"
                else "INVALID_JSON (size=${utf8Bytes}B)"
            )
        }.getOrElse { e ->
            ValidatedIslandJson(
                jsonString = "{}",
                sizeBytes = 2,
                isValid = false,
                validationNote = "JSON_BUILD_ERROR: ${e.localizedMessage}"
            )
        }
    }

    fun attachMiuiFocusBundles(
        context: Context,
        extras: Bundle,
        snapshot: IslandSnapshot,
        actionIntents: IslandPendingIntents
    ): ValidatedIslandJson {
        val validated = buildAndValidateFocusParamJson(snapshot, context)
        if (validated.isValid) {
            extras.putString(EXTRA_MIUI_FOCUS_PARAM, validated.jsonString)
        }

        val picsBundle = Bundle().apply {
            putParcelable(PIC_KEY_ICON, Icon.createWithResource(context, R.drawable.ic_stat_boost))
            putParcelable(PIC_KEY_STATE, Icon.createWithResource(context, R.drawable.ic_tile_boost))
        }
        extras.putBundle(EXTRA_MIUI_FOCUS_PICS, picsBundle)

        val allowButtonsNow = !snapshot.isDeviceLocked || snapshot.allowLockscreenActions
        if (allowButtonsNow) {
            val actionsList = arrayListOf(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_stat_boost),
                    context.getString(R.string.island_action_minus_10),
                    actionIntents.stepMinus10
                ).build(),
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_stat_boost),
                    context.getString(R.string.island_action_plus_10),
                    actionIntents.stepPlus10
                ).build(),
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_tile_boost),
                    if (snapshot.isRunning) context.getString(R.string.island_action_stop)
                    else context.getString(R.string.island_action_start),
                    actionIntents.togglePower
                ).build()
            )
            extras.putParcelableArrayList(EXTRA_MIUI_FOCUS_ACTIONS, actionsList)
        }
        return validated
    }
}

data class IslandPendingIntents(
    val openApp: PendingIntent,
    val stepMinus10: PendingIntent,
    val stepPlus10: PendingIntent,
    val togglePower: PendingIntent
)
