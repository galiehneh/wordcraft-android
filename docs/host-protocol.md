# WordCraft Host Protocol

Kontrak antara Android host (Java) dan WASM editor (Rust). Perubahan hanya lewat koordinator.

Origin tunggal: `https://appassets.androidplatform.net`. Semua fitur host aktif hanya jika `window.WordCraftHost` ada (build browser tak berubah).

## JS -> native (`WordCraftHost.postMessage`)

Listener: `WebViewCompat.addWebMessageListener("WordCraftHost", {origin})`.

Pesan teks = JSON `{ "type": ..., ...}`. Pesan biner = ArrayBuffer frame:

```
[u32 LE headerLen][header JSON UTF-8][payload bytes]
```

| type | field header | payload |
|---|---|---|
| `ready` | - | - |
| `state` | `dirty`, `hasDocument`, `editMode`, `backstage`, `dialog` (bool), `name` (string) | - |
| `save` | `ticket` (u32), `kind` (`save`\|`saveAs`\|`export`), `name`, `mime` | file bytes |
| `snapshot` | `name`, `rev` (u64) | docx bytes |
| `print` | `name` (nama dokumen tanpa ekstensi Word; host menambah `.pdf`) | PDF bytes |
| `ime` | `visible` (bool) | - |
| `pickOpen` | - | - |
| `openExternal` | `url` | - |
| `backResult` | `handled` (bool) | - |

Fallback base64 (bila `WEB_MESSAGE_ARRAY_BUFFER` tak didukung): pesan teks JSON `{"type":"chunk","id":<u32>,"index":<n>,"chunks":<n>,"header":{...pesan asli},"data":"<base64, maks 512 KB mentah>"}`; host merakit per `id`, lalu memperlakukan sebagai pesan biner bertipe `header.type`.

Aturan angka: semua bilangan di JSON <= 2^53. `ticket` u32. `rev` number (counter naik monoton; dikirim di `state` dan `snapshot`). `state` memuat `rev` dan `name` terbaru; `wordcraft_snapshot_now` hanya mengembalikan bytes, header `snapshot` memakai `name`/`rev` dari `state` terakhir.

## native -> JS

`replyProxy.postMessage(json)` -> `window.wordcraftHostEvent(msg)` -> export wasm. Kunci pembeda event selalu `type`. Event tak dikenal -> `console.warn`. Dispatcher mengantre event sampai `window.wasmBindings` ada; `ready` diposting dari JS setelah `TrunkApplicationStarted`.

| event | field | efek |
|---|---|---|
| `open` | `url` (`/__host/doc/<token>`), `name`, `recovered` (bool; true = restore dari snapshot, editor menandai dirty) | `wordcraft_open_url(url, name, recovered?)` |
| `saveResult` | `ticket`, `outcome` (`ok`\|`cancelled`\|`failed`), `message`, `name?` (nama file yang benar-benar ditulis; dipakai editor hanya untuk `saveAs` ok) | `wordcraft_save_result(ticket, outcome, msg, name?)` |
| `back` | - | `wordcraft_back() -> bool`, JS balas `backResult` |
| `snapshotNow` | - | `wordcraft_snapshot_now() -> Option<Uint8Array>`, JS kirim `snapshot` |

## Bytes dokumen native -> JS

`GET /__host/doc/<token>` via `WebViewAssetLoader` PathHandler, stream dari `ContentResolver` off main thread. Token sekali pakai.

## Sync kecil

`WordCraftSync` (`@JavascriptInterface`): `getClipboardText()`, `getClipboardHtml()`. Navigasi dikunci ke origin di atas.

## Export wasm (Trunk global `window.wasmBindings`)

`wordcraft_open_url(url, name, recovered?)`, `wordcraft_save_result(ticket, outcome, msg, name?)`, `wordcraft_back() -> bool`, `wordcraft_snapshot_now() -> Option<Uint8Array>`.

## Aturan save (kind)

- `save`: hanya nama `.docx`; boleh ditulis di tempat ke URI sumber bila writable dan nama sama.
- `saveAs`: selalu lewat CreateDocument; editor menolak `save`/`saveAs` kedua selama `saveAs` pertama belum dijawab. Jawab dengan `name` bila nama final berbeda.
- `export`: semua nama non-docx (pdf/png/odt/rtf/txt/...). Tidak pernah menimpa URI sumber dan tidak mengubah dokumen aktif; boleh bersamaan.
- `WordCraftSync.getClipboardText/Html` mengembalikan `""` bila halaman bukan origin app atau teks > 5M karakter.
