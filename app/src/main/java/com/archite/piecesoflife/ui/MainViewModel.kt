package com.archite.piecesoflife.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.archite.piecesoflife.data.AppDatabase
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.data.LogRepository
import com.archite.piecesoflife.data.LogType
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
    val debugMode: Boolean = false,
    val leftMode: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val logRepo = LogRepository(AppDatabase.getInstance(application).logDao())
    private val userRepo = UserPreferencesRepository(application)

    var focusDate: Int = TimeUtil.getTimeInt()
    var keyword: String = ""

    suspend fun completeQuestSync(logId: Long, isSuccess: Boolean) {
        logRepo.completeQuest(logId, isSuccess)
    }

    suspend fun runNewDayCheck(): NewDayChecker.NewDayResult {
        return NewDayChecker.check(logRepo, userRepo)
    }

    fun refresh(callback: (MainUiState) -> Unit) {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                val dayGroup = userRepo.getDayGroup()
                val userName = userRepo.getUserName()
                val items = userRepo.getItems()
                val debugMode = userRepo.getDebugMode()
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
                    .sortedWith(compareBy({ it.buildDate }, { it.buildTime }))

                val itemAbbrMap = items.filter { it.abbr.isNotEmpty() }
                    .associate { it.abbr to it.iconEmoji }

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
                    logText = "",
                ))
            }
            result.add(log)
        }
        return result
    }
}
