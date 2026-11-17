# Today — Rencana Sprint

Dokumen ini adalah acuan eksekusi. Perintah **"sprint-1"**, **"sprint-2"**, dst. berarti: kerjakan seluruh checklist sprint tersebut, centang item yang selesai di file ini, build APK debug, dan laporkan hasil serta apa yang belum terverifikasi.

- **Desain acuan**: `D:\Farhan\apps\Today-Android-Mascot-Polished.figcraft` (9 layar)
- **Kode**: `D:\Farhan\apps\TodayApp` (Kotlin + Jetpack Compose)
- **Status awal (sebelum Sprint 1)**: kerangka UI dengan data statis: Today, Kalender, Latihan, Karier, Keuangan, Tugas, dan navigasi bawah 5 tab. Belum ada database.

---

## 0. Keputusan arsitektur (berlaku untuk semua sprint)

### 0.1 "Backend" di MVP = backend lokal di dalam aplikasi

Sesuai brief, MVP adalah aplikasi **satu pengguna, offline, tanpa server**. Peran "backend" dijalankan oleh lapisan data di dalam aplikasi:

| Lapisan | Teknologi | Tanggung jawab |
|---|---|---|
| Database | **Room** (SQLite) | Sumber kebenaran tunggal semua data |
| Repository | Kotlin + Coroutines/Flow | Satu-satunya pintu ke DAO; validasi, aturan bisnis, transaksi DB |
| Use case | Kelas kecil di `domain/` | Logika lintas modul: agenda gabungan, sisa anggaran, progres proyek |
| Background | **AlarmManager** + **WorkManager** | Pengingat interview/tagihan/latihan, jadwal ulang setelah reboot |
| Preferensi | **DataStore** | Nama pengguna, jam ringkasan harian, tema |
| Backup | File JSON (Storage Access Framework) | Ekspor/impor seluruh data (Sprint 5) |

Server sinkronisasi **di luar MVP**. Skema sudah dibuat *sync-ready* (lihat 0.4) dan desain servernya ada di **Lampiran A**, jadi bisa ditambahkan nanti tanpa mengubah struktur data.

### 0.2 Struktur paket

```
com.farhan.today
├── TodayApplication.kt          // membuat AppContainer
├── di/AppContainer.kt           // DI manual: db, repository, scheduler
├── data/
│   ├── db/                      // TodayDatabase, entity, dao, converter
│   ├── repo/                    // *Repository (interface + impl)
│   ├── prefs/                   // UserPrefs (DataStore)
│   └── backup/                  // BackupExporter/Importer (S5)
├── domain/
│   ├── model/                   // model UI-agnostik (AgendaItem, Money, …)
│   └── usecase/                 // GetTodayAgenda, GetBudgetSummary, …
├── reminder/                    // ReminderScheduler, AlarmReceiver, BootReceiver
└── ui/
    ├── theme/  components/      // design system dari FigCraft
    ├── today/  calendar/  career/  workout/  finance/  tasks/  settings/
    └── TodayApp.kt              // NavHost + bottom nav
```

Pola per layar: `XxxScreen` (stateless, punya `@Preview`) ← `XxxRoute` (mengambil ViewModel) ← `XxxViewModel` (mengekspos `StateFlow<XxxUiState>` dan fungsi event).

### 0.3 Library (dikunci agar cocok dengan toolchain sekarang)

| Library | Versi | Catatan |
|---|---|---|
| AGP / Kotlin / Gradle | 8.7.3 / 2.0.0 / 8.10.2 | Sudah dipakai |
| KSP | 2.0.0-1.0.24 | Harus cocok dengan Kotlin 2.0.0 |
| Room | 2.6.1 | `room-runtime`, `room-ktx`, `room-compiler` (ksp) |
| Lifecycle | 2.8.6 | `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` |
| WorkManager | 2.9.1 | Jadwal ulang pengingat, ringkasan harian |
| DataStore | 1.1.1 | `datastore-preferences` |
| kotlinx-serialization-json | 1.6.3 | Backup JSON (S5) |
| Test | JUnit 4.13.2, Robolectric 4.13, coroutines-test 1.8.1, Turbine 1.1.0, room-testing 2.6.1 | |

DI dibuat **manual** (`AppContainer`) tanpa Hilt agar build lebih cepat dan sederhana untuk aplikasi satu modul. Waktu dipakai `java.time` bawaan (minSdk 26).

### 0.4 Konvensi data

- **ID**: `String` UUID v4 (bukan auto-increment), supaya kelak bisa disinkronkan tanpa bentrok.
- **Kolom wajib** di setiap tabel utama: `createdAt`, `updatedAt` (epoch millis), `deletedAt` (nullable, *soft delete*). Query normal selalu memakai `WHERE deletedAt IS NULL`.
- **Waktu**: momen tertentu (interview, transaksi) disimpan sebagai `Long` epoch millis UTC. Tanggal tanpa jam (deadline seharian, jatuh tempo tagihan) disimpan sebagai `LocalDate` → `Long` epochDay. Zona tampilan memakai zona perangkat (default Asia/Jakarta).
- **Uang**: `Long` dalam **rupiah utuh** (tanpa desimal). Nominal selalu positif; arah uang ditentukan kolom `kind` (`EXPENSE`/`INCOME`).
- **Enum** disimpan sebagai `TEXT` lewat TypeConverter (bukan ordinal), agar aman saat urutan enum berubah.
- **Skema**: `exportSchema = true` ke `app/schemas/`. **Seluruh skema v1 dibuat di Sprint 1.** Perubahan setelahnya wajib lewat `Migration` dan tes migrasi. Tidak memakai `fallbackToDestructiveMigration`, karena aplikasi dipakai sungguhan dan datanya tidak boleh hilang.

### 0.5 Agenda gabungan (inti Today & Kalender)

Setiap modul menyimpan tanggalnya sendiri. Kalender dan Today membaca dari **`AgendaRepository`**, yang menggabungkan beberapa Flow menjadi `List<AgendaItem>`:

| Sumber | Muncul sebagai | Warna |
|---|---|---|
| `workout_session` berstatus PLANNED, serta `routine_schedule` yang belum dibuatkan sesinya | Latihan | Lime |
| `job_event` (coding test, interview, follow-up) | Karier | Kuning |
| `bill` yang jatuh tempo pada tanggal itu | Keuangan | Pink |
| `task` dengan `dueDate` | Tugas | Biru |

```kotlin
data class AgendaItem(
    val id: String, val source: AgendaSource, val sourceId: String,
    val title: String, val subtitle: String,
    val date: LocalDate, val time: LocalTime?,   // null = seharian
    val done: Boolean,
)
```

Urutan dalam satu hari: item berjam (naik), lalu item seharian. **"Berikutnya"** di Today adalah item pertama yang belum selesai dan jamnya ≥ sekarang.

### 0.6 Cara verifikasi di HP (Redmi Note 10 Pro, MIUI)

MIUI memblokir `adb install` tanpa SIM. Jadi setiap sprint ditutup dengan `./gradlew assembleDebug`, lalu APK (`app/build/outputs/apk/debug/app-debug.apk`) disalin manual ke HP. Karena itu tes instrumentasi di perangkat tidak bisa dijalankan. **Semua tes otomatis berjalan di JVM** (Robolectric untuk Room/DAO). Cache Gradle ada di `D:\Android\gradle-home` dan SDK di `F:\Sdk`.

---

## 1. Skema database v1 (dibuat lengkap di Sprint 1)

Semua tabel memiliki `id TEXT PK`, `createdAt`, `updatedAt`, `deletedAt`. Kolom itu tidak diulang di bawah.

### Latihan
| Tabel | Kolom | Catatan |
|---|---|---|
| `exercise` | `name`, `muscleGroup` (CHEST/BACK/LEGS/SHOULDERS/ARMS/CORE/FULL_BODY), `equipment` (BARBELL/DUMBBELL/MACHINE/BODYWEIGHT/CABLE/OTHER) | Katalog gerakan; di-seed ±25 gerakan umum |
| `routine` | `name`, `focus` (teks, misal "Dada, bahu, trisep"), `archived` | Contoh: Push Day |
| `routine_exercise` | `routineId` FK, `exerciseId` FK, `position`, `targetSets`, `targetReps`, `targetWeightKg` (REAL), `restSeconds` | Index (`routineId`, `position`) |
| `routine_schedule` | `routineId` FK, `dayOfWeek` (1–7), `time` (menit sejak 00:00) | Jadwal mingguan berulang |
| `workout_session` | `routineId` FK nullable, `name` (salinan), `plannedDate` (epochDay), `plannedTime` nullable, `startedAt`, `endedAt` nullable, `status` (PLANNED/IN_PROGRESS/DONE/SKIPPED), `note` | Nama disalin agar riwayat tetap utuh walau rutinitas diubah |
| `set_log` | `sessionId` FK, `exerciseId` FK, `exerciseOrder`, `setIndex`, `weightKg`, `reps`, `done`, `completedAt` | Index (`sessionId`, `exerciseOrder`, `setIndex`) |

### Karier
| Tabel | Kolom | Catatan |
|---|---|---|
| `job_application` | `company`, `role`, `location`, `url`, `source` (LinkedIn, Glints, …), `status` (SAVED/APPLIED/CODING_TEST/INTERVIEW/OFFER/REJECTED/WITHDRAWN), `appliedDate` nullable, `deadlineDate` nullable, `salaryNote`, `notes` | |
| `job_event` | `applicationId` FK, `type` (CODING_TEST/INTERVIEW/FOLLOW_UP/OTHER), `title`, `startAt`, `durationMinutes`, `mode` (ONLINE/ONSITE), `locationOrLink`, `reminderMinutesBefore` nullable, `done` | Sumber agenda karier |
| `application_status_change` | `applicationId` FK, `fromStatus` nullable, `toStatus`, `changedAt` | Riwayat untuk stepper dan "Diperbarui 6 Nov" |

### Keuangan
| Tabel | Kolom | Catatan |
|---|---|---|
| `category` | `name`, `kind` (EXPENSE/INCOME), `iconKey`, `colorKey`, `position`, `archived` | Seed: Makan, Transport, Kos, Kuliah, Hiburan, Kesehatan, Lainnya; Uang saku, Gaji/Freelance |
| `budget` | `name`, `categoryId` FK nullable (null = semua pengeluaran), `amount`, `period` (WEEKLY/MONTHLY), `startDayOfMonth` (1–28) | Contoh: "Uang makan" Rp100.000/minggu |
| `money_transaction` | `kind`, `amount`, `categoryId` FK, `note`, `occurredAt`, `billPaymentId` nullable | Nama tabel menghindari kata kunci SQL `TRANSACTION`. Index (`occurredAt`) |
| `bill` | `name`, `amount`, `categoryId` FK, `repeat` (NONE/MONTHLY/YEARLY), `nextDueDate` (epochDay), `reminderDaysBefore`, `active` | |
| `bill_payment` | `billId` FK, `dueDate`, `paidAt`, `amount`, `transactionId` FK | Membayar tagihan = membuat transaksi + memajukan `nextDueDate` dalam satu DB transaction |

### Tugas & Proyek
| Tabel | Kolom | Catatan |
|---|---|---|
| `project` | `name`, `description`, `colorKey`, `deadlineDate` nullable, `archived` | Contoh: Skripsi |
| `task` | `projectId` FK nullable, `title`, `note`, `dueDate` nullable, `dueTime` nullable, `priority` (LOW/NORMAL/HIGH), `status` (TODO/DONE), `completedAt`, `position` | Index (`status`, `dueDate`) |

### Sistem
| Tabel | Kolom | Catatan |
|---|---|---|
| `reminder` | `sourceType` (JOB_EVENT/BILL/WORKOUT/TASK), `sourceId`, `triggerAt`, `title`, `body`, `state` (SCHEDULED/FIRED/CANCELLED) | Daftar alarm aktif. Dipakai untuk menjadwal ulang setelah reboot/update dan untuk membatalkan alarm saat sumbernya diubah |

Semua FK memakai `onDelete = CASCADE`, kecuali referensi ke `category` dan `routine` yang memakai `SET NULL`/`RESTRICT`. Repository tetap melakukan *soft delete*. Cascade hanya berlaku saat "hapus permanen".

---

## Sprint 1 — Fondasi data, Today & Kalender nyata

**Tujuan**: database lengkap berjalan, data contoh tersimpan di Room, dan Today serta Kalender membaca agenda gabungan dari DB. Komponen desain dikunci.

### Checklist
**Build & infrastruktur**
- [x] Tambah plugin KSP, Room, Lifecycle-compose, DataStore, dan dependensi tes (versi sesuai 0.3)
- [x] Konfigurasi `room.schemaLocation` → `app/schemas/`
- [x] `TodayApplication` + `AppContainer` (DI manual), daftarkan di Manifest

**Database**
- [x] Semua entity, DAO, dan `TypeConverters` (LocalDate, LocalTime, enum) sesuai Bagian 1
- [x] `TodayDatabase` versi 1, skema JSON ter-ekspor dan ikut di-commit
- [x] `SeedData`: saat pertama kali dibuka, isi data contoh yang sama dengan desain (Push Day 17.00, coding test GDP Labs 20.00, Revisi Bab 3, anggaran makan Rp42.000 dari Rp100.000, katalog gerakan, kategori). Ada flag di DataStore agar seed hanya jalan sekali.

**Lapisan data & domain**
- [x] Repository dasar per modul (baca saja dulu): `WorkoutRepository`, `CareerRepository`, `FinanceRepository`, `TaskRepository`
- [x] `AgendaRepository` + use case `GetAgendaForRange(start, end)` dan `GetTodayOverview()` (agenda, item berikutnya, ringkasan anggaran)
- [x] `UserPrefs` (nama pengguna, flag seed)
- [x] `TimeProvider` (jam yang bisa diganti saat tes)

**UI**
- [x] Font **Inter** di `res/font` (Regular, SemiBold, Bold, ExtraBold), lalu `AppFont` diganti
- [x] Rapikan design system: `AgendaCard`, `HeroCard`, `ListCard`, `Pill`, `StatusBadge` (ikon + teks), `PrimaryButton`, `SecondaryButton`, `EmptyState` (dengan maskot), `MoneyText` (tanda +/−)
- [x] `TodayViewModel` + `TodayScreen` dari DB: salam berisi nama dari prefs, strip 7 hari dengan titik agenda, kartu "Berikutnya", kartu ringkasan, dan aksi cepat
- [x] Kalender: navigasi bulan sebelumnya/berikutnya, beda visual antara hari ini, tanggal dipilih, dan tanggal yang punya agenda (titik warna modul), daftar agenda tanggal terpilih, ketuk agenda → layar sumbernya
- [x] Empty state: hari tanpa agenda di Today/Kalender memakai maskot melambai ukuran besar

**Tes**
- [x] DAO test (Robolectric, in-memory) untuk tiap DAO: insert, query, dan soft delete tidak ikut terbaca
- [x] Unit test `AgendaRepository`: penggabungan dan urutan, item seharian di akhir, pemilihan "berikutnya"
- [x] Unit test format rupiah dan helper tanggal

### Kriteria selesai
- Aplikasi dibuka pertama kali menampilkan data contoh dari DB. Setelah ditutup dan dibuka lagi, data tetap ada.
- Mengubah jam perangkat (atau `TimeProvider` saat tes) mengubah kartu "Berikutnya".
- Kalender bisa pindah bulan, dan titik agenda sesuai dengan data.
- `./gradlew testDebugUnitTest assembleDebug` hijau.

---

## Sprint 2 — Karier + Tugas & Proyek + mesin pengingat

**Tujuan**: dua modul CRUD penuh, alur prototipe pertama (Today → Detail Lamaran → Jadwalkan Interview → Kalender) berjalan dengan data nyata, dan pengingat interview muncul sebagai notifikasi.

### Checklist — Karier
- [x] `CareerRepository`: buat/ubah/hapus lamaran. `changeStatus()` menulis `application_status_change` dalam satu transaksi DB.
- [x] Layar **Karier**: filter status (Semua/Tersimpan/Apply/Tes/Interview/Offer/Ditolak) dengan jumlah per status, pencarian nama perusahaan/posisi, kartu menampilkan perusahaan, posisi, badge status, dan agenda berikutnya (atau "Belum ada jadwal")
- [x] Form **Tambah/Ubah lamaran**: perusahaan, posisi, lokasi, URL, sumber, status, tanggal apply, deadline, catatan. Validasi: perusahaan dan posisi wajib diisi.
- [x] Layar **Detail Lamaran** (desain 04): stepper tahapan, badge status, kartu agenda berikutnya, catatan, daftar semua event, tombol "Jadwalkan interview" dan "Lihat kalender", menu ubah status/hapus
- [x] Layar **Jadwalkan Interview** (desain 05): jenis event, tanggal (DatePicker M3), waktu (TimePicker M3), durasi, online/onsite + link/lokasi, pengingat (15 mnt/30 mnt/1 jam/1 hari/tidak ada). "Simpan ke kalender" menyimpan, lalu membuka Kalender pada tanggal tersebut.
- [x] Event yang lewat bisa ditandai selesai. Saat menjadwalkan interview, status lamaran otomatis naik jika masih di bawah INTERVIEW (dengan konfirmasi).

### Checklist — Tugas & Proyek
- [x] `TaskRepository`: CRUD proyek dan tugas, `toggleDone()`, urutan manual (`position`)
- [x] Layar **Tugas** (desain 09): kartu proyek dengan progres (selesai/total) dan deadline berikutnya, bagian "Berikutnya" (aktif, urut deadline), bagian "Selesai" (bisa diciutkan)
- [x] Badge deadline: Terlambat / Hari ini / Besok / tanggal. Dibedakan dengan teks dan ikon, tidak hanya warna.
- [x] Centang tugas dengan animasi, lalu snackbar "Batalkan"
- [x] Form tugas (judul, proyek, tanggal/jam opsional, prioritas, catatan) sebagai bottom sheet, dan form proyek
- [x] Layar **Detail Proyek**: daftar tugas proyek dan progres

### Checklist — Mesin pengingat (dipakai ulang S3–S5)
- [x] `ReminderScheduler`: `schedule(reminder)`, `cancel(sourceType, sourceId)`, `rescheduleAll()`
- [x] AlarmManager `setExactAndAllowWhileIdle` dengan izin `USE_EXACT_ALARM` (aplikasi kalender/pengingat). Jika izin tidak tersedia, fallback ke `setWindow`.
- [x] `AlarmReceiver` menampilkan notifikasi (channel "Pengingat agenda"). Mengetuk notifikasi membuka detail sumbernya (deep link).
- [x] `BootReceiver` dan `MY_PACKAGE_REPLACED` → `rescheduleAll()` lewat WorkManager
- [x] Minta izin `POST_NOTIFICATIONS` (Android 13+) pada saat pertama menyimpan pengingat, bukan saat aplikasi dibuka
- [x] Ubah/hapus event → alarm lama dibatalkan dan dijadwalkan ulang

### Tes
- [x] Repository: perubahan status mencatat riwayat, soft delete lamaran ikut menyembunyikan event-nya dari agenda
- [x] Logika badge deadline dan progres proyek
- [x] `ReminderScheduler` dengan AlarmManager palsu: jadwal, batal, jadwal ulang

### Kriteria selesai
- Alur 1 brief berjalan: Today → kartu Coding test → Detail → Jadwalkan interview → Simpan → Kalender menampilkan interview → ketuk → kembali ke Detail.
- Notifikasi pengingat muncul di HP pada waktunya (uji dengan pengingat 1–2 menit ke depan) dan tetap muncul setelah HP di-restart.
- Lamaran dengan judul sangat panjang tidak merusak layout (teks dipotong dengan elipsis).

---

## Sprint 3 — Workout Tracker

**Tujuan**: membuat rutinitas, menjadwalkannya mingguan, dan mencatat sesi latihan set demi set. Alur 2 brief (Today → Latihan → Mulai latihan → Sesi Latihan) berjalan dengan data nyata.

### Checklist
- [x] `WorkoutRepository`: CRUD rutinitas, gerakan (katalog + gerakan buatan sendiri), jadwal mingguan, sesi, dan set
- [x] `PlanWorkoutSessions` (use case harian via WorkManager dan saat aplikasi dibuka): membuat `workout_session` PLANNED untuk 7 hari ke depan dari `routine_schedule`, idempoten (tidak membuat duplikat)
- [x] Layar **Latihan** (desain 06): kartu rutinitas hari ini (atau rutinitas berikutnya) dengan maskot angkat beban, statistik minggu ini (sesi selesai/target, total durasi), riwayat ringkas (beberapa sesi terlihat sekaligus), daftar rutinitas
- [x] Form **Rutinitas**: nama, fokus, gerakan (pilih dari katalog dengan pencarian per kelompok otot), urutan (geser), target set/rep/beban/istirahat, hari & jam latihan
- [x] Layar **Sesi Latihan** (desain 07): header dengan maskot fokus dan progres gerakan (01/04), tabel set (SET/BEBAN/REP/selesai) dengan angka besar, set sebelumnya sebagai placeholder ("terakhir: 40 kg × 8"), stepper ± untuk beban (2,5 kg) dan rep, tombol selesai per set (area sentuh 48dp), tambah/hapus set, pindah gerakan
- [x] **Timer istirahat**: mulai otomatis saat set ditandai selesai, hitung mundur dari `restSeconds`, tetap berjalan saat layar mati (foreground service tipe `shortService` atau notifikasi berjalan), getar saat selesai
- [x] Sesi yang sedang berjalan bertahan saat aplikasi ditutup (status IN_PROGRESS), dan Today menampilkan "Lanjutkan latihan"
- [x] **Selesai latihan**: ringkasan (durasi, total volume, set selesai, PR baru) dengan maskot pose besar, lalu sesi disimpan sebagai DONE
- [x] Detail riwayat sesi dan grafik progres beban maksimum per gerakan (Compose Canvas sederhana)
- [x] Pengingat latihan opsional 30 menit sebelum jadwal, memakai mesin pengingat S2
- [x] Empty state: belum ada rutinitas / riwayat kosong, dengan maskot

### Tes
- [x] `PlanWorkoutSessions` idempoten dan menghormati sesi SKIPPED
- [x] Perhitungan volume, PR, dan statistik mingguan
- [x] Mengubah rutinitas tidak mengubah riwayat sesi lama

### Kriteria selesai
- Membuat rutinitas "Push Day" Senin/Kamis 17.00 → muncul di Today dan Kalender pada hari itu.
- Satu sesi lengkap bisa dicatat dan muncul di riwayat. Aplikasi yang ditutup di tengah sesi bisa dilanjutkan.
- Timer istirahat berbunyi/bergetar walau layar mati.

---

## Sprint 4 — Finance Tracker

**Tujuan**: mencatat transaksi dengan cepat, melihat sisa anggaran secara akurat, dan mengelola tagihan berulang.

### Checklist
- [x] `FinanceRepository`: CRUD transaksi, kategori, anggaran, dan tagihan. `payBill()` dalam satu DB transaction (membuat transaksi + `bill_payment` + memajukan `nextDueDate`).
- [x] Use case `GetBudgetSummary(budget, today)`: periode berjalan (mingguan/bulanan berdasarkan `startDayOfMonth`), terpakai, sisa, persentase, sisa hari, dan rata-rata harian yang aman
- [x] Layar **Keuangan** (desain 08): kartu sisa anggaran utama dengan maskot koin dan progress bar (status "Aman/Hampir habis/Lewat" dalam teks), transaksi terbaru dengan nominal rata kanan dan tanda +/− jelas, tagihan berikutnya
- [x] **Tambah transaksi cepat** (bottom sheet, maksimal 3 ketukan): keypad nominal, toggle Keluar/Masuk, chip kategori, catatan opsional, tanggal default sekarang. Bisa dibuka dari Today (aksi cepat) dan tombol + di Keuangan.
- [x] Layar **Semua transaksi**: dikelompokkan per hari dengan subtotal, filter bulan/kategori/jenis, pencarian catatan, geser untuk hapus (dengan "Batalkan")
- [x] Layar **Anggaran**: daftar anggaran, buat/ubah (nama, kategori, nominal, periode)
- [x] Layar **Tagihan**: daftar urut jatuh tempo, tandai lunas, riwayat pembayaran, pengingat H-n (mesin pengingat S2)
- [x] Ringkasan bulanan: total masuk/keluar, selisih, dan pengeluaran per kategori (bar horizontal)
- [x] Kelola kategori (tambah, ganti nama, arsip, urutkan)
- [x] Format nominal besar tetap muat: `Rp1.250.000` dan `Rp12,5 jt` untuk ruang sempit

### Tes
- [x] Batas periode anggaran (pergantian minggu/bulan, `startDayOfMonth` = 25, Februari)
- [x] `payBill` untuk tagihan bulanan pada tanggal 31 (maju ke akhir bulan berikutnya)
- [x] Ringkasan bulanan dan subtotal harian

### Kriteria selesai
- Mencatat "Makan siang Rp24.000" dari Today dalam ≤ 3 ketukan setelah mengisi nominal, dan sisa anggaran di Today serta Keuangan langsung berkurang.
- Melunasi tagihan gym membuat transaksi, memajukan jatuh tempo ke bulan berikutnya, dan menjadwalkan ulang pengingatnya.

---

## Sprint 5 — Pengingat harian, backup, pengaturan, kualitas rilis

**Tujuan**: aplikasi siap dipakai sehari-hari dalam jangka panjang: data aman, ada ringkasan pagi, onboarding, dan build rilis.

### Checklist
- [x] **Onboarding** (3 layar dengan maskot pose besar): nama panggilan, pilih modul yang dipakai, izin notifikasi. Opsi "Mulai dengan data contoh" atau "Mulai kosong" (hapus data seed).
- [x] **Ringkasan harian** (WorkManager, jam diatur di Pengaturan): notifikasi "3 agenda hari ini · sisa anggaran Rp42.000"
- [x] **Pengaturan**: nama, jam ringkasan harian, pengingat default per jenis, awal minggu, kelola kategori/gerakan, tentang aplikasi
- [x] **Backup & restore**: ekspor seluruh tabel ke JSON (`today-backup-YYYYMMDD.json`, berisi versi skema) lewat Storage Access Framework, dan impor dengan validasi versi + konfirmasi "timpa semua data". Pengingat backup mingguan opsional.
- [x] **Hapus semua data** (dengan konfirmasi ketik "HAPUS")
- [x] Tes migrasi Room (`MigrationTestHelper`) disiapkan, walau v1 belum punya migrasi, sebagai pola untuk perubahan berikutnya
- [x] Aksesibilitas: `contentDescription` semua ikon, target sentuh ≥ 48dp, uji font scale 1,3×, kontras teks di atas warna modul ≥ 4.5:1 _(font scale 1,3× belum diuji di perangkat)_
- [x] Performa: `LazyColumn` dengan `key` di daftar panjang, baseline profile opsional, tidak ada query DB di main thread (StrictMode di debug) _(baseline profile tidak dibuat)_
- [x] Ikon launcher adaptif dari maskot (foreground + background warna violet)
- [x] **Build rilis**: keystore lokal (di luar repo, path di `keystore.properties` yang di-ignore), R8 + shrink resources, `versionName 1.0.0`. Hasil: `app-release.apk` yang bisa disalin ke HP.
- [x] README: cara build, struktur kode, cara backup/restore

### Kriteria selesai
- Backup dari HP, hapus semua data, lalu restore → semua data kembali persis sama (diuji dengan tes round-trip di JVM dan manual di HP).
- APK rilis terpasang di HP, ukurannya jauh lebih kecil daripada debug, dan semua alur di atas berjalan.

---

## Lampiran A — Backend server (pasca-MVP, belum dijadwalkan)

Dikerjakan hanya jika nanti perlu sinkronisasi antarperangkat atau versi web. Skema di atas sudah siap untuk ini (UUID, `updatedAt`, `deletedAt`).

**Stack yang disarankan**: **Supabase** (PostgreSQL + Auth + Row Level Security), jadi tidak perlu menulis server sendiri. Alternatif self-host: **Ktor + PostgreSQL + Exposed** di VPS/Docker.

**Skema Postgres**: tabel yang sama dengan Room plus kolom `user_id uuid` (FK ke `auth.users`) dan index `(user_id, updated_at)`. RLS: `user_id = auth.uid()` untuk semua operasi.

**Protokol sinkronisasi** (last-write-wins per baris):
1. `GET /sync?since=<lastPulledAt>` → semua baris milik pengguna dengan `updated_at > since` (termasuk yang `deleted_at` terisi)
2. `POST /sync` dengan baris lokal yang berubah sejak sinkronisasi terakhir (kolom `dirty` ditambahkan via migrasi Room v2)
3. Konflik: `updated_at` terbaru menang. Server menetapkan `updated_at` dengan jam server untuk mencegah selisih jam perangkat.
4. Dijalankan oleh `SyncWorker` (WorkManager, periodik + saat ada koneksi)

**Endpoint minimal jika self-host**: `POST /auth/login`, `GET /sync`, `POST /sync`, `GET /health`.

**Di luar cakupan permanen untuk MVP**: integrasi transaksi bank, multi-pengguna, dan berbagi data.

---

## Log eksekusi

| Sprint | Status | Tanggal | Catatan |
|---|---|---|---|
| Sprint 1 | Selesai | 2026-11-09 | 17 tabel skema v1 (`app/schemas/.../1.json`), seed relatif terhadap hari ini, Today & Kalender dari DB, Inter variable font, 21 tes JVM lolos. Layar Latihan/Karier/Keuangan/Tugas masih statis (dikerjakan S2–S4). Belum dicek visual di HP. |
| Sprint 2 | Selesai (kode + tes) | 2026-11-09 | Karier (daftar+filter+cari, form, detail+stepper, jadwalkan event, naik tahap otomatis), Tugas & Proyek (bottom sheet, badge deadline, batalkan via snackbar, detail proyek), mesin pengingat (AlarmManager exact, Boot/Update → WorkManager, deep link notifikasi). 41 tes JVM lolos. **Belum diverifikasi di HP**: notifikasi muncul tepat waktu & setelah restart. Urutan manual tugas baru ada di repository (`reorder`), belum ada UI geser. |
| Sprint 3 | Selesai (kode + tes) | 2026-11-09 | Rutinitas (form, katalog + gerakan sendiri, hari & jam), perencana 7 hari idempoten (saat buka app + WorkManager harian), sesi set demi set (snapshot rutinitas, beban terakhir, stepper ±2,5 kg, tambah/hapus set & gerakan), timer istirahat (alarm exact + notifikasi hitung mundur + getar), lanjutkan sesi dari Today, ringkasan + PR, detail riwayat, grafik progres per gerakan, pengingat 30 mnt opsional. 56 tes JVM lolos. Penyesuaian: urutan gerakan memakai tombol ↑/↓ (bukan geser/drag). **Belum diverifikasi di HP**: getar timer saat layar mati, pengingat latihan. |
| Sprint 4 | Selesai (kode + tes) | 2026-11-10 | Keuangan dari DB: kartu sisa anggaran (status Aman/Hampir habis/Lewat + jatah aman per hari), sheet catat transaksi dengan keypad (dari Today "+ Transaksi" dan tab Keuangan), semua transaksi per hari dengan subtotal/filter/cari/geser-hapus + batalkan, anggaran mingguan/bulanan, tagihan (lunasi → transaksi + pembayaran + jatuh tempo maju dalam satu DB transaction, pengingat H-n), ringkasan bulanan per kategori, kelola kategori. Tagihan tgl 31 kembali ke 31 setelah Februari. 65 tes JVM lolos. **Belum diverifikasi di HP**. |
| Sprint 5 | Selesai (kode + tes) | 2026-11-10 | Onboarding 3 langkah (nama, modul aktif, izin notifikasi, data contoh/kosong), Pengaturan (nama, modul, ringkasan pagi + jam, default pengingat interview/tagihan, pengingat latihan, awal minggu untuk Kalender/Today, kelola kategori & gerakan, tentang), backup/restore JSON via SAF dengan validasi versi + ringkasan sebelum menimpa, hapus semua data (ketik HAPUS), pengingat backup mingguan, ringkasan pagi (WorkManager), tes migrasi (MigrationTestHelper, skema v1), LazyColumn+key di transaksi, StrictMode debug, target sentuh 48dp, ikon launcher adaptif, rilis R8 + shrink + keystore lokal (APK 2,5 MB vs debug 12,3 MB), README. 74 tes JVM lolos. Anggaran mingguan tetap Senin–Minggu. **Belum diverifikasi di HP**: APK rilis berjalan (R8), backup/restore manual, font scale 1,3×. Rilis tidak bisa dipasang di atas debug (beda kunci) — lihat README. |
