package com.example.homeassistantlauncher

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlin.system.exitProcess

class BaseApplication : Application(), Thread.UncaughtExceptionHandler {

    companion object {
        private const val MIN_CRASH_INTERVAL_MS = 5000L
        const val EXTRA_PREVIOUS_CRASH_TIME = "com.example.homeassistantlauncher.PREVIOUS_CRASH_TIME"
        var previousCrashTimeFromIntent: Long = 0L
    }

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Log.e("BaseApplication", "Uncaught exception: ", throwable)

        val currentTime = System.currentTimeMillis()
        val lastCrashTime = previousCrashTimeFromIntent

        if (lastCrashTime != 0L && (currentTime - lastCrashTime < MIN_CRASH_INTERVAL_MS)) {
            Log.e("BaseApplication", "Detected rapid crash (last crash at $lastCrashTime, current at $currentTime), suppressing restart to avoid boot loop.")
            
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, getString(R.string.rapid_crash_message), Toast.LENGTH_LONG).show()
                exitProcess(2)
            }

        } else {
            Log.i("BaseApplication", "Scheduling restart. Current time: $currentTime, Last recorded crash time via Intent: $lastCrashTime")

            val intent = Intent(applicationContext, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(EXTRA_PREVIOUS_CRASH_TIME, currentTime)

            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 100, pendingIntent)

            exitProcess(2)
        }
    }
}
