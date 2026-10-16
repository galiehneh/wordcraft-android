# TASK: Build and Package WordCraft Android APK

## Objective
Build and package `wordcraft-android` (package `com.galiehneh.wordcraft`) based on `storytold/wordcraft` as a native, 100% offline Android APK, following the established `pdfcraft-android` architecture and the `android-app-packaging` skill.

---

## Workspace Layout
- Upstream Web & Rust source: `/root/hermes_user_files/wordcraft-src`
- Android Wrapper Project: `/root/hermes_user_files/wordcraft-android`
- Android SDK: `/opt/android-sdk` (build-tools 35.0.0, platform android-35)
- Signing config: `/root/hermes_user_files/wordcraft-android/signing/`

---

## Detailed Implementation Steps

### Phase 1: Web Engine Adaptation (`/root/hermes_user_files/wordcraft-src`)
1. In `apps/wordcraft-web/Cargo.toml`:
   - Under `[target.'cfg(target_arch = "wasm32")'.dependencies].web-sys`, add `"Response"` and `"UrlSearchParams"` to the `features` list.
2. In `apps/wordcraft-web/src/web.rs`:
   - Implement `query_param(name: &str) -> Option<String>` using `web_sys::UrlSearchParams`.
   - Implement `async fn fetch_bytes(url: &str) -> Result<Vec<u8>, String>` using `window.fetch_with_str(url)`.
   - Inside `start()`, detect if `let file_param = query_param("file")` is present:
     - If present, spawn an async task to `fetch_bytes(&file_url)`, decode the file name, and push `(name, bytes)` into `inbox`.
     - When `file_param` is present, do NOT call `app.start_screen()` so the incoming document loads directly.
3. In `apps/wordcraft-web/index.html`:
   - Add the Blob download interceptor hook (`HTMLAnchorElement.prototype.click` delegating to `window.WordCraftAndroid.saveBlob`).
   - Add `window.handleAndroidBack` dispatching Escape key event.
   - Prevent default on `contextmenu` to avoid desktop right-click collision on touch screens.
4. Run `trunk build --release` in `apps/wordcraft-web/` to produce the compiled WASM and JS dist in `dist/web/`.

---

### Phase 2: Android Native Wrapper (`/root/hermes_user_files/wordcraft-android`)
1. Implement `app/src/main/java/com/galiehneh/wordcraft/MainActivity.java`:
   - Package: `com.galiehneh.wordcraft`
   - Setup WebView with full hardware acceleration, WideViewPort, DOM Storage, and JavaScript enabled.
   - Intercept requests under `appassets.androidplatform.net`:
     - Serve assets from `getAssets().open("web/" + path)` with MIME types mapped correctly (`.wasm` -> `application/wasm`, `.js` -> `text/javascript`, etc.) and CORS headers `Access-Control-Allow-Origin: *`.
     - Intercept `/doc/*` and stream the cached document from `getCacheDir()` with the correct document MIME type.
   - Implement SAF file picker (`onShowFileChooser`) supporting `.docx`, `.doc`, `.odt`, `.rtf`, `.txt`, `.md`, `.html`, `.pdf`.
   - Implement floating action button (FAB) "Buka Dokumen" with WordCraft blue theme (`#185ABD`), rounded corners, elevation, and haptic feedback.
   - Implement System Navigation Bar insets listener to ensure bottom controls are never submerged behind gesture bars.
   - Handle incoming intents (`ACTION_VIEW`, `ACTION_SEND`): copy stream to cache and navigate WebView with `?file=/doc/<encoded_name>`.
   - Implement `WordCraftAndroid` JavascriptInterface with `saveBlob(String dataUrl, String filename)` saving to `MediaStore.Downloads` (Android 10+) or public Downloads directory.
   - Handle hardware Back button: dismiss modals via `handleAndroidBack`, reset to Home/start screen, or exit.
2. Assets Synchronization:
   - Copy `wordcraft-src/dist/web/*` into `wordcraft-android/app/src/main/assets/web/`.
   - Ensure `wordcraft-android/app/src/main/assets/web/index.html` has the bridge scripts.

---

### Phase 3: Compilation and Signature Verification
1. Run `./gradlew assembleRelease` in `/root/hermes_user_files/wordcraft-android`.
2. Verify signature with `/opt/android-sdk/build-tools/35.0.0/apksigner verify -v app/build/outputs/apk/release/app-release.apk`.
3. Check APK badging with `/opt/android-sdk/build-tools/35.0.0/aapt2 dump badging app/build/outputs/apk/release/app-release.apk`.
4. Copy final release APK to `/root/hermes_user_files/wordcraft-v2026.10.16-release.apk`.
