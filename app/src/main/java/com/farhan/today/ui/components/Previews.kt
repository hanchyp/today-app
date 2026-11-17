package com.farhan.today.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.farhan.today.R
import com.farhan.today.ui.theme.TodayColors
import com.farhan.today.ui.theme.TodayTheme
import com.farhan.today.ui.theme.TodayType

/**
 * Preview untuk ukuran HP yang perlu dicek setiap mengubah UI:
 * HP kecil (320dp, teks 2×), HP umum (360dp, teks 1.3×), dan HP besar (411dp).
 */
@Preview(name = "Kecil · teks 2×", widthDp = 320, heightDp = 640, fontScale = 2f, showBackground = true, backgroundColor = 0xFFFDFCF8)
@Preview(name = "Umum · teks 1.3×", widthDp = 360, heightDp = 760, fontScale = 1.3f, showBackground = true, backgroundColor = 0xFFFDFCF8)
@Preview(name = "Besar", widthDp = 411, heightDp = 860, showBackground = true, backgroundColor = 0xFFFDFCF8)
annotation class PhonePreviews

@PhonePreviews
@Composable
private fun DetailScaffoldPreview() = TodayTheme {
    DetailScaffold(
        title = "Detail lamaran",
        onBack = {},
        actions = { TopBarAction(R.drawable.ic_edit, "Ubah") {} },
        bottomBar = { PrimaryButton("Jadwalkan interview", icon = R.drawable.ic_calendar) {} },
    ) {
        Text("GDP Labs", style = TodayType.Title, color = TodayColors.Headline)
        SearchField("", {}, "Cari perusahaan atau posisi")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Tahap: Interview", trailingIcon = R.drawable.ic_expand_more) {}
            Pill("Selesai", selected = true, icon = R.drawable.ic_check) {}
        }
        PrimaryButton("Menyimpan", loading = true) {}
        PrimaryButton("Masukkan nominal", enabled = false) {}
        SecondaryButton("Buka link lowongan", icon = R.drawable.ic_open_in_new) {}
        HeroCard(TodayColors.Workout, mascot = R.drawable.mascot_lift) {
            Text("Push Day dengan nama yang cukup panjang", style = TodayType.Section, color = TodayColors.Ink)
            Text("Dada, bahu, dan trisep", style = TodayType.Body, color = TodayColors.Ink)
        }
        EmptyState("Belum ada tugas", "Tambahkan langkah kecil berikutnya supaya progres tetap jalan.")
    }
}

@PhonePreviews
@Composable
private fun LoadingPreview() = TodayTheme {
    Column(Modifier.fillMaxSize().background(TodayColors.Background).padding(20.dp)) {
        LoadingSkeleton()
    }
}
