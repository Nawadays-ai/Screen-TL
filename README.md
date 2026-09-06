# Screen-TL

Android screen translator yang dirancang untuk menerjemahkan teks dari aplikasi lain melalui screen capture, OCR, translation, dan overlay.

## Status Saat Ini

| Bagian | Status | Catatan |
|---|---|---|
| Floating button | ✅ | Tampil dan dapat digeser di atas aplikasi lain |
| Overlay permission | ✅ | Berjalan |
| MediaProjection | ⚠️ | Screenshot berhasil dibuat; Android 14+ diarahkan ke full display agar aplikasi target dapat ikut tertangkap |
| OCR | ⚠️ | Pengujian sebelumnya menghasilkan teks ke History; cakupan seluruh aplikasi target masih perlu diverifikasi |
| Translation | ⚠️ | Pipeline manual sampai History pernah berjalan, tetapi ada bug terbaru setelah perubahan overlay yang sedang diperbaiki |
| Translation History | ⚠️ | Persisten; service-safe dan write menggunakan `commit()` |
| Translation overlay | 🧪 | Implementasi pertama ada, tetapi belum berhasil diverifikasi stabil di perangkat |
| Real-Time Translation | ❌ | Belum ada engine realtime |

## Masalah Aktif

1. Setelah menekan Manual TL pada build overlay pertama, perangkat dapat berhenti menampilkan floating button setelah model translation selesai diunduh dan tidak menghasilkan History. Ini mengindikasikan alur capture/service perlu dibuat lebih tahan terhadap frame yang tidak datang atau callback yang gagal.
2. Perbaikan terbaru tidak lagi menyembunyikan floating button sebelum `captureOnce` menerima frame. Jika frame tidak datang dalam 10 detik, pending capture dibatalkan dan floating button dipulihkan.
3. Masih perlu memastikan frame MediaProjection benar-benar berasal dari aplikasi target dan bukan UI Screen-TL/status bar.
4. Overlay terjemahan masih perlu verifikasi posisi, ukuran teks, dan kecocokan koordinat dengan layar nyata.
5. Toast hanya merupakan pesan status aplikasi/service; Toast bukan mekanisme untuk menampilkan terjemahan di atas aplikasi lain.
6. Realtime belum dikerjakan; jangan menganggap tombol Real-Time sebagai fitur yang sudah aktif hanya karena UI tombolnya ada.

## Roadmap

### Milestone 1 — Stabilkan Manual Translation

- [x] Floating button dan permission dasar.
- [x] MediaProjection dapat membuat screenshot.
- [x] OCR manager dengan bounding box.
- [x] ML Kit Translation manager.
- [~] Verifikasi bahwa frame yang diberikan ke OCR benar-benar berasal dari aplikasi yang sedang terlihat.
- [~] Pastikan seluruh hasil OCR dan translation masuk History secara konsisten.
- [ ] Tambahkan filtering untuk status bar, floating button, dan teks UI Screen-TL yang tidak relevan.
- [ ] Pastikan Manual TL tidak kehilangan floating button/service ketika menunggu frame atau model translation.

### Milestone 2 — Translation Overlay

- [x] Implementasi overlay teks berdasarkan `DetectedText.boundingBox`.
- [x] Tampilkan hasil terjemahan pada area teks yang terdeteksi.
- [x] Overlay dibuat `NOT_TOUCHABLE` agar tidak mengganggu interaksi aplikasi target.
- [ ] Verifikasi posisi overlay terhadap koordinat layar pada perangkat nyata.
- [ ] Sediakan hide/refresh/clear overlay yang mudah digunakan.
- [ ] Verifikasi overlay tidak membuat service berhenti pada perangkat target.

### Milestone 3 — Real-Time Translation

- [ ] Capture frame secara berkala.
- [ ] Deteksi perubahan layar.
- [ ] Jalankan OCR hanya ketika diperlukan.
- [ ] Cache hasil translation agar teks yang sama tidak diterjemahkan berulang.
- [ ] Update overlay hanya untuk teks baru/berubah.

### Milestone 4 — Translation Engine

- [x] Google ML Kit on-device sebagai baseline.
- [ ] DeepL API.
- [ ] Gemini AI.
- [ ] Pemilihan engine yang benar-benar terhubung ke pipeline Manual/Realtime.

## Catatan Perubahan

Setiap perubahan kode yang bermakna wajib dicatat di sini atau pada `PROJECT_NOTES.md`, termasuk tanggal, file, perubahan, alasan, hasil build/test, dan masalah yang masih tersisa.

### 2026-09-06 — Perbaikan Manual TL yang macet setelah download model

- `FloatingService.kt`: floating button tidak lagi disembunyikan sebelum frame capture diterima.
- `ScreenCaptureManager.kt`: menambahkan pembatalan pending capture agar request yang menggantung dapat dihentikan dengan aman.
- `FloatingService.kt`: menambahkan timeout 10 detik untuk capture; jika tidak ada frame, floating button dipulihkan dan pengguna mendapat pesan untuk mencoba lagi.
- `FloatingService.kt`: menambahkan guard agar Manual TL kedua tidak berjalan ketika proses pertama masih pending.
- `FloatingService.kt`: menambahkan penanganan exception di sekitar OCR, translation preparation, translation invocation, dan penyimpanan/display hasil agar kegagalan tidak diam-diam meninggalkan service dalam keadaan macet.
- `FloatingService.kt`: floating button dipulihkan pada jalur gagal maupun setelah proses selesai.
- Perubahan ini belum diverifikasi di perangkat; hasil build dan test perangkat harus dicatat setelah APK baru tersedia.

### 2026-09-06 — Perbaikan versi APK untuk update

- `app/build.gradle.kts`: `versionCode` dinaikkan dari `1` menjadi `2`.
- `versionName` dinaikkan dari `1.0` menjadi `1.1`.
- Tujuan: APK baru harus dapat dipasang sebagai update di atas instalasi Screen-TL yang sudah ada, bukan ditolak sebagai konflik versi lama.

### 2026-09-06 — Implementasi translation overlay pertama

- Menambahkan `TranslationOverlayView.kt` untuk menggambar hasil terjemahan di atas layar berdasarkan bounding box hasil OCR.
- Mengubah `FloatingService.kt` agar setiap hasil translation menyimpan teks terjemahan + koordinat OCR.
- Overlay menggunakan `TYPE_APPLICATION_OVERLAY` dan `FLAG_NOT_TOUCHABLE`, sehingga hasil dapat berada di atas aplikasi target tanpa mengambil alih sentuhan pengguna.
- Floating UI Screen-TL disembunyikan dan overlay lama dibersihkan sebelum capture berikutnya agar UI Screen-TL tidak ikut menjadi sumber OCR.
- Implementasi belum diuji dengan APK pada perangkat; status overlay masih 🧪.

### 2026-09-06 — Diagnosis pipeline dan perbaikan batas OCR

- Pengujian perangkat menghasilkan History dengan tiga hasil: `23:04`, `Status`, dan `Succese`.
- Dari inspeksi kode ditemukan `detectedTexts.take(3)` di `FloatingService`, sehingga manual translation memang sengaja hanya memproses maksimal tiga baris OCR.
- Menghapus pembatas tersebut agar semua baris yang ditemukan OCR diproses pada Manual TL.
- Belum mengklaim seluruh layar sudah terbaca; verifikasi perangkat berikutnya diperlukan.
- MediaProjection Android 14+ tetap menggunakan konfigurasi default display/full display.

### 2026-09-06 — Diagnosis pipeline

- Menambahkan logging bertag pada screen capture, OCR, service, dan history.
- Memastikan `TranslationHistory` dapat diinisialisasi langsung dari `FloatingService`.
- Mengubah penyimpanan history menjadi `commit()` agar keberhasilan penulisan dapat diverifikasi langsung.
- Mengubah permintaan MediaProjection pada Android 14+ untuk meminta konfigurasi default display/full display, karena aplikasi ini harus menangkap aplikasi lain, bukan hanya Screen-TL.
- Belum mengklaim bug capture selesai sampai perangkat mengonfirmasi bahwa teks aplikasi target masuk ke OCR.

## Dokumen Pengembangan

- `PROJECT_NOTES.md` — catatan teknis dan riwayat kerja untuk pemilik proyek.
- `AI_HANDOFF.md` — konteks teknis untuk AI yang melanjutkan pekerjaan.
- `AI_README.md` — aturan operasional AI: wajib membaca dokumentasi, mencatat perubahan, dan memperbarui roadmap.
