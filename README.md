# Screen-TL

Android screen translator yang dirancang untuk menerjemahkan teks dari aplikasi lain melalui screen capture, OCR, translation, dan overlay.

## Status Saat Ini

| Bagian | Status | Catatan |
|---|---|---|
| Floating button | ✅ | Tampil dan dapat digeser di atas aplikasi lain |
| Overlay permission | ✅ | Berjalan |
| MediaProjection | ⚠️ | Screenshot berhasil dibuat; sekarang diarahkan ke full display pada Android 14+ agar aplikasi target dapat ikut tertangkap |
| OCR | ⚠️ | Pengujian terbaru sudah menghasilkan teks ke History (`23:04`, `Status`, `Succese`), tetapi perlu memastikan semua teks aplikasi target terbaca |
| Translation | ⚠️ | Pipeline sudah berjalan sampai History; pengujian terbaru menghasilkan beberapa pasangan teks → hasil |
| Translation History | ⚠️ | Persisten; service melakukan inisialisasi sendiri dan write menggunakan `commit()` |
| Translation overlay | ❌ | Belum dibuat |
| Real-Time Translation | ❌ | Belum ada engine realtime |

## Masalah Aktif

1. Pengujian terbaru membuktikan pipeline manual sampai History menghasilkan teks, tetapi hasil yang terlihat hanya tiga baris. Kode sebelumnya memang membatasi terjemahan dengan `detectedTexts.take(3)`, sehingga pembatas ini menjadi penyebab langsung jumlah hasil maksimal tiga baris.
2. Pembatas tiga baris sudah dihapus. Pengujian berikutnya harus memastikan seluruh baris OCR dari aplikasi target diproses.
3. Masih perlu memastikan frame MediaProjection benar-benar berasal dari aplikasi target dan bukan UI Screen-TL/status bar.
4. History sekarang berjalan lebih defensif dengan inisialisasi langsung dari `FloatingService`, logging, dan penyimpanan sinkron.
5. Toast hanya merupakan pesan status aplikasi/service; Toast bukan mekanisme untuk menampilkan terjemahan di atas aplikasi lain.
6. Tujuan produk adalah hasil terjemahan muncul sebagai overlay di atas aplikasi lain, bukan hanya di Screen-TL.

## Roadmap

### Milestone 1 — Stabilkan Manual Translation

- [x] Floating button dan permission dasar.
- [x] MediaProjection dapat membuat screenshot.
- [x] OCR manager dengan bounding box.
- [x] ML Kit Translation manager.
- [~] Verifikasi bahwa frame yang diberikan ke OCR benar-benar berasal dari aplikasi yang sedang terlihat.
- [~] Pastikan seluruh hasil OCR dan translation masuk History secara konsisten.
- [ ] Tambahkan filtering untuk status bar, floating button, dan teks UI Screen-TL yang tidak relevan.

### Milestone 2 — Translation Overlay

- [ ] Buat overlay teks berdasarkan `DetectedText.boundingBox`.
- [ ] Tampilkan terjemahan di posisi yang sesuai dengan teks asli.
- [ ] Pastikan overlay tidak mengganggu interaksi dengan aplikasi target.
- [ ] Sediakan hide/refresh/clear overlay.

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
