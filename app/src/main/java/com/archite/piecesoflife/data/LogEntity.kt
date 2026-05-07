package com.archite.piecesoflife.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "log_data")
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "log_type")
    val logType: Int = LogType.ERROR,

    @ColumnInfo(name = "log_text")
    val logText: String = "",

    val remark: String = "",

    @ColumnInfo(name = "template_name", defaultValue = "")
    val templateName: String = "",

    @ColumnInfo(name = "build_date")
    val buildDate: Int = 0,

    @ColumnInfo(name = "build_time")
    val buildTime: Int = 0,

    @ColumnInfo(name = "change_date")
    val changeDate: Int = 0,

    @ColumnInfo(name = "change_time")
    val changeTime: Int = 0,

    @ColumnInfo(name = "flag0")
    val flag0: Int = 0,

    @ColumnInfo(name = "flag1")
    val flag1: Int = -1,

    @ColumnInfo(name = "items_json")
    val itemsJson: String = "[]",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Boolean = false
)
