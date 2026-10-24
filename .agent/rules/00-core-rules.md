# HyperBoost Core Rules

## Mandatory Constraints
1. **Immutable Invariants**:
   - Island, notification, tile, widget, and shortcuts must only expose `-10%`, `+10%`, and on/off toggle.
   - Never introduce an upper boost cap or volume/hearing warning dialogues or banners in the app.
   - Five navigation tabs: Home, Equalizer, Devices, Settings, System.
   - Localization: All UI and user-facing strings must come from Android string resources (`values/strings.xml` and `values-ru/strings.xml`). No hardcoded Cyrillic or localized English strings in Kotlin code.
   - Droplet icon, author MrQuster, applicationId `io.github.mrquster.hyperboost`, R8 enabled.

2. **Safety & Secrets**:
   - Work strictly in `chore/agent-audit`.
   - Never print, read, or commit keystore files, `keystore.properties`, `local.properties`, passwords, or tokens.
   - No push, tagging, or GitHub releases without explicit user permission.
   - No destructive git or file commands without prior confirmation.

3. **Performance & Threading**:
   - Zero allocations, locks, or logging inside the high-frequency PCM audio loop.
   - No blocking calls on Dispatchers.Main.
   - All `PendingIntent` instances must specify `PendingIntent.FLAG_IMMUTABLE`.

4. **Verification**:
   - Always run `./gradlew test lint assembleDebug` and verify all tests pass.
   - Always verify localization with `app/src/test/java/com/example/volumeboost/dsp/DspAndLocalizationTest.kt`.
