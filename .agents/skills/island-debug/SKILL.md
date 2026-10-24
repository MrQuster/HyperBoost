---
name: island-debug
description: Отладка динамического острова Xiaomi Super Island (HyperOS), AOSP Live Updates (Android 16+) и классических уведомлений HyperBoost.
---

# Xiaomi Super Island & Live Notification Debug Guide

## 1. Назначение
Верификация генерации JSON `miui.focus.param`, цепочки фолбэков, атомарности команд `−10%` / `+10%`, дебаунса обновлений и исключения зависших островов.

## 2. Структура Focus Notification
- Поле `miui.focus.param`: валидный JSON размерностью до 4096 байт с параметрами `bigIslandArea`, `smallIslandArea`, `islandProperty = 1`, `updatable = true`, монотонным `sequence`.
- Картинки: передаются через Bundle `miui.focus.pics` с `Icon.createWithResource`.
- Действия: строго три действия — переключатель Вкл/Выкл, `−10%`, `+10%`.

## 3. Фолбэк-цепочка презентеров
1. **Xiaomi Super Island**: при наличии HyperOS / MIUI и поддержки focus notifications.
2. **Android 16+ Live Updates** (`POST_PROMOTED_NOTIFICATIONS`, API 36+): стиль медиа/прогресса со статусом.
3. **Классическое Media/Foreground Notification**: гарантированная работа на всех устройствах Android 10-15.

## 4. Ловушки и правила
- **Потерянные нажатия / гонки**: обновления острова должны проходить через дебаунс (Channel `CONFLATED` или задержка 100-120 мс), чтобы быстрые клики пользователя не затирались старыми стейтами.
- **Зависший остров**: при остановке сервиса обязательно вызывать `notificationManager.cancel(NOTIF_ID)` и посылать финальный статус выключения.
- **PendingIntent**: строго с `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE` и уникальными `requestCode` для каждого действия.

## 5. Чек-лист
- [ ] Кнопки в острове и уведомлении: только «−10%» и «+10%» и компактный вкл/выкл.
- [ ] JSON валидируется и не содержит неэкранированных переносов строк.
- [ ] Логгер `IslandEventLogger` фиксирует все переходы и клики для экрана «Система».
