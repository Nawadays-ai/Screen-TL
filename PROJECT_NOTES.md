# Screen-TL — Catatan Proyek

## Tujuan
Screen-TL adalah aplikasi Android untuk menerjemahkan teks yang terlihat di layar menggunakan floating button. Target akhirnya adalah Manual Translation dan Real-Time Translation dengan hasil terjemahan sebagai overlay di atas teks asli aplikasi lain.

## Status Saat Ini
- Screen Capture: berhasil membuat screenshot, tetapi sumber frame yang diberikan ke OCR masih harus diverifikasi.
- Floating button: berhasil tampil, dapat digeser, dan tombol bekerja.
- Permission overlay dan MediaProjection: berhasil.
- OCR: engine terpasang dan sudah menghasilkan teks ke History; cakupan seluruh aplikasi target masih perlu diverifikasi.
- Google ML Kit Translation: pipeline Manual TL sudah berjalan sampai History.
- Translation History: diinisialisasi dari `FloatingService` dan menggunakan penyimpanan sinkron untuk diagnosis yang dapat dipercaya.
- Translation overlay: implementasi pertama sudah dibuat berdasarkan bounding box OCR, tetapi belum diuji pada perangkat.
- Realtime TL: belum dibuat.

## Masalah Aktif

### 1. Capture/OCR masih perlu verifikasi perangkat
Pengujian sebelumnya menunjukkan jam/status bar dapat terbaca. Pembatas tiga baris juga sudah ditemukan dan dihapus. Sekarang prioritasnya memastikan frame full display benar-benar berisi aplikasi target dan OCR membaca teks target secara lengkap.

### 2. History
History sebelumnya kosong. Perubahan yang sudah dilakukan:
- `FloatingService` memanggil `TranslationHistory.initialize(applicationContext)` sendiri.
- Penyimpanan history menggunakan `commit()` agar hasil write dapat langsung diverifikasi.
- Logging `ScreenTL-History` mencatat hasil write.

### 3. Toast hanya status aplikasi
Toast memang hanya pesan status singkat. Toast bukan target output produk. Hasil translation sekarang memiliki jalur overlay sendiri.

### 4. Translation Overlay
Implementasi pertama sudah ada di `TranslationOverlayView.kt` dan `FloatingService.kt`:
- Setiap `DetectedText` diterjemahkan.
- Koordinat bounding box asli dibawa bersama hasil translation.
- Overlay full-screen menggunakan `TYPE_APPLICATION_OVERLAY`.
- Overlay memakai `FLAG_NOT_TOUCHABLE` agar tidak mengambil alih sentuhan pengguna.
- Floating UI Screen-TL dan overlay lama dibersihkan sebelum capture berikutnya agar hasil overlay tidak ikut diproses OCR.

Yang belum terbukti:
- kecocokan koordinat bitmap dengan koordinat overlay pada berbagai resolusi/orientasi;
- ukuran teks overlay agar terbaca tanpa menutupi terlalu banyak layar;
- hasil overlay pada aplikasi/game nyata.

## Perubahan Terbaru — 2026-09-06

### `TranslationOverlayView.kt`
- File baru.
- Menggambar background dan teks hasil terjemahan pada area bounding box OCR.
- Menyesuaikan koordinat bitmap ke ukuran view overlay.
- Overlay tidak menerima touch.

### `FloatingService.kt`
- Menyimpan hasil translation bersama koordinat OCR.
- Menampilkan overlay setelah semua hasil Manual TL selesai.
- Menyembunyikan floating UI sebelum capture agar UI Screen-TL tidak ikut OCR.
- Membersihkan overlay lama sebelum capture baru.
- Menjaga History sebagai output debugging/persisten.

### Dokumentasi
- `README.md` memperbarui status dan roadmap Milestone 2.
- `AI_HANDOFF.md` perlu mencatat implementasi overlay ini sebagai fitur yang sudah dibuat tetapi belum diverifikasi perangkat.

## Testing Manual TL + Overlay

1. Build/install versi terbaru.
2. Pilih bahasa sumber dan target.
3. Tekan Play dan izinkan screen capture.
4. Buka aplikasi lain yang memiliki banyak teks besar dan jelas.
5. Tunggu aplikasi target tampil stabil.
6. Tekan floating button → Manual TL.
7. Tunggu sampai Toast menyatakan overlay ditampilkan.
8. Periksa apakah teks terjemahan muncul tepat di area teks asli.
9. Coba sentuh/scroll aplikasi target. Overlay seharusnya tidak menghalangi sentuhan.
10. Jalankan Manual TL lagi. Overlay lama harus dibersihkan sebelum capture baru.
11. Buka History untuk memastikan hasil tetap tersimpan.

### Interpretasi hasil
- Jika `ScreenTL-Capture` tidak menunjukkan `Fresh screen frame captured successfully`, masalah berada di capture pipeline.
- Jika capture sukses tetapi `ScreenTL-OCR` hanya menemukan jam/status bar, periksa bitmap/frame dan orientasi/ukuran sebelum mengubah OCR.
- Jika OCR menemukan teks target tetapi overlay salah posisi, fokuskan diagnosis pada skala/koordinat overlay, bukan translation engine.
- Jika overlay tepat posisi tetapi menutupi atau sulit dibaca, tuning ukuran teks/background dapat dilakukan setelah koordinat terbukti benar.

## Roadmap

### Milestone 1 — Stabilkan Manual Translation

- [x] Floating button dan permission dasar.
- [x] MediaProjection dapat membuat screenshot.
- [x] OCR manager dengan bounding box.
- [x] ML Kit Translation manager.
- [~] Pastikan frame yang diberikan ke OCR benar-benar berasal dari aplikasi yang sedang terlihat.
- [~] Pastikan hasil OCR dan translation masuk History secara konsisten.
- [ ] Filter status bar/floating button/teks Screen-TL yang tidak relevan.

### Milestone 2 — Translation Overlay

- [x] Buat overlay berdasarkan `DetectedText.boundingBox`.
- [x] Tampilkan terjemahan di posisi teks asli.
- [x] Jangan mengganggu interaksi aplikasi target.
- [ ] Verifikasi koordinat dan ukuran overlay pada perangkat.
- [ ] Tambahkan hide/refresh/clear overlay yang mudah digunakan.

### Milestone 3 — Real-Time Translation

- [ ] Capture frame berkala.
- [ ] Deteksi perubahan layar.
- [ ] OCR hanya saat diperlukan.
- [ ] Cache translation.
- [ ] Update overlay hanya untuk teks baru/berubah.

### Milestone 4 — Translation Engine

- [x] Google ML Kit on-device sebagai baseline.
- [ ] DeepL API.
- [ ] Gemini AI.
- [ ] Hubungkan pemilihan engine ke pipeline nyata.

## Prinsip Pengembangan
- Ubah sedikit bagian setiap tahap.
- Jangan merusak Screen Capture/OCR yang sudah terbukti bekerja.
- Bedakan implementasi dengan verifikasi nyata.
- Setiap perubahan penting wajib dicatat.
- Setiap roadmap harus menunjukkan status sebenarnya.
- Pemilik proyek harus selalu diberi ringkasan file yang berubah, alasan, hasil verifikasi, dan langkah testing berikutnya.
