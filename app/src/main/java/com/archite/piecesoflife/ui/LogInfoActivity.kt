package com.archite.piecesoflife.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.AppDatabase
import com.archite.piecesoflife.data.ApplyMode
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.data.LogItemChange
import com.archite.piecesoflife.data.LogRepository
import com.archite.piecesoflife.data.LogType
import com.archite.piecesoflife.data.QuestFlag
import com.archite.piecesoflife.data.QuestType
import com.archite.piecesoflife.data.UserPreferencesRepository
import com.archite.piecesoflife.databinding.ActivityLogInfoBinding
import com.archite.piecesoflife.util.ImageUtil
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LogInfoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LOG_ID = "logId"
        const val EXTRA_LOG_ENTITY = "logEntity"
        const val RESULT_REVERSED = 1001
    }

    private lateinit var binding: ActivityLogInfoBinding
    private lateinit var logRepo: LogRepository
    private lateinit var userRepo: UserPreferencesRepository
    private var logEntity: LogEntity? = null
    private var currentLogId = -1L
    private var hasReversed = false

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && logEntity != null) {
            handleReupload(uri)
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null && logEntity != null) {
            handleExport(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentLogId = intent.getLongExtra(EXTRA_LOG_ID, -1L)
        logRepo = LogRepository(AppDatabase.getInstance(this).logDao())
        userRepo = UserPreferencesRepository(this)

        setupSystemBars()
        renderBackgrounds()
        renderSprites()
        bindClickEvents()
        loadLogData()
    }

    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.background_top)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.background_bottom)
    }

    private fun renderBackgrounds() {
        binding.toolbarBg.setImageDrawable(SpriteLoader.background48(index = 0, pixelScale = 5))
        binding.bottomSpacer.background = SpriteLoader.background48(index = 0, pixelScale = 5)
        binding.contentBg.setImageDrawable(SpriteLoader.background48(index = 0, pixelScale = 5))
    }

    private fun renderSprites() {
        SpriteLoader.setButton(binding.btnReturn, SpriteDef.B16.RES, SpriteDef.B16.frame(17), downFrame = SpriteDef.B16.frame(17), scale = 5)
        SpriteLoader.setButton(binding.btnCancel, SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(2), scale = 5)
        SpriteLoader.setButton(binding.btnConfirm, SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(0), scale = 5)

        SpriteLoader.setButton(binding.btnReversal, SpriteDef.B24.RES, SpriteDef.B24.frame(24), scale = 5)
        SpriteLoader.setButton(binding.btnRename, SpriteDef.B24.RES, SpriteDef.B24.frame(20), scale = 5)
        SpriteLoader.setButton(binding.btnReupload, SpriteDef.B24.RES, SpriteDef.B24.frame(26), scale = 5)
        SpriteLoader.setButton(binding.btnExport, SpriteDef.B24.RES, SpriteDef.B24.frame(28), scale = 5)
    }

    private fun bindClickEvents() {
        binding.btnReturn.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnConfirm.setOnClickListener { finish() }

        binding.btnReversal.setOnClickListener { onReversalClick() }
        binding.btnRename.setOnClickListener { onRenameClick() }
        binding.btnReupload.setOnClickListener { onReuploadClick() }
        binding.btnExport.setOnClickListener { onExportClick() }
    }

    private fun loadLogData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val entity = logRepo.getLogById(currentLogId)
            logEntity = entity
            if (entity == null) {
                withContext(Dispatchers.Main) { finish() }
                return@launch
            }
            withContext(Dispatchers.Main) {
                binding.tvLogInfo.text = buildInfoString(entity)
                if (entity.logType == LogType.QUEST &&
                    (QuestFlag.isFinished(entity.flag0) || QuestFlag.isFailed(entity.flag0))) {
                    binding.questReversalRow.visibility = View.VISIBLE
                }
                if (entity.logType == LogType.PICTURE || entity.logType == LogType.DOCUMENT) {
                    binding.fileManagementSection.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun buildInfoString(entity: LogEntity): String {
        val typeName = when (entity.logType) {
            LogType.DEFAULT -> getString(R.string.log_info_type_default)
            LogType.QUEST -> getString(R.string.log_info_type_quest)
            LogType.PICTURE -> getString(R.string.log_info_type_picture)
            LogType.DOCUMENT -> getString(R.string.log_info_type_document)
            else -> getString(R.string.log_info_type_default)
        }
        val sb = StringBuilder()
        sb.append(getString(R.string.log_info_type)).append("：$typeName\n")
        sb.append(getString(R.string.log_info_id)).append("：${entity.id}\n")

        if (entity.logType == LogType.QUEST) {
            if (QuestFlag.isFinished(entity.flag0)) {
                sb.append(getString(R.string.log_info_complete_time)).append("：${TimeUtil.dateInt2String(entity.changeDate)} ${TimeUtil.timeInt2String(entity.changeTime)}\n")
            }
            sb.append(getString(R.string.log_info_deadline)).append("：")
            if (entity.changeDate >= 30000000) {
                sb.append(getString(R.string.log_info_no_deadline))
            } else {
                sb.append("${TimeUtil.dateInt2String(entity.changeDate)} 24:00")
            }
            sb.append("\n")
            sb.append(getString(R.string.log_info_quest_type)).append("：")
            sb.append(if (QuestFlag.isMinusType(entity.flag0)) getString(R.string.log_info_penalty_type) else getString(R.string.log_info_default_type))
            sb.append("\n")
            sb.append(getString(R.string.log_info_quest_status)).append("：")
            when {
                QuestFlag.isFinished(entity.flag0) -> sb.append(getString(R.string.log_info_completed))
                QuestFlag.isFailed(entity.flag0) -> sb.append(getString(R.string.log_info_failed_status))
                else -> sb.append(getString(R.string.log_info_long_term))
            }
            sb.append("\n")
            sb.append(getString(R.string.log_info_repeat)).append("：${buildRepeatString(entity)}")
            sb.append("\n")
        } else {
            sb.append(getString(R.string.log_info_create_time)).append("：${TimeUtil.dateInt2String(entity.buildDate)} ${if (entity.buildTime > 0) TimeUtil.timeInt2String(entity.buildTime) else ""}\n")
            sb.append(getString(R.string.log_info_modify_time)).append("：${TimeUtil.dateInt2String(entity.changeDate)} ${TimeUtil.timeInt2String(entity.changeTime)}\n")
        }
        sb.append(getString(R.string.log_info_word_count)).append("：${entity.logText.length}\n")

        if (entity.remark.isNotEmpty()) {
            val file = File(filesDir, entity.remark)
            if (file.exists()) {
                sb.append(getString(R.string.log_info_file_size)).append("：${formatFileSize(file.length())}\n")
                val displayName = extractDisplayName(entity.remark)
                sb.append("文件名：${displayName}\n")
            }
        }
        return sb.toString()
    }

    private fun buildRepeatString(entity: LogEntity): String {
        return when (entity.flag1) {
            QuestType.DAY -> "每天重复"
            QuestType.WEEK -> "每周${getDayOfWeek(entity.changeDate)}重复"
            QuestType.MONTH -> "每月${entity.changeDate % 100}日重复"
            else -> getString(R.string.log_info_no_repeat)
        }
    }

    private fun getDayOfWeek(dateInt: Int): String {
        val dayOfWeekNames = arrayOf("日", "一", "二", "三", "四", "五", "六")
        val calendar = java.util.Calendar.getInstance()
        calendar.set(dateInt / 10000, ((dateInt / 100) % 100) - 1, dateInt % 100)
        return dayOfWeekNames[calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1]
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))}MB"
        }
    }

    private fun extractDisplayName(remark: String): String {
        if (remark.isEmpty()) return "未知文件"
        val fileName = File(remark).name
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val ext = fileName.substringAfterLast(".", "")
        val parts = nameWithoutExt.split("_")
        val baseName = if (parts.size >= 3) {
            parts.drop(2).joinToString("_")
        } else {
            nameWithoutExt
        }
        return if (ext.isNotEmpty()) "$baseName.$ext" else baseName
    }

    private fun onReversalClick() {
        val entity = logEntity ?: return
        PixelDialog(this)
            .setType(PixelDialog.DialogType.WARN)
            .setTitle(getString(R.string.log_info_quest_reversal))
            .setMessage(getString(R.string.log_info_confirm_reversal))
            .setButtons(PixelDialog.ButtonMode.DUAL_CONFIRM_CANCEL)
            .onConfirm {
                lifecycleScope.launch(Dispatchers.IO) {
                    val items = userRepo.getItems().toMutableList()
                    val itemsBefore = items.toList()
                    val deltas = LogItemChange.fromJson(entity.itemsJson)
                    val mode = ApplyMode.fromQuest(LogType.QUEST, entity.flag0)
                    LogItemChange.apply(deltas, mode.inverse(), items)
                    val levelUpMsgs = LogItemChange.detectSkillLevelUp(itemsBefore, items)
                    val now = TimeUtil.getTimeInt()
                    val nowTime = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_SECOND)
                    for (msg in levelUpMsgs) {
                        logRepo.saveLog(LogEntity(
                            logType = LogType.HINT,
                            logText = msg,
                            buildDate = now,
                            buildTime = nowTime,
                            changeDate = now,
                            changeTime = nowTime,
                        ))
                    }
                    userRepo.setItems(items)
                    logRepo.completeQuest(entity.id, !QuestFlag.isFinished(entity.flag0))
                    hasReversed = true
                    withContext(Dispatchers.Main) {
                        setResult(RESULT_REVERSED, Intent().apply {
                            putExtra(EXTRA_LOG_ID, entity.id)
                        })
                        finish()
                    }
                }
            }
            .show()
    }

    private fun onRenameClick() {
        val entity = logEntity ?: return
        val currentName = File(entity.remark).nameWithoutExtension
        val nameWithoutPrefix = currentName.substringAfter("_").substringAfter("_")
        InputDialog(this)
            .setMode(InputDialog.InputMode.FILENAME)
            .setTitle(getString(R.string.log_info_rename))
            .setInitialText(nameWithoutPrefix)
            .onConfirm { newName ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val newRemark = ImageUtil.renameFile(this@LogInfoActivity, entity.remark, newName)
                    if (newRemark != null) {
                        val updated = entity.copy(remark = newRemark)
                        logRepo.saveLog(updated)
                        logEntity = updated
                        withContext(Dispatchers.Main) {
                            binding.tvLogInfo.text = buildInfoString(updated)
                            PixelDialog(this@LogInfoActivity)
                                .setType(PixelDialog.DialogType.INFO)
                                .setTitle(getString(R.string.log_info_success))
                                .setMessage(getString(R.string.log_info_success))
                                .setButtons(PixelDialog.ButtonMode.SINGLE_KNOWN)
                                .show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            PixelDialog(this@LogInfoActivity)
                                .setType(PixelDialog.DialogType.ERROR)
                                .setTitle(getString(R.string.log_info_failed))
                                .setMessage(getString(R.string.log_info_failed))
                                .setButtons(PixelDialog.ButtonMode.SINGLE_CLOSE)
                                .show()
                        }
                    }
                }
            }
            .show()
    }

    private fun onReuploadClick() {
        val entity = logEntity ?: return
        PixelDialog(this)
            .setType(PixelDialog.DialogType.ERROR)
            .setTitle(getString(R.string.log_info_reupload))
            .setMessage(getString(R.string.log_info_confirm_upload))
            .setButtons(PixelDialog.ButtonMode.DUAL_IMPORT_CONFIRM)
            .onConfirm {
                val mimeType = if (entity.logType == LogType.PICTURE) "image/*" else "text/*"
                filePickerLauncher.launch(mimeType)
            }
            .show()
    }

    private fun handleReupload(uri: android.net.Uri) {
        val entity = logEntity ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val newFileName = if (entity.logType == LogType.PICTURE) {
                    ImageUtil.copyImageFromUri(this@LogInfoActivity, uri)
                } else {
                    ImageUtil.copyDocumentFromUri(this@LogInfoActivity, uri)
                }
                if (newFileName != null) {
                    ImageUtil.deleteFile(this@LogInfoActivity, entity.remark)
                    val subDir = if (entity.logType == LogType.PICTURE) "images" else "documents"
                    val newRemark = "$subDir/$newFileName"
                    val newLogText = if (entity.logType == LogType.DOCUMENT) {
                        val content = ImageUtil.readDocumentContent(this@LogInfoActivity, newFileName) ?: ""
                        ImageUtil.extractPreview(content)
                    } else {
                        entity.logText
                    }
                    val updated = entity.copy(remark = newRemark, logText = newLogText)
                    logRepo.saveLog(updated)
                    logEntity = updated
                    if (entity.logType == LogType.PICTURE) {
                        ImageUtil.generateThumbnail(this@LogInfoActivity, newFileName)
                    }
                    withContext(Dispatchers.Main) {
                        binding.tvLogInfo.text = buildInfoString(updated)
                        PixelDialog(this@LogInfoActivity)
                            .setType(PixelDialog.DialogType.INFO)
                            .setTitle(getString(R.string.log_info_success))
                            .setMessage(getString(R.string.log_info_success))
                            .setButtons(PixelDialog.ButtonMode.SINGLE_KNOWN)
                            .show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        PixelDialog(this@LogInfoActivity)
                            .setType(PixelDialog.DialogType.ERROR)
                            .setTitle(getString(R.string.log_info_failed))
                            .setMessage(getString(R.string.log_info_failed))
                            .setButtons(PixelDialog.ButtonMode.SINGLE_CLOSE)
                            .show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    PixelDialog(this@LogInfoActivity)
                        .setType(PixelDialog.DialogType.ERROR)
                        .setTitle(getString(R.string.log_info_failed))
                        .setMessage(getString(R.string.log_info_failed))
                        .setButtons(PixelDialog.ButtonMode.SINGLE_CLOSE)
                        .show()
                }
            }
        }
    }

    private fun onExportClick() {
        val entity = logEntity ?: return
        val file = File(filesDir, entity.remark)
        if (!file.exists()) {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.ERROR)
                .setTitle(getString(R.string.log_info_failed))
                .setMessage(getString(R.string.log_info_failed))
                .setButtons(PixelDialog.ButtonMode.SINGLE_CLOSE)
                .show()
            return
        }
        val displayName = extractDisplayNameFull(entity.remark)
        exportLauncher.launch(displayName)
    }

    private fun handleExport(uri: android.net.Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(filesDir, logEntity?.remark ?: return@launch)
                if (!file.exists()) return@launch
                val bytes = file.readBytes()
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                withContext(Dispatchers.Main) {
                    PixelDialog(this@LogInfoActivity)
                        .setType(PixelDialog.DialogType.INFO)
                        .setTitle(getString(R.string.log_info_success))
                        .setMessage(getString(R.string.log_info_success))
                        .setButtons(PixelDialog.ButtonMode.SINGLE_KNOWN)
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    PixelDialog(this@LogInfoActivity)
                        .setType(PixelDialog.DialogType.ERROR)
                        .setTitle(getString(R.string.log_info_failed))
                        .setMessage(getString(R.string.log_info_failed))
                        .setButtons(PixelDialog.ButtonMode.SINGLE_CLOSE)
                        .show()
                }
            }
        }
    }

    private fun extractDisplayNameFull(remark: String): String {
        if (remark.isEmpty()) return "untitled"
        val fileName = File(remark).name
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val ext = fileName.substringAfterLast(".", "")
        val parts = nameWithoutExt.split("_")
        val baseName = if (parts.size >= 3) parts.drop(2).joinToString("_") else nameWithoutExt
        return if (ext.isNotEmpty()) "$baseName.$ext" else baseName
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
