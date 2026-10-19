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

/**
 * Построитель JSON "miui.focus.param" для Xiaomi Super Island (HyperIsland).
 * В раскрытом состоянии выводит устройство вывода, индикатор уровня и строго кнопки "−10%", "+10%" и "Выкл".
 */
object MiuiSuperIslandJsonBuilder {

    const val EXTRA_MIUI_FOCUS_PARAM = "miui.focus.param"
    const val EXTRA_MIUI_FOCUS_PICS = "miui.focus.pics"
    const val EXTRA_MIUI_FOCUS_ACTIONS = "miui.focus.actions"

    private const val PIC_KEY_ICON = "miui.focus.pic_boost_icon"
    private const val PIC_KEY_STATE = "miui.focus.pic_boost_state"

    private const val COLOR_ISLAND_BG = "#080C16"
    private const val COLOR_PULSE_HIGHLIGHT = "#FFFFFF"
    private const val COLOR_SUBTEXT_HEX = "#B8C4D9"
    private const val COLOR_IDLE_HEX = "#64748B"

    fun buildFocusParamJson(snapshot: IslandSnapshot): String {
        val fraction = BoostScaleMapper.percentToFraction(snapshot.boostPercent, snapshot.unlimitedMode)
        val progress100 = (fraction * 100f).roundToInt().coerceIn(0, 100)
        val activeColor = when {
            !snapshot.isRunning -> COLOR_IDLE_HEX
            snapshot.highlightPulse -> COLOR_PULSE_HIGHLIGHT
            else -> snapshot.accentHex
        }

        val shortLevelText = if (snapshot.styleMode == IslandStyleMode.ICON_ONLY) {
            ""
        } else {
            "${snapshot.boostPercent}%"
        }

        val expandedSubtitle = "${snapshot.deviceName} • ${snapshot.deviceTypeTag}"

        val paramIsland = JSONObject().apply {
            put("islandProperty", 1)
            put("islandOrder", true)
            put("enableFloat", false)
            put("reopen", false)
            put("updatable", true)
            put("aodShow", true)
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

            put("baseInfo", JSONObject().apply {
                put("type", 2)
                put("title", "Усиление: ${snapshot.boostPercent}%")
                put("colorTitle", "#FFFFFF")
                put("content", expandedSubtitle)
                put("colorContent", COLOR_SUBTEXT_HEX)
                put("subContent", if (snapshot.isRunning) snapshot.modeLabel else "ПАУЗА")
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

        return JSONObject().apply {
            put("param_v2", paramV2)
        }.toString()
    }

    fun attachMiuiFocusBundles(
        context: Context,
        extras: Bundle,
        snapshot: IslandSnapshot,
        actionIntents: IslandPendingIntents
    ) {
        extras.putString(EXTRA_MIUI_FOCUS_PARAM, buildFocusParamJson(snapshot))

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
    }
}

data class IslandPendingIntents(
    val openApp: PendingIntent,
    val stepMinus10: PendingIntent,
    val stepPlus10: PendingIntent,
    val togglePower: PendingIntent
)
