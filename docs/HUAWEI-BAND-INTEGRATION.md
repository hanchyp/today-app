# Integrasi Huawei Band 11 — Timer Istirahat Workout

Riset: 17 Nov 2026. Tujuan: sisa waktu istirahat antar set muncul (atau minimal terasa) di Huawei Band 11 saat workout.

## Ringkasan

| Yang diinginkan | Bisa? | Jalur |
|---|---|---|
| Band **bergetar saat istirahat selesai** | ✅ Ya, hampir tanpa kode | Notifikasi biasa → diteruskan Huawei Health |
| Band menampilkan **jam selesai / durasi** istirahat | ✅ Ya, perubahan kecil | Teks notifikasi statis ("Selesai 14:32:10") |
| Band menampilkan **hitung mundur yang berjalan** (90…89…88) | ⚠️ Secara teknis mungkin, tapi mahal & belum pasti didukung Band 11 | App lite-wearable di band + Wear Engine P2P |
| Tombol di band ("+30 dtk", "Lewati") | ⚠️ Mungkin | Wear Engine Notify (template dengan tombol) |

## Fakta Huawei Band 11 yang relevan

- OS: HarmonyOS 6 (versi wearable ringan). Layar AMOLED 1,62", 5 ATM, tanpa GPS, baterai ±8–14 hari.
- Wajib dipasangkan lewat **Huawei Health** (Android 9+). Kompatibel dengan HP non-Huawei, termasuk Xiaomi.
- Aplikasi bawaan: Timer, Stopwatch, Cuaca, Kalkulator, dsb. Ulasan menyebut Band 11 **tidak** menjalankan app pihak ketiga seperti smartwatch. AppGallery wearable ada di Huawei Health, tapi belum jelas apakah Band 11 bisa memasang app pihak ketiga.
- Notifikasi: Huawei Health **membaca notifikasi di status bar HP lalu mendorongnya ke band**. Isinya statis (judul + teks); band tidak menjalankan `setUsesChronometer`, jadi hitung mundur Android tidak ikut berjalan di band.
- Wear Engine (SDK resmi Huawei untuk app di HP): mendukung "Band 7 dan model setelahnya" untuk:
  - **Notify**: notifikasi berbasis template (judul, isi, sampai beberapa tombol) dikirim langsung dari app HP ke band.
  - **P2P**: kirim pesan/file antara app di HP dan app di band (butuh app di sisi band).
  - Developer individu hanya boleh mengajukan izin *info perangkat dasar* dan *notifikasi pesan*. Status pemakai (detak jantung, status dipakai) khusus enterprise.
  - HP non-Huawei: Android 6–14 menurut dokumen. **Android 15 belum tercantum.** Wajib Huawei Health + login HUAWEI ID.

## Kondisi TodayApp sekarang

`workout/RestTimer.kt` sudah:
1. Menyimpan `endsAt` di DataStore,
2. Memasang notifikasi *ongoing + silent* dengan chronometer hitung mundur,
3. Memasang alarm exact → `RestTimerReceiver` → notifikasi **"Istirahat selesai"** (prioritas tinggi, bergetar).

Akibatnya di band:
- Notifikasi berjalan **kemungkinan besar tidak diteruskan** (ongoing + silent biasanya disaring, dan kalaupun sampai, angkanya tidak bergerak).
- Notifikasi selesai **seharusnya sudah sampai** ke band, asalkan "Today" diaktifkan di Huawei Health → Perangkat → Notifikasi.

## Opsi, dari termurah

### Tier 0 — Atur saja (tanpa kode)
1. Huawei Health → Perangkat → Band 11 → Notifikasi → aktifkan **Today**.
2. Di MIUI: izinkan Huawei Health berjalan di latar (autostart, tanpa pembatasan baterai) dan matikan filter notifikasi.
3. Uji: mulai set, kunci HP, tunggu istirahat selesai → band harus bergetar dengan "Istirahat selesai".

### Tier 1 — Notifikasi yang ramah band ✅ diimplementasikan 17 Nov 2026

Yang sudah jadi: toggle **Pengaturan → Notifikasi → "Timer istirahat ke smartband"** (default mati). Teks "Istirahat 1:30 mnt · selesai 14:32:10" selalu tampil. Aksi "+30 dtk" / "Lewati" ada di notifikasi. Kalau toggle aktif: notifikasi mulai tidak silent/ongoing, peringatan "10 detik lagi" muncul untuk istirahat >20 dtk, lalu "Istirahat selesai". Rencana awalnya:
Perubahan di `RestTimer.kt` saja, tanpa SDK baru:
- Saat istirahat dimulai, tampilkan teks absolut: **"Istirahat 90 dtk · selesai 14:32:10"**. Di band ini terbaca walau tidak ada hitungan mundur.
- Opsional: **peringatan 10 detik terakhir** ("10 detik lagi"), dengan alarm exact kedua, supaya band bergetar pendek sebelum set berikutnya.
- Opsional: tombol aksi di notifikasi ("+30 dtk", "Lewati"). Tombol notifikasi Android tidak dijamin muncul di band. Di HP tetap berguna.
- Toggle di Pengaturan: "Kirim ke smartband" (notifikasi mulai jadi tidak silent, tapi pendek).

Konsekuensi: band menampilkan jam selesai, bukan angka yang berjalan. Untuk hitung mundur yang terlihat, pakai **Timer bawaan band**, atau langsung lirik jam di band dan bandingkan.

### Tier 2 — Wear Engine Notify (±2–4 hari + proses persetujuan Huawei)
- Daftar Huawei Developer (individu), buat app di AppGallery Connect, ajukan izin Wear Engine "notifikasi pesan". Prosesnya pakai review, waktunya tidak pasti.
- Tambah `com.huawei.hms:wearengine` dan repo Maven Huawei, alur auth `HiWear.getAuthClient`, lalu `NotifyClient` dengan template bertombol.
- Untungnya: tidak bergantung pada filter notifikasi Huawei Health/MIUI, dan tombol bisa memicu callback di HP (misalnya "+30 dtk").
- Risiko: kompatibilitas Android 15 di HP non-Huawei belum tercantum. SDK menambah dependensi HMS ke app yang sekarang bersih. Hasil di band tetap notifikasi statis.

### Tier 3 — App di band + P2P (hitung mundur sungguhan; tidak direkomendasikan sekarang)
- Buat app lite-wearable (DevEco Studio, ArkTS/JS) yang menerima `endsAt` lewat P2P lalu menghitung mundur sendiri di band.
- Hambatan: belum terkonfirmasi Band 11 menerima app pihak ketiga. Distribusinya lewat AppGallery wearable (review). Ada dua codebase, perlu sinkronisasi saat lewati/tambah waktu, dan perlu fingerprint sertifikat.
- Baru layak kalau Huawei mengonfirmasi dukungan app pihak ketiga untuk Band 11 dan Tier 1 terasa kurang.

## Rekomendasi

Kerjakan **Tier 0 → uji**, lalu **Tier 1** sebagai fitur kecil (cocok jadi satu sprint mini). Tunda Tier 2/3. Manfaat tambahannya (tombol di band, hitung mundur hidup) tidak sebanding dengan biaya akun developer, review Huawei, dependensi HMS, dan ketidakpastian dukungan Band 11 / Android 15.

## Belum terverifikasi
- Apakah Huawei Health meneruskan notifikasi *ongoing* (status Today sekarang): uji di perangkat.
- Daftar resmi per model Wear Engine untuk Band 11 (dokumen hanya bilang "Band 7 dan setelahnya").
- Dukungan app pihak ketiga di Band 11 (AppGallery wearable).

## Sumber
- [HUAWEI Band 11 – halaman produk](https://consumer.huawei.com/my/wearables/band11/)
- [Kompatibilitas Band 11 dengan Android/iOS](https://consumer.huawei.com/en/support/content/en-us16080033/)
- [Review Band 11 – Gadgetbyte Nepal](https://www.gadgetbytenepal.com/huawei-band-11-and-band-11-pro-review/)
- [Spesifikasi Band 11 – Gadgets & Wearables](https://gadgetsandwearables.com/technical-specs/huawei-band-11/)
- [Wear Engine – Service Introduction](https://developer.huawei.com/consumer/en/doc/connectivity-Guides/service-introduction-0000000000018585)
- [Wear Engine – Applying for the service](https://developer.huawei.com/consumer/en/doc/connectivity-Guides/applying-wearengine-0000001050777982)
- [Wear Engine Codelab](https://developer.huawei.com/consumer/en/codelab/WearEngine/index.html) · [GitHub huaweicodelabs/WearEngine](https://github.com/huaweicodelabs/WearEngine)
- [Notifikasi tidak masuk di band (HP non-Huawei)](https://consumer.huawei.com/en/support/content/en-us15945721/)
- [Timer di watch/band Huawei](https://consumer.huawei.com/en/support/content/en-us15818719/)
- [Mengelola app di watch Huawei](https://consumer.huawei.com/en/support/content/en-us15771217)
- [AppGallery smartwatch didesain ulang (Okt 2026)](https://the5krunner.com/2026/10/30/quick-read-huawei-smartwatch-appgallery-redesign/)
