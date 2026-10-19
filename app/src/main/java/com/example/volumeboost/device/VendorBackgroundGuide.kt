package com.example.volumeboost.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

enum class VendorSetupAction {
    OPEN_NOTIFICATIONS_AND_ISLAND,
    IGNORE_BATTERY_OPTIMIZATION,
    OPEN_VENDOR_AUTOSTART,
    OPEN_APP_DETAILS,
    OPEN_DEVELOPER_OPTIONS
}

data class VendorGuideStep(
    val stepTitleRu: String,
    val stepTitleEn: String,
    val descriptionRu: String,
    val descriptionEn: String,
    val actionType: VendorSetupAction
)

object VendorBackgroundGuide {

    fun getTitleForFamily(family: OsFamily, isEn: Boolean): String {
        return if (isEn) {
            "Background & System Setup (${family.displayNameEn})"
        } else {
            "Мастер настройки фона (${family.displayNameRu})"
        }
    }

    fun getStepsForProfile(profile: DeviceProfile): List<VendorGuideStep> {
        return when (profile.osFamily) {
            OsFamily.HYPER_OS, OsFamily.MIUI -> listOf(
                VendorGuideStep(
                    stepTitleRu = "Шаг 1. Включить «Суперостров / Фокусные уведомления»",
                    stepTitleEn = "Step 1. Enable Super Island / Focus Notifications",
                    descriptionRu = "В открывшемся окне уведомлений HyperOS активируйте тумблер «Фокусные уведомления» и показ на экране блокировки.",
                    descriptionEn = "Enable Focus Notifications and Lock Screen visibility in HyperOS settings.",
                    actionType = VendorSetupAction.OPEN_NOTIFICATIONS_AND_ISLAND
                ),
                VendorGuideStep(
                    stepTitleRu = "Шаг 2. Разрешить Автозапуск в HyperOS Security",
                    stepTitleEn = "Step 2. Allow Autostart in HyperOS Security",
                    descriptionRu = "Позволяет аудиосервису автоматически восстанавливаться при подключении Bluetooth-наушников.",
                    descriptionEn = "Allows the audio service to recover automatically when Bluetooth connects.",
                    actionType = VendorSetupAction.OPEN_VENDOR_AUTOSTART
                ),
                VendorGuideStep(
                    stepTitleRu = "Шаг 3. Контроль активности → «Нет ограничений»",
                    stepTitleEn = "Step 3. Battery Saver -> No Restrictions",
                    descriptionRu = "Отключает выгрузку усилителя системным контроллером питания Xiaomi при выключенном экране.",
                    descriptionEn = "Prevents Xiaomi power controller from stopping the booster when screen is off.",
                    actionType = VendorSetupAction.IGNORE_BATTERY_OPTIMIZATION
                )
            )

            OsFamily.ONE_UI -> listOf(
                VendorGuideStep(
                    stepTitleRu = "Шаг 1. Батарея → «Не ограничено» (Unrestricted)",
                    stepTitleEn = "Step 1. Battery -> Unrestricted",
                    descriptionRu = "В настройках приложения Samsung выберите раздел «Батарея» и установите режим «Не ограничено».",
                    descriptionEn = "Open App Info -> Battery and select Unrestricted.",
                    actionType = VendorSetupAction.IGNORE_BATTERY_OPTIMIZATION
                ),
                VendorGuideStep(
                    stepTitleRu = "Шаг 2. Исключить из «Спящих приложений» (Device Care)",
                    stepTitleEn = "Step 2. Exclude from Sleeping Apps (Device Care)",
                    descriptionRu = "Добавьте HyperBoost в список «Приложения, которые никогда не переходят в спящий режим».",
                    descriptionEn = "Add HyperBoost to 'Never auto sleeping apps' in Samsung Device Care.",
                    actionType = VendorSetupAction.OPEN_VENDOR_AUTOSTART
                ),
                VendorGuideStep(
                    stepTitleRu = "Шаг 3. Разрешить уведомления в реальном времени",
                    stepTitleEn = "Step 3. Enable Live Notifications",
                    descriptionRu = "Проверьте, что уведомления службы включены для отображения в строке состояния One UI.",
                    descriptionEn = "Ensure notifications are enabled for One UI status bar chip.",
                    actionType = VendorSetupAction.OPEN_NOTIFICATIONS_AND_ISLAND
                )
            )

            OsFamily.OXYGEN_OS, OsFamily.COLOR_OS, OsFamily.REALME_UI -> listOf(
                VendorGuideStep(
                    stepTitleRu = "Шаг 1. Разрешить работу в фоновом режиме",
                    stepTitleEn = "Step 1. Allow Background Activity",
                    descriptionRu = "В разделе «Использование батареи» включите «Разрешить фоновую активность» и «Разрешить автозапуск».",
                    descriptionEn = "In Battery Usage enable 'Allow background activity' and 'Allow auto-launch'.",
                    actionType = VendorSetupAction.OPEN_VENDOR_AUTOSTART
                ),
                VendorGuideStep(
                    stepTitleRu = "Шаг 2. Отключить оптимизацию батареи",
                    stepTitleEn = "Step 2. Disable Battery Optimization",
                    descriptionRu = "Запрещает системе останавливать аудиопоток при блокировке экрана.",
                    descriptionEn = "Prevents the system from stopping audio processing when locked.",
                    actionType = VendorSetupAction.IGNORE_BATTERY_OPTIMIZATION
                )
            )

            OsFamily.ORIGIN_FUNTOUCH_OS -> listOf(
                VendorGuideStep(
                    stepTitleRu = "Шаг 1. Разрешить высокое фоновое энергопотребление",
                    stepTitleEn = "Step 1. Allow High Background Power Usage",
                    descriptionRu = "Откройте менеджер vivo iManager и разрешите работу HyperBoost в фоне.",
                    descriptionEn = "Open vivo permissions manager and allow background power usage.",
                    actionType = VendorSetupAction.OPEN_VENDOR_AUTOSTART
                ),
                VendorGuideStep(
                    stepTitleRu = "Шаг 2. Отключить оптимизацию батареи",
                    stepTitleEn = "Step 2. Disable Battery Optimization",
                    descriptionRu = "Снимает системный лимит фоновой службы.",
                    descriptionEn = "Removes system background service restriction.",
                    actionType = VendorSetupAction.IGNORE_BATTERY_OPTIMIZATION
                )
            )

            OsFamily.MAGIC_OS, OsFamily.EMUI_HARMONY -> listOf(
                VendorGuideStep(
                    stepTitleRu = "Шаг 1. Запуск приложений → «Управление вручную»",
                    stepTitleEn = "Step 1. App Launch -> Manage Manually",
                    descriptionRu = "Выключите автоматическое управление для HyperBoost и оставьте включёнными все 3 тумблера (Автозапуск, Косвенный запуск, Работа в фоне).",
                    descriptionEn = "Disable automatic management and keep Autostart, Secondary launch, and Run in background enabled.",
                    actionType = VendorSetupAction.OPEN_VENDOR_AUTOSTART
                ),
                VendorGuideStep(
                    stepTitleRu = "Шаг 2. Игнорировать оптимизацию батареи",
                    stepTitleEn = "Step 2. Ignore Battery Optimization",
                    descriptionRu = "Разрешите работу приложения без ограничений энергосбережения.",
                    descriptionEn = "Allow unrestricted battery usage.",
                    actionType = VendorSetupAction.IGNORE_BATTERY_OPTIMIZATION
                )
            )

            else -> listOf(
                VendorGuideStep(
                    stepTitleRu = "Шаг 1. Использование батареи → «Без ограничений»",
                    stepTitleEn = "Step 1. Battery Usage -> Unrestricted",
                    descriptionRu = "Разрешите приложению работать в фоновом режиме без ограничений Android.",
                    descriptionEn = "Allow unrestricted background battery usage.",
                    actionType = VendorSetupAction.IGNORE_BATTERY_OPTIMIZATION
                ),
                VendorGuideStep(
                    stepTitleRu = "Шаг 2. Настройки уведомлений и Live Updates",
                    stepTitleEn = "Step 2. Notifications & Live Updates",
                    descriptionRu = "Убедитесь, что показ постоянных уведомлений в статус-баре разрешён.",
                    descriptionEn = "Ensure ongoing status bar notifications are enabled.",
                    actionType = VendorSetupAction.OPEN_NOTIFICATIONS_AND_ISLAND
                )
            )
        }
    }

    /**
     * Безопасный запуск системного экрана с цепочкой запасных Intent'ов под каждого вендора.
     * Гарантированно не выбрасывает ActivityNotFoundException.
     */
    fun executeActionSafely(context: Context, action: VendorSetupAction) {
        val pkg = context.packageName
        when (action) {
            VendorSetupAction.OPEN_NOTIFICATIONS_AND_ISLAND -> {
                val intents = listOf(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                    },
                    appDetailsIntent(pkg)
                )
                launchFirstResolvable(context, intents)
            }

            VendorSetupAction.IGNORE_BATTERY_OPTIMIZATION -> {
                val intents = listOf(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$pkg")
                    },
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    appDetailsIntent(pkg)
                )
                launchFirstResolvable(context, intents)
            }

            VendorSetupAction.OPEN_VENDOR_AUTOSTART -> {
                val vendorCandidates = listOf(
                    // Xiaomi HyperOS / MIUI
                    ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                    ComponentName("com.miui.securitycenter", "com.miui.powercenter.legacypowerrank.PowerDetailActivity"),
                    // Samsung One UI Device Care
                    ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                    ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                    // OnePlus / OPPO / realme (ColorOS / OxygenOS)
                    ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                    ComponentName("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"),
                    // vivo / iQOO
                    ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                    // Honor / Huawei
                    ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.optimize.process.ProtectActivity"),
                    ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                ).map { comp -> Intent().apply { component = comp } }

                launchFirstResolvable(context, vendorCandidates + appDetailsIntent(pkg))
            }

            VendorSetupAction.OPEN_DEVELOPER_OPTIONS -> {
                launchFirstResolvable(
                    context,
                    listOf(
                        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                        Intent(Settings.ACTION_SETTINGS)
                    )
                )
            }

            VendorSetupAction.OPEN_APP_DETAILS -> {
                launchFirstResolvable(context, listOf(appDetailsIntent(pkg), Intent(Settings.ACTION_SETTINGS)))
            }
        }
    }

    private fun appDetailsIntent(pkg: String): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkg, null)
        }
    }

    private fun launchFirstResolvable(context: Context, candidates: List<Intent>) {
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val succeeded = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (succeeded) return
        }
    }
}
