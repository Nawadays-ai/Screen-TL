# Screen-TL

Android screen translator yang dirancang untuk menerjemahkan teks dari aplikasi lain melalui screen capture, OCR, translation, dan overlay.

## Status Saat Ini

| Bagian | Status | Catatan |
|---|---|---|
| Floating button | ✅ | Tampil dan dapat digeser di atas aplikasi lain |
| Floating menu | 🧪 | Posisi dinamis mengikuti FAB; toggle ulang sekarang dirancang untuk menutup menu |
| Overlay permission | ✅ | Berjalan |
| MediaProjection | ⚠️ | Screenshot berhasil dibuat; Android 14+ diarahkan ke full display |
| OCR | ⚠️ | ML Kit menghasilkan line + bounding box; layout analyzer diterapkan |
| Manual Translation | ✅ | Capture → OCR → translation → History → overlay sudah pernah berhasil |
| Translation History | ✅ | Persisten; service-safe dan write menggunakan `commit()` |
| Translation overlay | 🧪 | Coordinate space diperbaiki agar overlay tidak terlihat seperti layar diperkecil; mask lebih solid |
| Hapus Overlay Manual | 🧪 | Tombol disiapkan setelah overlay Manual TL aktif |
| Real-Time Translation | ⏸️ | Dasar bekerja, tetapi flicker ditunda |

## Fokus Saat Ini

Fokus sementara adalah **Manual TL**. Real-Time sengaja ditunda sampai Manual TL stabil dan mode Manual TL baru dapat dirancang.

Target overlay Manual TL:
- hasil terjemahan langsung menimpa area teks sumber;
- source tidak boleh terlihat di bawah hasil terjemahan;
- ukuran teks mengikuti estimasi ukuran font source;
- area mask sedikit diperluas berdasarkan tinggi glyph;
- warna background area diambil dari screenshot di sekitar teks, bukan selalu hitam;
- mask dibuat lebih solid agar source benar-benar tertutup;
- jika translation lebih panjang, font dikecilkan hanya seperlunya;
- tersedia tombol `Hapus Overlay` setelah Manual TL menghasilkan overlay.

## Arsitektur Overlay

Alur Manual TL:
`Screen Capture → ML Kit OCR → TextLayoutAnalyzer → Translation → Overlay Renderer`

`TextLayoutAnalyzer.kt` mengubah line OCR menjadi data layout:
- koordinat mask yang diperluas;
- estimasi tinggi font dari bounding box element OCR;
- estimasi warna background dari piksel di sekitar area teks.

`TranslationOverlayView.kt` memakai data tersebut untuk menutup source dan merender translation dengan ukuran yang mengikuti source.

Window overlay Manual TL sekarang dibuat pada ukuran pixel yang sama dengan frame capture dan menggunakan `FLAG_LAYOUT_IN_SCREEN`, sehingga koordinat OCR tidak lagi dipaksa mengikuti ukuran area window yang berbeda.

## Masalah Aktif

1. Frame MediaProjection masih perlu dipastikan konsisten menangkap aplikasi target.
2. Visual overlay perlu diverifikasi ulang pada perangkat setelah perbaikan coordinate space.
3. Background sampling belum diuji pada gambar/gradient kompleks.
4. Floating menu adaptif dan `Hapus Overlay` perlu diuji ulang pada perangkat.
5. True backdrop blur belum dipakai; mask saat ini dibuat lebih solid. Blur backdrop nyata memerlukan pendekatan rendering/capture yang berbeda dan sengaja belum ditambahkan pada tahap ini.
6. Real-Time masih berkedip dan sengaja ditunda.
7. APK update masih bentrok; dugaan utama tetap perbedaan signing key.

## Roadmap

### Milestone 1 — Stabilkan Manual Translation
- [x] Floating button dan permission dasar.
- [x] MediaProjection dapat membuat screenshot.
- [x] OCR manager dengan bounding box.
- [x] ML Kit Translation manager.
- [~] Verifikasi frame target dan cakupan OCR.
- [x] Manual TL capture → OCR → translation → History → overlay pernah berhasil.
- [ ] Filter status bar/floating button/teks Screen-TL yang tidak relevan.
- [x] Timeout capture 3 detik + watchdog proses 30 detik.
- [x] Capture Manual TL diberi jeda 200 ms setelah menu ditutup agar menu tidak ikut masuk ke frame.

### Milestone 2 — Translation Overlay
- [x] Overlay berdasarkan OCR line.
- [x] Overlay non-touchable.
- [x] TextLayoutAnalyzer font-aware.
- [x] Mask OCR diperluas berdasarkan tinggi glyph.
- [x] Background area diestimasi dari screenshot.
- [x] Ukuran translation mengikuti source dan menyusut jika perlu.
- [x] Window overlay memakai pixel size frame capture + `FLAG_LAYOUT_IN_SCREEN`.
- [x] Mask background dibuat lebih solid.
- [~] Verifikasi visual pada perangkat.
- [ ] Verifikasi berbagai resolusi/orientasi.
- [ ] Penanganan background kompleks.

### Milestone 3 — Floating Menu
- [x] Real-Time / Manual TL / Hapus Overlay / Keluar.
- [x] Container menu dinamis.
- [x] Menu kiri/kanan mengikuti FAB.
- [x] Menu ditempatkan di atas ketika FAB dekat bawah.
- [x] Tap ulang FAB menutup menu yang terbuka.
- [x] Saat drag, menu ditutup setelah gerakan benar-benar terdeteksi.
- [~] Verifikasi beberapa posisi FAB pada perangkat.

### Milestone 4 — Real-Time Translation
- [x] Capture frame berkala tahap pertama.
- [x] OCR + translation loop dasar.
- [x] Update overlay.
- [~] Hilangkan flicker — ditunda.
- [ ] Change detection.
- [ ] Translation cache.
- [ ] Optimasi CPU/baterai.

### Milestone 5 — Translation Engine
- [x] Google ML Kit on-device sebagai baseline.
- [ ] DeepL API.
- [ ] Gemini AI.
- [ ] Pemilihan engine terhubung ke pipeline.

### Milestone 6 — Manual TL Modes
- [ ] Tambahkan mode Manual TL baru setelah overlay saat ini stabil.
- [ ] Dokumentasikan perilaku mode baru sebelum implementasi.

## Perubahan Terbaru — 2026-09-07

### Perbaikan berdasarkan uji perangkat terbaru
Screenshot pengguna menunjukkan tiga masalah utama: koordinat overlay membuat hasil tampak seperti layar diperkecil, menu Screen-TL ikut tertangkap OCR, dan tap ulang FAB tidak menutup submenu.

Perbaikan:
- `FloatingService.kt`: tap ulang FAB sekarang benar-benar toggle buka/tutup; menu ditutup saat drag mulai; Manual TL menunggu 200 ms setelah menu ditutup sebelum mengambil frame.
- `FloatingService.kt`: window overlay dibuat sebesar frame capture dan memakai `FLAG_LAYOUT_IN_SCREEN`, dengan insets dinonaktifkan pada Android R+ serta cutout mode penuh, agar ruang koordinat OCR = ruang koordinat overlay.
- `TranslationOverlayView.kt`: mask source dibuat sedikit lebih luas/solid, tetap memakai warna background lokal, dan font rendering dipertahankan seperti versi sebelumnya.

Commit overlay: `e6b0bd2fc03a2e55e3335dc2a4121c6155bd6e98`.
Commit service: `5ebb3bfda9a6474e0de027159ce7b2c6725923a`.

**Status:** kode sudah diubah, tetapi belum diuji ulang di perangkat pengguna dan belum boleh dianggap stabil secara visual.

### TextLayoutAnalyzer
File: `TextLayoutAnalyzer.kt`.
- estimasi font dari element OCR;
- mask source diperluas;
- background source diambil dari sampling lokal.

Commit: `e0ec39f595f2bb66e09d46d5be469fd7ae6deaab`.

### OCR + Overlay Renderer
`OcrManager.kt` membawa metadata layout sampai `TranslationOverlayView.kt`.

Commit OCR: `28dab21490b73a5c94848971259c9096207cca43`.
Commit renderer sebelumnya: `513e43e1f8c190903f2d1dc539028f0fed9e3e28`.

### Floating Menu + Hapus Overlay
`layout_floating_widget.xml` dan `FloatingService.kt` direvisi agar root WindowManager menyesuaikan menu dan FAB.

Commit layout: `e59a2b3adef5ff5391dcda1a12df8f0d6d19ec5e`.
Commit service sebelumnya: `9dbf8663eaab80f9844c64824964b3e5769e0156`.

### Build Automation + Verification
`.github/workflows/build.yml` menjalankan build otomatis setiap push ke `main`, dengan Gradle 8.2 melalui `gradle/actions/setup-gradle@v6`. `workflow_dispatch` tetap tersedia.

Commit workflow final: `cedddc4ce6bf26f2e1bf7a36ad609cca568854cd`.

Build terakhir yang terverifikasi sukses adalah artifact `ScreenTranslator-APK` untuk commit tersebut. Perubahan terbaru pada overlay/menu belum memiliki hasil build baru yang diverifikasi dalam catatan ini.

## Dokumen Pengembangan
- `PROJECT_NOTES.md` — catatan teknis dan riwayat kerja.
- `AI_HANDOFF.md` — konteks teknis untuk AI berikutnya.
- `AI_README.md` — aturan operasional AI.
