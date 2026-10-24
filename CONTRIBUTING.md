# Contributing to HyperBoost

Thank you for contributing to HyperBoost!

## How to Contribute

1. **Reporting Bugs:**
   - Use the [Bug Report template](.github/ISSUE_TEMPLATE/bug_report.yml).
   - Please include device model, Android / OS version, and the diagnostic report from the **System** tab (**Copy Report** button).

2. **Localization (i18n):**
   - Strings are kept in `app/src/main/res/values/strings.xml` (English) and `app/src/main/res/values-ru/strings.xml` (Russian).
   - Ensure 100% key parity between English and other languages before submitting PRs.

3. **Code Guidelines:**
   - Stack: Kotlin 2.x, Jetpack Compose, MVVM, StateFlow, DataStore.
   - Design Tokens: Use `LocalGlassTokens.current` instead of hardcoded dimension or color magic numbers.
   - Audio Loop: Zero allocations in sample processing loops. No GC pressure or blocking calls.
   - Android IPC: All `PendingIntent` must use `FLAG_IMMUTABLE`.

4. **Testing before PR:**
   ```bash
   ./gradlew test lintDebug assembleDebug
   ```
   All unit tests and lint checks must pass.
