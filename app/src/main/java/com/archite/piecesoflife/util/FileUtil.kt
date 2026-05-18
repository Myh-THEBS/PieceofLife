package com.archite.piecesoflife.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.archite.piecesoflife.data.AppDatabase
import com.archite.piecesoflife.data.UserItem
import com.archite.piecesoflife.data.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileUtil {

    private const val BACKUP_FILENAME = "pieces_of_life.db"
    private const val MANIFEST_NAME = "manifest.json"

    /**
     * 使用 SAF 将完整备份导出到用户选择的位置。
     *
     * 备份包为 ZIP 格式，后缀名建议为 `.piecesbackup`，包含：
     * - manifest.json：所有用户偏好（JSON，可直接编辑）+ 元信息
     * - pieces_of_life.db：Room 数据库（导出前执行 WAL checkpoint）
     *
     * 设计考量：用户配置写入 JSON 而非二进制格式，便于用户直接编辑 manifest.json
     * 修改配置（如 UUID、昵称、属性值），导入时通过 DataStore API 写回。
     */
    suspend fun exportToUri(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkpointWal(context)
            val dbFile = context.getDatabasePath(BACKUP_FILENAME)
            val prefRepo = UserPreferencesRepository(context)
            val userId = prefRepo.getOrCreateUserId()

            val userName = prefRepo.getUserName()
            val debugMode = prefRepo.getDebugMode()
            val lastDate = prefRepo.getLastDate()
            val dayGroup = prefRepo.getDayGroup()
            val userAvatar = prefRepo.getUserAvatar()
            val items = prefRepo.getItems()

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zip ->
                    val itemsArr = JSONArray()
                    for (item in items) {
                        itemsArr.put(JSONObject().apply {
                            put("name", item.name)
                            put("type", item.type)
                            put("iconEmoji", item.iconEmoji)
                            put("value", item.value)
                            put("abbr", item.abbr)
                            put("rule", item.rule)
                            put("levelExp", item.levelExp)
                            put("priority", item.priority)
                        })
                    }

                    val manifest = JSONObject().apply {
                        put("app_version", "1.3")
                        put("user_uuid", userId)
                        put("created_at", System.currentTimeMillis())
                        put("schema_version", 2)
                        put("preferences", JSONObject().apply {
                            put("user_name", userName)
                            put("debug_mode", debugMode)
                            put("last_date", lastDate)
                            put("day_group", dayGroup)
                            put("user_avatar", userAvatar)
                            put("items", itemsArr)
                        })
                    }

                    zip.putNextEntry(ZipEntry(MANIFEST_NAME))
                    zip.write(manifest.toString(2).toByteArray())
                    zip.closeEntry()

                    if (dbFile.exists()) {
                        zip.putNextEntry(ZipEntry(BACKUP_FILENAME))
                        FileInputStream(dbFile).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 使用 SAF 从用户选择的备份文件恢复数据。
     *
     * 解析 ZIP 包：
     * 1. 读取 manifest.json，校验 JSON 合法性和 UUID 格式
     * 2. 覆盖恢复 pieces_of_life.db
     * 3. 通过 DataStore API 写回所有偏好（非文件覆盖，保证格式正确）
     *
     * 如果 manifest.json 解析失败或 UUID 格式不合法，拒绝导入并返回失败原因。
     */
    suspend fun importFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            var dbRestored = false
            var manifestText: String? = null

            AppDatabase.getInstance(context).close()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == MANIFEST_NAME -> {
                                manifestText = BufferedReader(InputStreamReader(zip)).readText()
                            }
                            entry.name == BACKUP_FILENAME -> {
                                context.getDatabasePath(BACKUP_FILENAME).parentFile?.mkdirs()
                                FileOutputStream(context.getDatabasePath(BACKUP_FILENAME)).use { zip.copyTo(it) }
                                dbRestored = true
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            if (manifestText == null) {
                return@withContext Result.failure(IllegalArgumentException("备份包中未找到 manifest.json"))
            }

            val manifest = try {
                JSONObject(manifestText)
            } catch (e: Exception) {
                return@withContext Result.failure(IllegalArgumentException("manifest.json 格式错误，请检查 JSON 语法"))
            }

            val uuid = manifest.optString("user_uuid", "")
            if (!isValidUuid(uuid)) {
                return@withContext Result.failure(
                    IllegalArgumentException("UUID 格式不正确（应为 36 位含连字符），当前值: $uuid")
                )
            }

            if (dbRestored) {
                resetAutoIncrement(context)

                val prefRepo = UserPreferencesRepository(context)
                val prefs = manifest.optJSONObject("preferences")
                if (prefs != null) {
                    prefRepo.setUserUuid(uuid)
                    prefRepo.setUserName(prefs.optString("user_name", ""))
                    prefRepo.setDebugMode(prefs.optBoolean("debug_mode", false))
                    prefRepo.setLastDate(prefs.optInt("last_date", 19981228))
                    prefRepo.setDayGroup(prefs.optInt("day_group", 2))
                    prefRepo.setUserAvatar(prefs.optInt("user_avatar", 0))

                    val itemsArr = prefs.optJSONArray("items")
                    if (itemsArr != null) {
                        val items = mutableListOf<UserItem>()
                        for (i in 0 until itemsArr.length()) {
                            val obj = itemsArr.getJSONObject(i)
                            items.add(UserItem(
                                name = obj.optString("name", ""),
                                type = obj.optString("type", UserItem.TYPE_ATTRIBUTES),
                                iconEmoji = obj.optString("iconEmoji", if (obj.has("icon")) "\u2699" else "⚙"),
                                value = obj.optInt("value", 0),
                                abbr = obj.optString("abbr", ""),
                                rule = obj.optString("rule", ""),
                                levelExp = obj.optInt("levelExp", 1000),
                                priority = obj.optInt("priority", 0),
                            ))
                        }
                        if (items.isNotEmpty()) {
                            prefRepo.setItems(items)
                        }
                    }
                }
            }

            Result.success("恢复成功")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 自动备份到 APP 的外部私有存储目录。
     *
     * 保存到 `ExternalFilesDir("Backup")`，每日首次启动时由 PieceOfLifeApp 调用。
     * 此备份通过文件复制（非 ZIP）保存 Room 数据库和 DataStore 文件，
     * 用户不可直接访问（需 USB 调试），仅用于灾难恢复。
     *
     * @param context 上下文
     * @return 0=成功，-1=失败
     */
    suspend fun saveAutoBackup(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            checkpointWal(context)
            val dbFile = context.getDatabasePath(BACKUP_FILENAME)
            val dsFile = getDataStoreFile(context)
            if (!dbFile.exists() || !dsFile.exists()) return@withContext -1

            val backupDir = context.getExternalFilesDir("Backup") ?: return@withContext -1
            copyFile(dbFile, File(backupDir, BACKUP_FILENAME))
            copyFile(dsFile, File(backupDir, "user_preferences.preferences_pb"))
            0
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * 检查 `ExternalFilesDir("Backup")` 中是否存在自动备份文件。
     *
     * @param context 上下文
     * @return true=自动备份文件存在
     */
    fun autoBackupExists(context: Context): Boolean {
        val backupDir = context.getExternalFilesDir("Backup") ?: return false
        return File(backupDir, BACKUP_FILENAME).exists()
    }

    /**
     * 执行 SQLite WAL checkpoint，将 WAL 文件中的变更合并到主 .db 文件。
     * Room 默认启用 WAL 模式，备份前需确保 .db 文件包含所有最新数据。
     * 静默处理异常，不中断备份流程。
     */
    private fun checkpointWal(context: Context) {
        try {
            AppDatabase.getInstance(context).openHelper.writableDatabase
                .execSQL("PRAGMA wal_checkpoint(FULL)")
        } catch (_: Exception) { }
    }

    /**
     * 重置 SQLite 自增计数器，使下一行 id 从当前 MAX(id)+1 开始。
     * 导入旧备份后调用，防止自增 id 跳跃。
     */
    private fun resetAutoIncrement(context: Context) {
        try {
            SQLiteDatabase.openDatabase(
                context.getDatabasePath(BACKUP_FILENAME).absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                db.execSQL("DELETE FROM sqlite_sequence WHERE name='log_data'")
            }
        } catch (_: Exception) { }
    }

    /**
     * 校验 UUID 字符串是否为标准的 36 位格式（含连字符）。
     */
    private fun isValidUuid(uuid: String): Boolean {
        return try {
            UUID.fromString(uuid)
            uuid.length == 36
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 获取 DataStore 文件在 APP 内部存储中的绝对路径。
     */
    private fun getDataStoreFile(context: Context): File =
        File(context.filesDir, "datastore/user_preferences.preferences_pb")

    /**
     * 复制文件（使用 FileChannel 零拷贝，效率高于流式复制）。
     *
     * @param source 源文件
     * @param dest 目标文件（父目录不存在时自动创建）
     * @return true=复制成功
     */
    private fun copyFile(source: File, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            FileInputStream(source).use { input ->
                FileOutputStream(dest).use { output ->
                    input.channel.transferTo(0, input.channel.size(), output.channel)
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }
}
