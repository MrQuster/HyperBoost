---
name: code-review
description: Чек-лист и методология независимого код-ревью изменений в репозитории HyperBoost перед слиянием в ветку chore/agent-audit.
---

# Code Review Checklist for HyperBoost

## 1. Назначение
Независимый аудит предлагаемого git diff на предмет багов, утечек памяти, потокобезопасности и регрессий.

## 2. Чек-лист проверки изменений
1. **Утечки ресурсов и контекста**:
   - Нет ли утечек `Context` / `Activity` внутри синглтонов или static-переменных?
   - Все ли экземпляры `AudioEffect` (`LoudnessEnhancer`, `Equalizer`), `AudioRecord`, `AudioTrack` освобождаются в `onDestroy()`?
2. **Жизненный цикл сервиса**:
   - Вызывается ли `startForeground()` в течение первых 5 секунд после `startForegroundService()`?
   - Корректно ли обрабатывается завершение сервиса через `stopSelf()` и `onTaskRemoved()`?
3. **Потокобезопасность**:
   - Доступ к разделяемым переменным защищён `@Volatile`, `Atomic*` или корутинными мьютексами.
   - Нет ли блокировок на главном потоке?
4. **Android IPC и безопасность**:
   - `PendingIntent` содержит флаг `FLAG_IMMUTABLE`.
   - Защита `BroadcastReceiver` через `exported="false"` или строгие permissions.
5. **Тестирование**:
   - На каждое исправление логики или баг добавлен юнит-тест.
   - Сборка `./gradlew test lint assembleDebug` проходит без ошибок.
