# WordCraft Android

> **The Open-Source Word Processor for Android** — Clean-room reimplementation of Microsoft Word rebuilt in Rust & WebAssembly.

Porting & Packaging dari upstream [storytold/wordcraft](https://github.com/storytold/wordcraft) untuk perangkat Android. Berjalan 100% offline di sisi klien tanpa ketergantungan server eksternal, telemetry, atau akun.

---

## Fitur Utama

- **100% Offline & Private:** Semua pemrosesan dokumen (.docx, .doc, .odt, .rtf, .txt, .md, .html, .pdf) dieksekusi langsung di perangkat Anda via WebAssembly engine.
- **Peralatan Lengkap Dokumen:**
  - Word Processing: Ribbon UI, styles, tables, track changes, references, mail merge, footnotes/endnotes, table of contents.
  - Dukungan Format: Membaca & menulis .docx, .odt, .rtf, .html, .md, .txt; membuka .doc (Word 97-2003); ekspor ke PDF (teks selectable).
  - High Performance: Paragraph layout ter-cache, rendering on-demand via WebGL2/WebGPU canvas hardware acceleration.
- **Android Storage Integration:**
  - Terintegrasi langsung dengan Android Storage Access Framework (SAF) untuk memilih berkas dari memori internal / SD Card / cloud storage.
  - Fitur simpan/ekspor berkas mengalir langsung ke folder publik `Download/` perangkat Android via MediaStore API.

---

## Arsitektur & Teknologi

| Komponen | Spesifikasi / Teknologi |
|---|---|
| **Core Engine** | Pure Rust (`wordcraft-engine`, `wordcraft-doc`, `wordcraft-docx`, `wordcraft-layout`) dikompilasi ke WebAssembly (`wasm32-unknown-unknown`) |
| **GUI Framework** | `egui` / `eframe` (v0.36) via WebGL2/WebGPU canvas |
| **Android Wrapper** | Native Android WebView (`com.galiehneh.wordcraft`) |
| **Asset Interception** | `WebViewClient.shouldInterceptRequest` menyajikan aset lokal offline dengan MIME `application/wasm` dan CORS headers |
| **Download Handler** | JavaScript Blob Bridge ke Android MediaStore (`Environment.DIRECTORY_DOWNLOADS`) |

---

## Spesifikasi Build

- **Application ID:** `com.galiehneh.wordcraft`
- **Version Name:** `2026.10.16`
- **Version Code:** `20261016`
- **Minimum SDK:** Android 7.0 (API 24)
- **Target SDK:** Android 15 (API 35)

---

## Cara Build dari Source

### Prasyarat
- JDK 17
- Android SDK (Platform 35, Build-Tools 35.0.0+)
- Rust + wasm32-unknown-unknown + trunk

### Build APK Release
```bash
./gradlew assembleRelease
```
Berkas APK akan dihasilkan di:
`app/build/outputs/apk/release/app-release.apk`

---

## Lisensi & Atribusi

- Proyek ini dibundel di bawah lisensi [MIT](LICENSE).
- Upstream project: [storytold/wordcraft](https://github.com/storytold/wordcraft) oleh ArtCraft Team (Dual-licensed bawah MIT / Apache 2.0).
