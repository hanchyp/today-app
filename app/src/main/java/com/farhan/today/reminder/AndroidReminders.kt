package com.farhan.today.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.farhan.today.MainActivity
import com.farhan.today.R
import com.farhan.today.appContainer
import com.farhan.today.data.db.ReminderSource
import com.farhan.today.data.db.ReminderState
import com.farhan.today.ui.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val CHANNEL_AGENDA = "agenda_reminders"
const val EXTRA_REMINDER_ID = "reminder_id"
const val EXTRA_ROUTE = "route"

fun createNotificationChannels(context: Context) {
    val channel = NotificationChannel(CHANNEL_AGENDA, "Pengingat agenda", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Interview, coding test, tagihan, dan latihan"
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

fun canPostNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** AlarmManager: exact bila diizinkan (USE_EXACT_ALARM/SCHEDULE_EXACT_ALARM), selain itu jendela 1 menit. */
class AndroidAlarmGateway(private val context: Context) : AlarmGateway {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private fun pending(reminderId: String, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, reminderId.hashCode(),
        Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_REMINDER_ID, reminderId),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun set(reminderId: String, triggerAt: Long) {
        val pi = pending(reminderId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exactAllowed) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        else alarms.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 60_000L, pi)
    }

    override fun cancel(reminderId: String) {
        pending(reminderId, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
    }
}

/** Menampilkan notifikasi; mengetuknya membuka layar sumber lewat EXTRA_ROUTE. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val pending = goAsync()
        val container = context.appContainer
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = container.db.reminderDao().byId(id)
                if (reminder != null && reminder.state == ReminderState.SCHEDULED && reminder.deletedAt == null) {
                    val route = when (reminder.sourceType) {
                        ReminderSource.JOB_EVENT -> container.db.careerDao().event(reminder.sourceId)
                            ?.let { Routes.careerDetail(it.applicationId) } ?: Routes.CAREER
                        ReminderSource.TASK -> Routes.TASKS
                        ReminderSource.BILL -> com.farhan.today.ui.finance.FinanceRoutes.BILLS
                        ReminderSource.WORKOUT -> Routes.WORKOUT
                    }
                    val tap = PendingIntent.getActivity(
                        context, id.hashCode(),
                        Intent(context, MainActivity::class.java)
                            .putExtra(EXTRA_ROUTE, route)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    val notification = NotificationCompat.Builder(context, CHANNEL_AGENDA)
                        .setSmallIcon(R.drawable.ic_calendar)
                        .setContentTitle(reminder.title)
                        .setContentText(reminder.body)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.body))
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(tap)
                        .setAutoCancel(true)
                        .build()
                    if (canPostNotifications(context)) {
                        @Suppress("MissingPermission")
                        NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
                    }
                    container.reminderScheduler.markFired(id)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarm hilang setelah reboot atau update aplikasi; jadwalkan ulang lewat WorkManager. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<RescheduleWorker>().build())
        }
    }
}

class RescheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.appContainer.reminderScheduler.rescheduleAll()
        return Result.success()
    }
}
