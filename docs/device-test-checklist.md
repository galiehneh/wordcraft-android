# Checklist uji device

Build: `./gradlew assembleDebug assembleRelease`. Helper: `scripts/device-test.sh` (install, logcat, kill, push-samples, recovery-ls).
Jalankan `logcat` di terminal terpisah selama uji; lampirkan `wordcraft-logcat.txt` untuk setiap FAIL.
Ideal: satu device Android 15 + satu Android 7-10.

Siapkan: folder berisi `a.docx`, `b.odt`, `c.txt`, satu .docx berpassword → `push-samples <folder>`.

| # | Kasus | Langkah | Diharapkan | Hasil |
|---|---|---|---|---|
| T1 | Startup tanpa izin INTERNET | Install debug, buka app | Editor tampil; tak ada `ERR_`/halaman error. Gagal → kembalikan `<uses-permission android:name="android.permission.INTERNET"/>` | |
| T2 | Back saat dirty | Buka a.docx, ketik, tekan Back | Editor menangani (dialog/backstage). Jika keluar app: task ke background (`moveTaskToBack`), bukan tertutup | |
| T3 | Back saat bersih/tanpa dokumen | Back dari layar awal | App `finish()`; tidak menggantung >4 dtk | |
| T4 | Recovery | Ketik di a.docx → HOME → `kill` → buka app | Dokumen pulih, ditandai dirty; `recovery-ls` menunjukkan snapshot sebelum kill | |
| T5 | Save setelah recovery | Lanjut T4, Save | In-place ke URI asal bila masih writable, jika tidak minta lokasi; recovery terhapus | |
| T6 | Save in-place .docx | Buka a.docx dari app Files, edit, Save | Tanpa dialog; file di Files terbarui; recovery terhapus | |
| T7 | .odt jadi salinan | Buka b.odt, edit, Save | Selalu dialog buat file (.docx); b.odt asli utuh | |
| T8 | Export | Export PDF dan .odt | Dialog lokasi; dokumen aktif tak berubah; sumber tak tertimpa | |
| T9 | Keyboard Android 15 | Ketuk area teks | Editor tidak tertutup keyboard; caret terlihat | |
| T10 | Rotate | Rotasi saat mengetik dengan keyboard | Tak reload; isi tetap | |
| T11 | Dark mode | Toggle tema sistem saat dokumen terbuka | Tema ikut; dokumen tak reload; status bar terbaca | |
| T12 | Print | Print → Simpan sebagai PDF | Dialog print muncul; PDF valid; logcat tanpa `fdsan` | |
| T13 | Intent masuk | VIEW/Share dari app lain: .docx, .txt, octet-stream, file tak didukung | Yang didukung terbuka; yang tidak ditolak dengan toast | |
| T14 | Berpassword | Buka .docx berpassword, edit | `recovery-ls` kosong; Save minta lokasi | |
| T15 | Paste | Salin teks + teks berformat dari app lain, tempel | Keduanya tertempel | |
| T16 | Link eksternal | Ketuk link di dokumen; juga muat dokumen berisi link tanpa ketuk | Browser luar hanya terbuka saat diketuk | |
| T17 | Release (R8) | `install-release`, ulangi T1, T4, T6, T12 | Tanpa crash `ClassNotFound`; bridge hidup | |

Catatan hasil: tulis PASS/FAIL + ringkas gejala; tempel logcat untuk FAIL.
