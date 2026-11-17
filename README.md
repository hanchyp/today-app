# Today — Android Personal Dashboard

Aplikasi Android offline untuk satu pengguna: jadwal, latihan, lamaran kerja, keuangan, dan tugas dalam satu layar **Today**.
Desain acuan: `../Today-Android-Mascot-Polished.figcraft`. Rencana & log pengerjaan: [`docs/SPRINTS.md`](docs/SPRINTS.md).

## Build

Tanpa Android Studio. Yang dibutuhkan:

| Komponen | Lokasi di mesin ini |
|---|---|
| JDK 17 | `C:\Program Files\Microsoft\jdk-17.0.12.7-hotspot` |
| Android SDK (API 35) | `F:\Sdk` (`local.properties` → `sdk.dir`) |
| Cache Gradle | `D:\Android\gradle-home` (env `GRADLE_USER_HOME`) |

```bash
./gradlew testDebugUnitTest     # semua tes JVM (Robolectric untuk Room)
./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease       # app/build/outputs/apk/release/app-release.apk (R8, ±2,5 MB)
```

Robolectric mengunduh runtime Android ke `D:\Android\robolectric-home` (diatur di `app/build.gradle.kts`).

### Build rilis & keystore

- Keystore: `D:\Android\keystores\today-release.jks` (di luar repo).
- Path & sandi: `keystore.properties` di root proyek (di-ignore git); salinannya ada di `D:\Android\keystores\today-release.keystore.properties`.
- **Simpan cadangan keystore + file properties itu di tempat lain.** Tanpa keystore yang sama, versi rilis berikutnya tidak bisa dipasang di atas versi lama (harus uninstall, data di HP hilang kecuali sudah di-backup).
- Tanpa `keystore.properties`, `assembleRelease` tetap jalan tetapi APK tidak ditandatangani.

### Memasang di HP (Redmi / MIUI)

MIUI memblokir `adb install` tanpa SIM, jadi salin APK ke HP lalu buka dari File Manager.

APK debug dan rilis ditandatangani dengan kunci berbeda, sehingga **rilis tidak bisa dipasang di atas debug**. Untuk pindah:

1. Di aplikasi debug: **Atur → Backup ke file**.
2. Uninstall aplikasi debug.
3. Pasang `app-release.apk`, selesaikan onboarding (pilih "Kosong").
4. **Atur → Pulihkan dari backup** → pilih file tadi.

## Struktur kode

```
app/src/main/java/com/farhan/today/
├── TodayApplication.kt        # membuat AppContainer, channel notifikasi, worker
├── di/AppContainer.kt         # DI manual: db, repository, scheduler, DataManager
├── data/
│   ├── db/                    # Room: Entities, Daos, Converters, TodayDatabase (skema v1), SeedData
│   ├── repo/                  # Workout/Career/Finance/Task/AgendaRepository (aturan bisnis)
│   ├── prefs/UserPrefs.kt     # DataStore: nama, modul aktif, pengaturan notifikasi
│   └── backup/DataManager.kt  # ekspor/impor JSON, hapus semua data
├── domain/                    # model, formatter (Rp, tanggal), WorkoutMath, use case Today
├── reminder/                  # AlarmManager (ReminderScheduler), Boot/Alarm receiver, worker harian
├── workout/RestTimer.kt       # timer istirahat + perencana sesi harian
└── ui/
    ├── theme/  components/    # token desain FigCraft, kartu, chip, form
    ├── today/  calendar/  career/  workout/  finance/  tasks/  settings/
    └── TodayApp.kt            # onboarding gate, NavHost, navigasi bawah
```

Konvensi data (lihat `docs/SPRINTS.md` §0.4): ID UUID, `createdAt/updatedAt/deletedAt` (soft delete), uang `Long` rupiah utuh, tanggal `epochDay`. Skema diekspor ke `app/schemas/`; perubahan skema wajib lewat `Migration` + tes (`MigrationTest`).

## Backup & restore

- **Atur → Backup ke file** menyimpan `today-backup-YYYYMMDD.json` lewat pemilih file Android (bisa ke Download, Google Drive, dsb.).
- Isinya semua tabel + nama pengguna, beserta `schemaVersion`. File dari versi skema yang lebih baru ditolak.
- **Pulihkan dari backup** menampilkan ringkasan isi file lalu meminta konfirmasi; data saat ini **diganti seluruhnya** dalam satu transaksi (gagal = tidak ada yang berubah). Pengingat masa depan dipasang ulang otomatis.
- **Hapus semua data** meminta mengetik `HAPUS`; katalog gerakan dan kategori keuangan bawaan dibuat ulang.
- Opsi pengingat backup mingguan ada di Pengaturan.

## Lisensi pihak ketiga

Font Inter (SIL Open Font License 1.1) di `res/font/inter_variable.ttf`.

Katalog gerakan dari [hasaneyldrm/exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset):
- `assets/exercise-catalog.json`: 1.324 gerakan (data MIT, lihat `assets/exercise-catalog-license.txt`).
- `assets/exercise-media/videos/*.webp`: animasi gerakan 180×180, dikonversi dari GIF asli ke **WebP animasi** (lossy q70, method 6) supaya ±3× lebih kecil. JPG thumbnail tidak dipakai; frame pertama WebP berfungsi sebagai gambar diam (Android 8.x hanya menampilkan frame itu). Media © Gym visual — https://gymvisual.com/ (lihat `assets/exercise-media-notice.md`); atribusi tampil di dialog gerakan.
- Di JSON, kolom `animation` berisi `videos/<id>-<media>.webp`.
- Untuk memperbarui: unduh repo, konversi `videos/*.gif` ke `.webp` (Pillow: `save_all=True, quality=70, method=6`), salin ke `assets/exercise-media/videos/`, lalu ganti `gif` → `animation` dengan ekstensi `.webp` di JSON.

## Identitas visual

- Logo "Matahari" (`docs/today-logo.svg`, ikon launcher di `res/drawable/ic_launcher_foreground.xml` + versi monokrom). Konsep lain ada di `docs/logo-concepts/`.
- Palet (`ui/theme/Theme.kt`): dasar putih hangat `#FDFCF8`, kuning `#FFD23F` untuk kartu fokus/FAB (teks navy), navy `#1A1A2E` untuk aksi utama & teks, coral `#FF5A36` sebagai aksen. Token warna merek bernama `Brand*` (dulu `Violet*`).
