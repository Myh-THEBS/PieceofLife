package com.archite.piecesoflife.data

import kotlinx.coroutines.flow.Flow

class LogRepository(private val dao: LogDao) {

    suspend fun saveLog(entity: LogEntity): Long = dao.insertOrUpdate(entity)

    suspend fun getLogById(id: Long): LogEntity? = dao.getById(id)

    suspend fun deleteLog(id: Long) = dao.deleteById(id)

    suspend fun getLogIdsInDateRange(dateS: Int, dateE: Int): List<Long> =
        dao.getIdsByDateRange(dateS, dateE)

    suspend fun searchLogs(keyword: String, endDate: Int): List<LogEntity> =
        dao.searchByKeyword(keyword, endDate)

    suspend fun isLogInDate(date: Int): Boolean = dao.existsByDate(date)

    suspend fun getTotalLogCount(): Long = dao.getCount()

    suspend fun getActiveQuests(): List<LogEntity> = dao.getActiveQuests()

    suspend fun getAllLogs(): List<LogEntity> = dao.getAll()

    fun observeLogCountByDate(date: Int): Flow<Int> = dao.observeCountByDate(date)

    fun observeLogIdsByDateRange(dateS: Int, dateE: Int): Flow<List<Long>> =
        dao.observeIdsByDateRange(dateS, dateE)
}
