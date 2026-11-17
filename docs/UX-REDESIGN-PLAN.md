# Rencana redesign Today untuk Android

Today adalah dashboard pribadi offline untuk agenda, tugas, latihan, lamaran kerja, dan keuangan. Rekomendasinya adalah membuat **Today sebagai tempat mengambil tindakan berikutnya**. Dalam satu pandangan, pengguna bisa tahu apa yang perlu diperhatikan, membuka item yang dimaksud, atau mencatat sesuatu tanpa mencari tab yang tepat.

Rencana ini memakai Redmi/MIUI sebagai perangkat utama berdasarkan README dan catatan sprint. Asumsi yang perlu diuji adalah frekuensi penggunaan Today, Tugas, dan catat transaksi. Audit membaca implementasi Compose, alur data agenda, dan thumbnail FigCraft sembilan layar. Benchmark memakai dokumentasi resmi produk dan Android. ADB tidak menemukan perangkat dan emulator gagal di lingkungan ini. Temuan kode bisa diperiksa langsung, sedangkan keterbacaan, clipping, dan dampak pada pengguna masih perlu diuji di perangkat. Belum ada pengukuran waktu atau studi pengguna.

## Temuan dan prioritas

| Prioritas | Bukti saat ini | Perubahan yang direncanakan |
| --- | --- | --- |
| P0 | `TodayScreen.kt` menaruh `QuickActions` setelah agenda. Transaksi langsung membuka formulir, sedangkan Tugas dan Karier hanya membuka tab. `AgendaItem.route()` membuka modul umum untuk Tugas, Latihan, dan Uang. | Sediakan `Tambah` dari posisi konsisten. Buka item atau tindakan yang relevan sesuai tipe dan identitas sumber. |
| P0 | `AgendaRepository.pickNext()` memilih item berjam yang belum lewat, lalu item seharian. Jika masih ada item berjam belum selesai tetapi semua jam sudah lewat, kartu dapat mengatakan “Sisa hari bebas”. | Pisahkan waktu yang lewat dari status selesai. Hanya tampilkan klaim kosong jika memang tidak ada item aktif. Untuk acara yang kejadiannya tidak diketahui, gunakan “Waktu sudah lewat”. |
| P0 | `TodayApp.kt` memakai enam tab saat semua modul aktif. Lebar enam tab, jarak, dan padding berjumlah 428 dp, sehingga bar menggulir pada lebar 360–393 dp. | Lima tujuan dengan label terlihat pada ukuran standar. Pengaturan pindah ke header Today dengan label aksesibel. Tentukan fallback untuk teks besar. |
| P1 | `SummaryGrid` mengubah lebar kartu pertama menurut paritas jumlah sel dan mencampur saldo dengan agenda. | Gunakan daftar kronologis satu kolom dan tempatkan saldo dalam ringkasan terpisah. Penambahan satu item tidak mengubah tata letak item lainnya. |
| P1 | `WeekStrip` membagi sisa lebar setelah padding 48 dp dan enam sela 6 dp. Pada 360 dp tiap kolom terukur sekitar 39 dp. Tinggi strip 62 dp tetap. Kalender memakai angka visual 36 dp di dalam target klik. | Ukur area sentuh aktual dan overlap. Pada ruang sempit, pakai akses Kalender dan pilihan tanggal yang tetap terbaca. Sediakan agenda harian alternatif pada grid bulan. |
| P1 | `Theme.kt` menyediakan skema terang dan sejumlah warna tetap. `BottomNav` membedakan tab aktif secara visual lewat warna latar, sementara modifier tab di kode tidak mengekspresikan `selected`. | Pindahkan komponen yang diubah ke token semantik terang/gelap. Gunakan komponen navigasi dengan state terpilih dan uji TalkBack. |
| P1 | `GetTodayOverview` memakai rentang minggu berjalan dan Today memuat ulang waktu saat layar kembali tampil. Filter modul aktif terlihat pada bar navigasi. | Audit kesesuaian filter di Today, Kalender, Tambah, dan notifikasi. Tambahkan pembaruan status saat melewati jam agenda atau tengah malam. |
| P1 | Onboarding tiga langkah menawarkan notifikasi dan awal dengan data contoh sebagai default. Aksi “Kosong” menjelaskan penghapusan data yang ada. | Jadikan mulai kosong sebagai default untuk instalasi baru. Pisahkan data contoh dan pemulihan backup dengan jelas. Minta izin ketika pengguna mengaktifkan pengingat yang relevan. |

`P0` mendahului pekerjaan visual lintas modul. `P1` mencakup penyempurnaan utama dan hardening yang diperlukan sebelum rilis. Konfirmasi kasus data lama dan aturan notifikasi sebelum perubahan perilaku onboarding atau modul.

## Riset dan penerapannya

| Sumber primer | Pola yang diamati | Implikasi untuk Today |
| --- | --- | --- |
| [Todoist Today](https://www.todoist.com/help/todoist/get-started/plan-your-day-with-the-today-view-UVUXaiSs) | Today mengumpulkan pekerjaan lintas proyek dan memberi jalan untuk menjadwalkan ulang pekerjaan tertunda. | Pisahkan item yang perlu tindakan dari jadwal yang akan datang. Letakkan `Jadwalkan ulang` pada detail tugas atau menu item. |
| [Google Tasks untuk Android](https://support.google.com/tasks/answer/7675838?co=GENIE.Platform%3DAndroid&hl=en) | Pengguna bisa mencatat judul dahulu. Detail, tanggal, dan pengulangan tersedia bila diperlukan. | Buka formulir ringkas untuk tugas. Untuk pintasan dari Today, tanggal boleh terisi “Hari ini” selama terlihat dan bisa dikosongkan. |
| [Hevy](https://www.hevyapp.com/features/) | Riwayat set dan timer adalah bagian dari pencatatan sesi. | Today sudah punya set sebelumnya dan rest timer. Tampilkan keduanya dekat set aktif dan prioritaskan kembali ke sesi yang sedang berjalan. |
| [YNAB](https://support.ynab.com/en_us/how-to-add-transactions-in-ynab-HyDwA_byi?mobile-help=true) | Akses transaksi dari beberapa konteks, input nominal lebih awal, akun dan kategori terlihat sebelum simpan. | Pertahankan keypad dan sheet transaksi yang sudah ada. Buat jenis transaksi, dompet, kategori, dan nominal terpilih terbaca sebelum simpan. |
| [Teal Job Tracker](https://help.tealhq.com/en/articles/9530119-job-tracker-guidance) | Status lamaran dikaitkan dengan tindakan berikutnya seperti follow-up dan persiapan interview. | Tampilkan satu langkah berikutnya yang tersedia pada kartu lamaran. Detail tetap memuat riwayat status dan agenda. |
| [NN/g Heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/) | Status sistem terlihat, opsi mudah dikenali, dan tindakan bisa dipulihkan. | Tampilkan state nyata dan hasil simpan. Gunakan kembali pola `Batalkan` yang sudah ada di Tugas dan Uang bila inversi data aman. |
| [Android Compose Accessibility](https://developer.android.com/develop/ui/compose/accessibility) · [Codelab](https://developer.android.com/codelabs/jetpack-compose-accessibility) · [Insets](https://developer.android.com/develop/ui/compose/system/insets) | Komponen perlu target efektif 48×48 dp, label dan state semantik, fokus yang masuk akal, serta penanganan system bar dan keyboard. | Pakai komponen Material bila sesuai, uji TalkBack dan keyboard pada perangkat. Gunakan 4,5:1 untuk teks biasa serta 3:1 untuk teks besar dan indikator visual esensial sebagai target kontras. [W3C contrast](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) · [W3C non-text contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) |

Pola produk di atas menjadi hipotesis untuk prototipe. Pengamatan ini tidak mengukur pengaruhnya pada penggunaan Today.

## Today dan Kalender

Susunan Today pada ponsel:

1. **Header** dengan tanggal, sapaan singkat, Kalender, dan Pengaturan. Pendekkan header saat font membesar agar aksi utama tetap mudah ditemukan.
2. **Kartu fokus** dengan satu aksi yang namanya menjelaskan hasil. Contoh `Lanjutkan Push Day`, `Buka jadwal interview`, atau `Lihat tugas`. Maskot kecil boleh menemani kartu pada layar yang cukup lebar.
3. **Agenda** dengan `Perlu tindakan`, `Berikutnya`, `Seharian`, dan `Selesai`. Bagian kosong disembunyikan, item selesai bisa dilipat. Setiap baris memuat waktu, judul, sumber, dan status dalam teks. Kelompok `Berikutnya` diurutkan jam. Kandidat kartu fokus tidak diulang di daftar aktif.
4. **Ringkasan** berisi saldo atau progres bila relevan. Label saldo harus menjelaskan cakupan dan periode yang sebenarnya. Uji opsi sembunyikan nominal dari layar Today.
5. **Tambah** selalu bisa dijangkau di Today. Satu pemicu membuka sheet berisi aksi berlabel seperti `Tugas`, `Acara`, `Transaksi`, dan `Lamaran`. Saat hanya satu opsi berlaku, uji membuka formulir langsung. Latihan memakai aksi kontekstual `Mulai` atau `Lanjutkan` agar tidak membuat sesi secara tidak sengaja.

Aturan fokus awal untuk diuji adalah sesi aktif terlebih dahulu, kemudian agenda berjam berikutnya, kemudian item aktif seharian. Agenda yang perlu tindakan tetap tampil sebagai kelompok tersendiri. Uji skenario interview yang segera mulai bersamaan dengan sesi aktif, supaya melanjutkan latihan tidak menyembunyikan informasi waktu penting. Item lampau belum selesai tetap terlihat dengan status sesuai domain. Pencarian kewajiban dari hari sebelumnya memerlukan query dan definisi terpisah per tipe, karena data Today sekarang terbatas pada minggu berjalan. Jangan menyimpulkan interview terlewat atau tagihan belum dibayar semata-mata dari jam agenda.

Untuk setiap baris, definisikan kontrak tujuan: tugas membuka editor/detail tugas sesuai ID, acara membuka item di tanggal itu, Karier membuka lamaran dan menyorot agendanya bila ada ID event, tagihan membuka tagihan tanpa otomatis membayar, dan Latihan membuka sesi atau rencana yang sesuai statusnya. Periksa apakah `sourceId` menunjuk induk atau kejadian. Jika item sudah dihapus, tampilkan pesan dan kembali ke konteks asal. Pertahankan pilihan tanggal, filter, dan posisi saat kembali dari detail. Uji tautan dari notifikasi serta tombol dan gesture Back.

Kalender tetap memberi ikhtisar bulan. Tambahkan daftar agenda tanggal terpilih dengan label `Hari ini` dan `Tanggal terpilih` yang terbaca. Hari di grid dibacakan dengan tanggal lengkap dan status terpilih. Pada lebar kecil, tujuh kolom tidak selalu muat bersama target 48 dp dan padding. Gunakan daftar tanggal atau agenda harian sebagai tampilan pengganti yang bisa dipakai dengan TalkBack, serta pertahankan cara lompat ke tanggal.

## Perbaikan per modul

| Modul | Alur yang dituju | Kriteria penerimaan |
| --- | --- | --- |
| Tugas | `Tambah → Tugas → judul → Simpan`. Proyek, prioritas, dan waktu opsional. Selesaikan dari baris saat konteks jelas. | Tanggal bawaan terlihat, draft bertahan saat rotasi atau kembali dari pemilih tanggal, dan pembatalan penyelesaian mengembalikan state sebenarnya. Uji tugas berulang dan penjadwalan ulang beserta pengingat. |
| Uang | Masuk ke sheet transaksi dari Today, nominal dulu, pilih jenis/dompet/kategori, simpan. Pisahkan aksi bayar tagihan dari aksi edit tagihan. | Tampilkan ringkasan nominal dan dompet sebelum bayar. Uji Rp besar, saldo yang tidak mencukupi bila ditolak oleh aturan saat ini, dompet belum ada, error simpan, dan tap ganda. Pertahankan undo penghapusan dan cek saldo serta riwayat. |
| Latihan | `Lanjutkan sesi → set aktif → tandai selesai → timer`. Nilai sebelumnya dekat input berat/repetisi. | Kembali dari lock screen dan perpindahan tab mengembalikan sesi yang sama. Timer punya label, waktu terbaca, serta kontrol lewati. Set baru dan pengubahan set lama tidak membuat set duplikat. |
| Karier | Daftar menonjolkan perusahaan, posisi, status, dan satu agenda berikutnya. Detail membedakan `Ubah status` dan `Jadwalkan agenda`. | Jadwal, zona waktu yang digunakan, dan status pengingat terbaca. Edit jadwal memperbarui agenda yang sama dan pengingatnya. Uji interview tanpa jam, agenda lewat, dan perubahan status tanpa agenda. |
| Onboarding & Pengaturan | Pengguna bisa mulai kosong dengan cepat, menyesuaikan modul nanti, memilih data contoh secara sadar, atau masuk ke pemulihan backup. | Penolakan izin notifikasi tetap mengizinkan penggunaan. Backup/restore menjelaskan bahwa restore mengganti data, menampilkan pratinjau, dan menyampaikan hasil. Uji pemasangan di atas versi lama dengan tanda tangan yang sesuai. |

## Arah visual dan sistem komponen

### Capy sebagai karakter visual Today

Gunakan kontras antara satu area ekspresif dan area kerja yang tenang. Desain FigCraft sudah punya violet, Inter, warna modul, serta ilustrasi Capy melambai, angkat beban, fokus, dan memegang koin. Ambil ulang aset itu sebagai bagian dari komposisi tiap layar. Contoh Today padat: tanggal kecil dan judul “Hari ini”, kartu violet dengan judul “Interview · 14.30” dan Capy fokus di sisi kanan, lalu daftar agenda berlabel warna di bawah. Saat hari kosong, Capy melambai menemani “Belum ada agenda hari ini” dan tombol “Tambah rencana”. Saat layar sempit, judul dan tombol mendapat ruang terlebih dahulu, ilustrasi bisa pindah atau mengecil.

| Layar | Titik visual yang memberi karakter | Area kerja |
| --- | --- | --- |
| Today | Kartu fokus violet dengan ilustrasi sesuai konteks. Ringkasan seperti “2 agenda tersisa” memberi ritme setelah header. | Baris agenda netral, waktu sejajar, ikon dan label kecil berwarna modul. |
| Kalender | Tanggal terpilih dengan bidang violet, tanggal hari ini diberi outline, aksen warna kecil pada agenda tanggal tersebut. | Grid bulan sederhana dan daftar harian dengan jam serta nama agenda yang mudah dipindai. |
| Latihan | Capy angkat beban di kartu mulai atau ringkasan selesai, dengan aksen lime untuk menonjolkan progres. | Set aktif punya penanda jelas. Angka berat dan repetisi sejajar, timer mudah terlihat ketika tangan sedang sibuk. |
| Uang | Satu kartu saldo dengan angka besar, label periode, dan Capy koin pada ruang yang cukup. | Transaksi memakai nominal rata kanan, tanda plus/minus, serta ikon atau emoji kategori. Formulir dan konfirmasi pembayaran memakai label yang eksplisit. |
| Karier | Kartu agenda interview beraksen amber. Inisial perusahaan dalam wadah konsisten memberi penanda visual pada daftar. | Status berupa label seperti “Interview” atau “Menunggu kabar”, dengan jadwal dan tindakan berikutnya dekat judul. |
| Tugas | Aksen biru pada progres proyek atau bagian selesai. Capy fokus menemani keadaan kosong. | Kotak centang dan judul tetap jadi titik baca pertama. Prioritas dan tenggat punya label yang bisa dipindai. |
| Onboarding | Capy melambai sebagai pembuka dan satu contoh kartu Today untuk memperlihatkan kegunaan aplikasi. | Pemilihan modul dan tindakan mulai memakai ikon vektor dengan teks. |

### Aturan emoji, ikon, dan gerak

- **Emoji untuk kategori yang akrab.** Pertahankan pola kategori yang sudah ada di Uang dan uji pasangan seperti “🍜 Makan”, “🚌 Transport”, dan “☕ Kopi”. Emoji juga bisa muncul sesekali pada copy ringan seperti “Latihan selesai! 🎉”. Hindari pengulangan emoji yang sama di setiap baris. Tetapkan label kategori sebagai nama aksesibel dan uji bentuk emoji di Redmi/MIUI serta Android lain. Siapkan ikon vektor ketika tampilan glyph terlalu berbeda.
- **Ikon vektor untuk tindakan dan status sistem.** Navigasi, tambah, kembali, edit, hapus, pengingat, serta status transaksi memakai satu keluarga ikon dengan bobot yang selaras. Ikon yang menjalankan aksi punya label terlihat atau nama aksesibel. Simpan, bayar, hapus, error, dan izin tetap memakai bahasa langsung.
- **Gerak mengikuti perubahan nyata.** Uji transisi singkat saat tugas pindah ke “Selesai” atau timer istirahat mulai. Ilustrasi diam saat membaca atau mengisi formulir. Saat animasi dikurangi, perubahan status tetap terlihat dan terbaca.

### Review visual prototipe

Buat dua varian dengan data, susunan, dan aksi yang sama. Varian **Capy ekspresif** memakai ilustrasi lebih menonjol dan warna modul pada elemen ringkasan. Varian **Capy ringkas** memakai ilustrasi lebih kecil dan aksen warna lebih sedikit. Gunakan Today hari biasa, Today padat, keadaan kosong, dan sheet Tambah sebagai papan perbandingan. Bawa varian ekspresif sebagai kandidat awal. Bandingkan pilihan pengguna, alasan mereka, dan aksi pertama yang ditemukan. Varian terpilih perlu memperjelas fokus di layar kecil, mempertahankan keterbacaan ketika font diperbesar, serta memberi hasil setara atau lebih baik pada uji menemukan agenda dan tombol Tambah. Siapkan cuplikan tema terang dan gelap sebelum memperluas desain ke modul lain.

### Fondasi komponen

- Pertahankan violet, Inter, dan Capy dari FigCraft. Pakai permukaan netral untuk daftar, warna modul untuk ikon dan label singkat, serta violet untuk aksi utama. Jadikan keadaan terlambat, selesai, dan terpilih terbaca melalui teks atau ikon juga. *(Diganti 17 Nov 2026: violet → palet "Matahari" kuning/navy, lihat README.)*
- Gunakan satu kartu fokus dengan judul yang kuat dan ruang untuk teks panjang. Pada layar kecil atau font besar, kecilkan atau sembunyikan ilustrasi dekoratif. Capy bisa muncul di onboarding, keadaan kosong, dan ringkasan sesi. Tandai ilustrasi dekoratif agar TalkBack melewatinya.
- Petakan `TodayColors` ke peran seperti `surface`, `onSurface`, `primary`, `error`, dan pasangan warna modul. Gunakan `TodayType` dan `TodayShapes` sebagai fondasi. Konsolidasikan baris agenda, aksi utama, pesan kosong/error, dan umpan balik simpan di `ui/components`.
- Formulir perlu label persisten, validasi dekat field, CTA yang tetap terlihat ketika keyboard terbuka, dan state sedang menyimpan yang mencegah pengiriman ganda. Saat simpan gagal, pertahankan input dan jelaskan tindakan pemulihannya. Beri konfirmasi sebelum membuang draft yang berisi perubahan.
- Untuk layar lebar, uji navigation rail dan susunan agenda/detail. Untuk ponsel, uji pada lebar 320/360/393 dp dan font sistem 100%, 130%, serta 200% di Android yang mendukung. Uji juga gesture/3-button, dark mode, TalkBack, dan pengaturan pengurangan animasi. [Android adaptive layouts](https://developer.android.com/develop/ui/compose/build-adaptive-apps) · [Android font scaling](https://developer.android.com/about/versions/14/features#non-linear-font-scaling)

## Urutan pengerjaan

| Fase | Pekerjaan | Syarat lanjut |
| --- | --- | --- |
| 0 · Baseline | Pasang build aktif di Redmi/MIUI dan satu emulator API 35+. Rekam Today kosong/padat, Kalender, formulir, sesi aktif, dan alur dari notifikasi dengan data uji. Ukur skenario di bawah. | Bukti layar dan waktu tersedia, aturan status agenda disepakati, dan kasus pencadangan versi lama siap diuji. |
| 1 · Prototipe | Buat prototipe Today + Kalender + Tambah dengan data yang sudah dianonimkan. Bandingkan dua varian visual dengan susunan yang sama. Uji juga akses Kalender lewat header dan strip minggu pada skenario yang sama. | Pengguna menemukan fokus dan Tambah tanpa petunjuk. Pilih intensitas visual, susunan layar, dan aturan fallback teks besar sebelum implementasi. |
| 2 · Alur inti | Implementasikan navigasi, kontrak route, pengelompokan agenda, sheet Tambah, dan status kosong/error. Reuse editor dan sheet yang sudah ada. Mulai dari jalur Today → Tugas sebagai satu alur lengkap. | Semua jalur P0 mengarah ke item benar. Back dan notifikasi tetap membuka konteks yang tepat. Tambahkan tes regresi untuk agenda lewat, pergantian tanggal, ID hilang, dan modul aktif bila cakupan lama belum memadai. |
| 3 · Modul dan visual | Terapkan komponen bersama dan token terang/gelap ke Today dan alur prioritas, kemudian Latihan, Uang, Karier, dan Pengaturan. Kerjakan onboarding setelah state modul dan izin diputuskan. | Formulir, animasi, state aksesibel, dan copy konsisten. Lulus skenario data kosong, panjang, dan padat. |
| 4 · Verifikasi rilis | Jalankan unit test, build debug, verifikasi perangkat, uji migrasi jika skema berubah, cek backup/restore, dan uji offline. | Target UX inti tercapai atau selisihnya terdokumentasi, tanpa bug pemblokir dan tanpa kehilangan data. |

Mulai dengan perubahan di `ui/today`, `ui/calendar`, `ui/TodayApp.kt`, dan komponen bersama. Gunakan `GetTodayOverview`, repository, pengingat, dan alur penyimpanan yang ada. Upayakan tahap inti bisa dikirim tanpa migrasi Room. Perlu keputusan terpisah jika akses agenda lampau atau filter modul menuntut perluasan model. Periksa data lama, seri berulang, jadwal pengingat, serta kasus rollback sebelum mengubah mutasi. Penambahan widget, sinkronisasi cloud, dan personalisasi kompleks berada di luar fase ini.

## Cara mengukur hasil

Uji terlebih dahulu dengan pemilik aplikasi dalam konteks penggunaan sehari-hari. Jika memungkinkan, tambah 3–5 pengguna Android untuk menguji apakah orang baru memahami label dan navigasi. Gunakan data dan titik awal yang sama di build lama serta prototipe, selang-seling urutan keduanya untuk mengurangi efek belajar. Catat keberhasilan tanpa bantuan, waktu sampai hasil tersimpan, salah arah, jumlah perpindahan layar, dan bagian yang membuat ragu. Untuk sampel kecil, laporkan angka per peserta dan pola masalah. Target berikut merupakan sasaran awal yang disesuaikan setelah baseline.

| Skenario | Target awal |
| --- | --- |
| Buka Today dan sebutkan agenda berikutnya serta status item yang waktunya lewat | ≤5 detik untuk menemukan agenda, interpretasi status benar, tanpa scroll pada layar standar dengan data normal. |
| Buka detail tugas dari agenda | Satu tap pada baris menuju item benar. Kembali mempertahankan konteks. |
| Tambah tugas dan transaksi dari Today | Formulir ditemukan dalam ≤2 tap. Waktu median hingga simpan turun ≥20% terhadap baseline tiap alur, dengan akurasi data setara atau lebih baik. |
| Buka sesi aktif dan tandai satu set | Satu tap untuk membuka sesi yang tepat. Status set dan timer tetap konsisten setelah meninggalkan layar dan kembali. |
| Reschedule tugas, lalu periksa agenda dan pengingat | Tanggal baru terlihat, reminder lama tidak muncul, tidak ada kejadian duplikat. |
| Temukan Kalender, Pengaturan, dan aksi Tambah memakai TalkBack | Alur selesai tanpa menebak ikon. Fokus dan state terpilih terbaca. Tombol simpan terjangkau saat keyboard terbuka dan font sistem diperbesar. |

Matriks data mencakup 0/1/20+ agenda, semua selesai, semua waktu lewat, perpindahan hari/bulan, jadwal tengah malam, nama panjang, nominal besar, satu atau empat modul aktif, item terhapus saat detail dibuka, mode pesawat, izin ditolak, simpan gagal, dan tap simpan berulang. Gunakan fitur aplikasi untuk mencatat baseline secara lokal. Periksa waktu buka Today dan kelancaran scroll dengan data padat sebelum dan sesudah perubahan agar ilustrasi dan daftar tetap ringan.

Keputusan pertama yang perlu divalidasi di prototipe adalah posisi akses Kalender, urutan fokus ketika ada konflik jadwal, dan apakah saldo perlu tampil di Today. Ketiganya bisa diuji sebelum memperluas desain ke semua modul.
