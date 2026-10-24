---
description: Замеры метрик производительности приложения (запуск, кадры, память, размер APK)
---

# Workflow: /measure

## Шаги выполнения:
1. Замерить размер APK (debug и release):
   ```bash
   ls -lh app/build/outputs/apk/*/*/*.apk 2>/dev/null || true
   ```
2. Проверить доступность adb:
   ```bash
   adb devices
   ```
3. Если устройство подключено:
   - Замерить время холодного старта: `adb shell am start -W -n io.github.mrquster.hyperboost/com.example.volumeboost.MainActivity`
   - Замерить память: `adb shell dumpsys meminfo io.github.mrquster.hyperboost`
   - Замерить пропуск кадров: `adb shell dumpsys gfxinfo io.github.mrquster.hyperboost framestats`
4. Если устройства нет, зафиксировать метрики как «не измерено (нет устройства)» и сформировать команды для пользователя.

## Условие остановки:
Нет.

## Формат итога:
Сравнительная таблица метрик «До» / «После» с пометкой измеренных и оценочных величин.
