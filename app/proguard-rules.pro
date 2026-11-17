# Room, WorkManager, Navigation, dan Compose membawa consumer rules sendiri.
# Entity Room dibaca lewat kode hasil KSP, jadi tidak perlu aturan tambahan.

# Nama enum disimpan sebagai TEXT di database & backup: jangan diobfuscate.
-keepclassmembers enum com.farhan.today.** { *; }

# Stack trace yang masih terbaca saat ada crash di build rilis.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
