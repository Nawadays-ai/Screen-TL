# Screen-TL — Catatan Proyek

## Tujuan
Screen-TL adalah aplikasi Android untuk menerjemahkan teks yang terlihat di layar menggunakan floating button. Target akhirnya adalah Manual Translation dan Real-Time Translation dengan hasil terjemahan sebagai overlay di atas teks asli aplikasi lain.

## Status Saat Ini
- Screen Capture: berhasil membuat screenshot, tetapi sumber frame yang diberikan ke OCR masih harus diverifikasi.
- Floating button: berhasil tampil, dapat digeser, dan tombol bekerja.
- Permission overlay dan MediaProjection: berhasil.
- OCR: engine terpasang, tetapi pengujian terbaru hanya mendeteksi teks tertentu seperti jam/status bar.
- Google ML Kit Translation: implementasi tersedia, tetapi alur end-to-end terbaru belum terbukti menerjemahkan teks dari aplikasi target.
- Translation History: sekarang diinisialisasi juga dari `FloatingService` dan menggunakan penyimpanan sinkron untuk diagnosis yang lebih dapat dipercaya.
- Translation overlay: belum dibuat.
- Realtime TL: belum dibuat.

## Masalah Aktif

### 1. OCR hanya membaca jam/status bar
Gejala: screenshot berhasil dibuat, tetapi teks aplikasi target tidak muncul sebagai hasil OCR. Jam di layar justru terdeteksi.

Hipotesis pertama yang harus diuji adalah **frame capture**, bukan langsung menyalahkan OCR. Logging sekarang mencatat dimensi frame dan setiap hasil OCR.

### 2. History kosong
History sebelumnya tidak memberikan hasil yang terlihat. Perubahan terbaru:
- `FloatingService` memanggil `TranslationHistory.initialize(applicationContext)` sendiri.
- Penyimpanan history menggunakan `commit()` agar hasil write dapat langsung diverifikasi.
- Logging `ScreenTL-History` mencatat apakah write berhasil.

Jika tidak ada entry setelah Manual TL, log pipeline harus menentukan apakah proses berhenti sebelum translation selesai.

### 3. Toast hanya status aplikasi
Toast memang hanya pesan status singkat. Toast bukan target output produk. Hasil translation seharusnya ditampilkan sebagai overlay di atas aplikasi target dan sementara disimpan di History untuk debugging.

## Perubahan Terbaru — 2026-09-06

### `ScreenCaptureManager.kt`
- Menambahkan diagnostic logging untuk MediaProjection, ukuran display, VirtualDisplay, ImageReader, frame, dan callback.
- Menambahkan penanganan error saat konversi `ImageReader` menjadi Bitmap.
- Menandai `captureRequested` sebagai `@Volatile`.
- Membersihkan callback saat release.

### `OcrManager.kt`
- Menambahkan logging saat OCR mulai/selesai.
- Mencatat jumlah line yang terdeteksi dan bounding box setiap line.
- Mencatat exception OCR.

### `FloatingService.kt`
- Menambahkan logging untuk lifecycle service dan seluruh pipeline Manual TL.
- Memastikan History diinisialisasi dari service, bukan hanya mengandalkan Activity.
- Memberi status completion setelah hasil translation disimpan.

### `TranslationHistory.kt`
- Menambahkan logging.
- Membuat operasi utama tersinkronisasi.
- Menggunakan `commit()` untuk memastikan hasil penyimpanan dapat diverifikasi langsung.

### Dokumentasi
- `README.md` sekarang menjadi ringkasan status, masalah aktif, roadmap, dan change log.
- `AI_README.md` dibuat sebagai aturan wajib untuk AI yang melanjutkan proyek.
- `AI_HANDOFF.md` tetap menjadi konteks teknis AI.

## Testing Manual TL

1. Build/install versi terbaru.
2. Pilih bahasa sumber dan target.
3. Tekan Play dan izinkan screen capture.
4. Buka aplikasi lain yang memiliki teks besar dan jelas.
5. Tunggu aplikasi target tampil stabil.
6. Tekan floating button → Manual TL.
7. Buka History setelah proses selesai.
8. Periksa Logcat menggunakan tag:
   - `ScreenTL-Capture`
   - `ScreenTL-OCR`
   - `ScreenTL-Service`
   - `ScreenTL-History`

### Interpretasi hasil
- Jika `ScreenTL-Capture` tidak menunjukkan `Fresh screen frame captured successfully`, masalah berada di capture pipeline.
- Jika capture sukses tetapi `ScreenTL-OCR` hanya menemukan jam/status bar, periksa bitmap/frame dan orientasi/ukuran sebelum mengubah OCR.
- Jika OCR menemukan teks target tetapi History kosong, periksa `ScreenTL-Service` dan `ScreenTL-History` pada tahap translation.

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

- [ ] Buat overlay berdasarkan `DetectedText.boundingBox`.
- [ ] Tampilkan terjemahan di posisi teks asli.
- [ ] Jangan mengganggu interaksi aplikasi target.
- [ ] Refresh/hide/clear overlay.

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
