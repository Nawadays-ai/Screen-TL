# Screen-TL — Catatan Proyek

## Tujuan
Screen-TL adalah aplikasi Android untuk menerjemahkan teks yang terlihat di layar menggunakan floating button. Target akhirnya adalah Manual Translation dan Real-Time Translation dengan hasil terjemahan sebagai overlay di atas teks asli aplikasi lain.

## Status Saat Ini
- Screen Capture: berhasil membuat screenshot; cakupan frame terhadap aplikasi target masih perlu diverifikasi lebih luas.
- Floating button: berhasil tampil, dapat digeser, dan tombol bekerja.
- Permission overlay dan MediaProjection: berhasil.
- OCR: engine terpasang dan menghasilkan teks dengan bounding box; cakupan seluruh aplikasi target masih perlu diverifikasi.
- Google ML Kit Translation: pipeline Manual TL sudah berjalan sampai History dan overlay pada pengujian perangkat terbaru.
- Translation History: diinisialisasi dari `FloatingService` dan menggunakan penyimpanan sinkron untuk diagnosis yang dapat dipercaya.
- Translation overlay: sudah bekerja pada Manual TL; perapian visual dan verifikasi lintas perangkat ditunda.
- Realtime TL: engine tahap pertama sudah diimplementasikan dan **sudah terbukti berjalan pada perangkat**, tetapi overlay berkedip dan perlu diperbaiki.

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

Yang belum terbukti:
- kecocokan koordinat bitmap dengan koordinat overlay pada berbagai resolusi/orientasi;
- ukuran teks overlay agar terbaca tanpa menutupi terlalu banyak layar;
- hasil overlay pada berbagai aplikasi/game nyata.

Perapian UI sengaja ditunda sampai fungsi inti stabil.

## Real-Time TL — Implementasi Tahap Pertama
`FloatingService.kt` memiliki loop:

`Real-Time aktif → capture frame → OCR → translate setiap baris → update overlay → tunggu → capture lagi`

Karakteristik implementasi saat ini:
- interval dasar antar-frame sekitar 1,2 detik;
- hanya satu frame diproses pada satu waktu;
- model translation dipersiapkan saat Real-Time mulai;
- callback sesi lama dilindungi dengan `realtimeGeneration`;
- saat Real-Time dihentikan, pending capture dibatalkan dan overlay dihapus, tetapi foreground service tetap hidup;
- hasil Real-Time tidak masuk History pada tahap ini;
- change detection dan cache translation belum dibuat.

### Bug Real-Time yang ditemukan pada pengujian perangkat
Pengguna sudah mengonfirmasi Real-Time **berhasil menerjemahkan**, tetapi hasil overlay berkedip:
- overlay muncul sekitar 2 detik;
- overlay kemudian hilang;
- beberapa detik kemudian muncul lagi;
- kadang jeda tanpa overlay mencapai sekitar 4 detik.

Dari inspeksi kode, penyebab paling mungkin adalah `scheduleRealtimeCapture()` memanggil `removeTranslationOverlay()` sebelum setiap frame. Artinya WindowManager memang kehilangan overlay selama capture + OCR + translation berlangsung.

### Keputusan perbaikan berikutnya
Perbaikan yang direncanakan:
- jangan `removeView()` overlay setiap siklus Real-Time;
- pertahankan satu instance/window overlay;
- saat frame baru diambil, buat isi overlay tidak terlihat sementara tanpa menghapus window, agar hasil overlay lama tidak masuk input OCR;
- setelah frame diterima, kembalikan overlay lalu perbarui isinya ketika translation selesai.

**Status:** belum diterapkan pada repository pada sesi ini. Jadi belum boleh dianggap teruji.

## Perubahan Terbaru — 2026-09-07

### Verifikasi Real-Time oleh pengguna
- Pengguna menjalankan Real-Time TL pada perangkat dan menyatakan berhasil.
- Capture → OCR → translation → overlay berjalan berulang.
- Masalah yang ditemukan adalah kedipan/hilangnya overlay antar-siklus.

### Catatan penting untuk AI berikutnya
- Jangan menganggap masalah kedipan sudah selesai.
- Jangan melakukan perubahan UI visual dulu.
- Prioritas pertama adalah mempertahankan overlay window selama loop Real-Time dan menghilangkan periode kosong antar-frame.
- Setelah patch kedipan dibuat, statusnya harus `[~]` / belum teruji sampai pengguna mencoba APK baru.

## Roadmap

### Milestone 1 — Stabilkan Manual Translation

- [x] Floating button dan permission dasar.
- [x] MediaProjection dapat membuat screenshot.
- [x] OCR manager dengan bounding box.
- [x] ML Kit Translation manager.
- [~] Verifikasi bahwa frame yang diberikan ke OCR benar-benar berasal dari aplikasi yang sedang terlihat.
- [x] Manual TL capture → OCR → translation → History → overlay berhasil pada pengujian perangkat terbaru.
- [ ] Filter status bar/floating button/teks Screen-TL yang tidak relevan.
- [x] Manual TL memiliki timeout capture 3 detik dan watchdog pemrosesan 30 detik.

### Milestone 2 — Translation Overlay

- [x] Implementasi overlay teks berdasarkan `DetectedText.boundingBox`.
- [x] Tampilkan hasil terjemahan pada area teks yang terdeteksi.
- [x] Overlay dibuat `NOT_TOUCHABLE` agar tidak mengganggu interaksi aplikasi target.
- [ ] Verifikasi posisi overlay terhadap koordinat layar pada berbagai perangkat/orientasi.
- [ ] Sediakan hide/clear overlay yang mudah digunakan.
- [ ] Rapikan visual overlay setelah fungsi inti stabil.

### Milestone 3 — Real-Time Translation

- [x] Capture frame berkala tahap pertama.
- [x] OCR dan translation loop dasar.
- [x] Update overlay dari hasil frame terbaru.
- [x] Stop Real-Time tanpa menghentikan service.
- [~] Hilangkan kedipan overlay pada loop Real-Time.
- [ ] Verifikasi kestabilan Real-Time setelah perbaikan kedipan.
- [ ] Deteksi perubahan layar agar frame yang tidak berubah tidak diproses ulang.
- [ ] Cache translation agar teks yang sama tidak diterjemahkan berulang.
- [ ] Optimalkan interval dan beban CPU/baterai berdasarkan hasil device test.
- [ ] Update overlay hanya untuk teks baru/berubah.

### Milestone 4 — Translation Engine

- [x] Google ML Kit on-device sebagai baseline.
- [ ] DeepL API.
- [ ] Gemini AI.
- [ ] Pemilihan engine yang benar-benar terhubung ke pipeline Manual/Realtime.

## Testing Real-Time berikutnya

1. Gunakan APK yang berisi patch khusus kedipan.
2. Aktifkan Real-Time pada aplikasi target dengan beberapa baris teks jelas.
3. Amati minimal 15–30 detik tanpa mengubah layar terlebih dahulu.
4. Pastikan overlay tidak hilang di antara siklus translation.
5. Scroll/ubah layar target dan pastikan overlay ikut diperbarui.
6. Matikan Real-Time dan pastikan overlay hilang serta floating service tetap hidup.
7. Jalankan Manual TL setelahnya untuk memastikan pipeline Manual TL tidak rusak.
