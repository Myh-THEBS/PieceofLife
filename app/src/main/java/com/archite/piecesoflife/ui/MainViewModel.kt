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
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val logRepo = LogRepository(AppDatabase.getInstance(application).logDao())
    private val userRepo = UserPreferencesRepository(application)

    var focusDate: Int = TimeUtil.getTimeInt()
    var keyword: String = ""
    var debugMode: Boolean = false

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
        LogItemChange.apply(deltas, mode, items)
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
        return NewDayChecker.check(logRepo, today, userName, items)
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

                val dateInterval = TimeUtil.calDateInterval(focusDate, dayGroup)
                val rangeStart = dateInterval[0]
                val rangeEnd = dateInterval[1]

                val rangeLogs = if (keyword.isNotEmpty()) {
                    logRepo.searchLogs(keyword, rangeEnd)
                } else {
                    logRepo.getLogsInDateRange(rangeStart, rangeEnd)
                }

                val activeQuests = logRepo.getActiveQuests()

                val logs = (rangeLogs + activeQuests)
                    .distinctBy { it.id }
                    .filter { debugMode || (it.logType != LogType.DEBUG && it.logType != LogType.ERROR) }
                    .sortedWith(compareBy({ it.buildDate }, { it.buildTime }))

                val itemAbbrMap = items.filter { it.abbr.isNotEmpty() }
                    .associate { it.abbr to it.iconEmoji }
                val labelNames = items.filter { it.type == UserItem.TYPE_LABEL }
                    .map { it.name }.toSet()

                val displayLogs = if (keyword.isNotEmpty()) {
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
                    logText = "\n${TimeUtil.getTimeString(TimeUtil.TIME_TYPE_DATE_WEEK)}",
                ))
            }
            result.add(log)
        }
        return result
    }
}
