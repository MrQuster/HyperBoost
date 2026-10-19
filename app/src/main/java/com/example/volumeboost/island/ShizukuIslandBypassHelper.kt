package com.example.volumeboost.island

import android.content.Context
import android.content.pm.PackageManager

/**
 * Опциональный помощник для проверки Shizuku и формирования команд разблокировки
 * фокусных уведомлений на прошивках Xiaomi HyperOS, где включён строгий белый список.
 *
 * ВАЖНО: Это экспериментальный обходной путь. По умолчанию ВЫКЛЮЧЕН.
 * Может перестать работать после обновления SystemUI в будущих версиях HyperOS.
 */
object ShizukuIslandBypassHelper {

    private const val SHIZUKU_MANAGER_PACKAGE = "moe.shizuku.privileged.api"

    fun isShizukuInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(SHIZUKU_MANAGER_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    /**
     * Возвращает точную ADB / Shizuku shell-команду для разрешения фокусных уведомлений
     * и промоушена Live Updates на Android 16/17 + HyperOS.
     */
    fun buildManualAdbCommands(packageName: String): String = buildString {
        appendLine("# 1. Разрешить Live Updates / Promoted Notifications (Android 16/17):")
        appendLine("adb shell cmd appops set $packageName POST_PROMOTED_NOTIFICATIONS allow")
        appendLine("# 2. Разрешить показ фокусных уведомлений в настройках Xiaomi:")
        appendLine("adb shell settings put secure miui_focus_notification_enabled 1")
    }
}
