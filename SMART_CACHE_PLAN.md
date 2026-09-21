# Smart Cache + Batch Translation — Working Plan

> **Scope:** catatan kerja khusus fitur Smart Cache/Translation Unit/Batch Translation.
> **Aturan konteks:** gunakan file ini sebagai konteks utama untuk task ini. Jangan meminta pembacaan README, AI handoff, atau catatan proyek lain untuk memahami rencana ini.
> **Status:** diskusi desain; belum ada implementasi dari rancangan ini.
> **Build policy:** jangan menjalankan Gradle/compile/APK lokal. Build hanya melalui GitHub Actions.

## Keputusan yang sudah selesai

- [DONE] Cache Klip saat ini memakai seluruh crop sebagai satu source/cache item. Karena itu History dapat menunjukkan `0/1` setiap kali OCR gabungan berubah.
- [DONE] Tiga contoh Klip belum identik: area seleksi, jumlah baris, spasi, dan OCR berbeda. Itu menjelaskan cache miss, tetapi belum membuktikan cache dasar rusak.
- [DONE] Target arsitektur: `OCR → segmentation → Translation Units → normalize → deduplicate → cache lookup → translate misses → merge → geometry/overlay`.
- [DONE] Manual dan Klip memakai cache yang sama. Cache key tidak bergantung pada geometry, posisi, atau mode Manual/Klip.
- [DONE] Cache normalization konservatif: Unicode, line ending, whitespace, dan trim. Jangan memperbaiki typo OCR Jepang secara agresif.
- [DONE] Translation Unit tidak selalu sama dengan OCR line atau seluruh OCR block. Paragraph/bubble jelas tetap satu unit; UI item berdampingan tidak otomatis digabung.
- [DONE] Batch tidak memecah unit semantik atau menggabungkan hasil menjadi satu paragraf. Batch hanya optimasi transport.
- [DONE] Provider batching bersifat provider-specific. DeepL kandidat pertama; Gemini/OpenRouter memerlukan response ber-ID; ML Kit boleh tetap unit-by-unit.
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

Status: [IN PROGRESS]

- Reuse `TranslationCache`; jangan membuat cache kedua.
- Bentuk unit dari segmentation yang sudah ada.
- Setiap unit mempertahankan source text, geometry, orientation, overlay metadata, cache status, dan translation.
- Manual dan Klip melewati pipeline unit yang sama setelah OCR.
- Klip tidak lagi mengirim seluruh crop sebagai satu translation/cache item.
- Pada tahap ini provider boleh tetap menerjemahkan miss satu per satu agar correctness mudah diuji.

### Tahap 2 — Deduplication

Status: [PENDING]

- Deduplicate berdasarkan cache identity/source text normalized, bukan geometry.
- Satu unique source miss diterjemahkan sekali.
- Hasilnya dipakai oleh semua unit/geometry yang sama.
- Jangan mengubah segmentation hanya demi menaikkan cache hit.

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
- Gemini/OpenRouter hanya setelah format response ber-ID dan fallback error disepakati.
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

- [ ] Klip tidak menganggap seluruh crop sebagai satu translation/cache item.
- [ ] UI item yang berbeda dapat menjadi unit terpisah.
- [ ] Paragraph/bubble manga tetap dapat menjadi satu unit.
- [ ] Manual dan Klip berbagi cache.
- [ ] Teks sama di lokasi berbeda menjadi cache hit.
- [ ] Duplicate source tidak diterjemahkan berulang.
- [ ] Cache miss tidak otomatis berarti satu request per unit.
- [ ] Batch response tetap terpisah dan kembali ke geometry yang benar.
- [ ] History tetap satu entry per sesi translation.
- [ ] Performance/History membedakan metrik wajib.
- [ ] Vertical Japanese/manga handling tidak dihapus.
- [ ] GitHub Actions build berhasil.
- [ ] Uji perangkat berhasil untuk Klip, Manual, cache, overlay, dan provider.

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
