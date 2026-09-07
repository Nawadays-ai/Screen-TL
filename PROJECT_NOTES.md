# Screen-TL — Catatan Proyek

## Tujuan
Screen-TL adalah aplikasi Android untuk menerjemahkan teks yang terlihat di layar menggunakan floating button. Target akhirnya adalah Manual Translation dan Real-Time Translation dengan hasil terjemahan sebagai overlay di atas teks asli aplikasi lain.

## Status Saat Ini
- Screen Capture: berhasil membuat screenshot; cakupan frame terhadap aplikasi target masih perlu diverifikasi lebih luas.
- Floating button: berhasil tampil dan dapat digeser.
- Floating menu: sekarang mempunyai layout yang dapat berpindah sisi berdasarkan posisi floating button; belum diuji pengguna pada build terbaru.
- Permission overlay dan MediaProjection: berhasil.
- OCR: engine terpasang dan menghasilkan teks dengan bounding box; cakupan seluruh aplikasi target masih perlu diverifikasi.
- Google ML Kit Translation: pipeline Manual TL sudah berjalan sampai History dan overlay pada pengujian perangkat sebelumnya.
- Translation History: diinisialisasi dari `FloatingService` dan menggunakan penyimpanan sinkron untuk diagnosis.
- Manual overlay terbaru: sekarang dirancang untuk menutupi teks sumber dan menyesuaikan ukuran hasil translation; belum diuji pengguna pada build terbaru.
- Tombol Hapus Overlay Manual: sudah diimplementasikan; muncul setelah Manual TL menghasilkan overlay dan disembunyikan setelah overlay dihapus; belum diuji pengguna pada build terbaru.
- Real-Time TL: dasar sudah terbukti bekerja pada perangkat, tetapi flicker ditunda sementara.

## Masalah Aktif

### 1. Capture/OCR masih perlu verifikasi perangkat
Pengujian sebelumnya menunjukkan jam/status bar dapat terbaca. Pembatas tiga baris sudah dihapus. Sekarang perlu memastikan frame full display benar-benar berisi aplikasi target dan OCR membaca teks target secara lengkap.

### 2. History
History sebelumnya kosong. Perubahan yang sudah dilakukan:
- `FloatingService` memanggil `TranslationHistory.initialize(applicationContext)` sendiri.
- Penyimpanan history menggunakan `commit()` agar hasil write dapat langsung diverifikasi.
- Logging `ScreenTL-History` mencatat hasil write.

Manual TL memakai History sebagai hasil persisten. Real-Time tidak menulis setiap frame ke History agar frame berulang tidak membuat puluhan entri duplikat.

### 3. Toast hanya status aplikasi
Toast hanya pesan status singkat. Hasil translation menggunakan overlay, bukan Toast.

### 4. Translation Overlay
Implementasi ada di `TranslationOverlayView.kt` dan `FloatingService.kt`:
- Setiap `DetectedText` diterjemahkan.
- Koordinat bounding box asli dibawa bersama hasil translation.
- Overlay full-screen menggunakan `TYPE_APPLICATION_OVERLAY`.
- Overlay memakai `FLAG_NOT_TOUCHABLE` agar tidak mengambil alih sentuhan pengguna.
- Overlay lama dilepas sebelum Manual TL capture berikutnya agar hasil overlay tidak menjadi input OCR.
- Patch terbaru membuat background translation menutup penuh area OCR.
- Ukuran teks dimulai dari ukuran yang mengikuti tinggi teks sumber dan hanya mengecil jika terjemahan lebih panjang dari area sumber.

**Status visual:** patch sudah masuk repository tetapi belum diuji pengguna pada build terbaru.

### 5. Floating Menu dan Hapus Overlay
`layout_floating_widget.xml` sekarang mempunyai:
- Real-Time
- Manual TL
- Hapus Overlay
- Keluar

`FloatingService.kt` mengatur:
- menu ke kanan ketika FAB berada di sisi kiri;
- menu ke kiri ketika FAB berada di sisi kanan;
- menu ke atas ketika FAB berada dekat bawah layar;
- Hapus Overlay hanya terlihat ketika Manual TL mempunyai overlay aktif;
- menekan Hapus Overlay hanya menghapus overlay, tidak menghentikan service dan tidak menghapus History.

**Status:** belum diuji pengguna pada build terbaru.

## Real-Time TL
Real-Time dasar sudah terbukti pada perangkat pengguna:

`Real-Time aktif → capture frame → OCR → translate → overlay → ulangi`

Karakteristik:
- interval dasar sekitar 1,2 detik;
- satu frame diproses pada satu waktu;
- model translation dipersiapkan saat mulai;
- callback sesi lama dilindungi `realtimeGeneration`;
- hasil Real-Time tidak ditulis ke History.

### Bug Real-Time: Flicker
Pengguna melaporkan overlay muncul sekitar 2 detik, hilang, lalu muncul kembali. Kadang jeda mencapai sekitar 4 detik.

Penyebab paling mungkin tetap `removeTranslationOverlay()` sebelum setiap capture pada loop Real-Time.

**Status:** sengaja ditunda. Jangan menganggap flicker sudah diperbaiki.

Solusi yang direncanakan nanti:
- pertahankan satu window overlay;
- jangan remove/re-add window setiap frame;
- kendalikan isi/visibility overlay saat capture tanpa menghancurkan WindowManager window.

## Perubahan Terbaru — 2026-09-07

### Manual Overlay menimpa source
File: `TranslationOverlayView.kt`

Perubahan:
- background menjadi opaque agar source text tertutup;
- ukuran awal teks mengikuti tinggi bounding box OCR;
- teks hanya dikecilkan bila translation terlalu lebar;
- teks tetap di-clipping di area OCR.

Commit: `1116dc9a2f5f197b10bdf2113ad88366c8bdb2dd`

Status: **belum diuji pengguna**.

### Floating Menu adaptif + Hapus Overlay
File:
- `layout_floating_widget.xml`
- `FloatingService.kt`

Perubahan:
- tombol `Hapus Overlay` ditambahkan dan default tersembunyi;
- tombol ditampilkan setelah Manual TL berhasil menghasilkan overlay;
- tombol dihilangkan saat overlay dihapus;
- menu diposisikan ke sisi yang sesuai dengan posisi FAB;
- menu ditempatkan di atas bila FAB dekat bawah layar;
- service tetap hidup ketika overlay dihapus.

Commit layout: `7197c6b01541727a4c0b9e106b05afefb0dc16d8`.
Commit service: `f77d90e10a12372524e3d5e5997213ba198ea8c5`.

Status: **belum diuji pengguna**.

## Prioritas Berikutnya
1. Build APK dari perubahan terbaru jika workflow dapat dijalankan.
2. Uji Manual TL pada teks pendek dan panjang.
3. Pastikan source text benar-benar tertutup dan hanya translation yang terlihat.
4. Uji menu floating di beberapa posisi layar.
5. Uji Hapus Overlay: muncul setelah Manual TL, menghapus overlay, lalu hilang.
6. Setelah semua fungsi Manual TL stabil, implementasikan mode Manual TL baru yang direncanakan pengguna.
7. Real-Time flicker ditangani setelah fokus Manual TL selesai.

## Catatan Build
Tool GitHub yang tersedia pada sesi ini tidak menyediakan aksi untuk memulai `workflow_dispatch`, sehingga build baru tidak boleh dianggap berhasil sampai hasil workflow benar-benar tersedia.

## Aturan untuk AI Berikutnya
- Baca `README.md`, `AI_README.md`, dan `AI_HANDOFF.md` sebelum perubahan besar.
- Jangan menganggap patch terbaru sudah teruji.
- Manual TL adalah prioritas saat ini.
- Real-Time flicker ditunda.
- Setiap perubahan bermakna wajib dicatat di dokumen dan status `[~]` digunakan untuk perubahan yang belum diuji pengguna.
