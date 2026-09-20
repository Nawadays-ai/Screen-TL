# Screen-TL

> ## 🤖 AI AGENT — BACA DOKUMEN INI TERLEBIH DAHULU
> Sebelum mengubah build, signing, package ID, APK distribution, atau melanjutkan pekerjaan dari agent sebelumnya, baca **`AI_README.md`**, **`AI_HANDOFF.md`**, dan **`PROJECT_NOTES.md`**. Untuk aturan signing permanen, baca **`SIGNING_SETUP.md`** jika file tersebut tersedia pada branch/default repository. **Jangan membuat Android signing key baru sebagai workaround.**

Android screen translator yang dirancang untuk menerjemahkan teks dari aplikasi lain melalui screen capture, OCR, translation, dan overlay.

## Status Saat Ini

| Bagian | Status | Catatan |
|---|---|---|
| Floating button | 🧪 | Tampil dan dapat digeser; toggle menu baru saja diperbaiki dan perlu uji ulang |
| Floating menu | 🧪 | Posisi dinamis; tap ulang FAB dirancang untuk menutup menu |
| Overlay permission | ✅ | Berjalan |
| MediaProjection | ⚠️ | Screenshot berhasil dibuat; Android 14+ diarahkan ke full display |
| OCR | ⚠️ | ML Kit menghasilkan line + bounding box; layout analyzer diterapkan |
| Manual Translation | ✅ | Capture → OCR → translation → History → overlay sudah pernah berhasil |
| Translation History | ✅ | Persisten; service-safe dan write menggunakan `commit()` |
| Translation overlay | 🧪 | Coordinate space diperbaiki; sizing dan fitting diperbarui; blur lokal + bounding box terkontrol baru ditambahkan dan belum diuji perangkat |
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
- bounding box translation boleh melebar dari source tetapi dibatasi sekitar 155% lebar source;
- bentuk huruf tetap proporsional, tanpa `textScaleX`;
- blur lokal ringan digunakan untuk menyamarkan glyph source sambil mempertahankan tekstur background;
- tersedia tombol `Hapus Overlay` setelah Manual TL menghasilkan overlay.

## Arsitektur Overlay

Alur Manual TL:
`Screen Capture → ML Kit OCR → TextLayoutAnalyzer → Translation → Overlay Renderer`

`TextLayoutAnalyzer.kt` mengubah line OCR menjadi data layout:
- koordinat mask yang diperluas;
- estimasi tinggi font dari bounding box element OCR;
- estimasi warna background dari piksel di sekitar area teks;
- patch blur lokal berukuran kecil untuk menutup source tanpa kotak warna datar.

`TranslationOverlayView.kt` memakai data tersebut untuk menutup source dan merender translation dengan ukuran yang mengikuti source. Bounding box hasil translation dapat melebar secara terbatas agar kalimat Indonesia yang lebih panjang tidak langsung dipaksa terlalu kecil.

Window overlay Manual TL sekarang dibuat pada ukuran pixel yang sama dengan frame capture dan menggunakan `FLAG_LAYOUT_IN_SCREEN`, sehingga koordinat OCR tidak lagi dipaksa mengikuti ukuran area window yang berbeda.

## Masalah Aktif

1. Frame MediaProjection masih perlu dipastikan konsisten menangkap aplikasi target.
2. Visual overlay perlu diverifikasi ulang pada perangkat setelah perubahan sizing terbaru.
3. Blur lokal dan bounding box 155% belum diuji pada background kompleks/gradient.
4. Floating menu adaptif dan `Hapus Overlay` perlu diuji ulang pada perangkat.
5. True backdrop blur tingkat-window belum digunakan; implementasi sekarang adalah **local source-patch blur**, bukan blur langsung terhadap window aplikasi di bawah.
6. Real-Time masih berkedip dan sengaja ditunda.
7. APK update sedang dimigrasikan ke **permanent CI signing key**; aturan AI handoff dan `SIGNING_SETUP.md` menjadi sumber aturan signing.

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
- [x] Bounding box translation boleh melebar secara terkontrol.
- [x] Font fitting memakai ukuran uniform tanpa `textScaleX`.
- [~] Local blur replacement patch — implementasi baru, belum diuji perangkat.
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
- [~] Translation cache — cache persisten LRU untuk hasil sukses telah diimplementasikan; menunggu build CI dan uji perangkat.
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

### Iterasi berikutnya — sizing, bounding box, dan blur — 2026-09-07
Berdasarkan screenshot pengguna berikutnya, ukuran overlay sudah lebih dekat ke target tetapi masih perlu sedikit dirapikan. Pengguna juga meminta:
- blur pada area source;
- bounding box translation yang boleh lebih panjang dari source tetapi tidak berlebihan;
- font tetap natural dan tidak stretch.

Implementasi baru:
- `TextLayoutAnalyzer.kt`: membuat patch blur lokal dari screenshot dengan downsample/upscale ringan dan tetap membawa metadata background/font.
- `OcrManager.kt`: membawa patch blur bersama `DetectedText` sampai proses translation selesai.
- `TranslationOverlayView.kt`: translation bounding box boleh melebar maksimal sekitar `1.55x` lebar source, tetap terpusat pada source, dan font dikecilkan uniform jika diperlukan.
- `TranslationOverlayView.kt`: menghilangkan pemaksaan `textScaleX`; teks tetap proporsional.
- `FloatingService.kt`: meneruskan patch blur ke overlay dan membersihkannya saat gagal/dihapus agar bitmap tidak bocor.

Commit kode terakhir: `5973de45f8dc6a77bd6cdfe930427348e53d8915`.
Commit overlay renderer: `5f26382a76eb919a61103a73c3eb57c1d9e8119a`.
Commit layout/blur preparation: `0478c9f26cc42d74c26ea458d829b50afc6945e9`.

**Status:** implementasi baru belum diuji build/perangkat. Jangan dianggap stabil sebelum GitHub Actions dan uji device selesai.

### Build Verification
Latest build run sebelum iterasi blur: `34112476978` (run #88).

- commit: `0c0c9d4a6d1e4d27e4b0f5124c391598a19c027e`
- result: `success`
- task: `assembleDebug`
- artifact: `ScreenTranslator-APK`
- artifact ID: `10014932485`
- SHA-256: `9f5d8e06f18dd1fecaa6c559594978f25c3efe7424158cbf160bb82b80df9458`

Artifact tersebut **belum** mencakup iterasi blur/bounding-box terbaru.

## Dokumen Pengembangan
- `PROJECT_NOTES.md` — catatan teknis dan riwayat kerja.
- `AI_HANDOFF.md` — konteks teknis untuk AI berikutnya; termasuk aturan permanent signing.
- `AI_README.md` — aturan operasional AI.
- `SIGNING_SETUP.md` — **kontrak permanen Android signing; wajib dibaca jika tersedia pada branch/default repository**.
