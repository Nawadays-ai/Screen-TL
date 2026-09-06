# Screen-TL — AI Development Rules

Dokumen ini wajib dibaca oleh AI sebelum mengubah kode repository.

## Aturan Utama

1. Repository adalah source of truth. Selalu inspeksi file aktual sebelum mengasumsikan implementasi masih sama dengan dokumentasi.
2. Jangan mengulang pekerjaan yang sudah selesai tanpa alasan teknis.
3. Lakukan perubahan kecil dan dapat diuji.
4. Jangan menganggap fitur bekerja hanya karena kode terlihat benar. Nyatakan hasil build/test secara jujur.
5. Setelah perubahan kode yang bermakna, **wajib mencatat perubahan dan memperbarui roadmap**.
6. Setiap sesi harus meninggalkan konteks yang cukup agar AI berikutnya dapat melanjutkan tanpa menebak-nebak.
7. Jika menemukan bug, catat gejala, dugaan penyebab, eksperimen yang dilakukan, dan hasilnya.
8. Jangan menghapus dokumentasi historis hanya untuk membuat status terlihat lebih rapi.

## Prosedur Sebelum Mengubah Kode

- Baca `README.md`.
- Baca `AI_HANDOFF.md`.
- Baca `PROJECT_NOTES.md`.
- Periksa file implementasi yang berkaitan langsung dengan tugas.
- Periksa workflow/build jika perubahan dapat memengaruhi kompilasi.

## Prosedur Setelah Mengubah Kode

### 1. Catat Change Log

Catat minimal:
- tanggal;
- file yang berubah;
- perubahan yang dilakukan;
- alasan teknis;
- hasil build/test;
- bug atau blocker yang masih ada.

Lokasi utama: `README.md` dan/atau `PROJECT_NOTES.md`.

### 2. Perbarui Roadmap

Gunakan status:
- `[x]` selesai dan sudah diverifikasi;
- `[~]` sedang dikerjakan/belum terbukti stabil;
- `[ ]` belum dikerjakan.

Jangan memakai `[x]` hanya karena implementasi sudah ditulis. Fitur harus benar-benar diverifikasi.

### 3. Perbarui Handoff

Jika arsitektur, flow, bug, atau prioritas berubah, perbarui `AI_HANDOFF.md` agar AI berikutnya mendapat konteks terbaru.

### 4. Laporkan ke Pemilik

Setelah perubahan langsung ke repository, jelaskan:
- file apa saja yang berubah;
- apa tujuan tiap perubahan;
- commit yang dibuat;
- apa yang sudah diverifikasi;
- apa yang harus diuji di perangkat;
- apa yang masih belum bekerja.

## Prinsip Khusus Screen-TL

Tujuan produk bukan sekadar mengambil screenshot atau menampilkan Toast. Tujuan akhirnya adalah:

`Aplikasi lain → Screen Capture → OCR → Translation → Overlay di atas aplikasi lain`

Toast hanya boleh digunakan untuk status/error singkat. Hasil terjemahan tidak boleh bergantung pada Toast.

Untuk Manual TL, prioritas diagnosis adalah memastikan bitmap yang masuk OCR benar-benar merupakan layar aplikasi target. Jika OCR hanya menemukan jam/status bar, jangan menganggap OCR sebagai penyebab utama sebelum frame capture diperiksa.

Untuk Realtime TL, jangan menandai fitur selesai hanya karena tombol dapat berubah menjadi mode aktif. Engine capture/OCR/translation loop dan overlay harus benar-benar berjalan.

## Kondisi Terakhir yang Diketahui

Tanggal: 2026-09-06

- Screenshot berhasil dibuat.
- Floating button dan permission dasar bekerja.
- Pengujian terbaru menunjukkan OCR hanya mendeteksi teks tertentu seperti jam/status bar, bukan teks aplikasi target.
- History pernah kosong; service sekarang secara eksplisit menginisialisasi `TranslationHistory` dan penyimpanan menggunakan `commit()` untuk memudahkan verifikasi.
- Logging diagnostic telah ditambahkan pada capture, OCR, service, dan history dengan tag `ScreenTL-*`.
- Translation overlay belum dibuat.
- Realtime translation belum dibuat.

## Prioritas Berikutnya

1. Build dan install versi terbaru.
2. Jalankan Manual TL pada aplikasi target yang memiliki teks jelas.
3. Periksa log dengan tag `ScreenTL-Capture`, `ScreenTL-OCR`, `ScreenTL-Service`, dan `ScreenTL-History`.
4. Tentukan apakah bitmap capture memang berisi aplikasi target.
5. Jika capture sudah benar, lanjut diagnosis OCR/filtering.
6. Pastikan History berisi hasil setelah translation selesai.
7. Setelah Manual TL stabil, implementasikan translation overlay menggunakan bounding box.
8. Baru setelah itu bangun realtime loop dan cache.
