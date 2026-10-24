# HyperBoost — Карта проекта (PROJECT_MAP)

Дата аудита: 2026-10-24
Репозиторий: `io.github.mrquster.hyperboost`
Автор: MrQuster
Ветка аудита: `chore/agent-audit`

## 1. Общие сведения и стек
- **Язык**: Kotlin 2.0.21
- **UI фреймворк**: Jetpack Compose (BOM 2024.10.01, Material 3, Haze 1.1.1, AGSL runtime shaders)
- **Архитектура**: MVVM + StateFlow + Kotlin Coroutines
- **Хранилище**: Jetpack Preferences DataStore 1.1.1
- **Сервис фоновой обработки**: Foreground Service (`mediaPlayback|mediaProjection`)
- **Виджет**: AndroidX Glance 1.1.1 (AppWidget)
- **Сборка**: Gradle 8.9, AGP 8.7.2, JVM 17 (OpenJDK 17 на Arch Linux)
- **Min / Target SDK**: minSdk 29 (Android 10), targetSdk 35 (Android 15), с адаптацией под API 36/37 (HyperOS 4 / Android 16-17)

## 2. Модули и файловая структура
```text
.
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/volumeboost/
│       │   │   ├── MainActivity.kt                  # Главная Activity, темы, навигация
│       │   │   ├── data/
│       │   │   │   └── AppSettings.kt               # Модели данных, enum'ы, DataStore репозиторий
│       │   │   ├── viewmodel/
│       │   │   │   └── AppViewModels.kt             # Home, Eq, Devices, Settings, System ViewModel
│       │   │   ├── service/
│       │   │   │   ├── AudioBoosterService.kt       # Аудио-движок (Session 0 + Capture DSP), FGS
│       │   │   │   ├── BoosterTileService.kt        # Quick Settings Tile
│       │   │   │   └── BootCompletedReceiver.kt     # Автозапуск после перезагрузки
│       │   │   ├── island/
│       │   │   │   ├── IslandPresenter.kt           # Презентеры: Xiaomi Super Island, AOSP Live Update, Classic
│       │   │   │   ├── MiuiSuperIslandJsonBuilder.kt# Формирование "miui.focus.param" JSON
│       │   │   │   ├── IslandActionReceiver.kt      # Прием кликов из острова (-10%, +10%, Toggle)
│       │   │   │   ├── IslandEventLogger.kt         # Кольцевой буфер событий острова
│       │   │   │   └── ShizukuIslandBypassHelper.kt # Поддержка Shizuku для белого списка
│       │   │   ├── dsp/
│       │   │   │   └── SoftwarePcmDspProcessor.kt   # 64-bit Biquad Low-Shelf, Mid-Side stereo, limiter
│       │   │   ├── device/
│       │   │   │   ├── DeviceProfileDetector.kt     # Определение ROM, вендора, API, способностей
│       │   │   │   ├── FeatureRegistry.kt           # Выбор стратегии острова и стекла
│       │   │   │   └── VendorBackgroundGuide.kt     # Диагностика и инструкции для фона вендоров
│       │   │   ├── ui/
│       │   │   │   ├── theme/LiquidGlassTokens.kt   # Дизайн-токены стекла и цветов
│       │   │   │   ├── components/
│       │   │   │   │   ├── LiquidGlassDesignSystem.kt# Базовые компоненты (GlassCard, Button, Slider)
│       │   │   │   │   └── GlassLensTabBar.kt       # Нижняя панель с линзой преломления AGSL
│       │   │   │   └── screens/
│       │   │   │       ├── HomeScreen.kt            # Главная (круг усиления, волна, карточки)
│       │   │   │       ├── EqualizerScreen.kt       # 5-полосный эквалайзер, бас, 3D, баланс
│       │   │   │       ├── DevicesScreen.kt         # Профили по типам аудиовыходов
│       │   │   │       ├── SettingsScreen.kt        # Настройки острова, лимитера, языка, темы
│       │   │   │       ├── SystemScreen.kt          # Диагностика ROM, лог острова, тест эффектов
│       │   │   │       └── OnboardingOverlay.kt     # Стартовое обучение
│       │   │   ├── widget/
│       │   │   │   └── BoosterGlanceWidget.kt       # Glance виджет рабочего стола
│       │   │   └── util/
│       │   │       ├── AppInfo.kt                   # Константы, ссылки, ревизия
│       │   │       └── LocaleHelper.kt              # Переключение локали RU/EN
│       │   └── res/                                 # Ресурсы, drawables, strings (values, values-ru)
│       └── test/                                    # JVM тесты DSP и детектора устройств
└── .github/workflows/
    ├── ci.yml                                       # CI проверка сборки и тестов
    └── release.yml                                  # Сборка и публикация GitHub Release
```

## 3. Точки входа (Android Components)
1. **Activity**: `com.example.volumeboost.MainActivity` (MAIN, LAUNCHER, singleTop, ярлыки shortcuts.xml).
2. **Foreground Service**: `com.example.volumeboost.service.AudioBoosterService` (`foregroundServiceType="mediaPlayback|mediaProjection"`, `stopWithTask="false"`).
3. **Quick Settings Tile**: `com.example.volumeboost.service.BoosterTileService` (`android.service.quicksettings.action.QS_TILE`).
4. **Broadcast Receivers**:
   - `com.example.volumeboost.island.IslandActionReceiver` (actions: `TOGGLE`, `STEP_MINUS`, `STEP_PLUS`).
   - `com.example.volumeboost.service.BootCompletedReceiver` (`BOOT_COMPLETED`).
   - `com.example.volumeboost.widget.BoosterWidgetReceiver` (`APPWIDGET_UPDATE`).

## 4. Зависимости и версии (libs.versions.toml)
- AGP: `8.7.2`
- Kotlin: `2.0.21` (с Compose Compiler plugin)
- AndroidX Core KTX: `1.15.0`
- AndroidX Lifecycle: `2.8.7`
- AndroidX Activity Compose: `1.9.3`
- Compose BOM: `2024.10.01`
- DataStore Preferences: `1.1.1`
- Glance: `1.1.1`
- Haze: `1.1.1`
- ProfileInstaller: `1.4.1`
- JUnit 4: `4.13.2`
- Org.JSON: `20240303`

## 5. Базовое состояние сборки и проверок («точка ДО»)
- `./gradlew --version`: Gradle 8.9, Kotlin 1.9.23/2.0.21, OpenJDK 17.0.21 на Linux 7.2.11-zen1
- `adb devices`: физических устройств не подключено (замеры на устройстве пропускаются, команды задокументированы)
- `./gradlew clean test lint assembleDebug`:
  - **Статус**: BUILD SUCCESSFUL (3m 32s, 79 задач)
  - **Тесты**: 8 пройденных тестов (DspAndLocalizationTest, DeviceProfileDetectorTest)
  - **Ошибки компиляции**: 0
  - **Предупреждения компилятора Kotlin**:
    - `DspAndLocalizationTest.kt:73`: Nullable receiver call `parentFile?.parentFile`
    - `SoftwarePcmDspProcessor.kt:7`: Deprecated `Virtualizer`
    - `GlassLensTabBar.kt:144`: Condition is always true
    - `SettingsScreen.kt:683` / `SystemScreen.kt:266`: Deprecated Icons (нужен `AutoMirrored`)
  - **Lint**:
    - InlinedApi: `AudioDeviceInfo.TYPE_BLE_HEADSET` (API 31, minSdk 29), `FEATURE_SENSOR_HINGE_ANGLE` (API 30)
    - PrivateApi: `SystemProperties` reflection в `DeviceProfileDetector` и `IslandPresenter`
    - UnusedAttribute: `targetCellWidth` / `targetCellHeight` в `booster_widget_info.xml`
  - **Размер debug APK**: 60 MB
