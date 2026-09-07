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

Fokus saat ini adalah stabilisasi overlay Manual TL. Uji perangkat terakhir menunjukkan:
- koordinat translation tampak seperti layar diperkecil: bagian atas bergeser turun dan bagian bawah bergeser naik;
- menu Screen-TL ikut diterjemahkan;
- tap kedua floating button belum menutup menu;
- ukuran font translation sudah dianggap cukup baik.

Perbaikan terbaru sudah dibuat dan berhasil di-build, tetapi belum diuji ulang di perangkat.

### Perbaikan Terbaru
- `FloatingService.kt`: overlay window memakai ukuran pixel frame capture, `FLAG_LAYOUT_IN_SCREEN`, insets Android R+ dinonaktifkan, dan cutout mode penuh untuk menyamakan ruang koordinat OCR dengan overlay.
- `FloatingService.kt`: submenu ditutup sebelum Manual TL dan capture menunggu 200 ms agar menu tidak masuk frame.
- `FloatingService.kt`: FAB tap sekarang menggunakan toggle pada `ACTION_UP`; drag tetap menutup menu setelah benar-benar bergerak.
- `TranslationOverlayView.kt`: mask sedikit diperluas dan dibuat lebih solid, menggunakan background lokal; font source tetap dipertahankan.

True backdrop blur belum diimplementasikan. Jangan mengerjakan blur sebelum alignment dasar diverifikasi.

### Hapus Overlay
Sudah diimplementasikan. Tombol muncul setelah Manual TL membuat overlay dan menghapus overlay tanpa menghentikan service/history. Belum diuji ulang setelah perubahan terbaru.

### Real-Time
Dasar Real-Time pernah berjalan tetapi overlay berkedip. Pekerjaan flicker **ditunda**. Jangan mengerjakannya kecuali pemilik meminta.

### Translation Engine
Google ML Kit on-device adalah engine aktif saat ini. DeepL dan Gemini masih roadmap.

### Build
Build otomatis berjalan setiap push ke `main` dengan Gradle 8.2 dan `gradle/actions/setup-gradle@v6`.

Build terbaru yang sudah diverifikasi sukses untuk perubahan Manual TL:
- GitHub Actions run `34112476978`
- `assembleDebug`: success
- artifact `ScreenTranslator-APK`
- artifact ID `10014932485`
- SHA-256 `9f5d8e06f18dd1fecaa6c559594978f25c3efe7424158cbf160bb82b80df9458`

Catatan: dokumentasi dapat memiliki commit lebih baru daripada artifact tersebut. Jangan menyatakan build terbaru lulus tanpa memeriksa Actions untuk commit terbaru.

### APK Update Conflict
`versionCode` sudah 2 / `versionName` 1.1. Konflik update masih diduga akibat signing key debug yang berbeda. Jangan menyatakan masalah ini selesai tanpa bukti perangkat.

## Diagnostic Logging
Gunakan tag:
- `ScreenTL-Capture`
- `ScreenTL-OCR`
- `ScreenTL-Service`
- `ScreenTL-History`

## Prioritas Berikutnya
1. Build otomatis untuk perubahan terakhir jika diperlukan.
2. Uji APK Manual TL pada perangkat.
3. Pastikan koordinat overlay tidak lagi membuat layar terlihat mengecil.
4. Pastikan menu Screen-TL tidak ikut diterjemahkan.
5. Pastikan tap kedua FAB menutup menu.
6. Pastikan source tertutup dan mask lebih solid.
7. Uji `Hapus Overlay`.
8. Setelah Manual TL stabil, baru bahas mode Manual TL baru.
9. Real-Time flicker tetap ditunda.

## Jangan Lakukan
- Jangan menganggap dokumentasi lama lebih benar daripada kode aktual.
- Jangan mengerjakan Real-Time flicker tanpa permintaan.
- Jangan menambahkan blur sebelum masalah koordinat selesai diverifikasi.
- Jangan menyatakan fitur/device test berhasil hanya karena build sukses.
- Jangan meminta pengguna mengulang seluruh konteks yang sudah tersedia di repository.
