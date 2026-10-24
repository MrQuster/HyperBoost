---
name: android-build-test
description: Сборка, тесты, lint и разбор ошибок сборки Android в репозитории HyperBoost. Используй при любой проверке кода и подготовке релизной сборки.
---

# Android Build & Test Guide

## 1. Назначение
Используется для проверки компиляции Kotlin/Compose, прогона юнит-тестов и lint-анализа проекта HyperBoost.

## 2. Команды запуска
- **Полная проверка проекта**:
  ```bash
  ./gradlew test lint assembleDebug
  ```
- **Только юнит-тесты**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Проверка релизной сборки**:
  ```bash
  ./gradlew assembleRelease
  ```

## 3. Разбор типичных ошибок и аномалий
1. **Несовместимость JDK и Gradle**: проект собран под Java 17. Если в системе выбран JDK 21+ или JDK 11, настройте `JAVA_HOME=/usr/lib/jvm/java-17-openjdk`.
2. **Нехватка памяти Gradle Daemon (OutOfMemoryError)**: настроена опция `org.gradle.jvmargs=-Xmx3072m`. При ошибках памяти проверяйте `free -m` и не запускайте параллельные сборки.
3. **Ошибки зависимостей и кэша**: при странных сбоях компилятора Kotlin используйте `./gradlew --stop` и `./gradlew clean`.

## 4. Чек-лист проверки
- [ ] Все тесты в `app/src/test` завершились успешно (0 failures).
- [ ] Lint-отчёт `app/build/reports/lint-results-debug.html` не содержит фатальных ошибок (Error = 0).
- [ ] Сформирован APK файл `app/build/outputs/apk/debug/app-debug.apk`.
