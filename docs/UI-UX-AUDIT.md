# Audit UI/UX — Today (Android)

Revisi 3 · 2026-11-17 · Acuan: skill `mobile-android-design` (wshobson) + panduan Material 3
**Status: perbaikan sudah diterapkan di kode. Lihat [Status perbaikan](#status-perbaikan) di bawah.** Temuan aslinya tetap dicatat sebagai riwayat.
Cakupan: **semua layar** di `app/src/main/java/com/farhan/today/ui/**`, yaitu Today, Kalender, Latihan (daftar, form rutinitas, sesi, ringkasan), Karier (daftar, detail, form, jadwal), Keuangan (beranda, transaksi, sheet), Tugas (daftar, proyek, sheet), Pengaturan, dan Onboarding.
Metode: audit statis kode. Belum diverifikasi visual di perangkat (lihat checklist di bagian akhir).

**Prioritas**
- **P0**: langsung terasa "aneh" atau rusak oleh pengguna.
- **P1**: bentuk, ukuran, atau alur kurang pas di HP.
- **P2**: konsistensi & polish.

**Kesimpulan:** fondasinya sudah bagus. Palet dan tipografi terpusat, target sentuh umumnya ≥ 48 dp, ada undo lewat snackbar, ada semantics untuk TalkBack, dan sudah ada fallback untuk teks besar. Masalah utamanya ada di **shell navigasi** (transisi, bottom bar, inset), **form** (keyboard, tombol simpan, perubahan yang belum disimpan), dan **inkonsistensi pola** antar modul. Setelah semua P0 + P1 di bawah beres, UI/UX menurut saya sudah *okay* untuk dipakai sehari-hari. P2 adalah polish.

---

## Status perbaikan

Keterangan: ✅ selesai · 🟡 sebagian · ⏭️ sengaja tidak dikerjakan (alasannya ada di kolom catatan).
Semua perubahan sudah lolos compile dan 79 unit test, dan sudah dicek sekilas di emulator Pixel 4 (API 34): splash, onboarding, Today, daftar Karier, dan detail lamaran. **Belum dicek di HP sungguhan**, jadi jalankan checklist di bagian akhir.

### Fondasi baru (dipakai semua layar)
- `ui/components/Layout.kt`, berisi:
  - `ScreenColumn`: kolom layar tab, sumber tunggal padding & inset.
  - `DetailScaffold`: top app bar M3 menempel + bar aksi bawah yang tetap terlihat saat keyboard terbuka.
  - `LoadingSkeleton` / `LoadingDetail`.
  - `TodayFab`.
  - `SearchField`.
  - `rememberBackGuard`: konfirmasi "Buang perubahan?".
  - `KeepScreenOn`, `LightStatusBarIcons`, `rememberConfirmHaptic`.
- `ui/components/Previews.kt`: anotasi `@PhonePreviews` untuk 320dp/teks 2×, 360dp/teks 1.3×, dan 411dp.
- `ui/theme/Theme.kt`:
  - Skema warna M3 lengkap.
  - Tipografi M3 memakai Inter.
  - Token `Spacing`.
  - `TodayType.Title` (26 sp) untuk judul detail.
  - Ukuran teks: Display 32 sp, Hero 28 sp, Tiny 11 sp.
- 27 ikon vektor baru di `res/drawable` (`ic_arrow_back`, `ic_chevron_*`, `ic_expand_*`, `ic_check`, `ic_close`, `ic_delete`, `ic_edit`, `ic_cat_*`, dan lain-lain) menggantikan glyph teks & emoji.

| Item | Status | Catatan |
|---|---|---|
| A1 Transisi | ✅ | Fade through antar-tab, shared axis X (geser ~8% + fade) untuk push/pop, easing Material. `TodayApp.kt` |
| A2 Bottom nav | ✅ | Hanya tampil di rute akar tab (`Routes.isTabRoot`). Muncul/hilang dengan slide |
| A3 Layar kosong saat memuat | ✅ | Semua `return` kosong diganti `LoadingDetail` / `LoadingSkeleton` |
| A4 Form tugas di atas layar kosong | ✅ | Dari Today, sheet dibuka langsung di atas Today. Rute `tasks/form` (deep link) menampilkan daftar Tugas di belakang sheet |
| A5 Predictive back | ✅ | `android:enableOnBackInvokedCallback="true"` |
| A6 BackHandler | ✅ | Onboarding mundur 1 halaman. Form lamaran, jadwal, rutinitas, dan acara kalender menanyakan "Buang perubahan?" |
| A7 Kalender tanpa tombol kembali | ✅ | `DetailScaffold` + tombol Tambah di top bar |
| A8 Animasi ganti bulan / halaman | ✅ | `AnimatedContent` di Kalender, Transaksi, Ringkasan, Onboarding. Kalender juga bisa digeser (swipe) |
| B1 Inset status bar | ✅ | `Scaffold(contentWindowInsets = WindowInsets(0))`. Ikon status bar terang di sesi latihan |
| B2 Keyboard menutupi form | ✅ | `ScreenColumn`/`DetailScaffold` memakai inset keyboard. Semua sheet ditambah `imePadding()` |
| B3 Kartu fokus Today | ✅ | Judul 22 sp maks. 2 baris, maskot 72 dp (hanya bila lebar ≥ 280 dp) |
| B4 Maskot menumpuk teks | ✅ | `HeroCard` & kartu sesi latihan memakai `Row` |
| B5 Tombol utama terkubur | ✅ | Bar aksi bawah di detail lamaran, form lamaran, jadwal, rutinitas, dan ringkasan selesai. Tombol Simpan menempel di bawah sheet transaksi |
| B6 Padding tidak seragam | ✅ | `Spacing.ScreenH = 20.dp` di semua layar |
| B7 Judul terlalu besar | ✅ | Tab 32 sp, detail memakai judul di top bar + `Title` 26 sp |
| B8 Daftar panjang tanpa Lazy | 🟡 | Tugas diubah ke `LazyColumn`; Today, Transaksi, dan Gerakan memang sudah lazy. Beranda Keuangan, daftar Karier, dan riwayat Latihan masih `Column`; itemnya sedikit (Keuangan menampilkan maks. 5 transaksi + 3 tagihan) |
| B9 Statistik 10 sp terpotong | ✅ | Tata letak 2 + 1, label 11 sp, ellipsis, angka tabular |
| C1 Tombol tanpa disabled/loading | ✅ | `PrimaryButton(enabled, loading)` dengan spinner. Dipakai di semua form |
| C2 Form acara di AlertDialog | ✅ | Pindah ke bottom sheet, error berwarna, validasi saat Simpan |
| C3 TimePicker | ✅ | Dialog sendiri + tombol ganti jam/keyboard. Default keyboard di layar < 560 dp |
| C4 Kolom pencarian | ✅ | `SearchField` di Karier, Transaksi, dan Gerakan |
| C5 Isian hilang | ✅ | Lihat A6 |
| C6 "WIB" permanen | ✅ | Dihapus |
| C7 Glyph teks | ✅ | Semua diganti ikon vektor |
| C8 Teks tombol rata kiri | ✅ | Rata tengah |
| D1 `ScrollColumn` duplikat | ✅ | 4 duplikat dihapus, diganti `ScreenColumn` / `DetailScaffold` |
| D2 Tombol Tambah di 4 tempat | ✅ | `TodayFab` di Today, Latihan, Karier, Keuangan, Tugas. Mengecil saat digulir |
| D3 Header detail | ✅ | `TopAppBar` M3 menempel di semua layar turunan |
| D4 Dua jenis tombol / daftar | 🟡 | Tombol sudah satu jenis. Baris daftar: Today tetap `ListItem` M3 (diberi border dan bentuk kartu yang sama), modul lain kartu custom |
| D5 Emoji sebagai ikon | ✅ | Ikon vektor per kategori (`categoryIcon`). Chip kategori juga memakai ikon |
| D6 Latar tidak seragam | ✅ | Form rutinitas memakai latar krem seperti form lain |
| D7 Pilih tahap lamaran | ✅ | Bottom sheet + radio |
| E1 Subtitle hilang di Today | ✅ | Dibungkus `Column` |
| E2 Centang dari Today | ✅ | Checkbox di baris tugas + snackbar "Batalkan" + haptic |
| E3 Urutan Today | ✅ | Berikutnya → Seharian → Terlewat (bisa dilipat) → Selesai |
| E4 Kalender | ✅ | Lihat A7 & B6. Grid kini muat di HP 320 dp |
| E5 Legenda kalender | ✅ | Ditampilkan di bawah grid |
| E6 Badge tahap 28 dp | ✅ | Tombol 48 dp dengan ikon ▼ dan label TalkBack |
| E7 Label stepper 10 sp | ✅ | 12 sp |
| E8 "Lunas" → aksi | ✅ | "Bayar" |
| E9 Petunjuk geser hapus | ✅ | Dipindah ke atas daftar. Ikon hapus di latar geser |
| E10 Nominal terpotong | ✅ | Ukuran 40 → 32 → 26 sp sesuai panjang |
| E11 Header tanggal | ✅ | `stickyHeader` |
| E12 Tombol "Hapus sesi" berulang | ✅ | Pindah ke menu ⋮ |
| E13 Layar mati saat latihan | ✅ | `KeepScreenOn()` di layar sesi |
| E14 Hapus set tersembunyi | ✅ | Tombol "Hapus set" muncul saat set dipilih (long-press tetap jalan) |
| E15 Switch pengingat di beranda | ✅ | Dihapus dari beranda. Sudah ada di Pengaturan → Notifikasi |
| E16 Proyek mendorong tugas | ✅ | Carousel horizontal satu baris |
| E17 Checkbox 44 dp | ✅ | 48 dp |
| E18 Placeholder "Farhan" | ✅ | "Nama kamu" |
| F1 Haptic | ✅ | Centang tugas (Today & Tugas), set selesai, timer istirahat habis |
| F2 Skema warna M3 | ✅ | Semua slot diisi, tidak ada ungu bawaan |
| F3 Animasi daftar | ✅ | `animateItem()` di Today, Tugas, Transaksi. Riwayat tagihan memakai `AnimatedVisibility` |
| F4 Splash & loading | ✅ | Skeleton di semua layar. SplashScreen API menahan logo sampai status onboarding terbaca |
| F5 Dark mode & preview | 🟡 | Preview ✅ (`@PhonePreviews`). Dark mode ⏭️: `TodayColors` dipakai sebagai konstanta di ~480 tempat, jadi perlu memindahkannya ke `CompositionLocal` dan memeriksa ulang kontras palet kuning–navy. Lebih aman sebagai pekerjaan terpisah |

---

## Ringkasan

| Area | P0 | P1 | P2 |
|---|---|---|---|
| A. Navigasi & transisi | 4 | 3 | 1 |
| B. Layout & inset | 2 | 4 | 3 |
| C. Form & input | 1 | 4 | 3 |
| D. Konsistensi pola | – | 3 | 4 |
| E. Per layar | – | 8 | 10 |
| F. Feedback & sistem | – | 2 | 3 |
| **Total** | **7** | **24** | **24** |

---

## A. Navigasi & transisi

### A1 · P0 — NavHost tanpa transisi, jadi pakai default crossfade 700 ms
`ui/TodayApp.kt:163` — default navigation-compose 2.8 adalah `fadeIn(tween(700))` + `fadeOut(tween(700))`. Akibatnya dua halaman tumpang-tindih semi-transparan hampir 1 detik. Ini **penyebab utama transisi terasa aneh**.

**Perbaikan:**
- Pindah tab → *fade through*: keluar 90 ms, masuk fade + scale 0.92→1 selama 210 ms.
- Push detail/form → *shared axis X*: slide ±30 dp + fade, 300 ms. Saat pop, arahnya dibalik.
- Bedakan keduanya dengan `Routes.tabOf()` pada `initialState`/`targetState`.

### A2 · P0 — Bottom nav tampil di semua halaman
`ui/TodayApp.kt:151` — nav bar ikut tampil di form, detail, dan **sesi latihan gelap**. Saat konten bergerak, nav bar diam, jadi transisinya terasa patah. Selain itu ruang layar berkurang sekitar 80 dp.

**Perbaikan:** tampilkan hanya di 5 rute tab, dengan `AnimatedVisibility(slideInVertically/slideOutVertically)`.

### A3 · P0 — Halaman masih memuat = layar kosong
- `WorkoutScreens.kt:560` — `if (state.loading || session == null) return`
- `CareerScreens.kt:221` — `if (state.loading) return`
- `CareerScreens.kt:448` (form) dan `WorkoutScreens.kt:322` (form rutinitas) — `if (!vm.loaded) return`
- `SettingsScreens.kt` `SettingsRoute` — `?: return`
- `CareerRoute` — `state.loading -> {}`

Halaman masuk dalam keadaan kosong, lalu konten meloncat muncul. **Perbaikan:** selalu render kerangka (top bar + skeleton), jangan `return` dengan layar kosong.

### A4 · P0 — "Tambah tugas" dari Today membuka sheet di atas halaman kosong
`TasksScreens.kt:96` `TaskFormDestination` hanya berisi `ModalBottomSheet`. Rute `tasks/form` tidak punya isi di belakangnya, jadi pengguna melihat layar krem kosong (plus bottom nav dengan tab Tugas aktif) lalu sheet muncul. Setelah ditutup, ada halaman kosong lagi selama animasi pop.

**Perbaikan:** jadikan `tasks/form` sebagai `dialog()`/bottom-sheet destination, atau buka sheet langsung dari Today tanpa navigasi. Pola yang sama perlu dicek untuk "Tambah → Acara" (Kalender + dialog) supaya konsisten.

### A5 · P1 — Predictive back belum aktif
`AndroidManifest.xml` belum punya `android:enableOnBackInvokedCallback="true"`. Navigation 2.8 sudah mendukungnya, jadi tinggal diaktifkan.

### A6 · P1 — Tidak ada `BackHandler` sama sekali
Tidak ditemukan satu pun `BackHandler` di kode UI. Akibatnya:
- **Onboarding**: tombol/gestur kembali di halaman 2–3 langsung **keluar dari app** (halamannya disimpan di state, bukan navigasi).
- **Form lamaran, jadwal interview, rutinitas**: kembali = isian hilang tanpa konfirmasi. Ironisnya, sheet tugas sudah punya konfirmasi "Buang perubahan?".

**Perbaikan:** `BackHandler(page > 0) { page-- }` di onboarding. Form dengan `dirty` → dialog "Buang perubahan?", disamakan dengan pola di `TaskFormSheet`.

### A7 · P1 — Kalender tidak punya tombol kembali
`CalendarScreen.kt:166` — Kalender dibuka dari Today tetapi tidak punya top bar atau tombol kembali. Satu-satunya jalan keluar adalah gestur sistem atau tab bawah (yang menyorot "Today").

### A8 · P2 — Ganti bulan dan ganti halaman onboarding tanpa animasi
Bulan di Kalender, bulan di Transaksi, dan halaman onboarding berganti mendadak. Pakai `AnimatedContent` dengan slide horizontal sesuai arah (‹/›). Sebagai bonus, Kalender bisa digeser (swipe) dengan `HorizontalPager`.

---

## B. Layout & inset

### B1 · P0 — Edge-to-edge patah: inset status bar dimakan Scaffold
`ui/TodayApp.kt:163` — `padding(inner).consumeWindowInsets(inner)` membuat semua `statusBarsPadding()` di layar tidak berpengaruh. Akibatnya area status bar **selalu krem**, termasuk di atas sesi latihan yang gelap (`#111116`) dan form rutinitas yang putih. Ikon status bar juga tetap gelap di atas layar gelap.

**Perbaikan:** `Scaffold(contentWindowInsets = WindowInsets(0))`, lalu biarkan layar yang menangani inset. Atur ikon status bar terang di layar sesi.

### B2 · P0 — Keyboard menutupi field & tombol simpan
Hanya `RoutineForm` (`WorkoutScreens.kt:325`) yang memakai `imePadding()`. Form lamaran, jadwal, pengaturan, dan field pencarian di bawah layar tertutup keyboard.

**Perbaikan:** `imePadding()` di `ScrollColumn` bersama (lihat D1).

### B3 · P1 — Kartu fokus Today terlalu besar
`TodayScreen.kt:287` — judul 32 sp berbagi baris dengan maskot 90 dp, sehingga lebar teks hanya sekitar 180–215 dp di HP 360–393 dp. Judulnya pecah 3–4 baris. **Perbaikan:** 22–24 sp, `maxLines = 2`, maskot 64–72 dp.

### B4 · P1 — Maskot menumpuk teks
`WorkoutScreens.kt:186-200` (kartu sesi) dan `Components.kt:121` (`HeroCard`) — maskot diposisikan absolut di `TopEnd`, dan hanya judul (atau tidak ada sama sekali) yang diberi ruang. Badge dan teks fokus tertimpa. **Perbaikan:** `Row { Column(weight 1f); Image }`.

### B5 · P1 — Tombol utama terkubur di bawah halaman panjang
- Detail lamaran (`CareerScreens.kt:300`): "Jadwalkan interview" ada **di bawah riwayat tahap**.
- Form lamaran, jadwal, rutinitas, dan sheet transaksi: "Simpan" baru terlihat setelah scroll.

**Perbaikan:** taruh aksi utama di bar bawah yang menempel (`Scaffold(bottomBar)` atau `Box` + `align(BottomCenter)` dengan latar dan `imePadding`). Untuk sheet transaksi, tombol Simpan diletakkan tepat di bawah keypad.

### B6 · P1 — Tiga nilai padding horizontal berbeda
Today 20 dp, Kalender **12 dp**, modul lain 24 dp. Saat pindah layar, konten bergeser. **Perbaikan:** satu token, misalnya `Spacing.ScreenH = 20.dp`.

### B7 · P2 — Judul 36 sp di semua layar
`ScreenHeader` / `TodayType.Display` + eyebrow + subjudul memakan sekitar 110 dp. Di detail dan form, judulnya juga 36 sp ("Jadwalkan\ninterview" bahkan dipaksa 2 baris). Pertimbangkan 28 sp untuk detail/form, atau `LargeTopAppBar` yang mengecil saat scroll.

### B8 · P2 — `Column + verticalScroll` untuk daftar panjang
Beranda Finance, Career, Workout (riwayat), dan Tugas merender semua item sekaligus. Ganti dengan `LazyColumn`. Selain lebih ringan, ini juga membuka `Modifier.animateItem()` (lihat F3).

### B9 · P2 — Tiga kartu statistik dengan teks 10 sp
`FinanceScreens.kt:270` `MiniStat` — 3 kolom di 360 dp, label `TodayType.Tiny` (10 sp), nominal `maxLines = 1` **tanpa ellipsis**, sehingga nominal besar terpotong kasar. Pakai 2 kolom + 1 baris, atau `autoSize`/ellipsis.

---

## C. Form & input

### C1 · P0 — Tombol tidak punya state disabled / loading
`Components.kt:244` `PrimaryButton` tidak punya parameter `enabled`. Di jadwal interview, teksnya berubah jadi "Menyimpan…" tetapi tombol masih bisa ditekan, jadi ada risiko simpan ganda. Hal yang sama terjadi di form lamaran dan onboarding.

**Perbaikan:** tambahkan `enabled` + tampilan redup + `CircularProgressIndicator` kecil.

### C2 · P1 — Form acara Kalender dijejalkan ke `AlertDialog`
`CalendarScreen.kt:361` — 6 field + date/time picker yang **membuka dialog di atas dialog**. Pesan error memakai `Text` polos tanpa warna. Tombol Simpan disabled tanpa penjelasan kenapa. Di HP kecil ditambah keyboard, ruangnya sangat sempit.

**Perbaikan:** pindahkan ke bottom sheet / layar penuh seperti form tugas, dan beri warna `Danger` pada error.

### C3 · P1 — TimePicker di dalam `AlertDialog`
`Forms.kt:232` — jam analog M3 butuh sekitar 300 dp tinggi. Di dalam `AlertDialog` (yang punya padding sendiri) bisa terpotong atau perlu scroll di HP pendek / mode landscape. Selain itu tidak ada tombol ganti ke input keyboard. Gunakan `Dialog` custom (atau `TimePickerDialog` jika BOM dinaikkan) + toggle `TimeInput`.

### C4 · P1 — Kolom pencarian memakai field form biasa
Pencarian Career dan Transaksi memakai `TodayTextField` (label melayang, `ImeAction.Next`, tanpa ikon cari / tombol hapus). Seharusnya `ImeAction.Search`, ikon kaca pembesar, tombol ✕ untuk mengosongkan, dan tinggi lebih rendah.

### C5 · P1 — Isian hilang saat kembali tanpa konfirmasi
Lihat A6. Form lamaran, jadwal, rutinitas, dan sheet transaksi tidak punya penjaga `dirty`.

### C6 · P2 — "WIB" ditulis permanen
`Forms.kt:228` — `formatTime(it) + " WIB"` salah untuk pengguna di WITA/WIT atau saat bepergian. Pakai zona dari `clock`, atau hilangkan.

### C7 · P2 — Glyph teks sebagai ikon
"‹ Kembali", "‹ ›", "Ubah ›", "▾", "▴", "▶", "✓", "↗", "!". Ukuran dan ketebalan glyph bergantung font perangkat (MIUI sering mengganti font sistem), jadi tidak sejajar dengan ikon drawable. Ganti dengan ikon vektor (`Icons.AutoMirrored.Filled.ArrowBack`, `ChevronRight`, `ExpandMore`, `Check`, `OpenInNew`).

### C8 · P2 — Teks tombol rata kiri
`Components.kt:256` `PrimaryButton` memakai `contentAlignment = Alignment.CenterStart`. Tombol full-width di M3/Android biasanya teksnya di tengah. Ditambah tombol M3 asli di sheet tugas yang rata tengah dan berbentuk pill, ada 2 gaya tombol yang berbeda (lihat D3).

---

## D. Konsistensi pola antar modul

### D1 · P1 — `ScrollColumn` diduplikasi 4×
Career, Finance, Settings, dan Workout masing-masing punya `ScrollColumn` sendiri (ditambah Kalender dan Tugas yang menulis ulang manual). **Perbaikan:** satu `TodayScreenColumn` di `components/` dengan padding, `statusBarsPadding`, `imePadding`, dan bottom spacing yang sama. Ini sekaligus memperbaiki B2 dan B6.

### D2 · P1 — Tombol "Tambah" ada di 4 tempat berbeda
- Today → FAB kanan bawah
- Karier & Keuangan → pill di pojok header
- Tugas → pill full-width **di bawah daftar** (hilang kalau daftarnya panjang)
- Latihan → pill di tengah halaman

**Perbaikan:** satu pola. Saran saya FAB (`ExtendedFloatingActionButton`) di semua tab, yang mengecil saat scroll.

### D3 · P1 — Header detail bukan top app bar
`Forms.kt:270` `DetailTopBar` ikut ter-scroll hilang, tanpa judul, dan tombol kembali berupa pill teks. Sesi latihan punya versinya sendiri (`Pill("‹")`). **Perbaikan:** `TopAppBar` M3 dengan ikon panah, judul, aksi, dan `enterAlwaysScrollBehavior`.

### D4 · P2 — Dua jenis tombol & dua jenis daftar
- Tombol: `PrimaryButton` custom (radius 14, teks kiri) vs `material3.Button` (pill, teks tengah) di sheet tugas.
- Baris daftar: `ListItem` M3 di Today vs `Row` custom di modul lain, dengan tinggi dan padding berbeda.

### D5 · P2 — Ikon emoji dicampur ikon vektor
Kategori keuangan (🍜🚆🏠…) dan tagihan (🧾) memakai emoji, sementara modul lain memakai drawable. Emoji tampil berbeda per merek HP dan tidak mengikuti warna tema. Sebaiknya pakai ikon vektor per kategori.

### D6 · P2 — Latar layar tidak seragam
Kebanyakan layar memakai krem `Background`, tetapi form rutinitas putih `Surface` dan sesi gelap. Sesi gelap memang disengaja (mode fokus). Form rutinitas sebaiknya disamakan dengan form lain.

### D7 · P2 — Pola "tahap / status" berbeda-beda
Ubah tahap lamaran memakai `AlertDialog` berisi 8 pill full-width (tinggi sekitar 450 dp). Pilihan sejenis di tempat lain memakai `ChoiceChips`. Untuk pilihan > 5 opsi, pakai `ModalBottomSheet` + daftar radio.

---

## E. Temuan per layar

### Today
- **E1 · P1** — `AgendaRow` (`TodayScreen.kt:275`) menaruh **dua `Text` di slot `supportingContent`** `ListItem`. Slot itu tidak menyusun anak secara vertikal, jadi subtitle bisa tidak tampil atau menumpuk. Bungkus dengan `Column`.
- **E2 · P1** — Tidak bisa menandai tugas atau agenda selesai langsung dari Today. Setiap kali harus membuka modul lain. Tambahkan checkbox di baris tugas atau agenda (seperti `TaskRowCard`).
- **E3 · P2** — Urutan bagian: "Waktu sudah lewat" tampil **di atas** "Berikutnya". Untuk dashboard harian, yang akan datang biasanya lebih penting. Pertimbangkan membalik urutannya, atau melipat bagian "lewat".

### Kalender
- **E4 · P1** — Tidak ada tombol kembali (A7), dan padding 12 dp (B6).
- **E5 · P2** — `Legend()` (`CalendarScreen.kt:318`) sudah dibuat tetapi **tidak dipakai**, sehingga arti titik warna tidak dijelaskan ke pengguna.

### Karier
- **E6 · P1** — Badge "Tahap: … ▾" (`CareerScreens.kt:257`) adalah tombol dengan tinggi **28 dp**, di bawah minimum 48 dp, dan tidak terlihat seperti tombol.
- **E7 · P2** — Label stepper tahap memakai `TodayType.Tiny` (10 sp), terlalu kecil. Minimum yang nyaman 11–12 sp.

### Keuangan
- **E8 · P1** — Tombol "Lunas" di kartu tagihan terbaca seperti **status**, padahal itu **aksi**. Ganti dengan "Bayar" atau "Tandai lunas".
- **E9 · P2** — Hapus transaksi dengan geser kiri hanya dijelaskan lewat teks **di paling bawah daftar**. Tambahkan juga aksi hapus di sheet (sudah ada) dan petunjuk singkat di atas daftar, atau tampilkan sekali saja.
- **E10 · P2** — Nominal di sheet transaksi 40 sp `maxLines = 1`, sehingga nominal 12 digit terpotong di 360 dp. Gunakan `autoSize` atau perkecil fontnya.
- **E11 · P2** — Header tanggal di daftar transaksi tidak *sticky*. Pakai `stickyHeader`.

### Latihan
- **E12 · P1** — Riwayat latihan menampilkan tombol merah **"Hapus sesi" di bawah setiap item**. Ini noise visual dan rawan salah tekan. Pindahkan ke halaman ringkasan atau menu ⋮ / geser.
- **E13 · P1** — Layar sesi latihan **tidak menjaga layar tetap menyala**. Tidak ada `keepScreenOn`, jadi HP terkunci di antara set dan pengguna harus membuka kunci terus. Tambahkan `Modifier.keepScreenOn()` / `FLAG_KEEP_SCREEN_ON` selama sesi aktif.
- **E14 · P2** — Hapus set hanya bisa lewat **long-press** (tersembunyi). Tambahkan aksi yang terlihat saat set dipilih.
- **E15 · P2** — Switch "Ingatkan sebelum latihan" ada di beranda Latihan, padahal itu pengaturan. Lebih cocok di Pengaturan atau di form rutinitas.

### Tugas
- **E16 · P1** — Daftar proyek tampil **di atas** daftar tugas. Dengan beberapa proyek, tugas (isi utama tab) terdorong ke bawah lipatan. Lipat proyek jadi carousel horizontal, atau taruh di bawah.
- **E17 · P2** — Area checkbox 44 dp, padahal komentarnya bilang 48 dp (`TasksScreens.kt:300`).

### Onboarding & Pengaturan
- **E18 · P2** — Placeholder nama "Farhan" sebaiknya diganti contoh netral ("Nama kamu").

---

## F. Feedback & sistem

### F1 · P1 — Tidak ada haptic feedback
Tidak ada satu pun `performHapticFeedback`. Menandai set selesai, menyelesaikan tugas, dan timer istirahat habis adalah momen yang pas untuk getar singkat (`HapticFeedbackType.LongPress` / `Confirm`).

### F2 · P1 — Skema warna M3 hanya diisi sebagian
`theme/Theme.kt:104` — hanya `primary`, `background`, dan `surface` yang diatur. Komponen bawaan seperti `DatePicker`, `TimePicker`, `AlertDialog`, `ModalBottomSheet`, `Switch`, dan `Snackbar` memakai `secondary`, `tertiary`, `surfaceContainer*`, dan `primaryContainer` default, sehingga **muncul ungu/lavender bawaan M3** di dalam app yang bertema kuning–navy. Lengkapi `lightColorScheme(...)`.

### F3 · P2 — Daftar berubah tanpa animasi
Tugas yang dicentang pindah ke "Selesai" secara mendadak. Begitu juga transaksi yang dihapus dan agenda yang selesai. Pakai `LazyColumn` + `Modifier.animateItem()`, atau `AnimatedVisibility`.

### F4 · P2 — Splash & loading
Ada layar krem kosong sebelum status onboarding terbaca (`TodayApp.kt:131`), dan teks "Memuat…" polos (`Components.kt:321`). Pakai SplashScreen API + skeleton.

### F5 · P2 — Belum ada dark mode & preview minim
Hanya ada 3 `@Preview` di seluruh UI. Skill menyarankan preview per konfigurasi (fontScale 1.3/2.0, lebar 320/360/411, dark). Dark mode tidak wajib, tetapi warna sudah terpusat di `TodayColors`, jadi tinggal menambah varian.

---

## Urutan pengerjaan yang disarankan

| Tahap | Item | Hasil |
|---|---|---|
| 1 | A1, A2, B1, A4 (`TodayApp.kt` + rute form tugas) | Transisi mulus, nav bar hanya di tab, status bar benar |
| 2 | D1 → B2, B6 · C1 | Satu kolom layar: keyboard aman, padding seragam, tombol punya state |
| 3 | A3 · F4 | Tidak ada layar kosong saat memuat |
| 4 | D3, A5, A6, A7, C5 | Top app bar M3, predictive back, konfirmasi perubahan |
| 5 | B3, B4, B5, D2 | Kartu pas di HP kecil, aksi utama selalu terlihat, tombol Tambah konsisten |
| 6 | E1–E18 (P1 dulu), F1, F2, C2–C4 | Rapikan per layar |
| 7 | Sisa P2 | Polish |

## Checklist "UI/UX sudah okay"
Setelah tahap 1–6, cek di HP (dan di emulator 360×640 dp):
- [ ] Pindah tab dan buka/tutup detail terasa mulus, tanpa tumpang-tindih transparan.
- [ ] Bottom nav hanya muncul di 5 tab dan bergeser halus saat masuk/keluar detail.
- [ ] Status bar menyatu dengan warna layar (krem, putih, maupun gelap di sesi latihan).
- [ ] Di setiap form, field terakhir dan tombol Simpan tetap terlihat saat keyboard terbuka.
- [ ] Kembali dari form yang sudah diisi memunculkan konfirmasi. Kembali di onboarding mundur 1 halaman.
- [ ] Tidak ada teks yang tertimpa maskot. Judul kartu maksimal 2 baris di 360 dp.
- [ ] Font scale 1.3× dan 2.0×: tidak ada teks terpotong kasar dan tidak ada scroll horizontal.
- [ ] TalkBack: semua tombol ikon punya label, dan tahap/status terbaca.
- [ ] Layar tidak mati selama sesi latihan.
- [ ] Tidak ada warna ungu bawaan M3 di dialog atau picker.
