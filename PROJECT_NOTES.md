# Screen-TL — Catatan Proyek

## Tujuan
Screen-TL adalah aplikasi Android untuk menerjemahkan teks yang terlihat di layar menggunakan floating button. Target akhirnya adalah Manual Translation dan Real-Time Translation dengan hasil terjemahan sebagai overlay di atas teks asli aplikasi lain.

## Status Saat Ini
- Screen Capture: berhasil membuat screenshot; cakupan frame terhadap aplikasi target masih perlu diverifikasi lebih luas.
- Floating button: berhasil tampil dan dapat digeser.
- Floating menu: root dinamis dan menu dihitung relatif terhadap posisi FAB; belum diuji pengguna.
- Permission overlay dan MediaProjection: berhasil.
- OCR: ML Kit menghasilkan line + bounding box; metadata layout tambahan sudah diterapkan.
- Google ML Kit Translation: pipeline Manual TL sudah berjalan sampai History dan overlay pada pengujian sebelumnya.
- Translation History: persisten dan service-safe.
- Manual overlay: memakai `TextLayoutAnalyzer` untuk ukuran font, mask yang diperluas, dan estimasi warna background; belum diuji pengguna.
- Hapus Overlay: tersedia di menu dan hanya ditampilkan ketika overlay Manual TL aktif; belum diuji pengguna.
- Real-Time TL: dasar terbukti bekerja, flicker ditunda.

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
XML sebelumnya memiliki root 48dp dengan submenu yang dipaksa keluar memakai `translationX`. Itu membuat menu dapat bertumpuk dengan FAB dan tombol Hapus Overlay tidak terlihat seperti yang diharapkan.

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
File baru: `TextLayoutAnalyzer.kt`.
- estimasi ukuran font source dari tinggi element OCR;
- mask diperluas agar source tertutup;
- sampling warna background dari sekitar source.

Commit: `e0ec39f595f2bb66e09d46d5be469fd7ae6deaab`.

### OcrManager + TranslationOverlayView
`DetectedText` sekarang membawa ukuran font source dan background color. Renderer memakai metadata tersebut untuk menggambar translation.

Commit OCR: `28dab21490b73a5c94848971259c9096207cca43`.
Commit renderer: `513e43e1f8c190903f2d1dc539028f0fed9e3e28`.

### Floating Menu + Hapus Overlay
Root menu dan penempatan FAB/menu direvisi. Metadata OCR juga diteruskan dari `FloatingService` ke renderer.

Commit layout: `e59a2b3adef5ff5391dcda1a12df8f0d6d19ec5e`.
Commit service: `9dbf8663eaab80f9844c64824964b3e5769e0156`.

Status seluruh perubahan kode: **belum diuji pengguna**.

### Build Automation
`.github/workflows/build.yml` sekarang menjalankan `assembleDebug` otomatis setiap push ke `main`, sementara `workflow_dispatch` tetap tersedia.

Commit: `39b09bffdd878650ade24824872d85daaf08d824`.

Status CI pada sesi ini: belum ada status check yang dikembalikan, sehingga build belum boleh dianggap lulus.

## Prioritas Berikutnya
1. Tunggu/cek hasil build otomatis.
2. Uji Manual TL pada source kecil, sedang, dan besar.
3. Pastikan source tertutup dan background menyatu dengan area sekitar.
4. Pastikan ukuran translation mengikuti source dan mengecil jika diperlukan.
5. Uji menu FAB kiri, kanan, bawah-kiri, dan bawah-kanan.
6. Uji Hapus Overlay muncul setelah Manual TL dan hilang setelah ditekan.
7. Jika Manual TL stabil, lanjutkan mode Manual TL baru yang direncanakan.
8. Real-Time flicker tetap ditunda.

## Aturan untuk AI Berikutnya
- Baca `README.md`, `AI_README.md`, dan `AI_HANDOFF.md` sebelum perubahan besar.
- Periksa file aktual di branch `main`.
- Manual TL adalah prioritas.
- Jangan mengerjakan Real-Time flicker kecuali diminta.
- Setiap perubahan bermakna harus dicatat.
- Fitur yang belum diuji perangkat diberi status `[~]` / belum teruji.
- Jangan mengklaim build lulus tanpa hasil build nyata.
