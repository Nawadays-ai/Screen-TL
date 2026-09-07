# Screen-TL — Catatan Proyek

## Tujuan
Screen-TL adalah aplikasi Android untuk menerjemahkan teks yang terlihat di layar menggunakan floating button. Target akhirnya adalah Manual Translation dan Real-Time Translation dengan hasil terjemahan sebagai overlay di atas teks asli aplikasi lain.

## Status Saat Ini
- Screen Capture: berhasil membuat screenshot; cakupan frame terhadap aplikasi target masih perlu diverifikasi lebih luas.
- Floating button: berhasil tampil dan dapat digeser; toggle menu baru diperbaiki dan perlu uji ulang.
- Floating menu: root dinamis; submenu sekarang seharusnya bisa dibuka dan ditutup dengan tap ulang FAB.
- Permission overlay dan MediaProjection: berhasil.
- OCR: ML Kit menghasilkan line + bounding box; metadata layout tambahan sudah diterapkan.
- Google ML Kit Translation: pipeline Manual TL sudah berjalan sampai History dan overlay pada pengujian sebelumnya.
- Translation History: persisten dan service-safe.
- Manual overlay: coordinate space diperbaiki; sizing/fitting terbaru menghindari text stretch; local blur + bounded translation box baru diimplementasikan, belum diuji ulang pengguna.
- Hapus Overlay: tersedia di menu dan hanya ditampilkan ketika overlay Manual TL aktif; belum diuji ulang pengguna.
- Real-Time TL: dasar terbukti bekerja, flicker ditunda.

## Masalah Aktif

### 1. Capture/OCR
Frame full display dan cakupan OCR pada aplikasi target masih perlu diverifikasi. Filter status bar dan UI Screen-TL belum dibuat.

### 2. Translation Overlay
Uji sebelumnya menunjukkan hasil translation tampak seperti layar diperkecil: bagian atas bergeser turun dan bagian bawah bergeser naik. Ini menunjukkan ruang koordinat window overlay tidak sama dengan ruang koordinat frame capture.

Perbaikan coordinate space:
- window overlay dibuat dengan `sourceWidth x sourceHeight`;
- `FLAG_LAYOUT_NO_LIMITS` diganti dengan `FLAG_LAYOUT_IN_SCREEN`;
- Android R+ memakai `setFitInsetsTypes(0)`;
- Android P+ memakai `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`;
- renderer memakai scale langsung tanpa centering/aspect compensation.

Iterasi sizing terbaru berdasarkan screenshot pengguna:
- kalibrasi font diturunkan agar tidak terlalu besar;
- fitting memakai `textSize` uniform, bukan `textScaleX`;
- translation bounding box boleh melebar sampai sekitar 155% lebar source;
- box tetap berpusat terhadap source line dan dibatasi tepi layar;
- local blur patch dari screenshot dipakai untuk menyembunyikan glyph source sambil mempertahankan tekstur background.

True backdrop blur tingkat-window belum digunakan. Implementasi saat ini adalah local source-patch blur.

Status: **kode baru belum diverifikasi build/perangkat setelah iterasi blur + bounding box**.

### 3. Floating Menu / Capture Menu
Uji sebelumnya menunjukkan submenu Screen-TL ikut masuk ke frame Manual TL.

Perbaikan:
- `triggerManualTranslation()` menutup submenu terlebih dahulu;
- capture ditunda 200 ms setelah menu menjadi `GONE`.

Status: **perlu uji ulang perangkat**.

### 4. Floating Button Toggle
Bug touch listener sebelumnya menyebabkan tap kedua membuka kembali menu.

Perbaikan:
- `ACTION_DOWN` hanya merekam posisi;
- tap sederhana diproses pada `ACTION_UP`;
- drag yang melewati threshold menutup submenu.

Status: **perlu uji ulang perangkat**.

## Iterasi Manual TL — 2026-09-07

### Masukan pengguna
Screenshot terbaru menunjukkan ukuran translation sudah lebih dekat, tetapi masih perlu sedikit dirapikan. Pengguna meminta:
1. blur pada area source;
2. bounding box translation yang boleh lebih panjang dari source tetapi tidak terlalu panjang;
3. bentuk font tidak boleh stretch/tertarik.

### Implementasi
`TextLayoutAnalyzer.kt`:
- tetap menghitung glyph height dan source font size;
- membuat local blurred patch dengan downsample/upscale ringan;
- mempertahankan background sampling.

`OcrManager.kt`:
- `DetectedText` sekarang membawa `blurredPatch`;
- patch dibuat sebelum bitmap capture utama dilepas.

`TranslationOverlayView.kt`:
- translation box minimum mengikuti source box;
- box dapat melebar hingga ~155% source width;
- box tetap centered pada source dan clamped ke layar;
- font dikecilkan secara uniform bila translation lebih panjang;
- `textScaleX` dipertahankan `1.0`;
- local blurred patch digambar sebagai background replacement, dengan tint background tipis;
- patch direcycle saat overlay diganti/dihapus.

`FloatingService.kt`:
- ownership patch dipindahkan dari `DetectedText` ke `TranslationOverlayItem` hanya saat translation berhasil;
- failure path membersihkan patch yang belum ditransfer;
- overlay tetap non-touchable dan coordinate-space fix dipertahankan.

### Commit
- `TextLayoutAnalyzer.kt`: `0478c9f26cc42d74c26ea458d829b50afc6945e9`
- `OcrManager.kt`: `a5f01044b1c76bbb8ca315cd54c634b19f02671d`
- `TranslationOverlayView.kt`: `5f26382a76eb919a61103a73c3eb57c1d9e8119a`
- `FloatingService.kt`: `5973de45f8dc6a77bd6cdfe930427348e53d8915`

**Status:** implementasi selesai di repository, tetapi belum boleh dianggap stabil sebelum build Actions dan device test.

## Real-Time TL
Real-Time dasar sudah terbukti pada perangkat pengguna.

Bug aktif: overlay berkedip karena window dihapus sebelum setiap capture. Perbaikan flicker sengaja ditunda sampai Manual TL stabil.

## Perubahan Sebelumnya — 2026-09-07

### Manual Overlay Coordinate Fix
`FloatingService.kt` membuat window overlay pada ukuran pixel frame capture dan menggunakan `FLAG_LAYOUT_IN_SCREEN`, tanpa fit-insets pada Android R+. Tujuannya agar koordinat OCR `(x,y)` dipetakan 1:1 ke layar, bukan ke area window yang lebih kecil/ber-inset.

Commit: `5ebb3bfda9a6474e0de027159ce7b2c6725923a`.

### Manual Overlay Mask
`TranslationOverlayView.kt`:
- padding mask sedikit diperbesar;
- mask memakai warna background lokal dengan alpha 245;
- sudut dibuat sedikit rounded.

Commit: `e6b0bd2fc03a2e55e3335dc2a4121c6155bd6e98`.

### Floating Menu dan Capture Timing
`FloatingService.kt`:
- tap ulang FAB menutup submenu;
- drag menutup submenu setelah gerakan terdeteksi;
- Manual TL menunggu 200 ms setelah menu ditutup sebelum `captureOnce()`.

Commit yang sama: `5ebb3bfda9a6474e0de027159ce7b2c6725923a`.

## Build Verification
Build terakhir yang benar-benar diverifikasi sukses sebelum iterasi blur adalah GitHub Actions run `34112476978` (run #88):
- head commit: `0c0c9d4a6d1e4d27e4b0f5124c391598a19c027e`
- `assembleDebug`: sukses
- artifact: `ScreenTranslator-APK`
- artifact ID: `10014932485`
- SHA-256: `9f5d8e06f18dd1fecaa6c559594978f25c3efe7424158cbf160bb82b80df9458`

Artifact tersebut belum mencakup iterasi blur/bounding-box terbaru.

## Build Automation
`.github/workflows/build.yml` menjalankan build otomatis setiap push ke `main` dengan Gradle 8.2 melalui `gradle/actions/setup-gradle@v6`; `workflow_dispatch` tetap tersedia.

## Prioritas Berikutnya
1. Pastikan GitHub Actions build baru untuk commit terbaru berhasil.
2. Uji APK pada perangkat.
3. Pastikan koordinat overlay tidak lagi membuat layar terlihat mengecil.
4. Pastikan source tertutup oleh blur/mask.
5. Pastikan translation box boleh sedikit lebih panjang tetapi tidak berlebihan.
6. Pastikan font tidak stretch.
7. Uji background flat dan background kompleks.
8. Pastikan menu Screen-TL tidak ikut masuk frame Manual TL.
9. Pastikan tap kedua FAB menutup menu.
10. Uji Hapus Overlay.
11. Jika Manual TL stabil, lanjutkan mode Manual TL baru.
12. Real-Time flicker tetap ditunda.

## Aturan untuk AI Berikutnya
- Baca `README.md`, `AI_README.md`, dan `AI_HANDOFF.md` sebelum perubahan besar.
- Periksa file aktual di branch `main`.
- Manual TL adalah prioritas.
- Jangan mengerjakan Real-Time flicker kecuali diminta.
- Setiap perubahan bermakna harus dicatat.
- Fitur yang belum diuji perangkat diberi status `[~]` / belum teruji.
- Jangan mengklaim build lulus tanpa hasil build nyata.
