package com.farhan.today

import android.app.Application
import android.content.Context
import com.farhan.today.di.AppContainer
import android.os.StrictMode
import com.farhan.today.reminder.BackupReminderWorker
import com.farhan.today.reminder.DailySummaryWorker
import com.farhan.today.reminder.createDailyChannel
import com.farhan.today.reminder.createNotificationChannels
import kotlinx.coroutines.launch
import com.farhan.today.workout.PlanWorkoutsWorker

class TodayApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
        com.farhan.today.workout.RestTimer.createChannel(this)
        createDailyChannel(this)
        container = AppContainer(this)
        container.start()
        PlanWorkoutsWorker.schedule(this)
        container.appScope.launch {
            val st = container.prefs.settingsOnce()
            DailySummaryWorker.schedule(this@TodayApplication, st.dailySummary, st.dailySummaryTime)
            BackupReminderWorker.schedule(this@TodayApplication, st.backupReminder)
        }
        if (BuildConfig.DEBUG) {
            // Tangkap query DB/IO yang tidak sengaja jalan di main thread.
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().penaltyLog().build())
        }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as TodayApplication).container
