---
name: release-github
description: Подготовка релиза на GitHub Releases, валидация версии из git тега, подпись APK из переменных окружения, R8 и CI workflows.
---

# GitHub Release & Packaging Guide

## 1. Назначение
Проверка готовности проекта к публикации релиза, контроль целостности GitHub Actions workflows и проверка R8/Proguard.

## 2. Конфигурация релиза
- **Определение версии**:
  - `versionName` извлекается из git-тега через переменную `GITHUB_REF_NAME` или параметр `-PversionName=X.Y.Z`.
  - `versionCode` вычисляется детерминированно: `major * 10000 + minor * 100 + patch`.
- **Подпись**:
  - Секреты берутся исключительно из переменных окружения: `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
  - Запрещено коммитить `keystore.properties` или keystore-файлы в git.
- **R8 / Minification**:
  - Включен в release (`isMinifyEnabled = true`, `isShrinkResources = true`).
  - Правила `app/proguard-rules.pro` обязаны сохранять классы данных, рефлексию системных свойств и эффектов.

## 3. Чек-лист выпуска (до push и создания тега)
- [ ] `README.md` и `README.ru.md` написаны без рекламных штампов, содержат честную таблицу совместимости.
- [ ] `CHANGELOG.md` отражает все изменения текущей версии.
- [ ] Workflows `.github/workflows/ci.yml` и `.github/workflows/release.yml` используют актуальные actions.
- [ ] Сборка `assembleRelease` успешно собирается без ошибок обфускации.
- [ ] Никаких тегов и push без прямого подтверждения пользователя.
