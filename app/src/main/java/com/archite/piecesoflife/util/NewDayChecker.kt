package com.archite.piecesoflife.util

import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.data.LogType
import com.archite.piecesoflife.data.LogRepository
import com.archite.piecesoflife.data.QuestFlag
import com.archite.piecesoflife.data.QuestType
import com.archite.piecesoflife.data.UserPreferencesRepository

object NewDayChecker {

    data class NewDayResult(
        val isNewDay: Boolean,
        val deletedCount: Int = 0,
        val failedQuestCount: Int = 0,
        val newPeriodicQuestCount: Int = 0,
        val pendingQuestCount: Int = 0,
        val expiringQuestCount: Int = 0,
    )

    suspend fun check(
        logRepo: LogRepository,
        userPrefRepo: UserPreferencesRepository,
    ): NewDayResult {
        val today = TimeUtil.getTimeInt()
        val lastDate = userPrefRepo.getLastDate()

        if (today <= lastDate) return NewDayResult(isNewDay = false)

        userPrefRepo.setLastDate(today)

        val dateStampText = "\n${TimeUtil.getTimeString(TimeUtil.TIME_TYPE_DATE_WEEK)}"
        logRepo.saveLog(LogEntity(
            logType = LogType.HINT,
            logText = dateStampText,
            buildDate = today,
            buildTime = 0,
            changeDate = 99990000,
            flag0 = 999,
        ))

        val deletedCount = deleteExpiredLogs(logRepo, today)

        val questResult = processQuestLog(logRepo, today)

        val activeQuests = logRepo.getActiveQuests()
        val pendingCount = activeQuests.count { QuestFlag.isUnfinished(it.flag0) }
        val expiringCount = activeQuests.count {
            val diff = TimeUtil.dateIntMinus(today, it.changeDate)
            diff in 1..3
        }

        val debugText = "【系统日志】完成新一天日志检定！删除了${deletedCount}条过期日志！" +
                "存在${questResult.failedCount}个失败任务，新建了${questResult.newCount}个周期任务。"
        logRepo.saveLog(LogEntity(
            logType = LogType.DEBUG,
            logText = debugText,
            buildDate = today,
            buildTime = 0,
            changeDate = today + 3,
            flag1 = 3,
        ))

        return NewDayResult(
            isNewDay = true,
            deletedCount = deletedCount,
            failedQuestCount = questResult.failedCount,
            newPeriodicQuestCount = questResult.newCount,
            pendingQuestCount = pendingCount,
            expiringQuestCount = expiringCount,
        )
    }

    private suspend fun deleteExpiredLogs(logRepo: LogRepository, today: Int): Int {
        val allLogs = logRepo.getAllLogs()
        val toDelete = allLogs.filter {
            (it.logType == LogType.HINT || it.logType == LogType.DEBUG) &&
                    it.changeDate < today && it.changeDate > 0 && it.changeDate < 90000000
        }
        for (log in toDelete) {
            logRepo.softDeleteLog(log.id)
        }
        return toDelete.size
    }

    private data class QuestProcessResult(val failedCount: Int, val newCount: Int)

    private suspend fun processQuestLog(logRepo: LogRepository, today: Int): QuestProcessResult {
        val allQuests = logRepo.getAllLogs().filter { it.logType == LogType.QUEST && !it.isDeleted }
        var failedCount = 0
        var newCount = 0

        for (quest in allQuests) {
            if (!QuestFlag.isUnfinished(quest.flag0)) continue
            if (quest.changeDate >= 30000000 || quest.changeDate == 0) continue
            if (quest.changeDate >= today) continue

            val expiredQuest = quest.copy(flag0 = if (QuestFlag.isDefaultType(quest.flag0)) QuestFlag.DEFAULT_FAILED else QuestFlag.MINUS_FAILED)
            logRepo.saveLog(expiredQuest)
            failedCount++

            if (quest.flag1 != QuestType.DEFAULT) {
                val nextDate = TimeUtil.getNextDate(quest.changeDate, quest.flag1)
                val newQuest = LogEntity(
                    logType = LogType.QUEST,
                    logText = quest.logText,
                    remark = quest.remark,
                    buildDate = today,
                    buildTime = 0,
                    changeDate = nextDate,
                    changeTime = 235959,
                    flag0 = if (QuestFlag.isDefaultType(quest.flag0)) QuestFlag.DEFAULT_UNFINISHED else QuestFlag.MINUS_UNFINISHED,
                    flag1 = quest.flag1,
                )
                logRepo.saveLog(newQuest)
                newCount++
            }
        }

        return QuestProcessResult(failedCount, newCount)
    }
}
