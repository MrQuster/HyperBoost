---
description: Проверка готовности к релизу без публикации и push
---

# Workflow: /release-check

## Шаги выполнения:
1. Проверить чистоту git-дерева и статус ветки `chore/agent-audit`:
   ```bash
   git status
   ```
2. Проверить компиляцию release APK с R8:
   ```bash
   ./gradlew assembleRelease
   ```
3. Проверить отсутствие секретов в репозитории:
   ```bash
   git check-ignore -v release-key.jks keystore.properties local.properties
   ```
4. Проверить документацию (`README.md`, `README.ru.md`, `CHANGELOG.md`, `docs/RELEASING.md`).
5. Проверить CI workflow файлы (`.github/workflows/ci.yml`, `release.yml`).

## Условие остановки:
Падение сборки `assembleRelease` или наличие незаигноренных секретов.

## Формат итога:
Чек-лист готовности к релизу и список команд для пользователя (ручные шаги создания тега и push).
