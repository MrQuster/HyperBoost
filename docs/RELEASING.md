# Выпуск новой версии HyperBoost (Releasing Guide)

Руководство по подготовке, проверке, подписанию и публикации релизных сборок **HyperBoost** (`io.github.mrquster.hyperboost`).

---

## 1. Подготовка к релизу

1. **Актуализация журнала изменений:**
   - Внесите все изменения текущего релиза в [CHANGELOG.md](../CHANGELOG.md).
   - Присвойте секции версию по SemVer (например, `[1.4.0] - YYYY-MM-DD`).

2. **Прогон тестов и проверка сборки:**
   ```bash
   ./gradlew clean test lintDebug assembleRelease
   ```
   Убедитесь, что все unit-тесты (включая проверку соответствия ключей локализации RU/EN и тесты DSP) проходят успешно.

---

## 2. Автоматическая публикация через GitHub Actions

Основной и рекомендуемый способ выпуска релизов:

1. **Секреты репозитория:**
   В настройках репозитория GitHub (`Settings -> Secrets and variables -> Actions`) должны быть настроены секреты:
   - `KEYSTORE_BASE64` — файл `.jks`, закодированный в base64 (`base64 -w 0 release.jks`)
   - `KEYSTORE_PASSWORD` — пароль хранилища ключей
   - `KEY_ALIAS` — алиас ключа
   - `KEY_PASSWORD` — пароль ключа

2. **Создание и отправка тега:**
   > [!IMPORTANT]
   > Теги создаются и отправляются только с разрешения владельца репозитория (@MrQuster).
   ```bash
   git tag -a v1.4.0 -m "Release v1.4.0"
   git push origin v1.4.0
   ```

3. **Результат работы workflow:**
   Workflow `.github/workflows/release.yml`:
   - Декодирует keystore во временный файл раннера.
   - Запускает сборку релизного APK с включённым R8 (`minifyEnabled = true`, `shrinkResources = true`).
   - Проверяет валидность v2/v3 подписи через `apksigner verify`.
   - Генерирует файл контрольной суммы `.sha256`.
   - Удаляет временный файл keystore.
   - Публикует релиз на GitHub Releases с прикреплёнными файлами `HyperBoost-v1.4.0.apk` и `HyperBoost-v1.4.0.apk.sha256`.

---

## 3. Локальная сборка и подпись на Arch Linux

Если требуется собрать и подписать APK локально на рабочей машине с Arch Linux:

1. **Необходимое окружение:**
   ```bash
   # Установка JDK 17 и Android SDK / Platform Tools
   sudo pacman -S jdk17-openjdk android-tools
   archlinux-java set java-17-openjdk
   ```

2. **Настройка файла ключей (keystore.properties):**
   Создайте в корне проекта файл `keystore.properties` (он уже добавлен в `.gitignore` и не попадёт в git):
   ```properties
   storeFile=/абсолютный/путь/к/hyperboost-release.jks
   storePassword=ваш_пароль_хранилища
   keyAlias=hyperboost
   keyPassword=ваш_пароль_ключа
   ```
   *Либо передайте параметры через переменные окружения:*
   ```bash
   export KEYSTORE_FILE="/путь/к/hyperboost-release.jks"
   export KEYSTORE_PASSWORD="пароль"
   export KEY_ALIAS="hyperboost"
   export KEY_PASSWORD="пароль"
   ```

3. **Сборка релизного APK:**
   ```bash
   ./gradlew clean test assembleRelease -PversionName="1.4.0"
   ```
   Собранный файл будет находиться по пути:
   `app/build/outputs/apk/release/app-release.apk`

4. **Проверка подписи и генерация SHA-256:**
   ```bash
   # Проверка подписи apksigner
   apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk

   # Копирование с финальным именем и расчёт хэша
   cp app/build/outputs/apk/release/app-release.apk HyperBoost-v1.4.0.apk
   sha256sum HyperBoost-v1.4.0.apk > HyperBoost-v1.4.0.apk.sha256
   cat HyperBoost-v1.4.0.apk.sha256
   ```

5. **Установка на устройство через adb:**
   ```bash
   adb install -r HyperBoost-v1.4.0.apk
   ```

---

## 4. Правила безопасности

- Никогда не читайте, не выводите в логи и не коммитьте файлы ключей (`*.jks`, `*.keystore`), пароли и токены.
- Файлы `keystore.properties` и `local.properties` должны оставаться в `.gitignore`.
- Не изменяйте конфигурацию подписи в `.github/workflows/` без явного согласования.
