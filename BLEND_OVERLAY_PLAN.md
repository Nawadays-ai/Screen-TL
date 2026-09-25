# Native-Blend Overlay + Minimal Klip — Working Plan

> **Scope:** catatan kerja perubahan tampilan mode Manual (overlay menyatu dengan game) dan mode Klip (desain minimalis). Mode Real-Time di luar scope.
> **Aturan konteks:** gunakan file ini sebagai konteks utama untuk task ini. Jangan meminta pembacaan README, AI handoff, atau catatan proyek lain untuk memahami rencana ini.
> **Status:** Tahap 0 selesai. Tahap 1 selesai ditulis (kode), menunggu build + test perangkat oleh user. Tahap 2–3 belum dimulai. Tahap 4 ditunda (riset).
> **Build policy:** jangan menjalankan Gradle/compile/APK lokal. User yang build dan test APK sendiri.

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

- [x] **Sampling gaya glyph** di `TextLayoutAnalyzer`: piksel interior box di-grid (~192 sampel), di-split oleh luminance dengan k-means 3 cluster. Cluster terjauh dari median background = fill; cluster di antaranya = stroke bila jaraknya dari background ≥ 15 dan dari fill ≥ 40 (kalau tidak, dianggap bukan outline dan tidak digambar). Cluster < 4% sampel di-merge ke tetangga terdekat. Box satu warna → `null` → fallback.
- [x] `TranslationOverlayItem`/`DetectedText` membawa `glyphStyle` dan `alignment` dari analyzer lewat pipeline.
- [x] **Render outline:** `FILL_AND_STROKE`, `strokeWidth = 4.5%` ukuran teks hasil render (≈5% glyph height), `strokeJoin = ROUND`.
- [x] **Render drop shadow:** `setShadowLayer(radius 3% textSize, offset 1.8% textSize, hitam 40%)`. Teks tidak butuh software layer (shadow text didukung hardware canvas di semua API ≥ 24).
- [x] **Alignment:** dari bounding box baris ML Kit — spread kiri ≤ 0.5 glyph → LEFT; spread kanan ≤ 0.5 glyph → RIGHT; selain itu CENTER. Multi-line block saja; single line dan bubble vertikal tetap CENTER. Diterapkan per baris hasil wrap.
- [x] Guard kontras: fill dari sampling dipakai bila `separation ≥ 60` luminance dari warna patch; kalau tidak → fallback putih/hitam + outline kontras selalu aktif. Stroke sampling hanya dipakai bila kontras fill ≥ 40; kalau tidak, stroke fallback (fill terang → hitam, fill gelap → putih).
- [x] Ukuran/wrap/collision/layout tidak diubah di Tahap ini.

**Checklist test (user):**
- [ ] Dialog box gelap: teks terjemahan punya outline gelap + shadow, fill meniru warna teks asli.
- [ ] Panel/button terang: teks tetap kontras (tidak "tenggelam").
- [ ] Teks asli rata kiri → terjemahan juga rata kiri; yang center tetap center.
- [ ] Bubble vertikal JP: tetap terbaca, outline tidak membuat huruf "tebal berlebihan".

### Tahap 2 — Manual: patch penuh

Status: [TODO] Tujuan: area di bawah teks tidak terlihat sebagai kotak blok.

- [ ] **Gradient vertical:** ganti 1 warna rata dengan `LinearGradient` — median strip atas ~15% dan strip bawah ~15% dari interior box (dialog box game umumnya bergradient).
- [ ] **Feather edge:** tepi panel di-blur ±2–3px (scaled) supaya batas kotak tidak keras. Cara utama: `LAYER_TYPE_SOFTWARE` pada `TranslationOverlayView` + `BlurMaskFilter`. Bila frame turun → fallback: 2–3 cincin rounded-rect dengan alpha menurun.
- [ ] **Deteksi UI-box vs scene art:** stddev piksel interior. Rendah (uniform) → cukup gradient patch. Tinggi → lanjut ke tier-2.
- [ ] **Tier-2 scene-art patch:** ambil strip piksel tepat di kiri & kanan box, stretch/mirror masuk ke dalam box (render ke bitmap via software canvas), blur ringan, lalu feather. Tujuan: teks yang menempel di atas gambar scene tidak memblok scene dengan warna solid.
- [ ] Border: pertahankan rules sekarang (hanya panel terang, alpha rendah) sebagai fallback kontras; feather membuatnya makin jarang perlu.

**Checklist test (user):**
- Dialog box bergradient: patch tidak terlihat sebagai persegi beda warna.
- Teks di atas scene art (pohon/langit): scene tidak terblok solid, masih ada kontinuitas gambar.
- Sudut/edge panel: tidak ada garis pemisah keras antara patch dan layar asli.
- Performa Manual tidak turun terasa (frame capture → overlay).

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


## Aturan kerja untuk sesi berikutnya

**Tidak ada Android SDK di mesin ini, jadi `gradle assembleDebug` tidak bisa jalan — TAPI kode Kotlin bisa dikompilasi lokal memakai compiler dari Gradle cache.** Pakai ini sebelum mengandalkan CI, karena CI hanya melaporkan error pertama per kompilasi sehingga satu error menyembunyikan yang lain di bawahnya.

### Kompilasi lokal tanpa Android SDK (resep, sudah teruji)

Butuh `kotlin-compiler-embeddable-1.9.22.jar`, `kotlin-stdlib`, `trove4j`, dan `org.jetbrains:annotations` (semua ada di `~/.gradle/caches/modules-2`), plus **JDK 17** di `C:\Program Files\Java\jdk-17`. Jangan pakai Java 27: Kotlin 1.9.22 gagal parse string versi itu (`IllegalArgumentException: 27`).

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