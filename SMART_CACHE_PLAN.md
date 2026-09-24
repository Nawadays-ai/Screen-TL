# Smart Cache + Batch Translation — Working Plan

> **Scope:** catatan kerja khusus fitur Smart Cache/Translation Unit/Batch Translation.
> **Aturan konteks:** gunakan file ini sebagai konteks utama untuk task ini. Jangan meminta pembacaan README, AI handoff, atau catatan proyek lain untuk memahami rencana ini.
> **Status:** Tahap 0, 1, dan 2 selesai; tahap aktif berikutnya adalah Tahap 3 (validasi hasil).
> **Build policy:** jangan menjalankan Gradle/compile/APK lokal. Build hanya melalui GitHub Actions.

## Keputusan yang sudah selesai

- [DONE] Cache Klip saat ini memakai seluruh crop sebagai satu source/cache item. Karena itu History dapat menunjukkan `0/1` setiap kali OCR gabungan berubah.
- [DONE] Tiga contoh Klip belum identik: area seleksi, jumlah baris, spasi, dan OCR berbeda. Itu menjelaskan cache miss, tetapi belum membuktikan cache dasar rusak.
- [DONE] Target arsitektur: `OCR → segmentation → Translation Units → normalize → deduplicate → cache lookup → translate misses → merge → geometry/overlay`.
- [DONE] Manual dan Klip memakai cache yang sama. Cache key tidak bergantung pada geometry, posisi, atau mode Manual/Klip.
- [DONE] Cache normalization konservatif: Unicode, line ending, whitespace, dan trim. Jangan memperbaiki typo OCR Jepang secara agresif.
- [DONE] Translation Unit tidak selalu sama dengan OCR line atau seluruh OCR block. Paragraph/bubble jelas tetap satu unit; UI item berdampingan tidak otomatis digabung.
- [DONE] Batch tidak memecah unit semantik atau menggabungkan hasil menjadi satu paragraf. Batch hanya optimasi transport.
- [DONE] Provider batching bersifat provider-specific. DeepL kandidat pertama; OpenRouter memerlukan response ber-ID; ML Kit boleh tetap unit-by-unit.
- [DONE] Implementasi bertahap: verifikasi cache dasar → unit/cache per unit → deduplication → validasi overlay → batching provider.

## Tahapan kerja

### Tahap 0 — Verifikasi cache dasar

Status: [DONE]

- Crop fix diterapkan: Klip sekarang translasi origin mask, bukan scale proporsional. Unit OCR: 1 konsisten untuk elemen tunggal.
- Cache dasar berfungsi: hit `1/1` saat source identik (19:26:06, 19:26:17).
- Miss pada multi-block karena arsitektur cache gabungan (satu key untuk seluruh crop), bukan bug cache. Target Tahap 1.

- Gunakan layar/screenshot yang sama dan pilih isi tulisan yang sama. Ukuran/posisi crop boleh berbeda selama semua tulisan target tetap masuk, jelas, dan tidak ada tulisan lain.
- Jalankan Klip pertama kali dan simpan/catat History lengkapnya.
- Jalankan Klip kedua kali dengan crop yang sedikit berbeda tetapi isi target tetap sama.
- Bandingkan source OCR dua entry: karakter, spasi, line break, tanda baca, dan karakter OCR yang mirip.
- Catat apakah perbedaan hanya berasal dari whitespace/line break atau berasal dari karakter/isi OCR.
- Pastikan provider serta pasangan bahasa sama pada kedua percobaan.
- Expected run pertama: `cache 0/1`, provider request `1`.
- Expected run kedua jika source identik: `cache 1/1`, provider request `0`.
- Jika source identik tetapi tetap miss, audit provider scope, bahasa, normalisasi, dan persistence sebelum perubahan arsitektur.
- [DONE] Uji perangkat diterima: Klip zoom menghasilkan `Unit OCR: 2`, `Cache 0/1`, `Provider Requests: 1`; source OCR berbeda dari percobaan sebelumnya (`·スキル効果` dan `高敵へ休に/29`), sehingga miss dapat dijelaskan oleh source key yang berbeda.
- [DONE] Uji perangkat kedua menunjukkan `Unit OCR: 16`, `Cache 6/16`, `Provider Requests: 10`; cache bekerja sebagian dan belum menunjukkan cache dasar mati.
- [NOTE] Foto referensi memperlihatkan nama skill berada di header, sedangkan hasil Klip membaca bagian `スキル効果`/deskripsi. Klip saat ini menerjemahkan teks yang masuk rectangle, bukan memilih teks berdasarkan makna “nama skill”.

### Tahap 1 — Translation Unit + cache per unit

Status: [DONE]

- Reuse `TranslationCache`; jangan membuat cache kedua. ✅
- Bentuk unit dari segmentation yang sudah ada. ✅ Unit dibentuk di `OcrManager` (paragraph, line, dan vertical bubble) lalu dipakai apa adanya oleh Manual, Real-Time, dan Klip.
- Setiap unit mempertahankan source text, geometry, orientation, overlay metadata, cache status, dan translation. ✅ `DetectedText` → `TranslationOverlayItem` membawa `left/top/right/bottom`, `sourceTextSizePx`, `backgroundColor`, dan `orientation`.
- Manual dan Klip melewati pipeline unit yang sama setelah OCR. ✅ Keduanya memanggil `TranslationManager.translate()` per unit, sehingga scope provider dan `TranslationCache` identik.
- Klip tidak lagi mengirim seluruh crop sebagai satu translation/cache item. ✅ `KlipSelectionController.translateKlip()` mengiterasi per `DetectedText`.
- Pada tahap ini provider boleh tetap menerjemahkan miss satu per satu agar correctness mudah diuji. ✅ Masih satu request per miss; batching ditunda ke Tahap 4.

Catatan verifikasi: butir di atas diverifikasi lewat pembacaan kode, bukan build lokal (sesuai build policy). Bukti perangkat Tahap 1 sudah tercatat di Tahap 0: `Unit OCR: 2` (`Cache 0/1`) dan `Unit OCR: 16` (`Cache 6/16`).

### Tahap 2 — Deduplication

Status: [DONE]

- Deduplicate berdasarkan cache identity/source text normalized, bukan geometry. ✅ `TranslationTextNormalizer` diekstrak dari `TranslationCache` dan dipakai bersama, sehingga identitas dedup dijamin sama dengan identitas cache key.
- Satu unique source miss diterjemahkan sekali. ✅ `TranslationPipeline` mengelompokkan unit per identitas, memanggil provider sekali per grup, lalu menyebarkan hasilnya.
- Hasilnya dipakai oleh semua unit/geometry yang sama. ✅ Fan-out memakai geometry dan `orientation` milik masing-masing unit; urutan unit asli dipertahankan untuk overlay maupun History.
- Jangan mengubah segmentation hanya demi menaikkan cache hit. ✅ Segmentasi `OcrManager`/`TextLayoutAnalyzer` tidak disentuh.

Dampak arsitektur: tiga loop terjemahan terpisah (Manual, Real-Time, Klip) digantikan satu pipeline. `translateTexts`, `translateRealtimeTexts`, dan `processNext` dihapus. Enam metrik wajib dihitung di pipeline dan ditulis ke entri History. `Provider Requests` saat ini selalu sama dengan `Unique Misses` karena batching baru ada di Tahap 4.

Perubahan perilaku yang disengaja: `orientation` kini diteruskan di semua mode, sehingga overlay Manual dan Real-Time ikut menangani teks vertikal Jepang (sebelumnya hanya Klip), dan format entri History menyesuaikan diri ke enam metrik wajib.

### Tahap 3 — Validasi hasil

Status: [PENDING]

- Uji Klip lintas Manual untuk membuktikan shared cache.
- Uji item UI terpisah: `説明`, `スキル詳細`, nama skill, angka/status.
- Uji paragraph Jepang/manga agar tetap satu unit bila memang satu bubble/paragraph.
- Uji duplicate OCR source dengan beberapa geometry.
- Verifikasi overlay mapping, urutan hasil, History, dan cache status.

### Tahap 4 — Provider-specific batch

Status: [PENDING]

- Mulai dari DeepL setelah Tahap 1–3 stabil.
- Batch hanya berisi unique cache misses.
- Response harus dipetakan stabil ke unit; jangan mengandalkan urutan tanpa kontrak provider.
- OpenRouter hanya setelah format response ber-ID dan fallback error disepakati.
- ML Kit tidak dipaksa memakai network-style batching.

## Metrik wajib

Pisahkan field berikut:

- `OCR Units`: jumlah unit hasil OCR/segmentation.
- `Unique Units`: jumlah source identity unik.
- `Cache-hit Units`: jumlah unit geometry yang memakai cache.
- `Unique Cache Hits`: jumlah source identity unik yang hit.
- `Unique Misses`: jumlah source identity unik yang belum ada cache.
- `Provider Requests`: jumlah request transport aktual.

Contoh: `OCR Units: 24 | Unique Units: 20 | Cache-hit Units: 12 | Unique Cache Hits: 10 | Unique Misses: 8 | Provider Requests: 1`

## Acceptance criteria

Tahap 1 dan 2 menutup kriteria bertanda ✅ di bawah. Kriteria lain menunggu Tahap 3–4 dan/atau uji perangkat.

- [x] Klip tidak menganggap seluruh crop sebagai satu translation/cache item. ✅
- [x] UI item yang berbeda dapat menjadi unit terpisah. ✅
- [x] Paragraph/bubble manga tetap dapat menjadi satu unit. ✅
- [x] Manual dan Klip berbagi cache. ✅
- [ ] Teks sama di lokasi berbeda menjadi cache hit. (arsitektur sudah benar — geometry tidak ada di cache key dan dedup menyatukan identitas — tetapi bukti perangkat lintas posisi belum dicatat)
- [x] Duplicate source tidak diterjemahkan berulang. ✅ dalam satu sesi oleh `TranslationPipeline`; lintas sesi oleh `TranslationCache`.
- [ ] Cache miss tidak otomatis berarti satu request per unit. → Tahap 4
- [ ] Batch response tetap terpisah dan kembali ke geometry yang benar. → Tahap 4
- [x] History tetap satu entry per sesi translation. ✅
- [ ] Performance/History membedakan metrik wajib. (History sudah menampilkan enam metrik; field `PerformanceLogEntry` menunggu Tahap 3)
- [x] Vertical Japanese/manga handling tidak dihapus. ✅
- [ ] GitHub Actions build berhasil. (belum dijalankan untuk perubahan ini)
- [ ] Uji perangkat berhasil untuk Klip, Manual, cache, overlay, dan provider. (sebagian sudah; lihat Tahap 0)

## Aturan implementasi

- Jangan menulis kode sebelum tahap aktif disepakati.
- Selesaikan satu tahap, validasi, lalu tandai tahap `[DONE]` sebelum lanjut.
- Jangan mengubah UI besar atau provider architecture yang tidak diperlukan.
- Jangan memasukkan geometry ke cache key.
- Jangan menggunakan normalisasi agresif untuk memperbaiki typo OCR.
- Jika kualitas segmentation bertentangan dengan cache hit, prioritaskan segmentation.
- Setelah setiap perubahan: catat file, validasi, blocker, dan next step di file ini.

## Log singkat

### 2026-09-21 — Desain disepakati

- [DONE] Membahas gejala Klip selalu `Cache: tidak ada kalimat dari cache (0/1)`.
- [DONE] Menyepakati Smart Cache berbasis Translation Unit sebagai arah solusi.
- [DONE] Menyepakati batching ditunda sampai unit/cache per unit dan deduplication tervalidasi.
- [DONE] Menemukan dan memperbaiki risiko mapping koordinat Klip di `KlipSelectionController.kt`: crop sekarang memakai ukuran aktual `KlipMaskView`, bukan hanya `displayMetrics`, dan mencatat mapping selection/bitmap/crop ke Logcat. Ini menargetkan gejala area nama skill yang dipilih tetapi crop membaca deskripsi.
- [NEXT] Uji APK hasil GitHub Actions pada layar yang sama; periksa apakah hasil Klip sudah sesuai area yang terlihat dan, bila masih meleset, kirim log `Klip crop mapping` untuk audit lanjutan.

### Audit + penghapusan Gemini — Tahap 1 ditutup

- [DONE] Audit seluruh referensi silang selesai. Temuan: `GeminiTranslationProvider` dihapus; `TranslationOverlayViewCompat.setTranslationItems()` tidak pernah dipanggil (dead code); `ApiSettings.getManualProvider()` selalu mengembalikan `PROVIDER_ML_KIT` sehingga cabang `PROVIDER_DEEPL` di `TranslationManager.createProvider()` tidak terjangkau; `blurredPatch` tidak pernah diisi selain `null` sehingga seluruh logika recycle blur tidak terpakai; `ScreenCaptureSession.clear()`, `ScreenTLPerformanceTrace.finishWhenIdle()`, dan `OcrManager.recycleBlurredPatches()` tidak dipanggil; dependency `kotlinx-coroutines-android` tidak dipakai.
- [DONE] Gemini dihapus sepenuhnya karena seluruh model di `MODEL_QUEUE` selalu overload sehingga tidak pernah terpakai. File dihapus: `GeminiTranslationProvider.kt`. File diubah: `ApiSettings.kt` (API + konstanta Gemini dibuang, sisa key lama dibersihkan sekali saat `initialize()`), `TranslationManager.kt` (cabang provider, nama, dan cache scope), `SettingsActivity.kt` (daftar provider API kini `DeepL API` + `OpenRouter`), `FloatingService.kt` (callback toast rotasi dan log), `app/build.gradle.kts` (komentar).
- [DONE] Tahap 1 ditandai selesai. Bukti: Klip mengiterasi per `DetectedText`, Manual/Real-Time/Klip memakai `TranslationManager.translate()` dan `TranslationCache` yang sama, serta metadata geometry/orientasi ikut terbawa ke `TranslationOverlayItem`.
- [NOTE] Verifikasi hanya lewat pembacaan kode. Build tetap melalui GitHub Actions sesuai build policy, jadi kompilasi perubahan ini belum dibuktikan.
- [DONE] Kode mati dibersihkan pada commit terpisah: `TranslationOverlayViewCompat.kt` dihapus (beserta aturan ProGuard-nya), alur `blurredPatch` dihapus dari seluruh pipeline (data class, pemanggilan `recycle`, dan parameter `Result`), `ScreenCaptureSession.clear()`, `ScreenTLPerformanceTrace.finishWhenIdle()`, dan `OcrManager.recycleBlurredPatches()` dihapus, dependency `kotlinx-coroutines-android` beserta aturan ProGuard-nya dihapus. `clearTranslationsAndRecycle()` digabung menjadi `clearTranslations()`.
- [TEMUAN BARU] `FloatingService.toOverlayItem()` tidak meneruskan `orientation`, sedangkan `KlipSelectionController.toOverlayItem()` meneruskannya. Jadi overlay Manual dan Real-Time memperlakukan teks vertikal Jepang sebagai horizontal. Diperbaiki di Tahap 2.
- [NEXT] Jalankan GitHub Actions untuk memastikan build hijau, lalu mulai Tahap 2 (deduplication).

### Tahap 2 — Deduplication ditutup

- [DONE] Keputusan pemilik proyek: unifikasi ketiga mode lewat satu pipeline, perbaiki temuan `orientation` sekalian, dan tampilkan enam metrik di History sekarang.
- [DONE] File baru `TranslationTextNormalizer.kt`: `normalizeSourceText()` dipindahkan dari `TranslationCache` (sebelumnya `private`) menjadi normalizer bersama. `TranslationCache.keyFor()` kini memakainya, sehingga identitas dedup dan identitas cache key tidak bisa lagi menyimpang.
- [DONE] File baru `TranslationPipeline.kt`: `TranslationPipeline` (normalize → group by identity → satu terjemahan per unique source → fan-out) dan `TranslationSessionResult` (overlayItems, historyEntry, dan enam metrik wajib). Pembatalan lewat lambda `isCancelled`, diperiksa di batas langkah.
- [DONE] `FloatingService.kt`: `translateTexts` dan `translateRealtimeTexts` dihapus, digantikan `runManualPipeline()` dan pemanggilan pipeline langsung di jalur Real-Time. History untuk Real-Time tetap tidak ditulis (pipeline hanya membangun entri; persistensi diputuskan pemanggil), supaya History tidak dibanjiri satu entri per frame.
- [DONE] `KlipSelectionController.kt`: `translateKlip` menyusut dari loop rekursif menjadi pemanggilan pipeline; `processNext` dan `toOverlayItem` lokal dihapus.
- [DONE] `proguard-rules.pro`: aturan keep untuk tiga tipe baru.
- [DONE] Impor mati dibuang: `SimpleDateFormat`/`Date`/`Locale` dari `FloatingService` dan `KlipSelectionController`, `java.text.Normalizer` dari `TranslationCache`.
- [NOTE] `Provider Requests` selalu sama dengan `Unique Misses` selama batching belum ada (Tahap 4). Field metrik di data class belum dikonsumsi pemanggil; Tahap 3 yang memasangnya ke `PerformanceLogEntry`.
- [NOTE] Verifikasi tetap lewat pembacaan kode; kompilasi menunggu GitHub Actions.
- [NEXT] Tahap 3 — validasi hasil: pasang metrik ke `PerformanceLogEntry`/layar Performa, lalu uji perangkat (shared cache Klip↔Manual, item UI terpisah, paragraph manga, duplicate source multi-geometry, overlay mapping, dan urutan History).
