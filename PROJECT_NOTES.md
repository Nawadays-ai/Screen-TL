# Screen-TL — Catatan Proyek

## Tujuan
Screen-TL adalah aplikasi Android untuk menerjemahkan teks yang terlihat di layar menggunakan floating button. Target akhirnya adalah Manual Translation dan Real-Time Translation dengan hasil terjemahan sebagai overlay di atas teks asli aplikasi lain.

## Status Saat Ini
- Screen Capture: berhasil membuat screenshot; cakupan frame terhadap aplikasi target masih perlu diverifikasi lebih luas.
- Floating button: berhasil tampil dan dapat digeser.
- Floating menu: root sekarang dinamis dan menu dihitung relatif terhadap posisi FAB; belum diuji pengguna.
- Permission overlay dan MediaProjection: berhasil.
- OCR: ML Kit menghasilkan line + bounding box; metadata layout tambahan baru diterapkan.
- Google ML Kit Translation: pipeline Manual TL sudah berjalan sampai History dan overlay pada pengujian sebelumnya.
- Translation History: persisten dan service-safe.
- Manual overlay: sekarang memakai `TextLayoutAnalyzer` untuk ukuran font, mask yang diperluas, dan estimasi warna background; belum diuji pengguna.
- Hapus Overlay: tersedia di menu dan hanya ditampilkan ketika overlay Manual TL aktif; belum diuji pengguna.
- Real-Time TL: dasar terbukti bekerja, flicker tetap ditunda.

## Masalah Aktif

### 1. Capture/OCR
Frame full display dan cakupan OCR pada aplikasi target masih perlu diverifikasi. Filter status bar dan UI Screen-TL belum dibuat.

### 2. Translation Overlay
Masalah sebelumnya adalah overlay hanya memakai bounding box line dan background hitam tetap, sehingga source masih terlihat dan ukuran translation tidak konsisten.

Solusi baru:
- `TextLayoutAnalyzer.kt` menghitung mask yang sedikit lebih besar dari glyph OCR;
- tinggi element OCR dipakai untuk mengestimasi ukuran font source;
- warna piksel di sekitar mask dipakai sebagai warna background;
- metadata diteruskan melalui `DetectedText` sampai `TranslationOverlayItem`;
- renderer memulai font dari ukuran source dan mengecilkannya bila translation terlalu panjang.

Status: **belum diuji pengguna**.

### 3. Floating Menu / Hapus Overlay
XML sebelumnya memiliki root 48dp dengan submenu yang dipaksa keluar memakai `translationX`. Ini membuat menu dapat bertumpuk dengan FAB dan tombol Hapus Overlay tidak terlihat seperti yang diharapkan.

Sekarang:
- root `FrameLayout` memakai `wrap_content`;
- submenu tidak memakai `translationX`;
- ukuran WindowManager berubah mengikuti menu;
- menu kanan/kiri dihitung dari posisi FAB;
- ketika FAB dekat bawah, menu ditempatkan di atas;
- `Hapus Overlay` hanya visible saat overlay Manual TL aktif;
- menghapus overlay tidak menghentikan service dan tidak menghapus History.

Status: **belum diuji pengguna**.

## Real-Time TL
Real-Time dasar sudah terbukti pada perangkat pengguna.

Bug aktif: overlay berkedip karena window dihapus sebelum setiap capture. Perbaikan flicker sengaja ditunda sampai Manual TL stabil.

## Perubahan Terbaru — 2026-09-07

### TextLayoutAnalyzer
File baru:
`app/src/main/java/com/example/screentranslator/TextLayoutAnalyzer.kt`

Fungsi:
- estimasi ukuran font source dari tinggi element OCR;
- memperluas mask source agar teks tertutup;
- sampling warna background dari sekitar area source.

Commit: `e0ec39f595f2bb66e09d46d5be469fd7ae6deaab`.

### OcrManager
`DetectedText` sekarang membawa:
- `sourceTextSizePx`;
- `backgroundColor`;
- koordinat mask hasil analyzer.

Commit: `28dab21490b73a5c94848971259c9096207cca43`.

### TranslationOverlayView
Renderer sekarang:
- memakai background hasil sampling;
- menggunakan source font sebagai ukuran awal;
- mengecilkan font jika translation tidak muat;
- mempertahankan clipping dan non-touchable overlay.

Commit: `513e43e1f8c190903f2d1dc539028f0fed9e3e28`.

### Floating Menu / Service
`layout_floating_widget.xml` dan `FloatingService.kt` direvisi untuk memperbaiki ukuran root dan penempatan menu.

Commit layout: `e59a2b3adef5ff5391dcda1a12df8f0d6d19ec5e`.
Commit service: `e85ef0b3d7e5881c282dc98e77108d554e389f8f`.

**Penting:** perubahan kode di atas dibuat setelah pengguna menunjukkan screenshot build `e1c7006`, yang memperlihatkan source masih terlihat, ukuran overlay tidak sesuai, menu bertumpuk, dan tombol Hapus Overlay tidak muncul. Perubahan terbaru **belum diuji pengguna**.

## Prioritas Berikutnya
1. Dapatkan build APK dari perubahan terbaru bila workflow dapat dijalankan.
2. Uji Manual TL pada source kecil, sedang, dan besar.
3. Pastikan source tertutup dan background overlay menyatu dengan area sekitar.
4. Pastikan ukuran translation mengikuti source dan mengecil jika translation lebih panjang.
5. Uji menu FAB kiri, kanan, bawah-kiri, dan bawah-kanan.
6. Uji Hapus Overlay muncul setelah Manual TL dan hilang setelah ditekan.
7. Jika Manual TL stabil, lanjutkan mode Manual TL baru yang direncanakan.
8. Real-Time flicker tetap ditunda.

## Catatan Build
Tool GitHub yang tersedia tidak menyediakan aksi untuk memulai `workflow_dispatch`. Jangan mengklaim build baru berhasil tanpa hasil workflow yang nyata.

## Aturan untuk AI Berikutnya
- Baca `README.md`, `AI_README.md`, dan `AI_HANDOFF.md` sebelum perubahan besar.
- Periksa file aktual di branch `main`, jangan mengandalkan catatan lama saja.
- Manual TL adalah prioritas.
- Jangan mengerjakan Real-Time flicker kecuali diminta.
- Setiap perubahan bermakna harus dicatat.
- Fitur yang belum diuji perangkat diberi status `[~]` / belum teruji.
- Jangan mengklaim build lulus tanpa hasil build nyata.
