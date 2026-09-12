# Screen-TL — Rencana Refactor Besar

Branch kerja: `experiment/drive-test-big-change`

Dokumen ini menjadi catatan utama pengerjaan. Setiap tahap harus ditandai `[x]` setelah benar-benar selesai di kode dan telah diperiksa. Build APK tidak dilakukan pada setiap perubahan kecil; build dilakukan setelah rangkaian perubahan utama selesai.

## Aturan dan pengecualian khusus branch ini

- [x] Branch kerja menggunakan `experiment/drive-test-big-change`.
- [x] Titik awal branch berasal dari `experiment/telegram-test-upload`.
- [ ] **Jangan membuat GitHub Release otomatis.**
- [ ] Jangan mengandalkan GitHub Actions Artifact sebagai hasil utama karena keterbatasan kuota.
- [ ] Kembalikan mekanisme upload Telegram seperti pada `experiment/telegram-test-upload`.
- [ ] Sebelum dikirim ke Telegram, APK dikemas menjadi arsip ZIP. Jangan mengirim APK mentah sebagai hasil utama.
- [ ] Branch `experiment/telegram-test-upload` harus diperlakukan sebagai referensi dan tidak diubah.
- [ ] README lama, catatan AI lama, dan panduan lama boleh diabaikan atau dihapus bila mengganggu; dokumen ini menjadi catatan kerja utama.
- [ ] Semua perubahan besar dikerjakan terlebih dahulu, kemudian satu kali build dan pengujian terpadu.
- [ ] Jangan menyatakan fitur selesai hanya berdasarkan pemeriksaan kode; status penuh harus mempertimbangkan hasil build dan pengujian pada perangkat.

## Urutan pengerjaan

### 1. Audit workflow build dan upload Telegram
- [ ] Periksa workflow saat ini.
- [ ] Hapus atau nonaktifkan seluruh langkah GitHub Release otomatis.
- [ ] Pulihkan upload Telegram dari branch referensi.
- [ ] Tambahkan pengemasan APK menjadi ZIP sebelum upload.
- [ ] Pastikan token/secret yang digunakan hanya dibaca dari GitHub Secrets.
- [ ] Tandai tahap ini selesai setelah workflow diperiksa ulang.

### 2. Audit dan perbaikan OCR
- [ ] Periksa dependensi Google ML Kit melalui Google Play Services.
- [ ] Periksa alur OCR biasa untuk Jepang, Mandarin, Inggris, dan Indonesia.
- [ ] Periksa Manga OCR: pengunduhan model, validasi file, preprocessing, input ONNX, decoder, tokenizer, dan penanganan error.
- [ ] Pastikan model Manga OCR tidak dianggap siap sebelum benar-benar lolos build dan pengujian.
- [ ] Perbaiki masalah teknis yang ditemukan.
- [ ] Pastikan bitmap/crop sementara dibersihkan setelah OCR selesai atau gagal.
- [ ] Tandai tahap ini selesai setelah seluruh alur dapat ditelusuri dan diperiksa.

### 3. Perbaikan Gemini API
- [ ] Gunakan model Gemini Flash/Flash-Lite yang valid.
- [ ] Gunakan system instruction penerjemah tanpa tambahan penjelasan.
- [ ] Set temperature ke `0.0`.
- [ ] Hilangkan request percobaan yang tidak perlu pada `prepare()`.
- [ ] Tambahkan batching sekitar 10–20 kalimat jika arsitektur memungkinkan.
- [ ] Gunakan keluaran JSON array untuk batch dan parsing yang aman.
- [ ] Gunakan batas token keluaran yang menyesuaikan panjang input.
- [ ] Tampilkan error yang jelas jika API key belum diatur atau request gagal.

### 4. Pilihan OCR, bahasa, dan mode terjemahan
- [ ] Pastikan pengguna dapat memilih mode OCR.
- [ ] Pastikan source language dan target language diterapkan secara konsisten.
- [ ] Pastikan mode offline menggunakan ML Kit Translation.
- [ ] Pastikan model bahasa offline diunduh otomatis ketika diperlukan.
- [ ] Tampilkan status unduhan dan error dengan jelas.
- [ ] Pastikan mode online menggunakan provider yang dipilih dan memberi Toast jika konfigurasi API belum tersedia.

### 5. Desain floating panel
- [ ] Redesain tombol dan panel agar minimalis, nyaman, dan mengikuti gambar referensi.
- [ ] Tambahkan indikator online/offline beserta ikon.
- [ ] Tambahkan tombol expand/collapse.
- [ ] Saat diperluas, tampilkan pilihan online/offline, inpaint, loop, dan tombol mode kecil.
- [ ] Saat diminimalkan, tampilkan satu tombol kecil.
- [ ] Pastikan panel dan tombol dapat dipindahkan tanpa mengganggu fungsi utama.

### 6. Fitur perilaku tambahan
- [ ] Implementasikan pilihan inpaint aktif/nonaktif.
- [ ] Implementasikan loop dengan area snip yang tetap.
- [ ] Saat loop aktif, hilangkan mask layar yang tidak diperlukan dan terjemahkan dialog berulang.
- [ ] Pastikan tombol Cancel/Exit tetap berfungsi.
- [ ] Pastikan hasil terjemahan dan riwayat tidak hilang tanpa alasan.

### 7. Manajemen memori dan stabilitas
- [ ] Pastikan bitmap screenshot, crop, dan patch sementara dilepas pada jalur sukses maupun gagal.
- [ ] Pastikan recognizer, ONNX session, dan HTTP client ditutup dengan benar.
- [ ] Hindari proses OCR/terjemahan berjalan ganda secara bersamaan.
- [ ] Tambahkan logging yang berguna tanpa membocorkan API key.

### 8. Pemeriksaan akhir dan build terpadu
- [ ] Periksa diff seluruh perubahan.
- [ ] Periksa kemungkinan error compile dan resource.
- [ ] Jalankan build GitHub Actions satu kali setelah perubahan utama selesai.
- [ ] Periksa hasil build.
- [ ] Periksa arsip ZIP dan upload Telegram.
- [ ] Catat hasil pengujian perangkat, termasuk fitur yang belum dapat diverifikasi.
- [ ] Tandai tahap akhir selesai hanya setelah hasil nyata tersedia.

## Status saat dokumen dibuat

- Audit awal menemukan bahwa Manga OCR memakai ML Kit untuk menemukan wilayah teks lalu ONNX untuk mengenali teks.
- Kesiapan Manga OCR **belum terverifikasi penuh**; jangan menganggapnya sudah siap digunakan sebelum input model, decoder, tokenizer, model download, build, dan pengujian perangkat diperiksa.
- Tidak ada build terpadu yang dijalankan pada tahap pembuatan catatan ini.
