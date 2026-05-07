package com.archite.piecesoflife.data

import kotlinx.coroutines.flow.Flow

class LogRepository(private val dao: LogDao) {

    suspend fun saveLog(entity: LogEntity): Long {
        val toSave = if (entity.id > 0) entity.copy(updatedAt = System.currentTimeMillis()) else entity
        return dao.insertOrUpdate(toSave)
    }

    suspend fun getLogById(id: Long): LogEntity? = dao.getById(id)

    suspend fun softDeleteLog(id: Long) = dao.softDeleteById(id)

    suspend fun restoreLog(id: Long) = dao.restoreById(id)

    suspend fun permanentlyDeleteLog(id: Long) = dao.permanentlyDeleteById(id)

    suspend fun getDeletedLogs(): List<LogEntity> = dao.getDeletedLogs()

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
