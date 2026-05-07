package com.archite.piecesoflife.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LogEntity): Long

    @Update
    suspend fun update(entity: LogEntity)

    @Delete
    suspend fun delete(entity: LogEntity)

    @Transaction
    suspend fun insertOrUpdate(entity: LogEntity): Long {
        return if (entity.id <= 0) {
            insert(entity)
        } else {
            update(entity)
            entity.id
        }
    }

    @Query("SELECT * FROM log_data WHERE id = :id AND is_deleted = 0")
    suspend fun getById(id: Long): LogEntity?

    /** 软删除：标记为已删除，数据保留在数据库中 */
    @Query("UPDATE log_data SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDeleteById(id: Long, now: Long = System.currentTimeMillis())

    /** 恢复软删除的日志 */
    @Query("UPDATE log_data SET is_deleted = 0, updated_at = :now WHERE id = :id")
    suspend fun restoreById(id: Long, now: Long = System.currentTimeMillis())

    /** 物理删除（永久清除） */
    @Query("DELETE FROM log_data WHERE id = :id")
    suspend fun permanentlyDeleteById(id: Long)

    @Query("""
        SELECT id FROM log_data 
        WHERE is_deleted = 0
          AND ((build_date BETWEEN :dateS AND :dateE) 
           OR (log_type = :questType))
        ORDER BY build_date ASC, build_time ASC
    """)
    suspend fun getIdsByDateRange(dateS: Int, dateE: Int, questType: Int = LogType.QUEST): List<Long>

    @Query("""
        SELECT * FROM log_data 
        WHERE is_deleted = 0
          AND build_date <= :endDate 
          AND log_type IN (:searchTypes)
          AND log_text LIKE '%' || :keyword || '%'
        ORDER BY build_date DESC, build_time DESC
        LIMIT :limit
    """)
    suspend fun searchByKeyword(
        keyword: String,
        endDate: Int,
        searchTypes: List<Int> = listOf(LogType.DEFAULT, LogType.QUEST),
        limit: Int = 100
    ): List<LogEntity>

    @Query("SELECT COUNT(*) FROM log_data WHERE is_deleted = 0 AND build_date = :date")
    suspend fun existsByDate(date: Int): Boolean

    @Query("SELECT COUNT(*) FROM log_data WHERE is_deleted = 0")
    suspend fun getCount(): Long

    @Query("""
        SELECT * FROM log_data 
        WHERE is_deleted = 0
          AND log_type = :questType 
          AND (flag0 = :unfinishedDefault OR flag0 = :unfinishedMinus)
        ORDER BY change_date ASC, change_time ASC
    """)
    suspend fun getActiveQuests(
        questType: Int = LogType.QUEST,
        unfinishedDefault: Int = QuestFlag.DEFAULT_UNFINISHED,
        unfinishedMinus: Int = QuestFlag.MINUS_UNFINISHED
    ): List<LogEntity>

    @Query("SELECT * FROM log_data WHERE is_deleted = 0 ORDER BY build_date ASC, build_time ASC")
    suspend fun getAll(): List<LogEntity>

    @Query("SELECT * FROM log_data WHERE is_deleted = 1 ORDER BY updated_at DESC")
    suspend fun getDeletedLogs(): List<LogEntity>

    @Query("SELECT COUNT(*) FROM log_data WHERE is_deleted = 0 AND build_date = :date")
    fun observeCountByDate(date: Int): Flow<Int>

    @Query("""
        SELECT id FROM log_data 
        WHERE is_deleted = 0
          AND ((build_date BETWEEN :dateS AND :dateE) 
           OR (log_type = :questType))
        ORDER BY build_date ASC, build_time ASC
    """)
    fun observeIdsByDateRange(dateS: Int, dateE: Int, questType: Int = LogType.QUEST): Flow<List<Long>>
}
