package com.archite.piecesoflife.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.AppDatabase
import com.archite.piecesoflife.data.LogRepository
import com.archite.piecesoflife.data.LogType
import com.archite.piecesoflife.data.QuestFlag
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

private const val TAG = "QuestReminder"

const val CHANNEL_ID_QUEST_REMINDER = "quest_reminder"
const val NOTIFICATION_ID_QUEST = 1001
const val ACTION_QUEST_REMINDER = "com.archite.piecesoflife.ACTION_QUEST_REMINDER"
const val EXTRA_REMINDER_ENABLED = "reminder_enabled"

class QuestReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, ">>> onReceive entered")
        val isEnabled = intent?.getBooleanExtra(EXTRA_REMINDER_ENABLED, false) ?: false
        Log.d(TAG, "isEnabled from intent = $isEnabled")

        if (!isEnabled) {
            Log.w(TAG, "onReceive: disabled or no EXTRA, skipping")
            return
        }

        Log.d(TAG, "onReceive: alarm triggered, checking quests...")

        runBlocking(Dispatchers.IO) {
            val logRepo = LogRepository(AppDatabase.getInstance(context).logDao())

            val today = TimeUtil.getTimeInt()
            val allQuests = logRepo.getAllLogs()
                .filter { it.logType == LogType.QUEST && !it.isDeleted }

            val dueToday = allQuests.count {
                QuestFlag.isUnfinished(it.flag0) && it.changeDate == today && it.changeDate < 30000000
            }

            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, 6)
            val weekEnd = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_DATE, cal)
            val dueThisWeek = allQuests.count {
                QuestFlag.isUnfinished(it.flag0) && it.changeDate in (today + 1)..weekEnd && it.changeDate < 30000000
            }

            Log.d(TAG, "today=$today, dueToday=$dueToday, dueThisWeek=$dueThisWeek, totalQuests=${allQuests.size}")

            if (dueToday == 0 && dueThisWeek == 0) {
                Log.d(TAG, "onReceive: no due quests, skipping notification")
                return@runBlocking
            }

            val parts = mutableListOf<String>()
            if (dueToday > 0) parts.add("${dueToday}个任务将在今天到期")
            if (dueThisWeek > 0) parts.add("${dueThisWeek}个任务将在本周到期")

            val content = parts.joinToString("，") + "，请尽快处理！"

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_QUEST_REMINDER)
                .setSmallIcon(R.drawable.smallicon)
                .setContentTitle("任务临期通知")
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context, 0,
                        context.packageManager.getLaunchIntentForPackage(context.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_QUEST, notification)
            Log.d(TAG, "notification sent: $content")
        }
        Log.d(TAG, "<<< onReceive complete")
    }
}

/**
 * 提醒闹钟调度器。
 * 调度由 APP 内事件驱动（NewDayChecker / AppSetting），
 * 不在 BroadcastReceiver 中链式续期。
 */
object QuestNotificationScheduler {

    fun scheduleDailyReminder(context: Context, reminderEnabled: Boolean, reminderTime: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.w(TAG, "scheduleDailyReminder: AlarmManager unavailable")
            return
        }

        val intent = Intent(ACTION_QUEST_REMINDER).apply {
            putExtra(EXTRA_REMINDER_ENABLED, reminderEnabled)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "scheduleDailyReminder: cancelled old alarm, enabled=$reminderEnabled")

        if (!reminderEnabled) {
            Log.d(TAG, "scheduleDailyReminder: disabled, no alarm set")
            return
        }

        val hour = reminderTime / 100
        val minute = reminderTime % 100

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            Log.d(TAG, "scheduleDailyReminder: time already passed, scheduling tomorrow")
        }

        Log.d(TAG, "alarm set: ${calendar.time} (${calendar.timeInMillis}) with setExactAndAllowWhileIdle")
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "SCHEDULE_EXACT_ALARM denied, falling back to setAndAllowWhileIdle: ${e.message}")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    fun cancelDailyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.w(TAG, "cancelDailyReminder: AlarmManager unavailable")
            return
        }
        val intent = Intent(ACTION_QUEST_REMINDER).apply {
            putExtra(EXTRA_REMINDER_ENABLED, false)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "cancelDailyReminder: alarm cancelled")
    }
}
