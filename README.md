# Screen-TL

Android screen translator yang dirancang untuk menerjemahkan teks dari aplikasi lain melalui screen capture, OCR, translation, dan overlay.

## Status Saat Ini

| Bagian | Status | Catatan |
|---|---|---|
| Floating button | ✅ | Tampil dan dapat digeser di atas aplikasi lain |
| Overlay permission | ✅ | Berjalan |
| MediaProjection | ⚠️ | Screenshot berhasil dibuat; Android 14+ diarahkan ke full display agar aplikasi target dapat ikut tertangkap |
| OCR | ⚠️ | Sudah menghasilkan teks, tetapi cakupan seluruh aplikasi target masih perlu diverifikasi |
| Manual Translation | ✅ | Capture → OCR → translation → History → overlay sudah berhasil pada pengujian perangkat terbaru |
| Translation History | ✅ | Persisten; service-safe dan write menggunakan `commit()` |
| Translation overlay | 🧪 | Visual overlay baru dirapikan; patch belum diuji pengguna |
| Real-Time Translation | ⚠️ | Loop capture → OCR → translation → overlay sudah bekerja pada perangkat, tetapi overlay masih berkedip |

## Masalah Aktif

1. Frame MediaProjection masih perlu dipastikan konsisten menangkap aplikasi target dan bukan hanya jam/status bar atau UI Screen-TL.
2. Overlay masih perlu verifikasi posisi terhadap koordinat layar pada berbagai perangkat/orientasi.
3. Real-Time tahap pertama memproses frame secara berkala dan berurutan. Change detection dan cache translation belum dibuat.
4. Real-Time tidak menulis setiap frame ke History agar History tidak dipenuhi duplikasi.
5. APK update masih bentrok setelah `versionCode` dinaikkan. Dugaan utama tetap perbedaan signing key antara APK lama dan APK GitHub Actions.
6. **Bug Real-Time terbaru:** pengguna mengonfirmasi Real-Time berhasil menerjemahkan, tetapi overlay berkedip. Polanya: overlay muncul sekitar 2 detik, hilang, muncul lagi; kadang jeda hilang mencapai sekitar 4 detik.
7. **Patch visual overlay terbaru belum diuji pengguna.**

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
- [~] Rapikan visual overlay — patch sudah dibuat, belum diuji pengguna.

### Milestone 3 — Real-Time Translation

- [x] Capture frame berkala tahap pertama.
- [x] OCR dan translation loop dasar.
- [x] Update overlay dari hasil frame terbaru.
- [x] Stop Real-Time tanpa menghentikan service.
- [~] **Hilangkan kedipan overlay pada loop Real-Time.**
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

## Catatan Perubahan

Setiap perubahan kode yang bermakna wajib dicatat di sini atau pada `PROJECT_NOTES.md`, termasuk tanggal, file, perubahan, alasan, hasil build/test, dan masalah yang masih tersisa.

### 2026-09-07 — Perapian visual Translation Overlay

- `TranslationOverlayView.kt` diperbarui tanpa mengubah pipeline capture/OCR/translation.
- Background overlay dibuat lebih ringan dan sudutnya lebih halus.
- Padding teks dibuat konsisten.
- Ukuran teks sekarang adaptif terhadap tinggi/lebar bounding box dengan batas minimum/maksimum.
- Translation panjang diperkecil agar lebih aman masuk ke box dan dipotong dengan ellipsis jika tetap terlalu panjang.
- Teks dipusatkan secara vertikal dan di-clipping agar tidak meluber ke luar bounding box.
- `FLAG_NOT_TOUCHABLE` dan perilaku overlay tetap dipertahankan.
- **Status:** kode sudah diterapkan pada commit `55233dfe6a1d2d200bae92ed970944418d914ccd`, tetapi belum diuji pengguna.
- UI belum dianggap final.

### 2026-09-07 — Verifikasi Real-Time oleh pengguna

- Pengguna berhasil menjalankan Real-Time TL pada perangkat.
- Capture → OCR → translation → overlay terbukti berjalan berulang.
- Masalah yang ditemukan: overlay berkedip/hilang di antara siklus. Overlay biasanya terlihat sekitar 2 detik, lalu hilang, kemudian muncul kembali; pada beberapa siklus jeda dapat sekitar 4 detik.
- Penyebab yang dicurigai adalah loop sebelumnya melepas (`removeTranslationOverlay()`) overlay sebelum setiap capture, sehingga ada periode ketika WindowManager memang tidak memiliki overlay.
- Perbaikan kedipan belum diterapkan.
- **Build/test status:** Real-Time dasar sudah diuji pengguna dan berhasil secara fungsi; patch visual overlay terbaru belum diuji.

### 2026-09-06 — Perbaikan timeout Manual TL

- `FloatingService.kt`: timeout menunggu frame dipangkas dari 10 detik menjadi 3 detik.
- `FloatingService.kt`: menambahkan watchdog pemrosesan terpisah selama 30 detik setelah frame berhasil diterima.
- `FloatingService.kt`: jika OCR atau translation callback tidak kembali, floating button dipulihkan dan pending state dibersihkan.
- `FloatingService.kt`: menambahkan status Toast setelah frame diterima agar tahap capture dan OCR dapat dibedakan saat pengujian.
- Tujuan: timeout 3 detik hanya untuk kegagalan capture, bukan untuk download model translation yang memang dapat memerlukan waktu lebih lama.

### 2026-09-06 — Perbaikan Manual TL yang macet setelah download model

- `FloatingService.kt`: floating button tidak lagi disembunyikan sebelum frame capture diterima.
- `ScreenCaptureManager.kt`: menambahkan pembatalan pending capture agar request yang menggantung dapat dihentikan dengan aman.
- `FloatingService.kt`: menambahkan timeout capture dan guard agar Manual TL kedua tidak berjalan ketika proses pertama masih pending.
- `FloatingService.kt`: menambahkan penanganan exception di sekitar OCR, translation preparation, translation invocation, dan penyimpanan/display hasil agar kegagalan tidak diam-diam meninggalkan service dalam keadaan macet.

### 2026-09-06 — Perbaikan versi APK untuk update

- `app/build.gradle.kts`: `versionCode` dinaikkan dari `1` menjadi `2`.
- `versionName` dinaikkan dari `1.0` menjadi `1.1`.
- Setelah pengujian terbaru masih terjadi konflik update, kemungkinan masalah sekarang adalah signing key APK lama vs APK GitHub Actions, bukan lagi `versionCode`.

### 2026-09-06 — Implementasi translation overlay pertama

- Menambahkan `TranslationOverlayView.kt` untuk menggambar hasil terjemahan di atas layar berdasarkan bounding box hasil OCR.
- Mengubah `FloatingService.kt` agar setiap hasil translation menyimpan teks terjemahan + koordinat OCR.
- Overlay menggunakan `TYPE_APPLICATION_OVERLAY` dan `FLAG_NOT_TOUCHABLE`, sehingga hasil dapat berada di atas aplikasi target tanpa mengambil alih sentuhan pengguna.
- Setelah pengujian menemukan regresi capture, overlay lama sekarang dilepas sepenuhnya sebelum Manual TL capture berikutnya.

### 2026-09-06 — Diagnosis pipeline dan perbaikan batas OCR

- Pengujian perangkat menghasilkan History dengan tiga hasil: `23:04`, `Status`, dan `Succese`.
- Dari inspeksi kode ditemukan `detectedTexts.take(3)` di `FloatingService`, sehingga manual translation memang sengaja hanya memproses maksimal tiga baris.
- Menghapus pembatas tersebut agar semua baris yang ditemukan OCR diproses pada Manual TL.
- MediaProjection Android 14+ tetap menggunakan konfigurasi default display/full display.

### 2026-09-06 — Diagnosis pipeline

- Menambahkan logging bertag pada screen capture, OCR, service, dan history.
- Memastikan `TranslationHistory` dapat diinisialisasi langsung dari `FloatingService`.
- Mengubah penyimpanan history menjadi `commit()` agar keberhasilan penulisan dapat diverifikasi langsung.
- Mengubah permintaan MediaProjection pada Android 14+ untuk meminta konfigurasi default display/full display.

## Dokumen Pengembangan

- `PROJECT_NOTES.md` — catatan teknis dan riwayat kerja untuk pemilik proyek.
- `AI_HANDOFF.md` — konteks teknis untuk AI yang melanjutkan pekerjaan.
- `AI_README.md` — aturan operasional AI: wajib membaca dokumentasi, mencatat perubahan, dan memperbarui roadmap.
