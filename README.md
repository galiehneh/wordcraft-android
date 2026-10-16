# WordCraft Android

> **The Open-Source Word Processor for Android** — Clean-room reimplementation of Microsoft Word rebuilt in Rust & WebAssembly.

Porting & Packaging dari upstream [storytold/wordcraft](https://github.com/storytold/wordcraft) untuk perangkat Android. Berjalan 100% offline di sisi klien tanpa ketergantungan server eksternal, telemetry, atau akun.

---

## Fitur Utama

- **100% Offline & Private:** Semua pemrosesan dokumen dijalankan di perangkat lewat mesin WebAssembly. Tanpa server, telemetry, atau akun.
- **Peralatan Dokumen:** Ribbon UI, styles, tables, track changes, references, mail merge, footnotes/endnotes, table of contents.
- **Format:**
  - Buka: `.docx`, `.doc` (Word 97-2003), `.odt`, `.rtf`, `.txt`, `.md`, `.html`. **PDF tidak dapat dibuka** (hanya diekspor).
  - Simpan: `.docx` (di tempat). Ekspor sebagai salinan: PDF (teks selectable), ODT, RTF, TXT, dll.
  - Cetak lewat sistem cetak Android (termasuk "Save as PDF"); berkas diberi nama `<nama dokumen>.pdf`.
- **Pemulihan:** dokumen yang belum disimpan dipulihkan setelah proses dimatikan sistem (snapshot di penyimpanan privat, tidak ikut backup).
- **Tampilan:** mengikuti mode terang/gelap sistem; splash screen mengikuti warna latar editor. Bahasa antarmuka host: Inggris dan Indonesia.

### Alur file (Storage Access Framework)

- **Buka:** File > Open memakai pemilih berkas sistem (SAF), atau buka/bagikan berkas dari aplikasi lain (`content://`). Izin dipertahankan antar-restart bila penyedia mengizinkan.
- **Save:** menimpa berkas sumber di tempat, hanya untuk `.docx` dan hanya bila berkas dapat ditulis dan namanya sama. Jika tidak, Android menampilkan pemilih lokasi.
- **Save As:** selalu lewat pemilih lokasi; dokumen aktif berpindah ke berkas baru.
- **Export:** selalu menulis salinan baru (format non-docx); tidak mengganti berkas sumber dan tidak mengubah dokumen aktif.
- Tidak ada lagi penyimpanan otomatis ke folder `Download/`.

### Tidak tersedia di Android (desktop-only)

Dibanding versi desktop, build web/Android tidak punya: pemulihan lokal bawaan mesin (diganti pemulihan milik host di atas), akses langsung ke sistem berkas dan font sistem (hanya font bawaan dan font yang dipilih lewat pemilih berkas), pembukaan PDF, serta jendela/dialog OS native. Tombol Back diteruskan ke editor lebih dulu sebelum keluar dari aplikasi.

---

## Arsitektur & Teknologi

| Komponen | Spesifikasi / Teknologi |
|---|---|
| **Core Engine** | Pure Rust (`wordcraft-engine`, `wordcraft-doc`, `wordcraft-docx`, `wordcraft-layout`) dikompilasi ke WebAssembly (`wasm32-unknown-unknown`) |
| **GUI Framework** | `egui` / `eframe` (v0.36) via WebGL2/WebGPU canvas |
| **Android Wrapper** | Native Android WebView (`com.galiehneh.wordcraft`) |
| **Asset Interception** | `WebViewClient.shouldInterceptRequest` menyajikan aset lokal offline dengan MIME `application/wasm` dan CORS headers |
| **Host Bridge** | `WebMessageListener` (pesan biner berpotongan) untuk simpan/ekspor/cetak/buka; lihat `docs/host-protocol.md` |

---

## Spesifikasi Build

- **Application ID:** `com.galiehneh.wordcraft`
- **Version Name:** `2026.10.16.5`
- **Version Code:** `20261021`
- **Minimum SDK:** Android 7.0 (API 24)
- **Target SDK:** Android 16 (API 36)

---

## Cara Build dari Source

### Prasyarat
- JDK 17
- Android SDK (Platform 36, Build-Tools 35.0.0+)
- Rust + wasm32-unknown-unknown + trunk (hanya untuk menyinkronkan editor)

### Sinkron editor web (Rust/WASM)

Aset `app/src/main/assets/web/` adalah hasil build dari repo Rust terpisah; jangan diedit manual.
```bash
export PATH="$HOME/.cargo/bin:$PATH"   # cargo, trunk
WORDCRAFT_SRC=/path/ke/wordcraft-src scripts/sync-web.sh
```
Skrip menjalankan `trunk build --release` lalu mengganti isi `assets/web/`.

### Build, lint, test
```bash
./gradlew lint testDebugUnitTest assembleDebug
```

### Build APK Release
Penandatanganan membaca `signing/release-signing.properties` (di-gitignore; jangan commit keystore atau rahasia). Release memakai R8 (minify + shrink resources) dan tanda tangan v2.
```bash
./gradlew assembleRelease
```
Berkas APK akan dihasilkan di:
`app/build/outputs/apk/release/app-release.apk`

---

## Lisensi & Atribusi

- Proyek ini dibundel di bawah lisensi [MIT](LICENSE).
- Upstream project: [storytold/wordcraft](https://github.com/storytold/wordcraft) oleh ArtCraft Team (Dual-licensed bawah MIT / Apache 2.0).
