package com.farhan.today.workout

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import com.farhan.today.data.prefs.UserPrefs
import com.farhan.today.reminder.EXTRA_ROUTE
import com.farhan.today.reminder.canPostNotifications
import com.farhan.today.ui.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

const val CHANNEL_REST = "rest_timer"
private const val REST_NOTIFICATION_ID = 4210
private const val REQ_DONE = REST_NOTIFICATION_ID
private const val REQ_WARN = REST_NOTIFICATION_ID + 1
private const val REQ_EXTEND = REST_NOTIFICATION_ID + 2
private const val REQ_SKIP = REST_NOTIFICATION_ID + 3
private const val EXTRA_SESSION_ID = "session_id"
private const val ACTION_DONE = "com.farhan.today.rest.DONE"
private const val ACTION_WARN = "com.farhan.today.rest.WARN"
private const val ACTION_EXTEND = "com.farhan.today.rest.EXTEND"
private const val ACTION_SKIP = "com.farhan.today.rest.SKIP"

/** Peringatan dikirim sekian detik sebelum istirahat selesai (mode smartband). */
const val REST_WARN_SECONDS = 10
const val REST_EXTEND_SECONDS = 30

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Teks notifikasi yang tetap berguna di smartband: band hanya menampilkan teks statis
 * (chronometer Android tidak ikut berjalan), jadi tulis durasi + jam selesai secara absolut.
 */
fun restRunningText(totalSeconds: Int, endsAt: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    "Istirahat ${formatRestDuration(totalSeconds)} · selesai ${clockFormat.format(Instant.ofEpochMilli(endsAt).atZone(zone))}"

fun formatRestDuration(seconds: Int): String = when {
    seconds < 60 -> "$seconds dtk"
    seconds % 60 == 0 -> "${seconds / 60} mnt"
    else -> "${seconds / 60}:${"%02d".format(seconds % 60)} mnt"
}

/** Peringatan hanya masuk akal kalau istirahatnya cukup panjang. */
fun shouldWarn(totalSeconds: Int): Boolean = totalSeconds > REST_WARN_SECONDS * 2

/**
 * Timer istirahat yang tetap berjalan saat layar mati:
 * - waktu selesai disimpan di DataStore (UI menghitung mundur dari situ),
 * - notifikasi berjalan dengan chronometer hitung mundur + jam selesai, aksi "+30 dtk"/"Lewati",
 * - alarm exact membangunkan [RestTimerReceiver] untuk bergetar saat selesai
 *   (dan, di mode smartband, 10 detik sebelumnya).
 */
class RestTimer(private val context: Context, private val prefs: UserPrefs, private val clock: Clock) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private fun alarmIntent(action: String, requestCode: Int, sessionId: String?) = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(context, RestTimerReceiver::class.java).setAction(action).putExtra(EXTRA_SESSION_ID, sessionId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleAt(at: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at, 5_000L, pi)
        }
    }

    suspend fun start(seconds: Int, sessionId: String, exerciseName: String) {
        if (seconds <= 0) return
        arm(clock.millis() + seconds * 1000L, seconds, sessionId, exerciseName)
    }

    /** Tambah waktu istirahat yang sedang berjalan (aksi "+30 dtk"). */
    suspend fun extend(seconds: Int = REST_EXTEND_SECONDS) {
        val t = prefs.restTimerDetailOnce() ?: return
        val sessionId = t.sessionId ?: return
        val base = maxOf(t.endsAt, clock.millis())
        arm(base + seconds * 1000L, t.totalSeconds + seconds, sessionId, t.exerciseName)
    }

    private suspend fun arm(endsAt: Long, total: Int, sessionId: String, exerciseName: String) {
        prefs.setRestTimer(endsAt, total, sessionId, exerciseName)
        val band = prefs.settingsOnce().bandRestAlerts
        scheduleAt(endsAt, alarmIntent(ACTION_DONE, REQ_DONE, sessionId))
        val warnAt = endsAt - REST_WARN_SECONDS * 1000L
        val warn = alarmIntent(ACTION_WARN, REQ_WARN, sessionId)
        if (band && shouldWarn(total) && warnAt > clock.millis()) scheduleAt(warnAt, warn) else alarms.cancel(warn)
        notifyRunning(context, sessionId, exerciseName, endsAt, total, alert = band)
    }

    suspend fun cancel() {
        prefs.setRestTimer(null)
        alarms.cancel(alarmIntent(ACTION_DONE, REQ_DONE, null))
        alarms.cancel(alarmIntent(ACTION_WARN, REQ_WARN, null))
        NotificationManagerCompat.from(context).cancel(REST_NOTIFICATION_ID)
    }

    companion object {
        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_REST, "Timer istirahat", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Hitung mundur istirahat antar set"
                    enableVibration(true)
                }
            )
        }

        private fun openSession(context: Context, sessionId: String?) = PendingIntent.getActivity(
            context, REST_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ROUTE, sessionId?.let { Routes.workoutSession(it) } ?: Routes.WORKOUT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun action(context: Context, action: String, requestCode: Int) = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, RestTimerReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun runningBuilder(context: Context, sessionId: String, exerciseName: String, endsAt: Long, total: Int) =
            NotificationCompat.Builder(context, CHANNEL_REST)
                .setSmallIcon(R.drawable.ic_nav_workout)
                .setContentTitle("Istirahat · $exerciseName")
                .setContentText(restRunningText(total, endsAt))
                .setWhen(endsAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setContentIntent(openSession(context, sessionId))
                .addAction(0, "+$REST_EXTEND_SECONDS dtk", action(context, ACTION_EXTEND, REQ_EXTEND))
                .addAction(0, "Lewati", action(context, ACTION_SKIP, REQ_SKIP))

        /**
         * [alert] = mode smartband: notifikasi tidak ongoing dan tidak silent, karena Huawei Health
         * (dan aplikasi pendamping band lain) biasanya hanya meneruskan notifikasi yang berbunyi/bergetar.
         */
        @Suppress("MissingPermission")
        fun notifyRunning(context: Context, sessionId: String, exerciseName: String, endsAt: Long, total: Int, alert: Boolean) {
            if (!canPostNotifications(context)) return
            val n = runningBuilder(context, sessionId, exerciseName, endsAt, total)
                .setOngoing(!alert)
                .setOnlyAlertOnce(!alert)
                .setSilent(!alert)
                .build()
            NotificationManagerCompat.from(context).notify(REST_NOTIFICATION_ID, n)
        }

        @Suppress("MissingPermission")
        fun notifyWarning(context: Context, sessionId: String, exerciseName: String, endsAt: Long, total: Int) {
            if (!canPostNotifications(context)) return
            val n = runningBuilder(context, sessionId, exerciseName, endsAt, total)
                .setContentTitle("$REST_WARN_SECONDS detik lagi · $exerciseName")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(longArrayOf(0, 200))
                .build()
            NotificationManagerCompat.from(context).notify(REST_NOTIFICATION_ID, n)
        }

        @Suppress("MissingPermission")
        fun notifyFinished(context: Context, sessionId: String?) {
            vibrate(context)
            if (!canPostNotifications(context)) return
            val n = NotificationCompat.Builder(context, CHANNEL_REST)
                .setSmallIcon(R.drawable.ic_nav_workout)
                .setContentTitle("Istirahat selesai")
                .setContentText("Waktunya set berikutnya 💪")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVibrate(longArrayOf(0, 300, 150, 300))
                .setAutoCancel(true)
                .setContentIntent(openSession(context, sessionId))
                .build()
            NotificationManagerCompat.from(context).notify(REST_NOTIFICATION_ID, n)
        }

        fun vibrate(context: Context) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
        }
    }
}

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = context.appContainer
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                when (intent.action) {
                    ACTION_EXTEND -> container.restTimer.extend()
                    ACTION_SKIP -> container.restTimer.cancel()
                    ACTION_WARN -> {
                        val t = container.prefs.restTimerDetailOnce()
                        if (t?.sessionId != null && t.endsAt > now) {
                            RestTimer.notifyWarning(context, t.sessionId, t.exerciseName, t.endsAt, t.totalSeconds)
                        }
                    }
                    else -> {
                        val t = container.prefs.restTimerDetailOnce()
                        // Lewati alarm basi: timer sudah dibatalkan, atau diperpanjang dan alarm barunya belum tiba.
                        if (t != null && t.endsAt <= now + 1_500) {
                            RestTimer.notifyFinished(context, intent.getStringExtra(EXTRA_SESSION_ID) ?: t.sessionId)
                            container.prefs.setRestTimer(null)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Perencana harian: membuat sesi PLANNED 7 hari ke depan dari jadwal mingguan. */
class PlanWorkoutsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.appContainer.workoutRepository.planSessions()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "plan-workouts", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PlanWorkoutsWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
