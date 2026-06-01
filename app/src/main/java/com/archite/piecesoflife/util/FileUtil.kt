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

fun interface FileProgressCallback {
    fun onProgress(current: Int, total: Int, info: String)
}

object FileUtil {

    private const val BACKUP_FILENAME = "pieces_of_life.db"
    private const val MANIFEST_NAME = "manifest.json"
    private const val IMAGES_DIR = "images/"
    private const val DOCUMENTS_DIR = "documents/"

    suspend fun exportToUri(
        context: Context,
        uri: Uri,
        progress: FileProgressCallback? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkpointWal(context)
            val dbFile = context.getDatabasePath(BACKUP_FILENAME)
            val imagesDir = ImageUtil.getImagesDir(context)
            val documentsDir = ImageUtil.getDocumentsDir(context)
            val prefRepo = UserPreferencesRepository(context)
            val userId = prefRepo.getOrCreateUserId()

            val userName = prefRepo.getUserName()
            val debugMode = prefRepo.getDebugMode()
            val lastDate = prefRepo.getLastDate()
            val dayGroup = prefRepo.getDayGroup()
            val questReminder = prefRepo.getQuestReminderEnabled()
            val reminderTime = prefRepo.getReminderTime()
            val pixelFont = prefRepo.getPixelFont()
            val imageDisplayMode = prefRepo.getImageDisplayMode()
            val leftMode = prefRepo.getLeftMode()
            val items = prefRepo.getItems()

            // 统计需要打包的文件
            val imageFiles = imagesDir.listFiles()?.filter { it.isFile } ?: emptyList()
            val docFiles = documentsDir.listFiles()?.filter { it.isFile } ?: emptyList()
            val totalFiles = 1 + (if (dbFile.exists()) 1 else 0) + imageFiles.size + docFiles.size
            var processed = 0

            progress?.onProgress(processed, totalFiles, "正在创建备份包...")

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zip ->
                    // manifest.json
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
                        put("app_version", "1.4")
                        put("user_uuid", userId)
                        put("created_at", System.currentTimeMillis())
                        put("schema_version", 4)
                        put("preferences", JSONObject().apply {
                            put("user_name", userName)
                            put("debug_mode", debugMode)
                            put("last_date", lastDate)
                            put("day_group", dayGroup)
                            put("quest_reminder", questReminder)
                            put("reminder_time", reminderTime)
                            put("pixel_font", pixelFont)
                            put("image_display_mode", imageDisplayMode)
                            put("left_mode", leftMode)
                            put("items", itemsArr)
                            put("size_list", JSONArray(prefRepo.getSizeList()))
                            put("color_list", JSONArray(prefRepo.getColorList()))
                        })
                        put("file_manifest", JSONObject().apply {
                            put("total_images", imageFiles.size)
                            put("total_docs", docFiles.size)
                            put("exported_images", imageFiles.size)
                            put("exported_docs", docFiles.size)
                        })
                    }

                    zip.putNextEntry(ZipEntry(MANIFEST_NAME))
                    zip.write(manifest.toString(2).toByteArray())
                    zip.closeEntry()
                    processed++
                    progress?.onProgress(processed, totalFiles, "正在备份数据库...")

                    // 数据库
                    if (dbFile.exists()) {
                        zip.putNextEntry(ZipEntry(BACKUP_FILENAME))
                        FileInputStream(dbFile).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                    processed++
                    progress?.onProgress(processed, totalFiles, "正在备份图片文件...")

                    // 图片文件
                    for (imgFile in imageFiles) {
                        zip.putNextEntry(ZipEntry("$IMAGES_DIR${imgFile.name}"))
                        FileInputStream(imgFile).use { it.copyTo(zip) }
                        zip.closeEntry()
                        processed++
                        progress?.onProgress(processed, totalFiles, "图片: ${imgFile.name}")
                    }

                    progress?.onProgress(processed, totalFiles, "正在备份文档文件...")

                    // 文档文件
                    for (docFile in docFiles) {
                        zip.putNextEntry(ZipEntry("$DOCUMENTS_DIR${docFile.name}"))
                        FileInputStream(docFile).use { it.copyTo(zip) }
                        zip.closeEntry()
                        processed++
                        progress?.onProgress(processed, totalFiles, "文档: ${docFile.name}")
                    }
                }
            }
            progress?.onProgress(totalFiles, totalFiles, "导出完成")
            Result.success(Unit)
        } catch (e: Exception) {
            progress?.onProgress(0, 0, "导出失败: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        progress: FileProgressCallback? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            var dbRestored = false
            var manifestText: String? = null
            val imagesDir = ImageUtil.getImagesDir(context)
            val documentsDir = ImageUtil.getDocumentsDir(context)
            val missingImages = mutableListOf<String>()
            val missingDocs = mutableListOf<String>()
            val entries = mutableListOf<String>()

            progress?.onProgress(0, 0, "正在读取备份包...")

            AppDatabase.getInstance(context).close()

            // 先遍历 ZIP 收集所有 entry 名称
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        entries.add(entry.name)
                        entry = zip.nextEntry
                    }
                }
            }

            val totalSteps = entries.size
            var done = 0

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        when {
                            name == MANIFEST_NAME -> {
                                manifestText = BufferedReader(InputStreamReader(zip)).readText()
                                progress?.onProgress(++done, totalSteps, "解析 manifest...")
                            }
                            name == BACKUP_FILENAME -> {
                                context.getDatabasePath(BACKUP_FILENAME).parentFile?.mkdirs()
                                FileOutputStream(context.getDatabasePath(BACKUP_FILENAME)).use { zip.copyTo(it) }
                                dbRestored = true
                                progress?.onProgress(++done, totalSteps, "恢复数据库...")
                            }
                            name.startsWith(IMAGES_DIR) -> {
                                val fileName = name.removePrefix(IMAGES_DIR)
                                val targetFile = File(imagesDir, fileName)
                                try {
                                    targetFile.parentFile?.mkdirs()
                                    FileOutputStream(targetFile).use { zip.copyTo(it) }
                                } catch (_: Exception) {
                                    missingImages.add(fileName)
                                }
                                progress?.onProgress(++done, totalSteps, "恢复图片: $fileName")
                            }
                            name.startsWith(DOCUMENTS_DIR) -> {
                                val fileName = name.removePrefix(DOCUMENTS_DIR)
                                val targetFile = File(documentsDir, fileName)
                                try {
                                    targetFile.parentFile?.mkdirs()
                                    FileOutputStream(targetFile).use { zip.copyTo(it) }
                                } catch (_: Exception) {
                                    missingDocs.add(fileName)
                                }
                                progress?.onProgress(++done, totalSteps, "恢复文档: $fileName")
                            }
                            else -> {
                                progress?.onProgress(++done, totalSteps, "跳过: $name")
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
                return@withContext Result.failure(IllegalArgumentException("manifest.json 格式错误"))
            }

            val uuid = manifest.optString("user_uuid", "")
            if (!isValidUuid(uuid)) {
                return@withContext Result.failure(
                    IllegalArgumentException("UUID 格式不正确，当前值: $uuid")
                )
            }

            progress?.onProgress(done, totalSteps, "恢复用户配置...")

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
                    prefRepo.setQuestReminderEnabled(prefs.optBoolean("quest_reminder", false))
                    prefRepo.setReminderTime(prefs.optInt("reminder_time", 2200))
                    prefRepo.setPixelFont(prefs.optBoolean("pixel_font", false))
                    prefRepo.setImageDisplayMode(prefs.optBoolean("image_display_mode", true))
                    prefRepo.setLeftMode(prefs.optBoolean("left_mode", false))

                    val itemsArr = prefs.optJSONArray("items")
                    if (itemsArr != null) {
                        val items = mutableListOf<UserItem>()
                        for (i in 0 until itemsArr.length()) {
                            val obj = itemsArr.getJSONObject(i)
                            items.add(UserItem(
                                name = obj.optString("name", ""),
                                type = obj.optString("type", UserItem.TYPE_ATTRIBUTES),
                                iconEmoji = obj.optString("iconEmoji", ""),
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

                    val sizeArr = prefs.optJSONArray("size_list")
                    if (sizeArr != null) {
                        val sizes = List(sizeArr.length()) { sizeArr.getInt(it) }
                        if (sizes.isNotEmpty()) prefRepo.setSizeList(sizes)
                    }

                    val colorArr = prefs.optJSONArray("color_list")
                    if (colorArr != null) {
                        val colors = List(colorArr.length()) { colorArr.getString(it) }
                        if (colors.isNotEmpty()) prefRepo.setColorList(colors)
                    }
                }
            }

            // 记录缺失文件
            val missingParts = mutableListOf<String>()
            if (missingImages.isNotEmpty()) {
                missingParts.add("${missingImages.size}张图片")
            }
            if (missingDocs.isNotEmpty()) {
                missingParts.add("${missingDocs.size}个文档")
            }

            val resultMsg = buildString {
                append("恢复成功")
                if (missingParts.isNotEmpty()) {
                    append("，但有${missingParts.joinToString("、")}文件缺失")
                }
            }

            if (missingParts.isNotEmpty()) {
                val now = TimeUtil.getTimeInt()
                val nowTime = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_SECOND)
                val logRepo = com.archite.piecesoflife.data.LogRepository(
                    com.archite.piecesoflife.data.AppDatabase.getInstance(context).logDao()
                )
                logRepo.saveLog(com.archite.piecesoflife.data.LogEntity(
                    logType = com.archite.piecesoflife.data.LogType.HINT,
                    logText = "⚠️ 导入备份时发现 ${missingParts.joinToString("、")} 文件缺失，对应日志将显示为空",
                    buildDate = now,
                    buildTime = nowTime,
                    changeDate = now,
                    changeTime = nowTime,
                ))
            }

            progress?.onProgress(totalSteps, totalSteps, resultMsg)
            Result.success(resultMsg)
        } catch (e: Exception) {
            progress?.onProgress(0, 0, "导入失败: ${e.message}")
            Result.failure(e)
        }
    }

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
        } catch (_: Exception) {
            -1
        }
    }

    fun autoBackupExists(context: Context): Boolean {
        val backupDir = context.getExternalFilesDir("Backup") ?: return false
        return File(backupDir, BACKUP_FILENAME).exists()
    }

    private fun checkpointWal(context: Context) {
        try {
            AppDatabase.getInstance(context).openHelper.writableDatabase
                .execSQL("PRAGMA wal_checkpoint(FULL)")
        } catch (_: Exception) { }
    }

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

    private fun isValidUuid(uuid: String): Boolean {
        return try {
            UUID.fromString(uuid)
            uuid.length == 36
        } catch (_: Exception) {
            false
        }
    }

    private fun getDataStoreFile(context: Context): File =
        File(context.filesDir, "datastore/user_preferences.preferences_pb")

    private fun copyFile(source: File, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            FileInputStream(source).use { input ->
                FileOutputStream(dest).use { output ->
                    input.channel.transferTo(0, input.channel.size(), output.channel)
                }
            }
            true
        } catch (_: IOException) {
            false
        }
    }
}
