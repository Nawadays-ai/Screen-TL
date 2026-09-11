# Firefox Translator Roadmap

## Tujuan
Membangun pipeline translator manga/Firefox yang menggabungkan preprocessing, Dual OCR, smart layout analysis, background inpaint, translation, dan smart overlay tanpa mengganggu pipeline Manual TL/Klip yang sudah bekerja.

## Aturan Pengerjaan
- Satu pipeline arsitektur, tetapi implementasi dilakukan bertahap.
- Setiap tahap wajib build dan device test sebelum dianggap selesai.
- Setelah pengguna menyatakan hasil tahap bagus, tahap tersebut menjadi checkpoint/accepted dan dilanjutkan ke tahap berikutnya.
- Jangan mengerjakan tahap berikutnya secara bersamaan sebelum checkpoint tahap aktif diterima.
- Jangan merusak atau mengganti perilaku Klip yang sudah berhasil.
- Jangan menyatakan fitur berhasil hanya karena build sukses; device test tetap diperlukan.

## Urutan
1. [~] Text Preprocessing — tahap aktif
2. [ ] Dual OCR Pintar
3. [ ] Smart Layout Analysis
4. [ ] Background Inpaint
5. [ ] Firefox Translator
6. [ ] Integrasi penuh dan regression test

## Checkpoint
### Text Preprocessing
Status: aktif / belum diverifikasi perangkat.

Target awal:
- preprocessing adaptif, bukan filter agresif tetap;
- mempertahankan gambar asli sebagai fallback;
- menyediakan representasi yang lebih sesuai untuk OCR;
- tidak mengubah koordinat capture atau overlay;
- dapat dibandingkan dengan pipeline OCR lama.

## Catatan
Roadmap ini sengaja memisahkan checkpoint implementasi dari desain pipeline akhir. Komponen baru boleh memakai implementasi lama sebagai fallback sampai tahap tersebut diterima.
