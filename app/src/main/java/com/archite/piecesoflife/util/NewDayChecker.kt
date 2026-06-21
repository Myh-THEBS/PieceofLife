package com.archite.piecesoflife.util

import android.content.Context
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.data.LogType
import com.archite.piecesoflife.data.LogRepository
import com.archite.piecesoflife.data.QuestFlag
import com.archite.piecesoflife.data.QuestType
import com.archite.piecesoflife.data.UserItem
import java.io.File

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
        today: Int,
        userName: String,
        items: List<UserItem>,
        context: Context? = null,
    ): NewDayResult {
        val dateStampText = "\n${TimeUtil.getTimeString(TimeUtil.TIME_TYPE_DATE_WEEK)}"
        logRepo.saveLog(LogEntity(
            logType = LogType.HINT,
            logText = dateStampText,
            buildDate = today,
            buildTime = 0,
            changeDate = 99990000,
            flag0 = 999,
        ))

        val attrs = items.filter { it.type != UserItem.TYPE_PHRASES && it.type != UserItem.TYPE_LABEL }
        val itemSummary = attrs.take(4).joinToString("，") { item ->
            val displayValue = if (item.type == UserItem.TYPE_SKILL) {
                val level = item.value / item.levelExp
                if (level >= 99) "Lv99+" else "Lv$level"
            } else {
                "${item.value}"
            }
            "${item.iconEmoji}${item.name} $displayValue"
        }
        val welcomeText = buildString {
            append("欢迎回来，$userName！")
            if (itemSummary.isNotEmpty()) {
                append("您当前的属性为：$itemSummary。")
            }
        }

        val deletedCount = deleteExpiredLogs(logRepo, today)

        val purgedCount = context?.let { purgeOldDeletedLogs(logRepo, today, it) } ?: 0

        val questResult = processQuestLog(logRepo, today)

        val activeQuests = logRepo.getActiveQuests()
        val pendingCount = activeQuests.count { QuestFlag.isUnfinished(it.flag0) }
        val expiringCount = activeQuests.count {
            val diff = TimeUtil.dateIntMinus(today, it.changeDate)
            diff in 1..3
        }

        val welcomeTextFull = buildString {
            append(welcomeText)
            if (questResult.failedCount > 0) append("有${questResult.failedCount}个任务已失败。")
            if (pendingCount > 0) append("您目前还有${pendingCount}个未完成的任务。")
            if (expiringCount > 0) append("有${expiringCount}个任务即将到期。")
        }
        logRepo.saveLog(LogEntity(
            logType = LogType.HINT,
            logText = welcomeTextFull,
            buildDate = today,
            buildTime = 1,
            changeDate = today,
            flag0 = 0,
        ))

        val debugText = "【系统日志】完成新一天日志检定！删除了${deletedCount}条过期日志！" +
                "永久删除了${purgedCount}条超14天的软删除日志。" +
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
            logRepo.permanentlyDeleteLog(log.id)
        }
        return toDelete.size
    }

    private suspend fun purgeOldDeletedLogs(logRepo: LogRepository, today: Int, context: Context): Int {
        val allDeleted = logRepo.getDeletedLogs()
        val cutoff = today - 14
        val toPurge = allDeleted.filter { it.changeDate < cutoff && it.changeDate > 0 }
        val imagesDir = ImageUtil.getImagesDir(context)
        val documentsDir = ImageUtil.getDocumentsDir(context)

        for (log in toPurge) {
            if (log.logType == LogType.PICTURE && log.remark.isNotEmpty()) {
                File(imagesDir, log.remark.removePrefix("images/")).delete()
            } else if (log.logType == LogType.DOCUMENT && log.remark.isNotEmpty()) {
                File(documentsDir, log.remark.removePrefix("documents/")).delete()
            }
            logRepo.permanentlyDeleteLog(log.id)
        }
        return toPurge.size
    }

    private data class QuestProcessResult(val failedCount: Int, val newCount: Int)

    private suspend fun processQuestLog(logRepo: LogRepository, today: Int): QuestProcessResult {
        val allQuests = logRepo.getAllLogs().filter { it.logType == LogType.QUEST && !it.isDeleted }
        var failedCount = 0
        var newCount = 0

        // 第一遍：收集过期且未完成的（→ 失败）和过期且有重复类型的（→ 克隆）
        val toFail = mutableListOf<LogEntity>()
        val toRepeat = mutableListOf<LogEntity>()
        val repeatIds = mutableSetOf<Long>()

        for (quest in allQuests) {
            if (quest.changeDate >= 30000000 || quest.changeDate == 0) continue
            if (quest.changeDate >= today) continue

            val isUnfinished = QuestFlag.isUnfinished(quest.flag0)
            if (isUnfinished) {
                toFail.add(quest)
            }

            if (quest.flag1 != QuestType.DEFAULT) {
                toRepeat.add(quest)
                repeatIds.add(quest.id)
            }
        }

        // 第二遍：先克隆重复任务（修改原任务 flag1 → DEFAULT，创建新实例）
        for (quest in toRepeat) {
            val repeatType = quest.flag1

            // 修改原任务的 flag1 为 DEFAULT，使其不再被认为是重复任务
            logRepo.saveLog(quest.copy(flag1 = QuestType.DEFAULT))

            var nextDate = TimeUtil.getNextDate(quest.changeDate, repeatType)
            while (nextDate < today) {
                nextDate = TimeUtil.getNextDate(nextDate, repeatType)
            }

            val clonedFlag0 = when {
                QuestFlag.isFinished(quest.flag0) -> quest.flag0 - 100
                QuestFlag.isFailed(quest.flag0) -> quest.flag0 + 100
                else -> quest.flag0
            }

            logRepo.saveLog(LogEntity(
                logType = LogType.QUEST,
                logText = quest.logText,
                remark = quest.remark,
                buildDate = today,
                buildTime = 1,
                changeDate = nextDate,
                changeTime = 235959,
                flag0 = clonedFlag0,
                flag1 = repeatType,
            ))
            newCount++
        }

        // 第三遍：再标记过期且未完成的为失败
        // 如果同一个任务也已在 toRepeat 中，flag1 已被改为 DEFAULT，copy 时需统一
        for (quest in toFail) {
            val alsoRepeated = quest.id in repeatIds
            logRepo.saveLog(quest.copy(
                flag0 = if (QuestFlag.isDefaultType(quest.flag0)) QuestFlag.DEFAULT_FAILED else QuestFlag.MINUS_FAILED,
                flag1 = if (alsoRepeated) QuestType.DEFAULT else quest.flag1,
                buildDate = today,
                buildTime = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_SECOND),
                changeDate = today,
                changeTime = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_SECOND),
            ))
            failedCount++
        }

        return QuestProcessResult(failedCount, newCount)
    }
}
