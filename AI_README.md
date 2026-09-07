# Screen-TL — AI Development Rules

Dokumen ini wajib dibaca oleh AI sebelum mengubah kode repository.

## Cara Mulai Jika Pindah Akun / AI

Repository GitHub adalah source of truth. Jangan mengandalkan riwayat chat akun sebelumnya.

Urutan wajib:
1. Baca `AI_README.md`.
2. Baca `AI_HANDOFF.md`.
3. Baca `PROJECT_NOTES.md`.
4. Baca `README.md` untuk gambaran proyek.
5. Periksa file implementasi aktual yang berkaitan dengan tugas.
6. Periksa status GitHub Actions/build sebelum mengklaim build berhasil.

Jika konteks percakapan tidak tersedia, dokumen-dokumen di atas harus cukup untuk melanjutkan pekerjaan tanpa menebak keadaan proyek.

## Aturan Utama

1. Repository adalah source of truth. Selalu inspeksi file aktual sebelum mengasumsikan implementasi masih sama dengan dokumentasi.
2. Jangan mengulang pekerjaan yang sudah selesai tanpa alasan teknis.
3. Lakukan perubahan kecil dan dapat diuji.
4. Jangan menganggap fitur bekerja hanya karena kode terlihat benar. Nyatakan hasil build/test secara jujur.
5. Setelah perubahan kode yang bermakna, wajib mencatat perubahan dan memperbarui roadmap.
6. Setiap sesi harus meninggalkan konteks yang cukup agar AI berikutnya dapat melanjutkan tanpa menebak-nebak.
7. Jika menemukan bug, catat gejala, dugaan penyebab, eksperimen, dan hasilnya.
8. Jangan menghapus dokumentasi historis hanya untuk membuat status terlihat lebih rapi.

## Setelah Mengubah Kode

- Catat tanggal, file, perubahan, alasan teknis, hasil build/test, dan blocker.
- Perbarui `README.md`, `PROJECT_NOTES.md`, dan `AI_HANDOFF.md` jika perubahan bermakna.
- Gunakan status `[x]` hanya untuk hal yang benar-benar sudah diverifikasi.
- Gunakan `[~]` untuk implementasi yang belum stabil atau belum diuji perangkat.
- Laporkan kepada pemilik: file berubah, tujuan, commit, verifikasi, dan langkah pengujian berikutnya.

## Prinsip Produk

Tujuan utama:
`Aplikasi lain → Screen Capture → OCR → Translation → Overlay di atas aplikasi lain`

Toast hanya untuk status/error singkat. Hasil terjemahan tidak boleh bergantung pada Toast.

## Kondisi Terkini — 2026-09-07

### Manual TL
Pipeline capture → OCR → Google ML Kit Translation → History → overlay sudah pernah berhasil pada perangkat.

Fokus saat ini adalah stabilisasi overlay Manual TL. Uji perangkat menunjukkan ukuran overlay sudah lebih dekat dengan target, tetapi masih perlu perapian. Pengguna juga meminta blur lokal dan bounding box translation yang boleh lebih panjang dari source tetapi tidak berlebihan.

### Iterasi Terbaru — Blur + Bounding Box
- `TextLayoutAnalyzer.kt`: membuat local blurred patch dari screenshot menggunakan downsample/upscale ringan.
- `OcrManager.kt`: meneruskan patch blur bersama `DetectedText`.
- `TranslationOverlayView.kt`: translation bounding box dapat melebar maksimal sekitar 155% dari source width, tetap centered dan clamped ke layar.
- `TranslationOverlayView.kt`: fitting translation memakai `textSize` uniform dan `textScaleX = 1.0` agar glyph tidak stretch.
- `FloatingService.kt`: memindahkan ownership patch ke overlay setelah translation berhasil dan membersihkan patch pada failure/removal.

**Catatan teknis:** ini adalah local screenshot-patch blur, bukan true backdrop blur terhadap window aplikasi di bawah. Jangan menyebutnya true backdrop blur dalam status proyek.

### Hapus Overlay
Sudah diimplementasikan. Tombol muncul setelah Manual TL membuat overlay dan menghapus overlay tanpa menghentikan service/history. Belum diuji ulang setelah perubahan terbaru.

### Real-Time
Dasar Real-Time pernah berjalan tetapi overlay berkedip. Pekerjaan flicker **ditunda**. Jangan mengerjakannya kecuali pemilik meminta.

### Translation Engine
Google ML Kit on-device adalah engine aktif saat ini. DeepL dan Gemini masih roadmap.

### Build
Build otomatis berjalan setiap push ke `main` dengan Gradle 8.2 dan `gradle/actions/setup-gradle@v6`.

Build terakhir yang benar-benar diverifikasi sukses sebelum iterasi blur/bounding-box:
- GitHub Actions run `34112476978`
- `assembleDebug`: success
- artifact `ScreenTranslator-APK`
- artifact ID `10014932485`
- SHA-256 `9f5d8e06f18dd1fecaa6c559594978f25c3efe7424158cbf160bb82b80df9458`

**Penting:** commit blur/bounding-box terbaru belum memiliki hasil build yang diverifikasi dalam dokumen ini. Periksa Actions sebelum menyatakan build sukses.

### APK Update Conflict
`versionCode` sudah 2 / `versionName` 1.1. Konflik update masih diduga akibat signing key debug yang berbeda. Jangan menyatakan masalah ini selesai tanpa bukti perangkat.

## Diagnostic Logging
Gunakan tag:
- `ScreenTL-Capture`
- `ScreenTL-OCR`
- `ScreenTL-Service`
- `ScreenTL-History`

## Prioritas Berikutnya
1. Periksa GitHub Actions untuk build commit terbaru.
2. Uji APK Manual TL pada perangkat.
3. Pastikan koordinat overlay tidak lagi membuat layar terlihat mengecil.
4. Pastikan source tertutup oleh blur/mask.
5. Pastikan translation bounding box sedikit lebih panjang dari source tetapi tidak berlebihan.
6. Pastikan glyph tidak stretch.
7. Uji blur pada background sederhana dan kompleks.
8. Pastikan menu Screen-TL tidak ikut diterjemahkan.
9. Pastikan tap kedua FAB menutup menu.
10. Uji `Hapus Overlay`.
11. Setelah Manual TL stabil, baru bahas mode Manual TL baru.
12. Real-Time flicker tetap ditunda.

## Jangan Lakukan
- Jangan menganggap dokumentasi lama lebih benar daripada kode aktual.
- Jangan mengerjakan Real-Time flicker tanpa permintaan.
- Jangan menyatakan fitur/device test berhasil hanya karena build sukses.
- Jangan menyebut local screenshot-patch blur sebagai true backdrop blur.
- Jangan meminta pengguna mengulang seluruh konteks yang sudah tersedia di repository.
