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
- Manual overlay: coordinate space diperbaiki agar sama dengan frame capture; mask dibuat lebih solid; kode berhasil di-build, belum diuji ulang pengguna.
- Hapus Overlay: tersedia di menu dan hanya ditampilkan ketika overlay Manual TL aktif; belum diuji ulang pengguna.
- Real-Time TL: dasar terbukti bekerja, flicker ditunda.

## Masalah Aktif

### 1. Capture/OCR
Frame full display dan cakupan OCR pada aplikasi target masih perlu diverifikasi. Filter status bar dan UI Screen-TL belum dibuat.

### 2. Translation Overlay
Uji terbaru menunjukkan hasil translation tampak seperti layar diperkecil: bagian atas bergeser turun dan bagian bawah bergeser naik. Ini menunjukkan ruang koordinat window overlay tidak sama dengan ruang koordinat frame capture.

Perbaikan terbaru:
- window overlay dibuat dengan `sourceWidth x sourceHeight`, bukan `MATCH_PARENT`;
- `FLAG_LAYOUT_NO_LIMITS` diganti dengan `FLAG_LAYOUT_IN_SCREEN`;
- Android R+ memakai `setFitInsetsTypes(0)` agar system-bar insets tidak mengubah ruang koordinat;
- Android P+ memakai `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`;
- renderer tetap memakai scale fallback langsung dari source frame ke ukuran view, tanpa centering/aspect compensation;
- mask sedikit diperluas dan dibuat lebih solid.

True backdrop blur belum dipasang. Untuk sekarang fokusnya adalah alignment dan mask yang benar-benar menutup source.

Status: **kode berhasil di-build; belum diuji ulang pengguna**.

### 3. Floating Menu / Capture Menu
Uji terbaru menunjukkan submenu Screen-TL ikut masuk ke frame Manual TL sehingga tombol menu juga diterjemahkan.

Perbaikan:
- `triggerManualTranslation()` sekarang menutup submenu terlebih dahulu;
- capture ditunda 200 ms setelah menu menjadi `GONE`, memberi WindowManager waktu untuk menerapkan perubahan sebelum frame berikutnya dipakai OCR.

Status: **kode berhasil di-build; belum diuji ulang pengguna**.

### 4. Floating Button Toggle
Bug ditemukan pada touch listener: submenu disembunyikan pada `ACTION_DOWN`, lalu `ACTION_UP` melihat submenu sudah tersembunyi dan membukanya lagi. Akibatnya tap kedua terlihat tidak bisa menutup menu.

Perbaikan:
- `ACTION_DOWN` tidak lagi menyembunyikan menu;
- tap sederhana ditangani pada `ACTION_UP` sehingga menu benar-benar toggle;
- ketika gerakan drag melewati ambang, submenu ditutup dan drag tetap berjalan.

Status: **kode berhasil di-build; belum diuji ulang pengguna**.

## Real-Time TL
Real-Time dasar sudah terbukti pada perangkat pengguna.

Bug aktif: overlay berkedip karena window dihapus sebelum setiap capture. Perbaikan flicker sengaja ditunda sampai Manual TL stabil.

## Perubahan Terbaru — 2026-09-07

### Manual Overlay Coordinate Fix
`FloatingService.kt` membuat window overlay pada ukuran pixel frame capture dan menggunakan `FLAG_LAYOUT_IN_SCREEN`, tanpa fit-insets pada Android R+. Tujuannya agar koordinat OCR `(x,y)` dipetakan 1:1 ke layar, bukan ke area window yang lebih kecil/ber-inset.

Commit: `5ebb3bfda9a6474e0de027159ce7b2c6725923a`.

### Manual Overlay Mask
`TranslationOverlayView.kt`:
- padding mask sedikit diperbesar;
- mask memakai warna background lokal dengan alpha 245;
- sudut dibuat sedikit rounded;
- ukuran font dan fitting translation dipertahankan.

Commit: `e6b0bd2fc03a2e55e3335dc2a4121c6155bd6e98`.

### Floating Menu dan Capture Timing
`FloatingService.kt`:
- tap ulang FAB menutup submenu;
- drag menutup submenu setelah gerakan terdeteksi;
- Manual TL menunggu 200 ms setelah menu ditutup sebelum `captureOnce()`.

Commit yang sama: `5ebb3bfda9a6474e0de027159ce7b2c6725923a`.

**Status seluruh perubahan ini: belum diuji ulang pengguna.**

### Build Verification
GitHub Actions run `34112476978` (run #88) berhasil.
- head commit: `0c0c9d4a6d1e4d27e4b0f5124c391598a19c027e`
- `assembleDebug`: sukses
- artifact: `ScreenTranslator-APK`
- artifact ID: `10014932485`
- SHA-256: `9f5d8e06f18dd1fecaa6c559594978f25c3efe7424158cbf160bb82b80df9458`

Artifact ini sudah mencakup perubahan kode Manual TL terbaru sebelum dokumentasi build dicatat.

### TextLayoutAnalyzer
File baru: `TextLayoutAnalyzer.kt`.
- estimasi ukuran font source dari tinggi element OCR;
- mask diperluas agar source tertutup;
- sampling warna background dari sekitar source.

Commit: `e0ec39f595f2bb66e09d46d5be469fd7ae6deaab`.

### OcrManager + TranslationOverlayView
`DetectedText` membawa ukuran font source dan background color. Renderer memakai metadata tersebut untuk menggambar translation.

Commit OCR: `28dab21490b73a5c94848971259c9096207cca43`.
Commit renderer sebelumnya: `513e43e1f8c190903f2d1dc539028f0fed9e3e28`.

### Floating Menu + Hapus Overlay
Root menu dan penempatan FAB/menu direvisi. Metadata OCR juga diteruskan dari `FloatingService` ke renderer.

Commit layout: `e59a2b3adef5ff5391dcda1a12df8f0d6d19ec5e`.
Commit service sebelumnya: `9dbf8663eaab80f9844c64824964b3e5769e0156`.

## Build Automation
`.github/workflows/build.yml` sekarang menjalankan build otomatis setiap push ke `main` dengan Gradle 8.2 melalui `gradle/actions/setup-gradle@v6`; `workflow_dispatch` tetap tersedia.

## Prioritas Berikutnya
1. Pastikan build dari perubahan dokumentasi terakhir tetap sukses.
2. Uji APK pada perangkat.
3. Pastikan overlay tidak lagi membuat layar terlihat mengecil.
4. Pastikan menu Screen-TL tidak ikut masuk frame Manual TL.
5. Pastikan tap kedua FAB menutup menu.
6. Pastikan source tertutup dan background terlihat lebih solid.
7. Pastikan ukuran translation tetap mengikuti source.
8. Uji Hapus Overlay.
9. Jika Manual TL stabil, lanjutkan mode Manual TL baru yang direncanakan.
10. Real-Time flicker tetap ditunda.

## Aturan untuk AI Berikutnya
- Baca `README.md`, `AI_README.md`, dan `AI_HANDOFF.md` sebelum perubahan besar.
- Periksa file aktual di branch `main`.
- Manual TL adalah prioritas.
- Jangan mengerjakan Real-Time flicker kecuali diminta.
- Setiap perubahan bermakna harus dicatat.
- Fitur yang belum diuji perangkat diberi status `[~]` / belum teruji.
- Jangan mengklaim build lulus tanpa hasil build nyata.
