---
name: i18n-check
description: Проверка интернационализации и локализации (RU/EN), выявление захардкоженных строк, аудит ресурсов strings.xml и values-ru.
---

# Localization & i18n Review Guide

## 1. Назначение
Обеспечение 100% покрытия строк ресурсами, исключение непереведённого текста, поддержка per-app language switcher на Android 13+.

## 2. Команды проверки
- **Поиск кириллицы в коде Kotlin**:
  ```bash
  grep -rnP '[А-Яа-яЁё]' app/src/main/java
  ```
- **Сравнение набора ключей strings.xml и values-ru**:
  ```bash
  ./gradlew testDebugUnitTest --tests "*.DspAndLocalizationTest.*"
  ```

## 3. Правила проекта
1. Все пользовательские строки интерфейса, диалогов, виджетов и уведомлений обязаны быть определены в `app/src/main/res/values/strings.xml` и переведены в `app/src/main/res/values-ru/strings.xml`.
2. В Kotlin-файлах запрещены строковые литералы на русском языке (кроме отладочных комментариев).
3. Динамические подписи в Foreground Service и уведомлениях должны использовать контекст с актуальной локалью: `LocaleHelper.getLocalizedContext(context, langCode)`.

## 4. Чек-лист
- [ ] Ключи в `values/strings.xml` и `values-ru/strings.xml` идентичны (проверяется unit-тестом).
- [ ] Переключение языка в Настройках немедленно обновляет язык в приложении, виджете и острове HyperOS.
- [ ] XML-манифест содержит `android:localeConfig="@xml/locales_config"`.
