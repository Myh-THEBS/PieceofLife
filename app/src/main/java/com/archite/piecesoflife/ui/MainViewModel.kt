package com.archite.piecesoflife.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.archite.piecesoflife.data.AppDatabase
import com.archite.piecesoflife.data.ApplyMode
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.data.LogItemChange
import com.archite.piecesoflife.data.LogRepository
import com.archite.piecesoflife.data.LogType
import com.archite.piecesoflife.data.QuestFlag
import com.archite.piecesoflife.data.UserItem
import com.archite.piecesoflife.data.UserPreferencesRepository
import com.archite.piecesoflife.util.NewDayChecker
import com.archite.piecesoflife.util.QuestNotificationScheduler
import com.archite.piecesoflife.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val userName: String = "",
    val dayGroup: Int = 2,
    val items: List<UserItem> = emptyList(),
    val logs: List<LogEntity> = emptyList(),
    val itemAbbrMap: Map<String, String> = emptyMap(),
    val labelNames: Set<String> = emptySet(),
    val debugMode: Boolean = false,
    val leftMode: Boolean = false,
    val pixelFont: Boolean = true,
    val imageDisplayMode: Boolean = true,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val logRepo = LogRepository(AppDatabase.getInstance(application).logDao())
    private val userRepo = UserPreferencesRepository(application)

    var focusDate: Int = TimeUtil.getTimeInt()
    var keyword: String = ""
    var debugMode: Boolean = false
    var showDeleted: Boolean = false

    suspend fun completeQuestSync(logId: Long, isSuccess: Boolean) {
        logRepo.completeQuest(logId, isSuccess)
        val log = logRepo.getLogById(logId) ?: return
        val deltas = LogItemChange.fromJson(log.itemsJson)
        if (deltas.isEmpty()) return
        val mode = if (isSuccess) {
            if (QuestFlag.isMinusType(log.flag0)) ApplyMode.PENALTY_SUCCESS else ApplyMode.DEFAULT_SUCCESS
        } else {
            if (QuestFlag.isMinusType(log.flag0)) ApplyMode.PENALTY_FAILURE else ApplyMode.DEFAULT_FAILURE
        }
        val items = userRepo.getItems().toMutableList()
        val itemsBefore = items.toList()
        LogItemChange.apply(deltas, mode, items)
        val levelUpMsgs = LogItemChange.detectSkillLevelUp(itemsBefore, items)
        val today = TimeUtil.getTimeInt()
        val nowTime = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_SECOND)
        for (msg in levelUpMsgs) {
            logRepo.saveLog(LogEntity(
                logType = LogType.HINT,
                logText = msg,
                buildDate = today,
                buildTime = nowTime,
                changeDate = today,
                changeTime = nowTime,
            ))
        }
        userRepo.setItems(items)
    }

    suspend fun permanentlyDeleteLog(logId: Long) {
        logRepo.permanentlyDeleteLog(logId)
    }

    suspend fun runNewDayCheck(): NewDayChecker.NewDayResult {
        val today = TimeUtil.getTimeInt()
        val lastDate = userRepo.getLastDate()
        if (today <= lastDate) return NewDayChecker.NewDayResult(isNewDay = false)
        userRepo.setLastDate(today)
        val userName = userRepo.getUserName()
        val items = userRepo.getItems()
        val result = NewDayChecker.check(logRepo, today, userName, items, getApplication())
        // 每日检测后刷新提醒闹钟（确保闹钟在正确的时间点触发）
        val context = getApplication<android.app.Application>()
        QuestNotificationScheduler.scheduleDailyReminder(
            context, userRepo.getQuestReminderEnabled(), userRepo.getReminderTime()
        )
        return result
    }

    fun refresh(callback: (MainUiState) -> Unit) {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                val dayGroup = userRepo.getDayGroup()
                val userName = userRepo.getUserName()
                val items = userRepo.getItems()
                val debugMode = userRepo.getDebugMode()
                this@MainViewModel.debugMode = debugMode
                val leftMode = userRepo.getLeftMode()
                val pixelFont = userRepo.getPixelFont()
                val imageDisplayMode = userRepo.getImageDisplayMode()

                val showDeletedLogs = showDeleted
                showDeleted = false

                val dateInterval = TimeUtil.calDateInterval(focusDate, dayGroup)
                val rangeStart = dateInterval[0]
                val rangeEnd = dateInterval[1]

                val rangeLogs = if (showDeletedLogs) {
                    logRepo.getDeletedLogs()
                } else if (keyword.isNotEmpty()) {
                    logRepo.searchLogs(keyword, rangeEnd)
                } else {
                    logRepo.getLogsInDateRange(rangeStart, rangeEnd)
                }

                val activeQuests = if (!showDeletedLogs && keyword.isEmpty() && focusDate == TimeUtil.getTimeInt()) {
                    logRepo.getActiveQuests()
                } else {
                    emptyList()
                }

                val logs = (rangeLogs + activeQuests)
                    .distinctBy { it.id }
                    .filter { debugMode || (it.logType != LogType.DEBUG && it.logType != LogType.ERROR) }
                    .sortedWith(compareBy({ it.buildDate }, { it.buildTime }))

                val itemAbbrMap = items.filter { it.abbr.isNotEmpty() }
                    .associate { it.abbr to it.iconEmoji }
                val labelNames = items.filter { it.type == UserItem.TYPE_LABEL }
                    .map { it.name }.toSet()

                val displayLogs = if (showDeletedLogs || keyword.isNotEmpty()) {
                    insertDateHeaders(logs)
                } else {
                    logs
                }

                MainUiState(
                    userName = userName,
                    dayGroup = dayGroup,
                    items = items,
                    logs = displayLogs,
                    itemAbbrMap = itemAbbrMap,
                    labelNames = labelNames,
                    debugMode = debugMode,
                    leftMode = leftMode,
                    pixelFont = pixelFont,
                    imageDisplayMode = imageDisplayMode,
                )
            }

            callback(state)
        }
    }

    private fun insertDateHeaders(logs: List<LogEntity>): List<LogEntity> {
        if (logs.isEmpty()) return logs
        val result = mutableListOf<LogEntity>()
        var lastDate = 0
        var dateId = -1L
        for (log in logs) {
            if (log.buildDate != lastDate) {
                lastDate = log.buildDate
                result.add(LogEntity(
                    id = dateId--,
                    logType = LogType.HINT,
                    flag0 = 999,
                    buildDate = lastDate,
                    logText = "\n${TimeUtil.dateInt2String(lastDate)}",
                ))
            }
            result.add(log)
        }
        return result
    }
}
