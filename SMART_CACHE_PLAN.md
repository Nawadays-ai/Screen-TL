# Smart Cache + Batch Translation — Working Plan

> **Scope:** catatan kerja khusus fitur Smart Cache/Translation Unit/Batch Translation.
> **Aturan konteks:** gunakan file ini sebagai konteks utama untuk task ini. Jangan meminta pembacaan README, AI handoff, atau catatan proyek lain untuk memahami rencana ini.
> **Status:** Tahap 0 sampai 4 selesai dan tervalidasi di perangkat. Sisa satu item kosmetik: samakan tampilan layar Performa dengan enam metrik History.
> **Build policy:** jangan menjalankan Gradle/compile/APK lokal. Build hanya melalui GitHub Actions.

## Keputusan yang sudah selesai

- [DONE] Cache Klip saat ini memakai seluruh crop sebagai satu source/cache item. Karena itu History dapat menunjukkan `0/1` setiap kali OCR gabungan berubah.
- [DONE] Tiga contoh Klip belum identik: area seleksi, jumlah baris, spasi, dan OCR berbeda. Itu menjelaskan cache miss, tetapi belum membuktikan cache dasar rusak.
- [DONE] Target arsitektur: `OCR → segmentation → Translation Units → normalize → deduplicate → cache lookup → translate misses → merge → geometry/overlay`.
- [DONE] Manual dan Klip memakai cache yang sama. Cache key tidak bergantung pada geometry, posisi, atau mode Manual/Klip.
- [DONE] Cache normalization konservatif: Unicode, line ending, whitespace, dan trim. Jangan memperbaiki typo OCR Jepang secara agresif. Pengecualian yang disetujui: spasi tunggal yang diapit dua karakter CJK ikut dibuang, karena Bahasa Jepang tidak memakai spasi sebagai pemisah kata dan OCR terus berubah-pendapat soal keberadaan spasi setelah `、`/`。` maupun di dalam `【】`.
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

Status: [DONE]

- [x] Uji Klip lintas Manual untuk membuktikan shared cache.
- [x] Uji item UI terpisah: `説明`, `スキル詳細`, nama skill, angka/status.
- [x] Uji paragraph Jepang/manga agar tetap satu unit bila memang satu bubble/paragraph.
- [x] Uji duplicate OCR source dengan beberapa geometry.
- [x] Verifikasi overlay mapping, urutan hasil, History, dan cache status.

Bukti perangkat 18:13–18:15 (satu gambar sumber, Jepang → Indonesia, DeepL API):

- **Rantai cache.** Klip 18:13:47 `1 miss / 1 request` → Klip 18:13:57 `0 miss / 0 request` → Klip 18:14:27 `1 miss / 1 request` → Klip 18:14:42 `0 miss / 0 request`. Tiap konten baru diterjemahkan sekali, lalu seterusnya nol transport.
- **Shared cache Klip↔Manual.** Lima unit Klip 18:14:42 (`敵全体に243226の物理ダメージ`, `敵全体の物理防御力を78ダウン`, `味方全体の物理攻撃力を11670アップ`, `味方全体の物理クリティカルを150アップ`, `閉じる`) muncul lagi di Manual 18:14:58 dan 18:15:11 sebagai cache hit tanpa request baru.
- **Deduplication.** Manual `OCR Units: 19 → Unique Units: 17` pada dua run berturut-turut. Dua unit kembar (`敵全体に243226の物理ダメージ` dan `敵全体の物理防御力を78ダウン`) muncul dua kali di History namun dihitung satu kali sebagai unique source.
- **Cache habis.** Manual 18:15:11 `Cache-hit Units: 19 | Unique Misses: 0 | Provider Requests: 0`, dengan Performance `Terjemahan: —` (nol transport) dan total 1490 ms.
- **Konsistensi metrik.** `Unique Units = Unique Cache Hits + Unique Misses` terpenuhi di seluruh run (17 = 17 + 0; 17 = 10 + 7; 7 = 7 + 0; 6 = 5 + 1; 2 = 2 + 0; 2 = 1 + 1).
- **Trace ownership.** Semua entri Klip dan Manual memuat SS/OCR/Terjemahan/Tampilkan lengkap. Tidak ada lagi `manual capture timeout`.
- **Overlay mapping.** History berpasangan `sumber → hasil` satu baris per unit dan urutannya mengikuti urutan unit OCR.

- [NOTE] Satu item kosmetik tersisa dan sengaja ditunda: layar Performa masih menampilkan `Cache hit / request provider` (dua angka dari marker trace) sedangkan History menampilkan enam metrik pipeline. Angka intinya sudah konsisten; penyeragaman display ditunda ke Tahap 4 agar tidak menunda masuknya batching.

### Tahap 4 — Provider-specific batch

Status: [DONE]

- [DONE] Kontrak `TranslationProvider.translateBatch(texts, onSuccess, onFailure)`. Hasil dan kegagalan dilaporkan **per indeks**, sehingga batch yang gagal sebagian tidak pernah membuat pemanggil menebak entri mana yang hilang. Implementasi default menerjemahkan satu per satu, jadi ML Kit (tanpa endpoint batch) dan OpenRouter belum ikut berubah.
- [DONE] `DeepLTranslationProvider` memakai array `text` secara native. Dokumentasi DeepL menyatakan tiap elemen array diterjemahkan secara independen dan response kembali sesuai urutan request, sehingga batch tidak dapat menggabungkan unit maupun membiarkan satu unit memengaruhi unit lain. Panjang response diverifikasi terhadap panjang request.
- [DONE] `TranslationManager.translateBatch()` dengan urutan yang disengaja: cek cache **per teks** lebih dulu, hanya miss yang dikirim, chunk maksimal `MAX_BATCH_TEXTS = 20`, dan setiap hasil langsung disimpan ke cache dengan key-nya sendiri.
- [DONE] Retry sesuai kesepakatan: kegagalan parsial mengirim ulang **hanya** teks yang hilang; request yang ditolak seluruhnya dipecah menjadi dua bagian lalu masing-masing dicoba lagi; satu teks mencoba satu kali lagi sebelum kegagalannya dicatat. `allowSingleRetry` menjaga agar retry tidak berulang tanpa akhir.
- [DONE] `TranslationPipeline` memakai jalur batch. `Provider Requests` kini melaporkan jumlah transport call nyata dan tidak lagi selalu sama dengan `Unique Misses`.
- [DONE] `BatchTranslationResult` diperluas untuk membawa `fromCache` dan `requests`; aturan ProGuard untuk tipe baru.
- [NOTE] Cache key tetap per teks, bukan per batch. "Terjemahkan skill saja" tetap cache hit setelah seluruh layar pernah diterjemahkan.
- [NOTE] Batas 20 unit per request dipilih pemilik proyek. Ini pagar pengaman, bukan batas API DeepL yang terdokumentasi; dokumentasi hanya menyebut batas ukuran request 128 KiB. Menurunkannya cukup mengubah satu konstanta.
- [NOTE] Batching tidak mengurangi kuota karakter DeepL Free (500.000 karakter/bulan); yang berkurang adalah jumlah HTTP request dan waktu tunggu.
- [DONE] Uji perangkat 19:47–19:50 (Manual, DeepL API, satu gambar sumber) membuktikan batching bekerja. `Provider Requests: 1` pada setiap run yang punya miss, dengan `Unit OCR` 13–27, artinya puluhan unit hanya butuh satu transport call. Run dengan `Provider Requests: 0` selesai dengan `Terjemahan: —` karena seluruh unit dilayani cache.
- [DONE] Tidak ada teks yang tertukar posisi: History tetap berpasangan `sumber → hasil` per unit dan urutannya mengikuti urutan unit OCR.
- [DONE] Kecepatan meningkat nyata. Run Miss-before batching sebelumnya butuh 2818 ms terjemahan untuk 7 miss; run dengan jumlah unit lebih besar kini menyelesaikan 9 miss dalam 844 ms dan 4 miss dalam 425 ms.
- [DONE] Tidak ada error kompilasi maupun runtime setelah dua perbaikan yang ditemukan build: arity trailing lambda pada `processChunks` dan pemetaan indeks pada `DeepLTranslationProvider.translateBatch` (teks kosong dulu membatalkan seluruh batch, lalu perbaikannya membuat setiap terjemahan mendarat di unit yang salah karena indeks response dibandingkan dengan `texts.size` padahal yang dikirim `sendableTexts`).
- [NOTE] `Terjemahan: —` pada run cache penuh adalah perilaku yang benar, bukan kegagalan: tidak ada transport request sehingga tidak ada durasi terjemahan untuk dilaporkan.
- [NOTE] Sisa satu item kosmetik Tahap 3: layar Performa masih menampilkan dua angka `Cache hit / request provider`; samakan dengan enam metrik pipeline seperti di History. Angka di bawah ini adalah hit unit bukan request, jadi cukup membingungkan setelah batching.

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
- [x] Teks sama di lokasi berbeda menjadi cache hit. ✅ `敵全体に243226の物理ダメージ` dan `敵全体の物理防御力を78ダウン` masing-masing muncul dua kali pada Manual 18:15:11, dihitung satu kali sebagai unique source, dan diterjemahkan sekali untuk kedua posisi.
- [x] Duplicate source tidak diterjemahkan berulang. ✅ dalam satu sesi oleh `TranslationPipeline`; lintas sesi oleh `TranslationCache`.
- [x] Cache miss tidak otomatis berarti satu request per unit. ✅ `TranslationManager.translateBatch()` mengirim unique miss dalam chunk 20, jadi 7 miss menjadi 1 request transport.
- [x] Batch response tetap terpisah dan kembali ke geometry yang benar. ✅ `BatchTranslationResult` melaporkan hasil per indeks teks, dan `TranslationPipeline` memetakannya kembali ke `UnitGroup` lalu ke geometry masing-masing.
- [x] History tetap satu entry per sesi translation. ✅
- [ ] Performance/History membedakan metrik wajib. (History sudah menampilkan enam metrik dan tervalidasi di perangkat; `PerformanceLogEntry` baru memakai `cacheHits`/`providerRequests` dari marker trace. Penyeragaman ditunda ke Tahap 4.)
- [x] Vertical Japanese/manga handling tidak dihapus. ✅
- [x] GitHub Actions build berhasil. ✅ build hijau untuk `78f7c29`, aplikasi diuji di perangkat.
- [x] Uji perangkat berhasil untuk Klip, Manual, cache, overlay, dan provider. ✅ lihat bukti Tahap 3; timeout capture, trace ownership, normalisasi spasi CJK, dan dedup tervalidasi.

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

### Stabilisasi — timeout capture, cache miss spasi, dan kepemilikan trace

- [DONE] **Akar masalah timeout Manual (bukan regresi Tahap 2).** `MediaProjection` hanya mengirim frame saat isi layar berubah. `MANUAL_CAPTURE_UI_SETTLE_MS = 200L` menunda permintaan capture 200 ms, sehingga frame yang dihasilkan saat submenu menguncup sudah terbuang (listener menutup frame ketika `captureRequested` masih false). Layar yang sudah diam tidak mengirim frame lagi, jadi capture menunggu sampai timeout. Bukti di kode: `ScreenCaptureManager` terakhir diubah pada commit `307c36b`, jauh sebelum pekerjaan Tahap 2, dan jalur capture Manual tidak disentuh di Tahap 1 maupun 2.
- [DONE] **Frame cadangan.** `ScreenCaptureManager` kini mengingat frame terakhir (`armCaptureFallback` / `disarmCaptureFallback`) dan `captureOnce` memakai salinan frame itu bila tidak ada frame baru dalam `CAPTURE_FALLBACK_DELAY_MS = 400L`. Ingatan hanya diaktifkan lewat `armCaptureFallback()` saat alur akan segera meminta capture, sehingga tidak ada biaya decode frame di luar itu. Frame diambil secara atomik di bawah `latestFrameLock` agar tidak mungkin di-recycle oleh `disarm` saat sedang disalin.
- [DONE] **Klip memakai frame cadangan juga**, lewat `capture.armCaptureFallback()` sebelum jeda settle, dan di-disarm pada callback capture, `fail()`, serta `cancel()`.
- [DONE] **Normalisasi spasi CJK.** `TranslationTextNormalizer` kini membuang spasi tunggal yang kedua sisinya CJK/tanda baca CJK (`U+3000–U+303F`, hiragana, katakana, extensi katakana, extensi ideograf B, ideograf terpadu, fullwidth/halfwidth). Regex hanya mencocokkan spasi literal, tidak pernah line break, jadi line wrapping tetap bagian dari identitas. Bukti: tiga capture paragraf yang tidak berubah hanya berbeda pada spasi tersebut dan menghasilkan tiga cache key berbeda.
- [DONE] **Kegagalan capture `busy` tidak lagi meninggalkan trace menggantung.** `captureOnceInternal` kini menutup trace pada penolakan `captureRequested`, yang sebelumnya hanya memberi mark sehingga trace tidak pernah tercatat dan mark berikutnya bisa jatuh ke trace orang lain.
- [DONE] **Kepemilikan trace eksplisit per alur.** `FloatingService` menyimpan `manualTrace` dan `realtimeFrameTrace`; `KlipSelectionController` sudah memegang `performanceTrace`. Timeout, penolakan, dan pembatalan kini menutup trace milik alur itu sendiri, bukan `ScreenTLPerformanceTrace.current()` yang bisa menunjuk trace alur lain. Gejala yang sebelumnya dilihat: entri Klip tanpa SS/OCR/Terjemahan/Tampilkan setelah timeout Manual, karena timeout Manual menutup trace Klip yang sedang aktif.
- [DONE] **`finish()` idempoten.** `ScreenTLPerformanceTrace` memakai `AtomicBoolean` sehingga entri yang sama tidak tercatat dua kali ketika capture ditolak, pemanggil timeout, dan callback terlambat sama-sama mencapai `finish()`.
- [DONE] **Isolasi mode.** Manual menolak jalan saat Klip aktif atau Real-Time aktif; Real-Time menolak saat Klip aktif; Klip menghentikan Real-Time dan membuang capture Manual yang masih tertunda lewat `prepareForKlipPublic()`. Semua mode berbagi satu permukaan capture dan satu trace aktif, sehingga overlap hanya menghasilkan capture yang saling menolak dan timing yang salah sasaran.
- [DONE] **Trace diteruskan secara eksplisit** ke `OcrManager.recognize()`, `TranslationManager.prepare()`, `TranslationManager.translate()`, dan `TranslationPipeline`, memakai parameter `trace` opsional yang jatuh kembali ke `current()`. Jalur terjemahan terpanas sekarang tidak lagi bergantung pada global.
- [NOTE] Verifikasi tetap lewat pembacaan kode dan pemeriksaan keseimbangan kurung; kompilasi menunggu GitHub Actions sesuai build policy.
- [NEXT] Commit batch ini, lalu uji perangkat: Manual pada layar statis (harus selesai di bawah ~1 detik tanpa timeout, dengan entri Performance yang tetap punya SS/OCR/Terjemahan/Tampilkan), pengulangan capture yang sama (expect `Unique Misses: 0` dan `Provider Requests: 0`), dan Klip setelah Manual timeout (timing Klip harus lengkap).

### Tahap 3 — Validasi hasil ditutup

- [DONE] Uji perangkat 18:13–18:15 (satu gambar sumber, Jepang → Indonesia, DeepL API) memenuhi seluruh kriteria Tahap 3. Rincian per butir ada di bagian Tahap 3.
- [DONE] Timeout capture hilang. Manual pada layar diam selesai dengan `SS: 402 ms` / `SS: 401 ms`, bukan 3005 ms. Frame cadangan bekerja.
- [DONE] Trace ownership benar. Semua entri Klip dan Manual memuat SS/OCR/Terjemahan/Tampilkan lengkap; tidak ada lagi entri dengan timing kosong setelah Manual timeout.
- [DONE] Normalisasi spasi CJK terbukti. `Cache-hit Units` naik dari 12 ke 19 pada run ketiga, `Unique Misses` turun ke 0, dan `Provider Requests` 0.
- [DONE] Deduplication terbukti. `OCR Units: 19 → Unique Units: 17` konsisten di dua run; dua pasangan unit kembar dihitung satu kali.
- [DONE] Shared cache Klip↔Manual terbukti. Lima unit Klip 18:14:42 menjadi cache hit di Manual 18:14:58 tanpa request baru.
- [NOTE] Satu item kosmetik tersisa dan sengaja ditunda: layar Performa masih menampilkan `Cache hit / request provider` (dua angka dari marker trace) sedangkan History menampilkan enam metrik pipeline. Angka intinya sudah konsisten; penyeragaman display ditunda ke Tahap 4 agar tidak menunda masuknya batching.
- [NOTE] Baris pendek aneh di History (`法男`, `本意`, `撃ス`, `敵を`, `ス`, `NK`) **bukan** bug cache, dedup, atau segmentation. Owners mengonfirmasi sumbernya: saat pengujian, layar di-zoom pada nama skill beserta deskripsinya sehingga kolom menu di samping ikut terbaca dalam area Manual. OCR membaca apa yang benar-benar ada di layar; tiap potongan label menu menjadi unit sendiri lalu diterjemahkan sendiri (`ス → S`, `NK → NK`). Tidak ada tindakan yang diperlukan.
- [NEXT] Tahap 4 — provider-specific batch, dimulai dari DeepL.
