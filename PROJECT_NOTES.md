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
- Realtime TL: engine tahap pertama sudah diimplementasikan, tetapi belum diverifikasi pada perangkat.

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
- Overlay lama dilepas sebelum capture berikutnya agar hasil overlay tidak menjadi input OCR.

Yang belum terbukti:
- kecocokan koordinat bitmap dengan koordinat overlay pada berbagai resolusi/orientasi;
- ukuran teks overlay agar terbaca tanpa menutupi terlalu banyak layar;
- hasil overlay pada berbagai aplikasi/game nyata.

## Real-Time TL — Implementasi Tahap Pertama

`FloatingService.kt` sekarang memiliki loop dasar:

`Real-Time aktif → capture frame → OCR → translate setiap baris → update overlay → tunggu → capture lagi`

Karakteristik implementasi:
- interval dasar antar-frame sekitar 1,2 detik;
- hanya satu frame diproses pada satu waktu;
- model translation dipersiapkan saat Real-Time mulai;
- callback sesi lama dilindungi dengan `realtimeGeneration` agar tidak menghidupkan kembali loop setelah Real-Time dihentikan;
- saat Real-Time dihentikan, pending capture dibatalkan dan overlay dihapus, tetapi foreground service tetap hidup;
- hasil Real-Time tidak masuk History pada tahap ini;
- change detection dan cache translation belum dibuat.

Alasan belum membuat change detection/cache sekarang: fungsi dasar harus diverifikasi dulu pada perangkat sebelum optimasi ditambahkan.

## Perubahan Terbaru — 2026-09-07

### Implementasi Real-Time TL tahap pertama
`FloatingService.kt`:
- mengubah tombol Real-Time dari sekadar status menjadi loop capture/OCR/translation nyata;
- mempersiapkan model translation ketika Real-Time dimulai;
- mengambil frame layar berkala dengan `ScreenCaptureManager.captureOnce()`;
- menjalankan OCR pada frame terbaru;
- menerjemahkan hasil OCR secara berurutan;
- memperbarui translation overlay dari frame terbaru;
- mencegah overlap proses dengan flag `isRealtimeBusy`;
- menggunakan `realtimeGeneration` agar callback dari sesi sebelumnya diabaikan setelah Real-Time dihentikan;
- membatalkan pending capture dan menghapus overlay saat Real-Time dimatikan;
- tidak menulis setiap frame ke History untuk mencegah duplikasi.

### Verifikasi
- Kode sudah ditulis ke branch `main` pada commit `2f446e40fac1b5094f4a71ba72570ba7afb03060`.
- Build/device test untuk perubahan ini belum dilakukan.
- GitHub tooling yang tersedia di sesi ini tidak menyediakan aksi untuk memulai `workflow_dispatch`, sehingga jangan mengklaim APK baru sudah dibuild.

## Testing Real-Time Tahap Pertama

1. Install APK yang berisi commit Real-Time terbaru.
2. Jalankan Screen-TL dan izinkan MediaProjection.
3. Buka aplikasi lain dengan teks besar dan jelas.
4. Tekan floating button → Real-Time.
5. Tunggu beberapa detik.
6. Pastikan OCR/translation berjalan berulang dan overlay muncul/berubah mengikuti frame.
7. Ubah/scroll halaman target dan lihat apakah hasil overlay ikut berubah pada frame berikutnya.
8. Tekan floating button saat Real-Time aktif untuk menghentikannya.
9. Pastikan overlay hilang dan floating service tetap aktif.
10. Jalankan Manual TL setelah Real-Time dihentikan untuk memastikan pipeline Manual TL tetap bekerja.

### Log yang diharapkan
- `Realtime translation started`
- `Realtime translation model ready`
- `Realtime captureOnce returned=true`
- `Realtime frame captured`
- `Realtime OCR completed: N lines`
- `Realtime overlay updated: N items`
- Saat dihentikan: `Realtime translation stopped`

## Roadmap

### Milestone 1 — Stabilkan Manual Translation

- [x] Floating button dan permission dasar.
- [x] MediaProjection dapat membuat screenshot.
- [x] OCR manager dengan bounding box.
- [x] ML Kit Translation manager.
- [~] Pastikan frame yang diberikan ke OCR benar-benar berasal dari aplikasi yang sedang terlihat.
- [x] Manual TL capture → OCR → translation → History → overlay berhasil pada pengujian perangkat terbaru.
- [ ] Filter status bar/floating button/teks Screen-TL yang tidak relevan.

### Milestone 2 — Translation Overlay

- [x] Buat overlay berdasarkan `DetectedText.boundingBox`.
- [x] Tampilkan terjemahan di posisi teks asli.
- [x] Jangan mengganggu interaksi aplikasi target.
- [ ] Verifikasi koordinat dan ukuran overlay pada berbagai perangkat/orientasi.
- [ ] Tambahkan hide/clear overlay yang mudah digunakan.
- [ ] Rapikan visual overlay setelah fungsi inti stabil.

### Milestone 3 — Real-Time Translation

- [x] Capture frame berkala tahap pertama.
- [x] OCR dan translation loop dasar.
- [x] Update overlay dari hasil frame terbaru.
- [x] Stop Real-Time tanpa menghentikan service.
- [ ] Verifikasi kestabilan Real-Time pada perangkat.
- [ ] Deteksi perubahan layar.
- [ ] Cache translation agar teks yang sama tidak diterjemahkan berulang.
- [ ] Optimalkan interval dan beban CPU/baterai berdasarkan hasil device test.
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
