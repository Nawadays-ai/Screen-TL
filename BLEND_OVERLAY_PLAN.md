# Native-Blend Overlay + Minimal Klip — Working Plan

> **Scope:** catatan kerja perubahan tampilan mode Manual (overlay menyatu dengan game) dan mode Klip (desain minimalis). Mode Real-Time di luar scope.
> **Aturan konteks:** gunakan file ini sebagai konteks utama untuk task ini. Jangan meminta pembacaan README, AI handoff, atau catatan proyek lain untuk memahami rencana ini.
> **Status:** Tahap 0 selesai. **Tahap 1b–1e terverifikasi di perangkat oleh user — hasilnya sesuai ("lebih baik dari sebelumnya").** Tahap 2–3 belum dimulai. Tahap 4 ditunda (riset). Struktur overlay Manual dianggap **[DONE] untuk lingkup tipografi + patch solid**; yang tersisa adalah kerapian patch (lihat Tahap 2).
> **Build policy:** APK **tidak pernah** dibangun lokal — keputusan user, bukan kekurangan setup. Build hanya via GitHub Actions, lalu user tes di perangkat. Yang boleh: type-check Kotlin terisolasi via compiler di Gradle cache (ringan, detik) untuk menangkap error sebelum CI.

## Keputusan yang sudah selesai

- [DONE] **Manual = "tipografi match + patch penuh".** Dua bagian: (1) teks terjemahan digambar meniru gaya teks game asli (fill, outline, shadow, alignment); (2) background di bawah teks dipatch agar tidak terlihat sebagai kotak asing. Inpainting dengan model ML **ditunda** (Tahap 4).
- [DONE] **Klip = "ink card" solid minimalis.** Frosted glass (blur crop + wash + judul "Terjemahan" + divider) dibuang total.
- [DONE] Branch kerja: `feature/native-blend-overlay`, dibuat dari `debugging`. Branch `debugging` **tidak disentuh lagi** dan menjadi checkpoint; rollback dengan `git switch debugging`.
- [DONE] Perubahan belum di-commit dari sesi agen sebelumnya (3 file overlay) disimpan sebagai commit referensi `1639ed1` — tidak dihapus. Statusnya pondasi/referensi; boleh dipertahankan atau diganti per Tahap.
- [DONE] Layout Manual dari sesi sebelumnya dipertahankan sebagai dasar: grow-ke-kanan dari edge kiri control, collision check antar box, bubble vertikal 2.8x, `maxWidthRatio = 1.6`.
- [DONE] Sumber data gaya: sampling dari crop bitmap + bounding box ML Kit (sudah lewat `TextLayoutAnalyzer`). Tanpa dependency baru di Tahap 1–3.

## File yang terlibat

- `TextLayoutAnalyzer.kt` — sampling warna/karakteristik dari crop. Tempat menambah sampling gaya glyph.
- `TranslationOverlayView.kt` — render overlay Manual (dipakai juga untuk live Klip, `toleranceRatio > 1`). Tempat tipografi match + patch.
- `TranslationPipeline.kt` — pembuatan `TranslationOverlayItem`. Membawa style baru dari analyzer ke view.
- `KlipResultOverlayView.kt` — kartu hasil Klip. Tempat redesign ink card.

## Tahapan kerja

### Tahap 0 — Setup branch & referensi

Status: [DONE]

- Branch `feature/native-blend-overlay` dibuat dari commit `debugging` (`783f3a8`).
- Pekerjaan sesi sebelumnya di-commit sebagai `1639ed1` (reference). Tree bersih.

### Tahap 1 — Manual: tipografi match

Status: [CODE DONE] menunggu build + test perangkat.

- [x] ~~**Sampling gaya glyph** di `TextLayoutAnalyzer`~~ — **DIBATALKAN di Tahap 2.** Clustering k-means + `GlyphStyle` dihapus seluruhnya karena teks Manual sekarang selalu hitam/putih solid (lihat Tahap 2). Riwayat implementasi tetap dicatat di sini sebagai rujukan.
- [x] `TranslationOverlayItem`/`DetectedText` membawa `alignment` dan `patchSample` dari analyzer lewat pipeline.
- [x] **Render outline:** dua pass terpisah (`STROKE` lalu `FILL`), `strokeWidth = 4.5%` ukuran teks hasil render, `strokeJoin = ROUND`. Bukan `FILL_AND_STROKE`: satu Paint hanya punya satu warna, jadi `FILL_AND_STROKE` hanya bisa outline dengan warna fill-nya sendiri.
- [x] **Render drop shadow:** `setShadowLayer(radius 3% textSize, offset 1.8% textSize, hitam 40%)`. Teks tidak butuh software layer (shadow text didukung hardware canvas di semua API ≥ 24).
- [x] **Alignment:** dari bounding box baris ML Kit — spread kiri ≤ 0.5 glyph → LEFT; spread kanan ≤ 0.5 glyph → RIGHT; selain itu CENTER. Multi-line block saja; single line dan bubble vertikal tetap CENTER. Diterapkan per baris hasil wrap.
- [x] ~~Guard kontras berbasis `separation`~~ — **DIBATALKAN di Tahap 2.** Kontras kini dihitung terhadap warna panel yang benar-benar dicat (lihat Tahap 2), bukan terhadap sampling warna glyph.
- [x] Ukuran/wrap/collision/layout tidak diubah di Tahap ini.

**Checklist test (user):** — terverifikasi di perangkat s.d. `5210af7` kecuali yang bertanda.
- [x] Teks panjang (2+ baris): tidak ada baris yang keluar dari patch / menimpa teks asli.
- [x] Semua panel: teks selalu punya outline, tidak pernah fill polos tanpa tepi.
- [x] Layar manyaragraf: semua paragraf punya ukuran font konsisten satu sama lain; tidak ada
  paragraf tiba-tiba sangat kecil sementara di atasnya besar.
- [x] Kalimat berdempet vertikal: tidak saling rebut ruang; panel menyatu jadi satu patch tanpa
  seam/garis bulat, warnanya seragam.
- [x] Tidak ada baris nyasar: kalimat tidak terpecah jadi beberapa baris pendek padahal ruang
  horizontal masih lega.
- [ ] ~~Dialog box gelap: outline gelap + shadow, fill meniru warna teks asli.~~ **Tidak berlaku lagi** — sejak Tahap 2 fill selalu hitam/putih solid. Ganti jadi: "Dialog box gelap: teks putih solid + outline hitam, tetap terbaca."
- [ ] Panel/button terang: teks tetap kontras (tidak "tenggelam"). **Penting di Tahap 2** — panel sekarang bergradien, jadi kontras dihitung terhadap stop gradien yang paling buruk, bukan warna rata.
- [ ] Teks asli rata kiri → terjemahan juga rata kiri; yang center tetap center.
- [ ] Dua control berdampingan: batas antar keduanya tetap terlihat (tidak ter-merge).
- [ ] Bubble vertikal JP: tetap terbaca, outline tidak membuat huruf "tebal berlebihan".

### Tahap 1b — Perbaikan hasil test perangkat (2026-09-25)

Status: [CODE DONE] terverifikasi 0 error compile (Kotlin 1.9.22 lokal, JDK 17).

Dua keluhan user setelah test perangkat pertama, keduanya **bukan** masalah sampling Tahap 1
melainkan masalah geometri dan kontras di sisi render:

- [x] **Panel tidak menutupi teks terjemahan.** Tinggi panel sebelumnya selalu sama dengan tinggi
  kotak teks asli (`toleranceRatio=1` untuk Manual, jadi tidak ada pertumbuhan vertikal sama
  sekali), sementara terjemahan yang wrap ke 2+ baris meluber keluar patch karena tidak ada
  `clipRect` di seluruh app. Teks yang meluber itu menggambar tepat di atas glyph game.
  Perbaikan: panel dihitung dari **kebutuhan teks nyata**
  (`lineSpacing * lines + padding * 2`), tumbuh simetris di sekitar kotak asli, dibatasi
  `maxHeightRatio = 1.6` (bubble `1.25`). Setelah tumbuh, panel digeser masuk layar dengan
  menggeser kedua tepi sekaligus agar tepi bawah tidak hilang di batas layar.
  `clipRect` per-item ditambahkan sebagai jaring pengaman terakhir.
- [x] **Kontras diukur terhadap warna yang salah.** `drawPanel` mengecat panel dengan
  `adjustColor(base, 0.62)` untuk panel terang, sementara sampling fill diukur terhadap warna
  **asli** — jadi fill yang kontras terhadap background asli bisa jadi nyaris menyatu dengan
  panel yang dicat. Perbaikan: `paintedPanelColor()` jadi satu sumber kebenaran, dipakai panel
  dan teks; guard `minFillSeparation` kini mengukur terhadap warna panel yang benar-benar dicat.
- [x] **Outline mati justru saat style dipercaya.** `drawStroke` bisa `false` ketika sampling
  dipercaya tapi `hasStroke == false`, sementara jalur fallback selalu `drawStroke = true` —
  prioritasnya terbalik. Perbaikan: outline **selalu** digambar; warna fallback black/white
  dipilih yang furthest dari fill.
- [x] Bonus: `renderItems` dibersihkan sebelum build di `setTranslations` — sebelumnya
  `collidesWithOtherBox` mengukur box baru terhadap geometri frame sebelumnya, jadi frame
  pertama tidak punya deteksi tabrakan sama sekali.

### Tahap 1c — Font size seragam lintas paragraf (2026-09-25)

Status: [CODE DONE] terverifikasi 0 error compile (Kotlin 1.9.22 lokal, JDK 17).

Test perangkat atas build `b32d261` (GH Actions #355, artifact 32.7 MB) menunjukkan masalah
ketiga: **ukuran font antar-paragraf tidak konsisten**. Paragraf yang panjang membuat paragraf
di bawahnya mengalah — sebagian jadi kecil, sebagian jadi sangat kecil. Branch `debugging`
memiliki hasil yang jauh lebih enak dibaca untuk kasus yang sama.

- [x] **Penyebab:** `buildRenderItem` menghitung `fitScale` **per item**, tanpa koherensi
  antar-item. Setiap paragraf wrap berbeda, jadi tiap paragraf independen memutuskan seberapa
  jauh menyusut → pola zig-zag, bukan proporsi.
- [x] **Perbaikan (opsi A):** `setTranslations` jadi dua pass. `measureItem` mengukur kebutuhan
  tinggi tiap item pada ukuran font-nya sendiri; `uniformScaleFor` mengambil **skala terkecil
  yang dibutuhkan** di seluruh layar (dibatasi `minFontScale`); lalu `buildRenderItem` menerapkan
  **satu faktor yang sama** ke semua item. Paragraf pendek tetap ukuran penuh, paragraf panjang
  menarik semua paragraf lain turun dengan faktor yang sama — proporsional, bukan acak.
- [x] Konsekuensi yang disepakati: terjemahan panjang **boleh meluber** dari panelnya sendiri
  (sudah ada `clipRect` sebagai jaring pengaman), asalkan tidak lagi mengorbankan teks lain.

### Tahap 1d — Baris acak & box saling rebut (2026-09-26)

Status: [CODE DONE] terverifikasi 0 error compile (Kotlin 1.9.22 lokal, JDK 17).

Review user atas `980c7bd`: OCR benar membedakan paragraf dari kalimat, tapi terjemahan
"seperti di enter kebawah padahal space masih banyak", dan kalimat yang berdempet vertikal
saling rebut boundary box sehingga sebagian kecil / menumpuk.

- [x] **Baris nyasar (`normalizeParagraph`).** DeepL menerjemahkan satu baris JP/CN dan
  mengembalikan kalimat; ia bebas memecah kalimat itu ke beberapa baris diBalasan. `normalizeParagraph`
  lama **mempertahankan** `\n` itu, jadi kalimat yang muat 1 baris tiba-tiba jadi 3 baris pendek →
  panel lebih tinggi → paragraf di bawahnya makin sempit. Perbaikan: `\n` diperlakukan sebagai
  whitespace biasa (`replace(Regex("[\\s]+"), " ")`). Teks sumber JP/CN tidak punya baris yang
  bermakna, jadi tidak ada yang perlu dipertahankan; `wrapText` yang memutuskan baris dari
  lebar yang benar-benar tersedia.
  (Catatan: diagnosis awal sempat mengaitkannya ke prompt OpenRouter — provider yang dipakai
  adalah **DeepL**, yang tidak punya prompt seperti itu.)
- [x] **Box saling rebut (`collidesWithOtherBox` dihapus).** Bok yang tumbuh lalu ketemu box
  lain akan **menyerahkan ruangnya**. Untuk kolom dialog yang berdempet vertikal, tiap baris
  saling mengalah → zig-zag dan kadang benar-benar menumpuk. Ini diperparah oleh Tahap 1b/1c
  sendiri, karena panel sekarang tumbuh vertikal sampai 1.6x sehingga lebih sering bertabrakan.
  **Overlap adalah kondisi normal** untuk teks berdempet, bukan tabrakan yang harus dicegah.
  `minFontScale` bersama sudah mencegah satu paragraf mengorbankan tetangganya.
- [x] **`buildOverlapGroups()` dikembalikan** dari `debugging` (union-find).kotak yang
  bersinggungan dijadikan satu kelompok dengan **warna panel dirata-ratakan**, jadi deretan baris
  dari satu control digambar sebagai satu control — bukan kartu-kartu dengan warna berbeda.
  Warna grup juga dipakai untuk menghitung kontras teks (`effectivePanelColor`), jadi panel dan teks
  selalu sepakat soal warna yang benar-benar dicat. Growth **tidak** terpengaruh oleh pengelompokan.

### Tahap 1e — Boundary menyatu untuk kolom vertikal (2026-09-26)

Status: [CODE DONE] terverifikasi 0 error compile (Kotlin 1.9.22 lokal, JDK 17).

Tahap 1d menyamakan **warna** antar-box yang overlap, tapi tiap baris masih menggambar panel
rounded-rect-nya sendiri sehingga seam dan garis bulat terlihat di antaranya — terbaca sebagai
kartu bertumpuk, bukan satu panel. Paragraf sudah sesuai; yang tersisa adalah kalimat berdempet
vertikal.

- [x] **Satu panel per grup.** `buildOverlapGroups` kini juga menghitung **union rect** tiap
  kelompok, dan `onDraw` menggambar **satu** rounded-rect per kelompok, bukan satu per item.
  Teks **tetap per item** (diusulkan user): tiap baris mempertahankan terjemahan, lebar,
  alignment, dan paddingnya sendiri — yang disatukan hanya background di bawahnya.
- [x] **Pengelompokan dipersempit jadi kolom vertikal saja** (`stacksVertically`). Sebelumnya
  *intersection apa pun* dianggap satu kelompok, sehingga dua control yang berdampingan bisa
  ikut ter.merge dan batas yang relied pemain untuk membedakan keduanya ikut hilang. Sekarang
  syaratnya: overlap horizontal ≥ 50% lebar yang lebih sempit, ada urutan vertikal (a.top
  berbeda dari b.top), dan jarak vertikal ≤ 0.75× lebar. Caption di sebelah kanan kotak dialog
   karena itu tidak pernah ikut ter-merge walau sentuhnya.
- [x] Warna grup tetap jadi sumber warna panel **dan** guard kontras teks (`effectivePanelColor`),
  jadi yang digambar dan yang dinilai kontras selalu warna yang sama.

### Tahap 1f — Geser kiri + kelompok longgar (2026-09-26)

Status: [CODE DONE] terverifikasi 0 error compile (Kotlin 1.9.22 lokal, JDK 17).

 dua keluhan setelah test `5210af7`.

- [x] **Panel tidak bergeser ke kiri sama sekali** (`leftSlideRatio = 0.20f`). Panel terkunci di
  tepi kiri kontrol dan hanya tumbuh ke kanan, jadi terjemahan yang panjang_than control mentok di
  tepi kanan layar dan **dipaksa wrap** ke baris tambahan — padahal sisi kiri layar kosong dan
  tidak dipakai. Sekarang panel boleh bergeser ke kiri sebesar 20% lebarnya (angka dari user)
  **hanya ketika panel itu benar-benar meluber melewati tepi kanan layar**; kalau muat, panel
  tidak bergerak sama sekali. Sisa yang tidak tertangani 20% tetap menjadi baris baru — itu batas
  yang disengaja, supaya panel tidak pernah menutupi nama/ikon di sebelah kirinya.
  (Koreksi: versi pertama menggeserpanel *selama ada ruang*, sehingga hampir semua panel meluncur
  ke kiri dan terjemahan terlihatarching ke kiri dari teks sumber. Gejala ini dilaporkan user.)
- [x] **Kelompok vertikal terlalu ketat.** `stacksVertically` sebelumnya mensyaratkan overlap
  horizontal ≥ 50% lebar sempit; ML Kit memecah satu blok dialog jadi baris dengan lebar berbeda
  (baris terpanjang jauh melewati baris terpendek), sehingga satu run baris terpecah jadi beberapa
  kelompok dan tiap baris dapat patch sendiri — persis gejala "boundary terlihat membedakan tiap
  kalimat / kadang mepet jadi satu". Syarat overlap horizontal **dihapus**; yang menentukan hanya
  ada intersection + urutan vertikal + jarak vertikal ≤ 1× lebar tersempit. Dua control berdampingan
  tetap tidak ter-merge karena `a.top != b.top` (sejajar horizontal = satu tinggi = bukan run vertikal).

**Sisa yang masih terbuka (tidak dikerjakan di sini):**
- [ ] Kontras panel/button **terang** (user: "untuk sekarang kontrasnya kurang").
- [ ] **Bubble vertikal JP** — user: terjemahan jelek & tidak konsisten (ukuran teks varied, panel
  kadang memenuhi layar padahal source kecil). Discussion terpisah, lihat catatan di bawah.
- [ ] Manga OCR untuk bubble vertikal — **ditunda**, dikerjakan setelah plan ini selesai.

### Tahap 1g — Koreksi geser-kiri (2026-09-26)

Status: [CODE DONE] terverifikasi 0 error compile (Kotlin 1.9.22 lokal, JDK 17).

Tahap 1f membuat panel meluncur ke kiri **hampir selalu** — logikanya "kalau ada ruang, pakai",
padal yang benar "kalau tidak muat di kanan, baru geser". Akibatnya terjemahan duduk di sebelah
kiri teks sumber dan terbaca salah tempat.

- [x] **Geser hanya saat overflow.** `overflow = boxRight - width`; slide = `min(overflow, 20% lebar)`.
  Kalau `overflow == 0`, panel **tidak bergerak** dan kembali persis di atas source seperti
  sebelumnya. Kalau overflow ada, slide menutupi sebanyak mungkin, sisa overflow jadi wrap.
- [x] Konfirmasi tidak ada transparansi: `backgroundPaint.alpha` dan `textPaint.alpha` sudah 255
  di semua jalur (panel, outline, fill). Yang terasa transparan adalah `paintedPanelColor` yang
  **menggelapkan** panel terang (0.62×) — itu opacity penuh, bukan veil. Dicatat di KDoc
  `paintedPanelColor` supaya tidak disalahartikan lagi.
  (Bila panel terang masih terasa "tembus", masalahnya kontras warna panel terhadap teks —
  bukan alpha. Itu masuk daftar terbuka Tahap 1f.)

### Tahap 2 — Manual: patch penuh

Status: [SELESAI — menunggu verifikasi perangkat] Tujuan: area di bawah teks tidak terlihat sebagai kotak blok.
**Catatan:** Tahap 2 adalah soal patch terlihat *natural*, bukan soal patch *cukup menutup*.
Keluhan "teks source masih terlihat" sudah ditangani di Tahap 1b lewat geometri.

- [x] **Gradient vertical:** ganti 1 warna rata dengan `LinearGradient` — median strip atas ~15% dan strip bawah ~15% dari interior box (dialog box game umumnya bergradient). Ketiga stop adalah blend RGB opaque; panel tetap digambar di alpha 255, jadi tidak adajeu yang tembus.
- [x] **Feather edge:** cincin `Paint.Style.STROKE` dengan `strokeWidth = 2 × feather` (4–6px) di-*inset* sebesar `feather` (2–3px), lalu di-blur. Di-inset sebesar setengah lebar stroke supaya tepi stroke tepat flush dengan tepi panel — tidak ada satu pun piksel game yang belum tertutup panel yang bisa terkena blur. Cincin memakai `LinearGradient` yang sama dengan isi panel, kalau tidak tepi 4–6px itu jadi garis rata yang hard-step melawan gradien yang seharusnya dilembutkan. Fallback bentuk rounded-rect dipakai, jadi degrade di hardware canvas (mask filter diabaikan) tetap aman.
- [x] **Deteksi UI-box vs scene art:** *dibatalkan.* Standar-deviasi interior tidak membedakan UI-box dari scene art secara reliable di layar game, dan ambangnya jadi konstanta yang tak terjustifikasi.
- [x] **Tier-2 scene-art patch:** *dibatalkan.* Butuh piksel asli di dalam box, sedangkan yang tersedia hanya sampling+BBM. Versi yang sempat ditulis (menyalin strip kiri/kanan melebar) hanya menghasilkan coretan 1-D, bukan scene — lebih buruk daripada patch solid. Butuh bitmap capture hidup di pipeline; lihat "Utang terbuka".
- [x] Border: pertahankan rules sekarang (hanya panel terang, alpha rendah) sebagai fallback kontras.
- [x] Teks terjemahan: fill dan outline keduanya hitam/putih solid (permintaan eksplisit user), bukan warna glyph yang di-sampling. `GlyphStyle` beserta seluruh jalur sampling-nya dihapus karena tidak lagi dipakai.
- [x] **Kontras teks dihitung dari rasio WCAG, bukan ambang tetap.** Panel bergradien tidak punya "satur warna", jadi ambang tetap (luminance 150) tidak berlaku: hitam dan putih tidak simetris — teks hitam baru terbaca di luminance ~80, sementara teks putih pelan-pelan memburuk seiring panel menjadi terang. `chooseTextColor` sekarang menghitung rasio kontras hitam vs putih terhadap stop **paling buruk** gradien dan memilih yang menang. Ini identik dengan ambang lama saat panel rata, dan selalu lebih baik saat bergradien. Drift gradien sendiri tetap dikunci `maxGradientDrift` (22 luminance) supaya panel tidak pernah menyapu seluruh rentang nada.
  - Diverifikasi numerik: 297 kombinasi (11 luminance panel × 9 sampel × 3 skenario arah gradien) → **0 kasus di bawah 3:1**. Kasus balasan review (stop 30→80→200) dan kasus yang gagal pada versi ambang-150 (panel luminance 42) keduanya benar pada implementasi ini.
- [x] **Sampel patch diagregasi per group, bukan "item pertama yang beririsan".** Dialog 3 baris adalah satu panel: kalau gradien diambil dari baris pertama, `bottomColor` jadi warna tepi bawah baris atas yang diregangkan ke dua baris lain. Aturannya sama dengan `mergeVerticalLayouts` di `OcrManager`: atas dari anggota teratas, bawah dari anggota terbawah. Bug ini juga bisa tertukar sampel dari group lain yang kebetulan beririsan.
- [x] `sampleInterior` kini menghasilkan `SampleGrid` (satu `IntArray` per baris) sehingga pita atas/bawah diturunkan dari grid yang sama — bitmap dilewati **sekali** per box, bukan tiga kali. `backgroundColor` terverifikasi identik dengan versi lama pada 500 kasus acak.

**Catatan performance:** `LAYER_TYPE_SOFTWARE` sengaja **tidak** dipasang. Overlay full-screen di software layer memaksa seluruh panel diraster di CPU tiap frame — risiko drop frame yang nyata, berlawanan dengan target "performa Manual tidak turun". `BlurMaskFilter` diabaikan pada hardware canvas, jadi cincin feather turun degrade menjadi garis inner biasa, bukan blur.

**Review (subagent, read-only):** REQUEST CHANGES → 5 MAJOR ditangani. Putaran kedua: MAJOR "kontras gradien" **belum** benar pada perbaikan pertama — rata-rata stop extremes ternyata menghitung stop *tengah* (paling mudah dibaca), bukan terburuk, dan itu regresi dari warna rata. Diganti dengan rasio WCAG terhadap stop terburuk (lihat di atas) + harness numerik. Type-check terisolasi: `powershell -NoProfile -ExecutionPolicy Bypass -File .typecheck\check.ps1` → exit 0.

**Checklist test (user):** — semua masih perlu dijalankan di perangkat
- Dialog box bergradient: patch tidak terlihat sebagai persegi beda warna.
- Sudut/edge panel: tidak ada garis pemisah keras antara patch dan layar asli.
- Teks terjemahan: putih solid di atas panel gelap, hitam solid di atas panel terang.
- Performa Manual tidak turun terasa (frame capture → overlay).
- ~~Teks di atas scene art~~ — tidak berlaku, tier-2 dibatalkan.

**Utang terbuka:** scene-art continuity (Teks di atas pohon/langit) belum ada solusinya. Butuh keputusan: pertahankan bitmap capture sampai overlay selesai digambar, atau kirim potongan bitmap kecil per box. Keduanya menambah real memory cost pada jalur Manual.

### Tahap 3 — Klip: ink card minimalis

Status: [TODO] Redesign `KlipResultOverlayView.buildPanel()`. Posisi panel dan logika dim di luar selection **tidak berubah**.

Spesifikasi:

- **Fill:** solid `#17171A` alpha 245 (96%). Buang `ImageView` blur + wash + `RenderEffect`.
- **Radius:** 14dp (bukan 26dp).
- **Border:** hairline `rgba(255,255,255,0.06)` 1dp — atau hilangkan sama sekali bila terlihat cukup tegas.
- **Header:** hapus judul "Terjemahan" dan divider. Teks terjemahan = elemen pertama, langsung di atas scroll.
- **Tombol close:** ghost — tanpa lingkaran background, ikon alpha 55%, tetap di pojok kanan atas dengan tap target ≥ 36dp.
- **Tipografi:** teks `#F2F2F7` (bukan putih murni), 16–17sp, line spacing 1.35, padding 16dp konsisten semua sisi.
- **Animasi masuk:** fade + slide-up 12dp, 150ms, decelerate interpolator.
- **Dim luar selection:** alpha 150 → 110 (kartu cukup menonjol tanpa dim berat).
- **Edge case:** `selectedBitmap` masih di-recycle di `onDetachedFromWindow` (bitmap tidak lagi ditampilkan, tetap milik view).

**Checklist test (user):**
- Panel muncul solid, tanpa blur, tanpa judul/divider.
- Close berfungsi; posisi panel (bawah/atas selection) tidak berubah dari sebelumnya.
- Teks panjang tetap scroll; animasi masuk halus.
- Seleksi Klip dan dim tetap bekerja seperti sebelumnya.

### Tahap 4 — Ditunda (riset, bukan scope sekarang)

- [ ] Font profile per-game: bundle beberapa font (rounded/condensed/serif/bold-sans) + pemilihan per game. Ini solver sejati untuk "font game jarang = font sistem".
- [ ] Inpainting sungguhan (model kecil TFLite/OpenCV) untuk menghapus teks asli alih-alih menutupnya.
- [ ] Mode Real-Time.

## Referensi

- [Torii VN Translate](https://toriitranslate.com/visualnovel) — kontrol font/size/color/stroke untuk overlay VN; standar pendekatan untuk kasus game.
- [Game Lens Translator](https://thaluna.app/game-translator), [RSTGameTranslation](https://thanhkeke97.github.io/RSTGameTranslation/), [OverText (GitHub)](https://github.com/SiENcE/overtext) — tool sejenis: overlay terjemahan di atas teks asli.
- [Implementasi text outline di Android](https://devgex.com/en/article/00042659), [diskusi stroke/fill text](https://stackoverflow.com/questions/9044769/how-to-draw-text-with-different-stroke-and-fill-colors).

## Log keputusan (tambahkan saat kerja berjalan)

- 2026-09-25: dibuat; branch + referensi sesi sebelumnya diamankan sebagai `1639ed1`.
- 2026-09-25 Tahap 1: sampling glyph pakai k-means 3 cluster (bukan 2 — outline sering menyatu dengan background pada 2 cluster, terutama teks terang di panel gelap). Threshold outline vs background diturunkan ke 15 luminance: dark-on-dark outline memang bedanya kecil, dan tebakan salah aman karena stroke sewarna panel praktis tak terlihat. Shadow pakai `setShadowLayer` langsung (teks didukung hardware canvas, tanpa software layer).
- 2026-09-25 Tahap 1b: hasil test perangkat men perteneciente dua cacat render, bukan cacat sampling. (1) Panel tidak pernah tumbuh vertikal untuk mode Manual (`toleranceRatio=1`), jadi terjemahan yang wrap meluber ke bawah patch dan menimpa teks asli — tidak ada `clipRect` di app saat itu. (2) Kontras fill diukur terhadap warna background asli, padahal `drawPanel` mengecat panel lebih gelap (0.62x) untuk panel terang; hasilnya fill game bisa menyatu dengan panelnya sendiri. Outline juga bisa mati justru ketika sampling dipercaya, padahal jalur fallback selalu menggambarnya. Semua diperbaiki di `TranslationOverlayView.kt`; `paintedPanelColor()` sekarang satu-satunya sumber warna panel.
- 2026-09-25 Tahap 1c: test atas build `b32d261` (Actions #355) menunjukkan font size antar-paragraf tidak konsisten — paragraf panjang memaksa paragraf di bawahnya menyusut secara independen per item (`fitScale` dihitung per paragraf), sehingga hasilnya zig-zag acak dan sebagian teks jadi sangat kecil. Dipecah jadi dua pass: `measureItem` mengukur, `uniformScaleFor` memilih satu skala terkecil yang perlu di seluruh layar, `buildRenderItem` menerapkannya ke semua item. Konsekuensi yang disepakati user: terjemahan panjang boleh meluber dari panelnya sendiri, asalkan tidak mengorbankan paragraf lain. Catatan: `buildOverlapGroups()` yang ada di branch `debugging` hilang di branch ini — ia menyamakan **warna** antar-box overlap, bukan ukuran.
- 2026-09-26 Tahap 1d: review atas `980c7bd` menemukan dua hal. (1) `normalizeParagraph` mempertahankan `\n` dari DeepL sebagai baris baru, padahal DeepL memecah kalimat sesuka hatinya → kalimat muat 1 baris jadi 3 baris pendek, panel makin tinggi, paragraf bawah makin sempit. Diubah: `\n` jadi whitespace biasa. Diagnosis awal sempat salah mengaitkannya ke prompt OpenRouter; provider yang dipakai DeepL. (2) `collidesWithOtherBox` membuat box yang tumbuh menyerah ruang saat nabrak box lain — untuk kolom dialog berdempet vertikal tiap baris saling mengalah, diperparah oleh pertumbuhan vertikal Tahap 1b. Overlap adalah kondisi normal teks berdempet, jadi logikanya dihapus; `buildOverlapGroups()` (union-find) dari `debugging` dikembalikan untuk menyamakan **warna** panel antar-box overlap, dan warnanya juga dipakai untuk guard kontras teks.
- 2026-09-26 Tahap 1e: 1d menyamakan warna tapi tiap baris masih menggambar panel sendiri, jadi seam + garis bulat membuatnya terbaca sebagai kartu bertumpuk. `buildOverlapGroups` kini menghitung union rect per kelompok dan `onDraw` menggambar satu panel per kelompok; teks tetap per item sesuai usulan user (tiap baris punya terjemmaan, lebar, alignment sendiri — hanya background yang/shared). Pengelompokan dipersempit ke kolom vertikal saja lewat `stacksVertically` (overlap X ≥ 50% lebar sempit, ada urutan vertikal, jarak vertikal ≤ 0.75× lebar) supaya dua control berdampingan tidak ikut ter-merge dan batas yang membedakan keduanya tetap ada.
- 2026-09-26 Test perangkat `5210af7`: **user mengonfirmasi hasilnya lebih baik dari sebelumnya**. Paragraf sudah sesuai sejak Tahap 1c; 1d/1e menutup kasus kalimat berdempet vertikal. Lima item checklist ditandai terverifikasi. Sisa checklist (dialog gelap, panel terang, alignment, dua control berdampingan, bubble vertikal JP) belum diuji khusus dan tetap terbuka. Struktur overlay Manual dianggap selesai untuk lingkup tipografi + patch solid; sisa pekerjaan adalah kerapian patch (Tahap 2) dan mode Klip (Tahap 3). Manga OCR untuk bubble vertikal **sengaja ditunda** — dikerjakan setelah Tahap 2/3 selesai, agar tidak dicampur dengan pekerjaan yang belum diverifikasi.
- 2026-09-26 Tahap 1f: (1) Panel terkunci di tepi kiri & hanya tumbuh ke kanan → terjemahan panjang mentok di tepi kanan layar dan dipaksa wrap ke baris tambahan, padahal sisi kiri kosong. Ditambahkan `leftSlideRatio = 0.20f` (angka dari user) supaya panel bisa bergeser ke kiri recovering ruang itu sebelum wrap. (2) `stacksVertically` terlalu ketat (syarat overlap X ≥ 50% lebar sempit) — ML Kit memecah satu blok dialog jadi baris dengan lebar berbeda sehingga run baris terpecah jadi beberapa kelompok dan tiap baris dapat patch sendiri. Syarat overlap X dihapus; penentu sekarang hanya intersection + `a.top != b.top` + jarak vertikal ≤ 1× lebar tersempit. Sisa terbuka: kontras panel terang, bubble vertikal JP, dan manga OCR (ditunda terpisah).
- 2026-09-26 Catatan bubble vertikal JP (untuk diskusi terpisah): pipeline vertikal **sudah ada lengkap** — `isVerticalLine`/`isVerticalBlock`/`groupVerticalCandidates`/`areSameBubbleColumn`/`verticalBlockText` (sorting `centerX` descending = kanan→kiri, benar untuk JP), plus `bubbleWidthRatio = 2.80f` di render. Jadi masalahnya bukan fitting/pipeline, tapi **akurasi OCR vertikal ML Kit** — user melaporkan manual & klip membaca akurat, tapi overlay jelek & inconsistent. Ini yang akan dibahas terpisah.


## Aturan kerja untuk sesi berikutnya

**Keputusan user: tidak pernah membangun APK lokal.** APK selalu dibangun lewat GitHub Actions, lalu diuji user di perangkat. PC user low-end (i5 gen4, 8 GB) dan memang tidak ada niatan menyiapkan Android SDK/Studio. **Jangan usulkan setup build lokal**; itu keputusan user, bukan kekurangan yang perlu ditutup.

Yang boleh dan berguna: **type-check Kotlin terisolasi secara lokal** (detik, ringan, tanpa membebani PC) untuk menangkap error compile sebelum CI — karena CI hanya melaporkan error pertama per kompilasi, sehingga satu error menyembunyikan yang lain di bawahnya. Type-check lokal **bukan** pengganti build APK: `gradle assembleDebug` tetap mustahil di mesin ini karena Android SDK tidak ada.

### Kompilasi lokal tanpa Android SDK (resep, sudah teruji)

Butuh `kotlin-compiler-embeddable-1.9.22.jar`, `kotlin-stdlib`, `trove4j`, dan `org.jetbrains:annotations` (semua ada di `~/.gradle/caches/modules-2`, sisa setup awal project), plus **JDK 17** di `C:\Program Files\Java\jdk-17`. Jangan pakai Java 27: Kotlin 1.9.22 gagal parse string versi itu (`IllegalArgumentException: 27`).

Jalankan `org.jetbrains.kotlin.cli.jvm.K2JVMCompiler` dengan `-no-stdlib -cp <stdlib>` pada direktori berisi file target **plus stub** untuk `android.*` dan `com.google.mlkit.*`. Stub cukup berisi tanda tangan; nilai balik apa saja boleh. Perhatikan saat menulis stub: pakai method (`fun centerX() = 0`), jangan `@JvmField`; jadikan `var` setiap properti yang di-assign; sertakan **semua** overload yang dipanggil kode (mis. `drawRoundRect` 4-arg dan 6-arg); dan `companion object` harus di dalam class.

Stub yang keliru akan memunculkan error palsu (mis. `val cannot be reassigned` pada `typeface` yang stub-nya `val`). **Selalu periksa nama file yang muncul di error**: kalau yang error adalah stub, itu bukan bug project. Yang dihitung hanya error yang jatuh di file project.

Sudah terverifikasi bersih dengan metode ini: `TextLayoutAnalyzer.kt`, `TranslationOverlayView.kt`, `KlipResultOverlayView.kt` — 0 error pada Kotlin 1.9.22 / JDK 17.

### Jebakan yang sudah beberapa kali muncul

Semuanya dari kode yang *terlihat* benar:

1. **Properti setter-only.** Kotlin hanya menyintesis properti bila getter **dan** setter ada. Contoh: `android.graphics.Paint` punya `setStrokeColor()` tapi **tidak** punya `getStrokeColor()`, jadi `paint.strokeColor = x` gagal dengan `Unresolved reference`. Bandingkan: `strokeWidth`, `strokeJoin`, `color`, `alpha`, `style` aman karena punya getter+setter. Aturan: kalau nama field framework dipakai sebagai properti, pastikan pasangannya ada; kalau ragu, panggil setter-nya sebagai method — selalu kompilasi.
2. **Konstanta Android yang salah eja.** `View.OVERSCROLL_IF_CONTENT_SCROLLS` tidak ada; yang benar `View.OVER_SCROLL_IF_CONTENT_SCROLLS`. Sebelum commit, grep konstanta `View|Paint|Canvas|Color|*Layout` dan bandingkan dengan API asli.
3. **Baris padat yang ditulis ulang.** `if(cond)return false;val x=…` dalam satu baris mudah kehilangan segmen. Kalau menulis ulang seluruh file, verifikasi ulang **seluruh** file, bukan hanya baris yang terlihat berubah.

### Catatan tooling

- `Get-Content` di PowerShell shell ini membaca sebagai **ANSI** dan merusak karakter non-ASCII (Jepang jadi mojibake), sehingga diff berbasis string bisa salah. Bandingkan lewat decode UTF-8 eksplisit (`[System.IO.File]::ReadAllText(path, UTF8Encoding)`) atau `git diff`, bukan `Get-Content`.
- **Kesimpulan subagen wajib diverifikasi terhadap file asli sebelum diterapkan.** Audit terakhir melaporkan `val counts` terduplikasi di baris 131 dan `?: continue` ilegal di baris 135; kedua-duanya salah (baris 131 adalah `for (a in assignment) counts[a]++`, dan `?: continue` memang legal di statement position), dan ditolak setelah kompilasi nyata. Saran "hapus baris ini" dari laporan subagen berisiko menghapus pernyataan yang benar — selalu cek isi barisnya dulu.
- Bersihkan state yang jadi mati setelah redesign (field yang selalu `emptyList()`, `val` lokal tak terpakai, parameter yang tak dibaca) supaya file tidak menyesatkan pembaca berikutnya.
