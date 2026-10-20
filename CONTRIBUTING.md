```markdown
# Contributing to HyperBoost

Bug reports, device compatibility reports, and translations are welcome.

## Reporting device compatibility or bugs
1. Open the **System** tab in the app.
2. Run **Verify Audio Effects** and **Island Self-Test**.
3. Tap **Copy Report** and paste the output into a new GitHub Issue. The report contains only hardware/OS flags and no personal identifiers.

## Adding or fixing translations
- Default English strings are in `app/src/main/res/values/strings.xml`.
- Russian strings are in `app/src/main/res/values-ru/strings.xml`.
- Both files must contain the exact same `<string name="...">` keys. Run `./gradlew test` before submitting a PR — `DspAndLocalizationTest` automatically checks that no keys are missing.

## Commit message style
Use short, imperative commit messages describing one change per commit:
- `fix: route bass boost through DynamicsProcessing when hardware effect is unsupported`
- `feat: add backdrop blur to bottom navigation bar using Haze`
- `docs: update compatibility table in README`
