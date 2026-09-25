# Native-Blend Overlay + Minimal Klip — Working Plan

> **Scope:** catatan kerja perubahan tampilan mode Manual (overlay menyatu dengan game) dan mode Klip (desain minimalis). Mode Real-Time di luar scope.
> **Aturan konteks:** gunakan file ini sebagai konteks utama untuk task ini. Jangan meminta pembacaan README, AI handoff, atau catatan proyek lain untuk memahami rencana ini.
> **Status:** Tahap 0 selesai. Tahap 1–3 belum dimulai. Tahap 4 ditunda (riset).
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

Status: [TODO] Tujuan: teks terjemahan terbaca sebagai teks game, bukan teks UI Android.

- [ ] **Sampling gaya glyph** di `TextLayoutAnalyzer.estimate()` (atau fungsi baru): piksel interior box dipisah 2 cluster luminans — cluster terang = fill teks asli, cluster gelap = outline/warna dasar teks. Hasil per unit: `textFill`, `strokeColor`, skor pemisahan (stddev/jarak cluster). Bila pemisahan lemah (teks tanpa outline, kontras rendah) → fallback: fill putih/hitam sesuai luminans seperti sekarang.
- [ ] `TranslationOverlayItem` membawa field style baru (`textFill`, `strokeColor`, `alignment`) dari pipeline.
- [ ] **Render outline:** `Paint.Style.FILL_AND_STROKE`, `strokeWidth ≈ 5% glyph height` (pakai `sourceTextSizePx`), `strokeJoin = ROUND`, warna dari `strokeColor`.
- [ ] **Render drop shadow:** `textPaint.setShadowLayer(radius ≈ 2% glyph height, dx/dy ≈ 1.5%, hitam 40%)`. Fallback bila tidak muncul di hardware canvas: gambar teks dua kali (versi gelap offset dulu).
- [ ] **Alignment:** dari bounding box baris ML Kit — semua left edge dalam selisih < 0.5 glyph height → rata kiri; selain itu → center. Berlaku untuk garis hasil wrap juga (sekarang selalu center).
- [ ] Kontras utama: `textFill` dipakai hanya bila luminance-nya beda cukup dari warna patch (ambang awal: selisih luminance ≥ 60); kalau tidak, fallback putih/hitam + outline tetap jalan.
- [ ] Pertahankan aturan ukuran/wrap/collision yang ada. Jangan ubah layout di Tahap ini.

**Checklist test (user):**
- Dialog box gelap: teks terjemahan punya outline gelap + shadow, fill meniru warna teks asli.
- Panel/button terang: teks tetap kontras (tidak "tenggelam").
- Teks asli rata kiri → terjemahan juga rata kiri; yang center tetap center.
- Bubble vertikal JP: tetap terbaca, outline tidak membuat huruf "tebal berlebihan".

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
