# Выпуск новой версии HyperBoost (Releasing Guide)

1. Обновите список изменений в `CHANGELOG.md`.
2. Убедитесь, что локальные тесты и сборка проходят успешно:
   ```bash
   ./gradlew clean test assembleRelease
