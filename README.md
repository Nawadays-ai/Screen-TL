# Screen-TL

Android screen translator yang dirancang untuk menerjemahkan teks dari aplikasi lain melalui screen capture, OCR, translation, dan overlay.

## Status Saat Ini

| Bagian | Status | Catatan |
|---|---|---|
| Floating button | ✅ | Tampil dan dapat digeser di atas aplikasi lain |
| Floating menu | 🧪 | Menu sekarang disiapkan untuk berpindah sisi mengikuti posisi floating button; belum diuji pengguna pada build terbaru |
| Overlay permission | ✅ | Berjalan |
| MediaProjection | ⚠️ | Screenshot berhasil dibuat; Android 14+ diarahkan ke full display agar aplikasi target dapat ikut tertangkap |
| OCR | ⚠️ | Sudah menghasilkan teks, tetapi cakupan seluruh aplikasi target masih perlu diverifikasi |
| Manual Translation | ✅ | Capture → OCR → translation → History → overlay sudah berhasil pada pengujian perangkat terbaru |
| Translation History | ✅ | Persisten; service-safe dan write menggunakan `commit()` |
| Translation overlay | 🧪 | Patch terbaru membuat area OCR tertutup penuh dan menyesuaikan ukuran teks; belum diuji pengguna |
| Hapus Overlay Manual | 🧪 | Tombol tambahan muncul saat Manual TL menghasilkan overlay dan hilang setelah overlay dihapus; belum diuji pengguna |
| Real-Time Translation | ⏸️ | Dasar sudah bekerja pada perangkat, tetapi pengembangan Real-Time ditunda sementara karena flicker |

## Fokus Saat Ini

Fokus sementara adalah **Manual TL**. Real-Time sengaja ditunda sampai desain/fungsi Manual TL berikutnya selesai.

Target overlay Manual TL:
- hasil terjemahan langsung menimpa area teks sumber;
- teks sumber tidak boleh terlihat di bawah hasil terjemahan;
- ukuran teks mengikuti ukuran bounding box teks asli;
- jika terjemahan lebih panjang, ukuran teks disesuaikan agar tetap muat;
- tersedia tombol `Hapus Overlay` setelah Manual TL menghasilkan overlay;
- tombol `Hapus Overlay` hilang ketika overlay tidak ada.

## Masalah Aktif

1. Frame MediaProjection masih perlu dipastikan konsisten menangkap aplikasi target dan bukan hanya jam/status bar atau UI Screen-TL.
2. Overlay masih perlu verifikasi posisi terhadap koordinat layar pada berbagai perangkat/orientasi.
3. Patch visual terbaru belum diuji pengguna.
4. Menu floating adaptif dan tombol Hapus Overlay belum diuji pengguna pada build terbaru.
5. Real-Time masih berkedip karena implementasi saat ini melepas overlay setiap siklus. Perbaikannya ditunda.
6. APK update masih bentrok setelah `versionCode` dinaikkan. Dugaan utama tetap perbedaan signing key antara APK lama dan APK GitHub Actions.

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
- [x] Overlay Manual TL menutup area sumber dengan background penuh.
- [x] Ukuran teks adaptif berdasarkan bounding box dan panjang terjemahan.
- [x] Tombol `Hapus Overlay` disiapkan untuk hasil Manual TL.
- [~] Verifikasi visual pada perangkat.
- [ ] Verifikasi posisi overlay terhadap koordinat layar pada berbagai perangkat/orientasi.

### Milestone 3 — Floating Menu

- [x] Menu Real-Time / Manual TL / Keluar.
- [x] Menu disiapkan untuk muncul di sisi yang sesuai dengan posisi floating button.
- [x] Tombol Hapus Overlay disembunyikan ketika tidak ada overlay Manual TL.
- [~] Verifikasi menu pada beberapa posisi floating button.

### Milestone 4 — Real-Time Translation

- [x] Capture frame berkala tahap pertama.
- [x] OCR dan translation loop dasar.
- [x] Update overlay dari hasil frame terbaru.
- [x] Stop Real-Time tanpa menghentikan service.
- [~] Hilangkan kedipan overlay pada loop Real-Time — **ditunda**.
- [ ] Verifikasi kestabilan Real-Time setelah perbaikan kedipan.
- [ ] Deteksi perubahan layar agar frame yang tidak berubah tidak diproses ulang.
- [ ] Cache translation agar teks yang sama tidak diterjemahkan berulang.
- [ ] Optimalkan interval dan beban CPU/baterai berdasarkan hasil device test.
- [ ] Update overlay hanya untuk teks baru/berubah.

### Milestone 5 — Translation Engine

- [x] Google ML Kit on-device sebagai baseline.
- [ ] DeepL API.
- [ ] Gemini AI.
- [ ] Pemilihan engine yang benar-benar terhubung ke pipeline Manual/Realtime.

### Milestone 6 — Manual TL Modes

- [ ] Tambahkan mode Manual TL baru setelah overlay dan floating menu saat ini stabil.
- [ ] Dokumentasikan perilaku mode baru sebelum implementasi.

## Catatan Perubahan

Setiap perubahan kode yang bermakna wajib dicatat di sini atau pada `PROJECT_NOTES.md`, termasuk tanggal, file, perubahan, alasan, hasil build/test, dan masalah yang masih tersisa.

### 2026-09-07 — Manual Overlay + Floating Menu

- `TranslationOverlayView.kt` diperbarui agar background translation menutup penuh bounding box OCR sehingga teks sumber tidak terlihat di bawahnya.
- Ukuran teks sekarang dimulai dari ukuran berdasarkan tinggi teks sumber dan hanya mengecil jika terjemahan terlalu lebar.
- `layout_floating_widget.xml` menambahkan tombol `Hapus Overlay` yang default-nya tersembunyi.
- `FloatingService.kt` diperbarui untuk menampilkan tombol Hapus Overlay setelah Manual TL berhasil dan menyembunyikannya ketika overlay dihapus.
- Floating menu sekarang diposisikan relatif terhadap posisi floating button: sisi kiri membuka menu ke kanan, sisi kanan membuka menu ke kiri; ketika floating button berada dekat bawah layar, menu ditempatkan di atas.
- Perubahan FloatingService mempertahankan pipeline Manual TL dan tidak memperbaiki Real-Time flicker pada tahap ini.
- **Status:** perubahan kode belum diuji pengguna pada build terbaru.
- Commit kode overlay: `1116dc9a2f5f197b10bdf2113ad88366c8bdb2dd`.
- Commit layout: `7197c6b01541727a4c0b9e106b05afefb0dc16d8`.
- Commit FloatingService: `f77d90e10a12372524e3d5e5997213ba198ea8c5`.
- **Build:** belum diklaim berhasil; workflow build manual tidak dapat dipicu dari tool GitHub yang tersedia pada sesi ini.

### 2026-09-07 — Verifikasi Real-Time oleh pengguna

- Pengguna berhasil menjalankan Real-Time TL pada perangkat.
- Capture → OCR → translation → overlay terbukti berjalan berulang.
- Masalah yang ditemukan: overlay berkedip/hilang di antara siklus.
- Perbaikan flicker ditunda agar fokus kembali ke Manual TL.

### 2026-09-06 — Perbaikan timeout Manual TL

- `FloatingService.kt`: timeout menunggu frame dipangkas dari 10 detik menjadi 3 detik.
- `FloatingService.kt`: menambahkan watchdog pemrosesan terpisah selama 30 detik setelah frame berhasil diterima.
- `FloatingService.kt`: jika OCR atau translation callback tidak kembali, floating button dipulihkan dan pending state dibersihkan.

## Dokumen Pengembangan

- `PROJECT_NOTES.md` — catatan teknis dan riwayat kerja untuk pemilik proyek.
- `AI_HANDOFF.md` — konteks teknis untuk AI yang melanjutkan pekerjaan.
- `AI_README.md` — aturan operasional AI: wajib membaca dokumentasi, mencatat perubahan, dan memperbarui roadmap.
