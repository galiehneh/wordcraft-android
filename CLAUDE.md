# WordCraft Android

Single-activity Android WebView host (`app/src/main/java/com/galiehneh/wordcraft/MainActivity.java`) serving a prebuilt Rust/WASM editor offline.

## Commands
- Debug build: `./gradlew assembleDebug`
- Release build: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
- Lint: `./gradlew lint`
- Tests: none yet (`./gradlew test` once added)

## Notes
- `app/src/main/assets/web/` (wasm/js/html) are prebuilt artifacts from the separate Rust repo. Never hand-edit.
- Version bumps: edit `versionCode` / `versionName` in `app/build.gradle.kts`.
- Release signing reads `signing/release-signing.properties` (gitignored). Never commit keystores or secrets.
