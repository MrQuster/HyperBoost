---
description: Комплексный аудит кодовой базы по специализированным скиллам
---

# Workflow: /audit

## Шаги выполнения:
1. Запустить проверку локализации:
   ```bash
   grep -rnP '[А-Яа-яЁё]' app/src/main/java
   ./gradlew testDebugUnitTest --tests "*.DspAndLocalizationTest.*"
   ```
2. Запустить аудит аудио-эффектов и детектора устройств:
   ```bash
   ./gradlew testDebugUnitTest --tests "*.DeviceProfileDetectorTest.*"
   ```
3. Проверить AndroidManifest и безопасность:
   - Анализ атрибутов `android:exported`
   - Наличие флагов `FLAG_IMMUTABLE` у всех `PendingIntent`
4. Проверить компиляцию и lint:
   ```bash
   ./gradlew lintDebug
   ```
5. Свести результаты в единый отчёт.

## Условие остановки:
Критический дефект, нарушающий основные правила безопасности или ломающий сборку.

## Формат итога:
Таблица обнаруженных замечаний с разбивкой по областям (Звук, Сервис, Остров, UI, i18n, Безопасность).
