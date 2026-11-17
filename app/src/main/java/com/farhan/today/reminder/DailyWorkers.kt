package com.farhan.today.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.farhan.today.MainActivity
import com.farhan.today.R
import com.farhan.today.appContainer
import com.farhan.today.domain.formatRupiah
import com.farhan.today.domain.formatTime
import com.farhan.today.domain.model.TodayOverview
import com.farhan.today.ui.Routes
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

const val CHANNEL_DAILY = "daily_summary"
private const val DAILY_WORK = "daily-summary"
private const val BACKUP_WORK = "backup-reminder"

fun createDailyChannel(context: Context) {
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CHANNEL_DAILY, "Ringkasan harian", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Ringkasan pagi dan pengingat backup"
        }
    )
}

/** "3 agenda hari ini · sisa anggaran Rp42.000" + baris detail agenda berikutnya. */
object DailySummaryText {
    fun title(o: TodayOverview, name: String): String {
        val open = o.items.count { !it.done }
        return if (open == 0) "Pagi, $name! Hari ini longgar ☀️" else "Pagi, $name! $open agenda hari ini"
    }

    fun body(o: TodayOverview): String {
        val parts = mutableListOf<String>()
        val open = o.items.filter { !it.done }
        parts += if (open.isEmpty()) "Tidak ada agenda" else "${open.size} agenda hari ini"
        o.walletBalance?.let { parts += "saldo dompet ${formatRupiah(it)}" }
        val next = o.next?.let { n -> "Berikutnya: ${n.title}" + (n.time?.let { " jam ${formatTime(it)}" } ?: "") }
        return parts.joinToString(" · ") + (next?.let { "\n$it" } ?: "")
    }

    /** Jeda sampai jam ringkasan berikutnya (hari ini bila belum lewat, selain itu besok). */
    fun delayUntil(now: LocalDateTime, at: LocalTime): Duration {
        var target = now.toLocalDate().atTime(at)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target)
    }
}

private fun openApp(context: Context, route: String) = PendingIntent.getActivity(
    context, route.hashCode(),
    Intent(context, MainActivity::class.java).putExtra(EXTRA_ROUTE, route).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

class DailySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.appContainer
        val s = c.prefs.settingsOnce()
        if (!s.dailySummary || !canPostNotifications(applicationContext)) return Result.success()
        c.workoutRepository.planSessions()
        val overview = c.getTodayOverview().first()
        val body = DailySummaryText.body(overview)
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_DAILY)
            .setSmallIcon(R.drawable.ic_nav_today)
            .setContentTitle(DailySummaryText.title(overview, s.userName))
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp(applicationContext, Routes.TODAY))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(applicationContext).notify(7001, n)
        return Result.success()
    }

    companion object {
        /** Jadwalkan (atau batalkan) ringkasan harian sesuai pengaturan. */
        fun schedule(context: Context, enabled: Boolean, at: LocalTime) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) { wm.cancelUniqueWork(DAILY_WORK); return }
            val delay = DailySummaryText.delayUntil(LocalDateTime.now(), at)
            wm.enqueueUniquePeriodicWork(
                DAILY_WORK, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
                    .build(),
            )
        }
    }
}

class BackupReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.appContainer
        val s = c.prefs.settingsOnce()
        if (!s.backupReminder || !canPostNotifications(applicationContext)) return Result.success()
        val weekAgo = c.clock.millis() - 7 * 24 * 3_600_000L
        if ((s.lastBackupAt ?: 0) > weekAgo) return Result.success()
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_DAILY)
            .setSmallIcon(R.drawable.ic_nav_tasks)
            .setContentTitle("Saatnya backup data Today")
            .setContentText("Simpan salinan data ke file agar aman kalau HP rusak atau diganti.")
            .setContentIntent(openApp(applicationContext, Routes.SETTINGS))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(applicationContext).notify(7002, n)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) { wm.cancelUniqueWork(BACKUP_WORK); return }
            wm.enqueueUniquePeriodicWork(BACKUP_WORK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<BackupReminderWorker>(7, TimeUnit.DAYS).build())
        }
    }
}
