# Screen-TL

Android screen translator yang dirancang untuk menerjemahkan teks dari aplikasi lain melalui screen capture, OCR, translation, dan overlay.

## Status Saat Ini

| Bagian | Status | Catatan |
|---|---|---|
| Floating button | ✅ | Tampil dan dapat digeser di atas aplikasi lain |
| Floating menu | 🧪 | Menu sekarang memakai container dinamis dan penempatan relatif terhadap FAB; belum diuji pengguna |
| Overlay permission | ✅ | Berjalan |
| MediaProjection | ⚠️ | Screenshot berhasil dibuat; Android 14+ diarahkan ke full display |
| OCR | ⚠️ | ML Kit menghasilkan line + bounding box; layout analyzer baru ditambahkan dan perlu diuji |
| Manual Translation | ✅ | Capture → OCR → translation → History → overlay sudah pernah berhasil |
| Translation History | ✅ | Persisten; service-safe dan write menggunakan `commit()` |
| Translation overlay | 🧪 | Sekarang font-aware dan background-aware; perlu verifikasi perangkat |
| Hapus Overlay Manual | 🧪 | Tombol disiapkan setelah overlay Manual TL aktif |
| Real-Time Translation | ⏸️ | Dasar bekerja, tetapi flicker ditunda |

## Fokus Saat Ini

Fokus sementara adalah **Manual TL**. Real-Time sengaja ditunda sampai Manual TL stabil dan mode Manual TL baru dapat dirancang.

Target overlay Manual TL:
- hasil terjemahan langsung menimpa area teks sumber;
- source tidak boleh terlihat di bawah hasil terjemahan;
- ukuran teks mengikuti estimasi ukuran font source;
- area mask sedikit diperluas berdasarkan tinggi glyph;
- warna background area diambil dari screenshot di sekitar teks, bukan selalu hitam;
- jika translation lebih panjang, font dikecilkan hanya seperlunya;
- tersedia tombol `Hapus Overlay` setelah Manual TL menghasilkan overlay.

## Arsitektur Overlay Baru

Alur Manual TL sekarang dirancang sebagai:

`Screen Capture → ML Kit OCR → TextLayoutAnalyzer → Translation → Overlay Renderer`

`TextLayoutAnalyzer.kt` mengubah line OCR menjadi data layout:
- koordinat mask yang diperluas;
- estimasi tinggi font dari bounding box element OCR;
- estimasi warna background dari piksel di sekitar area teks.

`TranslationOverlayView.kt` kemudian:
- memakai koordinat mask tersebut;
- memakai ukuran font source sebagai ukuran awal;
- mengecilkan font bila translation terlalu lebar/tinggi;
- menggambar background hasil sampling agar source tertutup tanpa selalu memakai kotak hitam;
- tetap `FLAG_NOT_TOUCHABLE`.

## Masalah Aktif

1. Frame MediaProjection masih perlu dipastikan konsisten menangkap aplikasi target.
2. Posisi overlay perlu diverifikasi terhadap koordinat layar pada perangkat pengguna.
3. Sistem background sampling belum diuji pada background kompleks seperti gambar/gradient.
4. Floating menu adaptif dan `Hapus Overlay` belum diuji pada perangkat.
5. Real-Time masih berkedip dan sengaja ditunda.
6. APK update masih bentrok; dugaan utama tetap perbedaan signing key.

## Roadmap

### Milestone 1 — Stabilkan Manual Translation
- [x] Floating button dan permission dasar.
- [x] MediaProjection dapat membuat screenshot.
- [x] OCR manager dengan bounding box.
- [x] ML Kit Translation manager.
- [~] Verifikasi frame target dan cakupan OCR.
- [x] Manual TL capture → OCR → translation → History → overlay pernah berhasil.
- [ ] Filter status bar/floating button/teks Screen-TL yang tidak relevan.
- [x] Timeout capture 3 detik + watchdog proses 30 detik.

### Milestone 2 — Translation Overlay
- [x] Overlay berdasarkan OCR line.
- [x] Overlay non-touchable.
- [x] TextLayoutAnalyzer font-aware.
- [x] Mask OCR diperluas berdasarkan tinggi glyph.
- [x] Background area diestimasi dari screenshot.
- [x] Ukuran translation mengikuti source dan menyusut jika perlu.
- [~] Verifikasi visual pada perangkat.
- [ ] Verifikasi berbagai resolusi/orientasi.
- [ ] Penanganan background kompleks.

### Milestone 3 — Floating Menu
- [x] Real-Time / Manual TL / Hapus Overlay / Keluar.
- [x] Container menu dinamis.
- [x] Menu kiri/kanan mengikuti FAB.
- [x] Menu ditempatkan di atas ketika FAB dekat bawah.
- [~] Verifikasi beberapa posisi FAB pada perangkat.

### Milestone 4 — Real-Time Translation
- [x] Capture frame berkala tahap pertama.
- [x] OCR + translation loop dasar.
- [x] Update overlay.
- [~] Hilangkan flicker — ditunda.
- [ ] Change detection.
- [ ] Translation cache.
- [ ] Optimasi CPU/baterai.

### Milestone 5 — Translation Engine
- [x] Google ML Kit on-device sebagai baseline.
- [ ] DeepL API.
- [ ] Gemini AI.
- [ ] Pemilihan engine terhubung ke pipeline.

### Milestone 6 — Manual TL Modes
- [ ] Tambahkan mode Manual TL baru setelah overlay saat ini stabil.
- [ ] Dokumentasikan perilaku mode baru sebelum implementasi.

## Perubahan Terbaru — 2026-09-07

### Font-aware + background-aware Manual Overlay
File baru: `TextLayoutAnalyzer.kt`.

Perubahan:
- mengestimasi tinggi font dari element OCR;
- memperluas area mask agar glyph source tertutup;
- mengambil warna background dari sekitar source.

`OcrManager.kt` sekarang membawa metadata layout tersebut sampai hasil translation.

`TranslationOverlayView.kt` menggunakan metadata itu untuk merender translation dengan ukuran yang mengikuti source.

Commit utama:
- `e0ec39f595f2bb66e09d46d5be469fd7ae6deaab`
- `28dab21490b73a5c94848971259c9096207cca43`
- `513e43e1f8c190903f2d1dc539028f0fed9e3e28`

### Floating Menu
`layout_floating_widget.xml` dan `FloatingService.kt` direvisi agar root WindowManager benar-benar menyesuaikan ukuran menu dan posisi FAB.

Commit:
- `e59a2b3adef5ff5391dcda1a12df8f0d6d19ec5e`
- `e85ef0b3d7e5881c282dc98e77108d554e389f8f`

**Status semua perubahan di atas: belum diuji pengguna dan belum boleh dianggap stabil.**

## Catatan Build
Tool GitHub pada sesi ini tidak menyediakan aksi untuk memulai `workflow_dispatch`. Jangan mengklaim build baru berhasil sampai ada hasil workflow yang nyata.

## Dokumen Pengembangan
- `PROJECT_NOTES.md` — catatan teknis dan riwayat kerja.
- `AI_HANDOFF.md` — konteks teknis untuk AI berikutnya.
- `AI_README.md` — aturan operasional AI.
