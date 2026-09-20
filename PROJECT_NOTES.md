# Screen-TL — Catatan Proyek

## Tujuan
Screen-TL adalah aplikasi Android untuk menerjemahkan teks yang terlihat di layar menggunakan floating button. Target akhirnya adalah Manual Translation dan Real-Time Translation dengan hasil terjemahan sebagai overlay di atas teks asli aplikasi lain.

## Status Saat Ini
- Screen Capture: berhasil membuat screenshot; cakupan frame terhadap aplikasi target masih perlu diverifikasi lebih luas.
- Floating button: berhasil tampil dan dapat digeser; toggle menu perlu uji ulang.
- Permission overlay dan MediaProjection: berhasil.
- OCR: ML Kit menghasilkan line + bounding box; metadata layout tambahan sudah diterapkan.
- Google ML Kit Translation: pipeline Manual TL sudah berjalan sampai History dan overlay pada pengujian sebelumnya.
- Translation History: persisten dan service-safe.
- Translation cache: cache LRU persisten untuk hasil sukses telah dipasang di `TranslationManager`; belum diverifikasi CI/perangkat.
- Manual overlay: coordinate space diperbaiki; sizing/fitting terbaru menghindari text stretch.
- Eksperimen blur + bounding box sekarang berada di branch `experiment/blur-bounding-box`; belum boleh dianggap siap merge ke `main` sebelum build dan device test.
- Hapus Overlay: tersedia di menu; perlu uji ulang pengguna.
- Real-Time TL: dasar terbukti bekerja, flicker ditunda.

## Masalah Aktif

### 1. Capture/OCR
Frame full display dan cakupan OCR pada aplikasi target masih perlu diverifikasi. Filter status bar dan UI Screen-TL belum dibuat.

### 2. Translation Overlay
Uji sebelumnya menunjukkan hasil translation tampak seperti layar diperkecil: bagian atas bergeser turun dan bagian bawah bergeser naik. Ini menunjukkan ruang koordinat window overlay tidak sama dengan ruang koordinat frame capture.

Perbaikan coordinate space:
- window overlay dibuat dengan `sourceWidth x sourceHeight`;
- `FLAG_LAYOUT_NO_LIMITS` diganti dengan `FLAG_LAYOUT_IN_SCREEN`;
- Android R+ memakai `setFitInsetsTypes(0)`;
- Android P+ memakai `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`;
- renderer memakai scale langsung tanpa centering/aspect compensation.

### Eksperimen Branch — 2026-09-07: Left-Anchored Box + Color Blur
Masukan pengguna terbaru:
1. translation harus rata kiri seperti source;
2. jika translation lebih panjang, box hanya boleh melebar ke kanan;
3. jangan memperlebar ke kiri dan jangan menggeser titik awal translation;
4. blur sebelumnya terlihat seperti tulisan bertumpuk karena menyalin screenshot mentah;
5. pengguna meminta pendekatan yang mengambil warna di belakang tulisan lalu membuat efek blur-like;
6. eksperimen harus berada di branch terpisah agar `main` tetap aman dan mudah rollback.

Branch eksperimen:
`experiment/blur-bounding-box`

Implementasi branch:
- `TranslationOverlayView.kt` mengunci `left = source left`;
- lebar box minimum mengikuti source dan maksimum sekitar `1.55x` source width;
- ruang tambahan hanya tumbuh ke kanan;
- font tetap uniform (`textScaleX = 1.0`), lalu `textSize` diperkecil bila perlu;
- background tidak lagi menyalin raw screenshot patch;
- `TextLayoutAnalyzer.kt` hanya mengambil warna dari area sekitar OCR box;
- overlay merekonstruksi field warna penuh-opaque dengan gradient lembut dari warna lokal tersebut, sehingga source glyph tidak digambar ulang;
- pendekatan ini disebut **color-reconstructed blur-like background**, bukan true backdrop blur.

Alasan pendekatan:
- menyalin screenshot mentah di belakang translation terbukti menghasilkan source glyph yang tetap terlihat/bertumpuk;
- hanya menggunakan warna sekitar menghilangkan glyph source sepenuhnya;
- gradient lembut memberi transisi visual yang lebih dekat ke blur daripada kotak warna datar.

### Rollback / Membatalkan Eksperimen Blur
Jangan merge branch eksperimen ke `main` sebelum hasil perangkat dinyatakan bagus.

Jika blur ingin dibatalkan:
1. biarkan `main` pada commit sebelum eksperimen blur/bounding-box;
2. jangan cherry-pick commit branch eksperimen;
3. untuk kembali ke pendekatan sebelum permintaan blur, gunakan renderer mask/background-color lama dan hapus ketergantungan pada `blurredPatch` di pipeline OCR/overlay;
4. branch `experiment/blur-bounding-box` boleh dipertahankan sebagai arsip eksperimen, sehingga kode eksperimen tidak hilang.

Catatan: branch eksperimen dibuat dari head `main` setelah perbaikan build `20e1a2fc472f7d7d67d955429c6de093c7b7c14e` dan dokumentasi `7ea890445bd64d7de999b342f44647ae7e0af7a4`.

Status: **eksperimen belum diverifikasi build/perangkat**.

### 3. Floating Menu / Capture Menu
Uji sebelumnya menunjukkan submenu Screen-TL ikut masuk ke frame Manual TL.

Perbaikan:
- `triggerManualTranslation()` menutup submenu terlebih dahulu;
- capture ditunda 200 ms setelah menu menjadi `GONE`.

Status: **perlu uji ulang perangkat**.

### 4. Floating Button Toggle
Bug touch listener sebelumnya menyebabkan tap kedua membuka kembali menu.

Perbaikan:
- `ACTION_DOWN` hanya merekam posisi;
- tap sederhana diproses pada `ACTION_UP`;
- drag yang melewati threshold menutup submenu.

Status: **perlu uji ulang perangkat**.

## Build Verification
Build blur/bounding-box sebelumnya gagal pada `FloatingService.kt:528` karena `DetectedText.blurredPatch` didefinisikan sebagai `val` tetapi ownership transfer mencoba mengosongkannya. Perbaikan dilakukan dengan mengubah property menjadi `var` pada commit `20e1a2fc472f7d7d67d955429c6de093c7b7c14e`.

Build terakhir yang benar-benar diverifikasi sukses sebelum iterasi blur adalah GitHub Actions run `34112476978` (run #88), commit `0c0c9d4a6d1e4d27e4b0f5124c391598a19c027e`.

Commit branch eksperimen saat ini terakhir:
- `TranslationOverlayView.kt`: `c645aa13e488ef58270918975c86c929dbdab416`
- `TextLayoutAnalyzer.kt`: `225cf4b69235a5032cd550e40172db0ede101623`

**Jangan menyatakan build eksperimen lulus sebelum GitHub Actions selesai dan device test dilakukan.**

## Prioritas Berikutnya
1. Build branch `experiment/blur-bounding-box`.
2. Jika build gagal, perbaiki branch saja; jangan ubah `main` untuk eksperimen visual.
3. Uji APK pada perangkat.
4. Pastikan translation dimulai tepat di kiri source.
5. Pastikan box hanya melebar ke kanan dan tidak lebih dari batas yang ditentukan.
6. Pastikan source glyph tidak terlihat/bertumpuk.
7. Nilai apakah color-reconstructed blur-like background terlihat natural pada background flat dan kompleks.
8. Jika eksperimen bagus, baru pertimbangkan merge/cherry-pick ke `main`.
9. Jika eksperimen buruk, rollback cukup dengan tidak merge branch dan kembali memakai implementasi sebelum blur.
10. Real-Time flicker tetap ditunda.

## Aturan untuk AI Berikutnya
- Baca `README.md`, `AI_README.md`, dan `AI_HANDOFF.md` sebelum perubahan besar.
- Periksa file aktual di branch yang sedang dikerjakan.
- Manual TL adalah prioritas.
- Jangan mengerjakan Real-Time flicker kecuali diminta.
- Perubahan eksperimen visual harus dilakukan di branch terpisah bila diminta pengguna.
- Setiap perubahan bermakna harus dicatat beserta alasan dan rollback path.
- Fitur yang belum diuji perangkat diberi status belum teruji.
- Jangan mengklaim build lulus tanpa hasil build nyata.
