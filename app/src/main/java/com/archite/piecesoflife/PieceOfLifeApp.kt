package com.archite.piecesoflife

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.archite.piecesoflife.data.UserPreferencesRepository
import com.archite.piecesoflife.util.CHANNEL_ID_QUEST_REMINDER
import com.archite.piecesoflife.util.QuestNotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PieceOfLifeApp : Application() {

    // 应用级别的协程作用域，用于在 Application.onCreate 中执行挂起函数
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 创建通知渠道（Android 8.0+ 必需，通知渠道必须在发送任何通知之前创建）
        createNotificationChannels()

        // 调度每日提醒闹钟
        appScope.launch {
            val userRepo = UserPreferencesRepository(this@PieceOfLifeApp)
            QuestNotificationScheduler.scheduleDailyReminder(
                this@PieceOfLifeApp,
                userRepo.getQuestReminderEnabled(),
                userRepo.getReminderTime()
            )
        }
    }

    /**
     * 创建应用所需的所有通知渠道。
     *
     * Android 8.0（API 26+）要求所有通知必须归属于一个通知渠道。
     * 此方法应在 Application.onCreate 中调用，确保在发送任何通知之前渠道已就绪。
     */
    private fun createNotificationChannels() {
        // 任务提醒通知渠道
        val questChannel = NotificationChannel(
            CHANNEL_ID_QUEST_REMINDER,
            getString(R.string.channel_quest_reminder), // 渠道名称，显示在系统设置中
            NotificationManager.IMPORTANCE_DEFAULT       // 通知重要级别：DEFAULT 会在通知栏显示并发出声音
        ).apply {
            description = getString(R.string.channel_quest_reminder_desc) // 渠道描述，显示在系统设置中
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(questChannel)
    }

    companion object {
        lateinit var instance: PieceOfLifeApp
            private set

        fun getAppContext(): Context = instance.applicationContext
    }
}
