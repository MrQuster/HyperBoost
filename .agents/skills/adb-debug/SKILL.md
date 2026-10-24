---
name: adb-debug
description: Инструкции по отладке HyperBoost на физическом или виртуальном Android-устройстве через ADB (logcat, dumpsys, скриншоты, профилирование).
---

# ADB Debug Guide for HyperBoost

## 1. Назначение
Диагностика сервиса, острова HyperOS, переключения сессий и аудиоподсистемы через ADB.

## 2. Команды запуска
- **Проверка подключенных устройств**:
  ```bash
  adb devices -l
  ```
- **Установка Debug APK**:
  ```bash
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```
- **Запуск главной Activity**:
  ```bash
  adb shell am start -n io.github.mrquster.hyperboost/com.example.volumeboost.MainActivity
  ```
- **Фильтрация логов по тегам**:
  ```bash
  adb logcat -v time -s HyperBoost HyperBoost/Island AudioBoosterService:D AndroidRuntime:E
  ```
- **Диагностика уведомлений и Focus Notification**:
  ```bash
  adb shell dumpsys notification --noredact | grep -A 20 io.github.mrquster.hyperboost
  ```
- **Диагностика аудиомикшера**:
  ```bash
  adb shell dumpsys media.audio_flinger
  ```
- **Снятие скриншота**:
  ```bash
  adb exec-out screencap -p > docs/agent/logs/screen_$(date +%s).png
  ```

## 3. Чек-лист и ловушки
- [ ] Очищать персональные данные (номера, аккаунты) перед сохранением логов в `docs/agent/logs/`.
- [ ] Проверять статус разрешений: `adb shell pm grant io.github.mrquster.hyperboost android.permission.POST_NOTIFICATIONS`.
- [ ] Если `adb devices` пуст — устройство физически не подключено; не блокировать пайплайн, а документировать готовые команды для пользователя.
