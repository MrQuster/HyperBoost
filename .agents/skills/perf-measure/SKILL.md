---
name: perf-measure
description: Измерение производительности HyperBoost (время холодного запуска, пропуск кадров, потребление памяти и батареи, размер APK).
---

# Performance Measurement & Profiling Guide

## 1. Назначение
Проведение замеров производительности и оптимизация ресурсов без догадок.

## 2. Команды замеров (при подключенном устройстве)
- **Холодный запуск**:
  ```bash
  adb shell am force-stop io.github.mrquster.hyperboost
  adb shell am start -W -n io.github.mrquster.hyperboost/com.example.volumeboost.MainActivity
  ```
  *(Фиксировать `TotalTime` и `WaitTime`).*
- **Рендеринг и пропуск кадров (Jank / GfxInfo)**:
  ```bash
  adb shell dumpsys gfxinfo io.github.mrquster.hyperboost reset
  # выполнить скролл экранов и взаимодействие
  adb shell dumpsys gfxinfo io.github.mrquster.hyperboost framestats
  ```
- **Потребление оперативной памяти (PSS / RSS)**:
  ```bash
  adb shell dumpsys meminfo io.github.mrquster.hyperboost
  ```
- **Батарея и фоновая нагрузка**:
  ```bash
  adb shell dumpsys batterystats --reset
  adb shell dumpsys batterystats io.github.mrquster.hyperboost
  ```
- **Размер APK**:
  ```bash
  ls -lh app/build/outputs/apk/*/*/*.apk
  ```

## 3. Правила честной отчётности
1. Чётко разделять, какие метрики были **физически измерены** через ADB на подключённом оборудовании, а какие являются **аналитической оценкой**.
2. При отсутствии устройства adb пометить метрики как «не измерено (нет устройства)» и приложить готовые скрипты.
