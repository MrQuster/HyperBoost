---
description: Установка APK на устройство, запуск и снятие диагностического logcat
---

# Workflow: /install-log

## Шаги выполнения:
1. Проверить доступность подключенных adb устройств:
   ```bash
   adb devices -l
   ```
   *Если устройств нет, прервать с сообщением «Устройство не подключено».*
2. Собрать актуальный Debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. Установить приложение на устройство:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. Очистить буфер logcat и запустить приложение:
   ```bash
   adb logcat -c
   adb shell am start -n io.github.mrquster.hyperboost/com.example.volumeboost.MainActivity
   ```
5. Снять логи основных тегов приложения за 5 секунд:
   ```bash
   mkdir -p docs/agent/logs
   adb logcat -d -v time -s HyperBoost HyperBoost/Island AudioBoosterService:D AndroidRuntime:E > docs/agent/logs/install_run.log
   ```

## Условие остановки:
Отсутствие авторизованного устройства или ошибка установки APK.

## Формат итога:
- Статус установки: Успешно / Сбой
- Путь к сохранённому файлу логов
- Наличие фатальных исключений AndroidRuntime в логе
