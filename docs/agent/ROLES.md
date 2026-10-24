# Subagent Roles & Team Orchestration

В проекте HyperBoost используется ролевая модель разработки и контроля качества. Роли могут выполняться параллельно через Agent Manager / суб-агенты Antigravity, соблюдая изоляцию зон ответственности и обязательное независимое ревью.

## 1. Роли и зоны ответственности

| Роль | Зона ответственности | Доступ к файлам | Основной скилл |
| :--- | :--- | :--- | :--- |
| **Navigator** | Архитектура проекта, зависимости, карта модулей, Gradle настройки | Только чтение | `android-build-test` |
| **Audio Engineer** | Аудио-тракт, LoudnessEnhancer, DynamicsProcessing, Biquad Low-Shelf, Mid-Side стерео, лимитер | `dsp/*`, `service/AudioBoosterService.kt` | `audio-effects-check` |
| **UI Reviewer** | Compose экраны, Liquid Glass, AGSL шейдер линзы, волна, адаптивность, доступность | `ui/*`, `MainActivity.kt` | `glass-ui-review` |
| **Island Engineer** | Xiaomi Super Island (HyperOS), AOSP Live Updates, обычные уведомления, QS Tile, Widget | `island/*`, `service/BoosterTileService.kt`, `widget/*` | `island-debug` |
| **i18n and Docs** | Локализация RU/EN, исключение захардкоженных строк, README, CHANGELOG | `res/values*`, `docs/*`, `README*` | `i18n-check` |
| **Build and Release Engineer** | Gradle, R8/Proguard, подпись APK, GitHub Actions CI/CD workflows | `*.gradle.kts`, `proguard-rules.pro`, `.github/*` | `release-github` |
| **Perf Profiler** | Замеры времени старта, FPS/jank, памяти, батареи, оптимизация фаз Compose | Код и замеры через ADB | `perf-measure` |
| **Independent Reviewer** | Сквозное независимое ревью КАЖДОГО патча перед коммитом в `chore/agent-audit` | Все файлы (ревью diff) | `code-review` |

## 2. Правила оркестрации и изоляции
1. **Параллельное чтение**: роли аудита (анализ кода, поиск дефектов) могут работать параллельно без ограничений.
2. **Последовательное внесение правок**: две роли не должны редактировать один и тот же файл одновременно во избежание конфликтов слияния.
3. **Обязательное независимое ревью**: ни одно изменение не коммитится без подтверждения роли `Independent Reviewer` и успешного прохождения `./gradlew test lint assembleDebug`.
4. **Сводка результатов**: Tech Lead сводит результаты всех ролей в единый аудит-отчёт `docs/agent/AUDIT_REPORT.md`.
