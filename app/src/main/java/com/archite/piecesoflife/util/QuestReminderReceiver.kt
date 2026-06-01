package com.archite.piecesoflife.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.AppDatabase
import com.archite.piecesoflife.data.LogRepository
import com.archite.piecesoflife.data.LogType
import com.archite.piecesoflife.data.QuestFlag
import com.archite.piecesoflife.data.UserPreferencesRepository
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.runBlocking

const val CHANNEL_ID_QUEST_REMINDER = "quest_reminder"
const val NOTIFICATION_ID_QUEST = 1001
const val ACTION_QUEST_REMINDER = "com.archite.piecesoflife.ACTION_QUEST_REMINDER"

class QuestReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val userRepo = UserPreferencesRepository(context)
        Thread {
            try {
                runBlocking {
                    val isEnabled = userRepo.getQuestReminderEnabled()
                    val reminderTime = userRepo.getReminderTime()
                    QuestNotificationScheduler.scheduleDailyReminder(context, isEnabled, reminderTime)
                }
            } catch (_: Exception) { }
        }.start()

        Thread {
            try {
                runBlocking {
                    val logRepo = LogRepository(AppDatabase.getInstance(context).logDao())
                    val userRepo = UserPreferencesRepository(context)

                    if (!userRepo.getQuestReminderEnabled()) return@runBlocking

                    val today = TimeUtil.getTimeInt()
                    val allQuests = logRepo.getAllLogs()
                        .filter { it.logType == LogType.QUEST && !it.isDeleted }

                    val dueToday = allQuests.count {
                        QuestFlag.isUnfinished(it.flag0) && it.changeDate == today && it.changeDate < 30000000
                    }

                    val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+08:00"))
                    cal.add(Calendar.DAY_OF_MONTH, 6)
                    val weekEnd = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_DATE, cal)
                    val dueThisWeek = allQuests.count {
                        QuestFlag.isUnfinished(it.flag0) && it.changeDate in (today + 1)..weekEnd && it.changeDate < 30000000
                    }

                    if (dueToday == 0 && dueThisWeek == 0) return@runBlocking

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
                        .build()

                    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_QUEST, notification)
                }
            } catch (_: Exception) { }
        }.start()
    }
}

object QuestNotificationScheduler {

    fun scheduleDailyReminder(context: Context, reminderEnabled: Boolean, reminderTime: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(ACTION_QUEST_REMINDER).apply {
            setClass(context, QuestReminderReceiver::class.java)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!reminderEnabled) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val hour = reminderTime / 100
        val minute = reminderTime % 100

        val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+08:00")).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun cancelDailyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(ACTION_QUEST_REMINDER).apply {
            setClass(context, QuestReminderReceiver::class.java)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
