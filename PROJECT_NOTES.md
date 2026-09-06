# Screen-TL — Catatan Proyek

## Tujuan
Screen-TL adalah aplikasi Android untuk menerjemahkan teks yang terlihat di layar menggunakan floating button. Target akhirnya adalah Manual Translation dan Real-Time Translation dengan hasil terjemahan sebagai overlay di atas teks asli.

## Status Saat Ini
- Screen Capture: berhasil.
- Floating button: berhasil tampil, dapat digeser, dan tombol bekerja.
- Permission overlay dan MediaProjection: berhasil.
- OCR dengan Google ML Kit: berhasil mendeteksi teks pada screenshot.
- OCR mendukung Jepang, Mandarin, dan Latin melalui `OcrManager`.
- Google ML Kit Translation: sudah berhasil menerjemahkan pada pengujian.
- Manual TL: screenshot → OCR → translation sudah berjalan.
- Hasil Manual TL sekarang disimpan ke Translation History, bukan mengandalkan Toast untuk hasil panjang.
- Translation History disimpan menggunakan SharedPreferences dan dibatasi 50 entri terbaru.
- Realtime TL saat ini baru mengubah status tombol; engine realtime belum dibuat.
- Overlay hasil terjemahan belum dibuat.

## Perubahan Terbaru
1. `TranslationHistory.kt` diubah dari penyimpanan RAM sementara menjadi penyimpanan persisten melalui SharedPreferences.
2. `MainActivity.kt` menginisialisasi History, menampilkan history, dan menyediakan Clear History.
3. `FloatingService.kt` mengirim hasil translation Manual TL ke History dengan timestamp dan pasangan bahasa.
4. Toast hasil translation lengkap dihilangkan karena terpotong/kurang cocok untuk hasil multi-baris.
5. Resource `TranslationManager` ditutup saat service dihentikan.

## Format History
Contoh:

```text
[21:30:15]
Inggris → Indonesia

Start Game
→ Mulai Permainan

Settings
→ Pengaturan
```

## Catatan Testing
Saat mengetes Manual TL:
1. Pilih bahasa sumber dan target.
2. Tekan Play dan izinkan screen capture.
3. Buka aplikasi lain yang memiliki teks.
4. Tekan floating button → Manual TL.
5. Kembali ke Screen-TL untuk melihat History.

Jika History kosong, periksa apakah OCR menemukan teks dan apakah proses translation selesai.

## Langkah Berikutnya
Prioritas berikutnya:
1. Pastikan build otomatis tetap hijau setelah perubahan History.
2. Tes Manual TL di aplikasi lain dan cek History.
3. Perbaiki OCR agar tidak mengambil elemen UI/status bar yang tidak relevan jika diperlukan.
4. Buat overlay hasil translation berdasarkan `DetectedText` dan `boundingBox`.
5. Setelah overlay stabil, implementasikan Real-Time Translation dengan deteksi perubahan layar dan cache translation.
6. Setelah pipeline lokal stabil, baru integrasikan DeepL/Gemini sesuai kebutuhan.

## Prinsip Pengembangan
- Ubah sedikit bagian setiap tahap.
- Jangan merusak Screen Capture/OCR yang sudah terbukti bekerja.
- Utamakan fungsi sebelum UI polish.
- Setiap perubahan penting harus dijelaskan kepada pemilik proyek.
