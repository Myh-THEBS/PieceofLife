package com.archite.piecesoflife.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.AppDatabase
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.data.LogRepository
import com.archite.piecesoflife.data.LogType
import com.archite.piecesoflife.data.UserPreferencesRepository
import com.archite.piecesoflife.databinding.ActivityAppSettingBinding
import com.archite.piecesoflife.ui.NumberPickerDialog.PickerMode
import com.archite.piecesoflife.util.FileUtil
import com.archite.piecesoflife.util.QuestNotificationScheduler
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppSettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppSettingBinding
    private lateinit var userRepo: UserPreferencesRepository
    private lateinit var logRepo: LogRepository

    private var currentDebug = false
    private var currentLeft = false
    private var currentDayGroup = 2
    private var currentPixelFont = true
    private var currentQuestReminder = false
    private var currentReminderTime = 2200
    private var currentImageDisplayMode = true

    private var initialDebug = false
    private var initialLeft = false
    private var initialDayGroup = 2
    private var initialPixelFont = false
    private var initialQuestReminder = false
    private var initialReminderTime = 2200
    private var initialImageDisplayMode = true

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val progressDialog = ProgressDialog(this)
            .setTitle("导出数据")
            .setMessage("正在准备导出...")
            .setCancellable(false)
        progressDialog.show()

        lifecycleScope.launch {
            FileUtil.exportToUri(this@AppSettingActivity, uri) { current, total, info ->
                (this@AppSettingActivity as? android.app.Activity)?.runOnUiThread {
                    if (progressDialog.isShowing) {
                        if (total > 0) {
                            progressDialog.updateProgress(current, total, info)
                        } else {
                            progressDialog.setIndeterminate(info)
                        }
                    }
                }
            }.let { result ->
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    if (result.isSuccess) {
                        PixelDialog(this@AppSettingActivity)
                            .setType(PixelDialog.DialogType.INFO)
                            .setButtons(PixelDialog.ButtonMode.SINGLE_CLOSE)
                            .setTitle("导出成功")
                            .setMessage("数据已成功导出到所选位置。")
                            .show()
                    } else {
                        PixelDialog(this@AppSettingActivity)
                            .setType(PixelDialog.DialogType.ERROR)
                            .setButtons(PixelDialog.ButtonMode.SINGLE_CLOSE)
                            .setTitle("导出失败")
                            .setMessage(result.exceptionOrNull()?.message ?: "未知错误")
                            .show()
                    }
                }
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val progressDialog = ProgressDialog(this)
            .setTitle("导入数据")
            .setMessage("正在准备导入...")
            .setCancellable(false)
        progressDialog.show()

        lifecycleScope.launch {
            FileUtil.importFromUri(this@AppSettingActivity, uri) { current, total, info ->
                (this@AppSettingActivity as? android.app.Activity)?.runOnUiThread {
                    if (progressDialog.isShowing) {
                        if (total > 0) {
                            progressDialog.updateProgress(current, total, info)
                        } else {
                            progressDialog.setIndeterminate(info)
                        }
                    }
                }
            }.let { result ->
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    if (result.isSuccess) {
                        Toast.makeText(this@AppSettingActivity, "恢复成功，正在重启...", Toast.LENGTH_SHORT).show()
                        android.os.Handler(mainLooper).postDelayed({
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }, 500)
                    } else {
                        PixelDialog(this@AppSettingActivity)
                            .setType(PixelDialog.DialogType.ERROR)
                            .setButtons(PixelDialog.ButtonMode.SINGLE_CLOSE)
                            .setTitle("导入失败")
                            .setMessage(result.exceptionOrNull()?.message ?: "未知错误")
                            .show()
                    }
                }
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            enableQuestReminder()
        } else {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.INFO)
                .setButtons(PixelDialog.ButtonMode.SINGLE_KNOWN)
                .setTitle("权限被拒绝")
                .setMessage("通知权限已被拒绝，开启任务提醒后无法发送通知。\n\n如需授权，请前往 系统设置 → 应用 → PiecesOfLife → 权限 → 通知 手动开启。")
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()
        renderBackgrounds()
        initData()
        renderSprites()
        bindClickEvents()
    }

    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.background_top)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.background_bottom)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun renderBackgrounds() {
        binding.toolbarBg.setImageDrawable(SpriteLoader.background48(index = 0, pixelScale = 5))
        binding.bottomSpacer.background = SpriteLoader.background48(index = 0, pixelScale = 5)
        binding.contentBg.setImageDrawable(SpriteLoader.background48(index = 0, pixelScale = 5))
    }

    private fun initData() {
        userRepo = UserPreferencesRepository(this)
        logRepo = LogRepository(AppDatabase.getInstance(this).logDao())

        lifecycleScope.launch {
            initialDebug = userRepo.getDebugMode()
            initialLeft = userRepo.getLeftMode()
            initialDayGroup = userRepo.getDayGroup()
            initialPixelFont = userRepo.getPixelFont()
            initialQuestReminder = userRepo.getQuestReminderEnabled()
            initialReminderTime = userRepo.getReminderTime()
            initialImageDisplayMode = userRepo.getImageDisplayMode()

            currentDebug = initialDebug
            currentLeft = initialLeft
            currentDayGroup = initialDayGroup
            currentPixelFont = initialPixelFont
            currentQuestReminder = initialQuestReminder
            currentReminderTime = initialReminderTime
            currentImageDisplayMode = initialImageDisplayMode

            updateDebugButtons()
            updateLeftButtons()
            updateDisplayButtons()
            updatePixelFontButton()
            updateQuestReminderButton()
            updateReminderTimeDisplay()
            updateImageDisplayButton()
        }
    }

    private fun renderSprites() {
        SpriteLoader.setButton(binding.btnReturn, SpriteDef.B16.RES, SpriteDef.B16.frame(17), downFrame = SpriteDef.B16.frame(17), scale = 5)

        SpriteLoader.setButton(binding.btnCancel, SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(2), scale = 5)
        SpriteLoader.setButton(binding.btnConfirm, SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(0), scale = 5)

        binding.iconDebug.setImageBitmap(SpriteLoader.button16(12, scale = 5))
        binding.iconLeftMode.setImageBitmap(SpriteLoader.button16(1, scale = 5))
        binding.iconDisplay.setImageBitmap(SpriteLoader.button16(14, scale = 5))
        binding.iconBackup.setImageBitmap(SpriteLoader.button16(7, scale = 5))
        binding.iconUserSetting.setImageBitmap(SpriteLoader.button16(9, scale = 5))
        binding.btnUserSetting.setImageBitmap(SpriteLoader.button16(18, scale = 5))

        SpriteLoader.setButton(binding.btnDebugToggle, SpriteDef.B96x32.RES, SpriteDef.B96x32.frame(0), scale = 5)
        SpriteLoader.setButton(binding.btnLeftToggle, SpriteDef.B96x32.RES, SpriteDef.B96x32.frame(0), scale = 5)

        SpriteLoader.setButton(binding.btnDisplayMonth, SpriteDef.B32.RES, SpriteDef.B32.frame(6), scale = 5)
        SpriteLoader.setButton(binding.btnDisplayWeek, SpriteDef.B32.RES, SpriteDef.B32.frame(8), scale = 5)
        SpriteLoader.setButton(binding.btnDisplayDay, SpriteDef.B32.RES, SpriteDef.B32.frame(10), scale = 5)

        SpriteLoader.setButton(binding.btnBackupSave, SpriteDef.B48x32.RES, SpriteDef.B48x32.frame(4), scale = 5)
        SpriteLoader.setButton(binding.btnBackupLoad, SpriteDef.B48x32.RES, SpriteDef.B48x32.frame(6), scale = 5)

        binding.iconPixelFont.setImageBitmap(SpriteLoader.button16(33, scale = 5))
        SpriteLoader.setButton(binding.btnPixelFontToggle, SpriteDef.B96x32.RES, SpriteDef.B96x32.frame(0), scale = 5)

        binding.iconQuestReminder.setImageBitmap(SpriteLoader.button16(32, scale = 5))
        SpriteLoader.setButton(binding.btnQuestReminderToggle, SpriteDef.B96x32.RES, SpriteDef.B96x32.frame(0), scale = 5)

        binding.iconReminderTime.setImageBitmap(SpriteLoader.button16(31, scale = 5))

        binding.iconImageDisplay.setImageBitmap(SpriteLoader.button16(27, scale = 5))
        SpriteLoader.setButton(binding.btnImageDisplayToggle, SpriteDef.B96x32.RES, SpriteDef.B96x32.frame(0), scale = 5)
    }

    private fun updateDebugButtons() {
        binding.btnDebugToggle.setImageBitmap(SpriteLoader.button96x32(if (currentDebug) 1 else 0))
    }

    private fun updateLeftButtons() {
        binding.btnLeftToggle.setImageBitmap(SpriteLoader.button96x32(if (currentLeft) 1 else 0))
    }

    private fun updateDisplayButtons() {
        binding.btnDisplayMonth.setImageBitmap(SpriteLoader.button32(if (currentDayGroup == 0) 7 else 6))
        binding.btnDisplayWeek.setImageBitmap(SpriteLoader.button32(if (currentDayGroup == 1) 9 else 8))
        binding.btnDisplayDay.setImageBitmap(SpriteLoader.button32(if (currentDayGroup == 2) 11 else 10))
    }

    private fun updatePixelFontButton() {
        binding.btnPixelFontToggle.setImageBitmap(SpriteLoader.button96x32(if (currentPixelFont) 1 else 0))
    }

    private fun enableQuestReminder() {
        currentQuestReminder = true
        updateQuestReminderButton()
        if (!QuestNotificationScheduler.canScheduleExactAlarms(this)) {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.WARN)
                .setTitle("需要开启精确闹钟权限")
                .setMessage("系统默认禁止本应用设定精确闹钟，不开启时提醒可能不准时甚至不触发。\n\n点「确认」前往系统页面开启「闹钟和提醒」，返回本页后请点确认保存。")
                .onConfirm { launchExactAlarmSettings() }
                .show()
        }
    }

    private fun launchExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (e: Exception) {
            Log.w("AppSetting", "无法打开精确闹钟设置页: ${e.message}")
        }
    }

    private fun updateQuestReminderButton() {
        binding.btnQuestReminderToggle.setImageBitmap(SpriteLoader.button96x32(if (currentQuestReminder) 1 else 0))
    }

    private fun updateReminderTimeDisplay() {
        val display = String.format("%02d:%02d", currentReminderTime / 100, currentReminderTime % 100)
        binding.tvReminderTimeValue.text = display
    }

    private fun updateImageDisplayButton() {
        binding.btnImageDisplayToggle.setImageBitmap(SpriteLoader.button96x32(if (currentImageDisplayMode) 1 else 0))
    }

    private fun hasChanges(): Boolean {
        return currentDebug != initialDebug ||
                currentLeft != initialLeft ||
                currentDayGroup != initialDayGroup ||
                currentPixelFont != initialPixelFont ||
                currentQuestReminder != initialQuestReminder ||
                currentReminderTime != initialReminderTime ||
                currentImageDisplayMode != initialImageDisplayMode
    }

    private fun buildChangeLog(): String {
        val lines = mutableListOf<String>()
        if (currentDebug != initialDebug) {
            lines.add(" · 调试模式：${if (initialDebug) "开" else "关"} -> ${if (currentDebug) "开" else "关"}")
        }
        if (currentLeft != initialLeft) {
            lines.add(" · 左手主键：${if (initialLeft) "开" else "关"} -> ${if (currentLeft) "开" else "关"}")
        }
        if (currentDayGroup != initialDayGroup) {
            val names = mapOf(0 to "月", 1 to "周", 2 to "日")
            lines.add(" · 显示模式：${names[initialDayGroup]} -> ${names[currentDayGroup]}")
        }
        if (currentPixelFont != initialPixelFont) {
            lines.add(" · 像素字体：${if (initialPixelFont) "开" else "关"} -> ${if (currentPixelFont) "开" else "关"}")
        }
        if (currentQuestReminder != initialQuestReminder) {
            lines.add(" · 任务提醒：${if (initialQuestReminder) "开" else "关"} -> ${if (currentQuestReminder) "开" else "关"}")
        }
        if (currentReminderTime != initialReminderTime) {
            val oldStr = String.format("%02d:%02d", initialReminderTime / 100, initialReminderTime % 100)
            val newStr = String.format("%02d:%02d", currentReminderTime / 100, currentReminderTime % 100)
            lines.add(" · 提醒时间：$oldStr -> $newStr")
        }
        if (currentImageDisplayMode != initialImageDisplayMode) {
            lines.add(" · 大图模式：${if (initialImageDisplayMode) "开" else "关"} -> ${if (currentImageDisplayMode) "开" else "关"}")
        }
        return "APP属性调整：\n" + lines.joinToString("\n")
    }

    private fun buildChangeSummary(): String {
        val lines = mutableListOf<String>()
        if (currentDebug != initialDebug) lines.add(" · 调试模式")
        if (currentLeft != initialLeft) lines.add(" · 左手主键")
        if (currentDayGroup != initialDayGroup) lines.add(" · 显示模式")
        if (currentPixelFont != initialPixelFont) lines.add(" · 像素字体")
        if (currentQuestReminder != initialQuestReminder) lines.add(" · 任务提醒")
        if (currentReminderTime != initialReminderTime) lines.add(" · 提醒时间")
        if (currentImageDisplayMode != initialImageDisplayMode) lines.add(" · 大图模式")
        val showLines = if (lines.size > 3) lines.take(3) + listOf(" · …") else lines
        return showLines.joinToString("\n")
    }

    private fun applyAndLog() {
        lifecycleScope.launch {
            userRepo.setDebugMode(currentDebug)
            userRepo.setLeftMode(currentLeft)
            userRepo.setDayGroup(currentDayGroup)
            userRepo.setPixelFont(currentPixelFont)
            userRepo.setQuestReminderEnabled(currentQuestReminder)
            userRepo.setReminderTime(currentReminderTime)
            userRepo.setImageDisplayMode(currentImageDisplayMode)

            // 更新任务提醒调度（开启/关闭或修改时间后即时生效）
            QuestNotificationScheduler.scheduleDailyReminder(
                this@AppSettingActivity, currentQuestReminder, currentReminderTime
            )

            if (hasChanges()) {
                val now = TimeUtil.getTimeInt()
                val nowTime = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_SECOND)
                logRepo.saveLog(LogEntity(
                    logType = LogType.DEBUG,
                    logText = buildChangeLog(),
                    buildDate = now,
                    buildTime = nowTime,
                    changeDate = now,
                    changeTime = nowTime,
                    flag1 = 1,
                ))
            }

            initialDebug = currentDebug
            initialLeft = currentLeft
            initialDayGroup = currentDayGroup
            initialPixelFont = currentPixelFont
            initialQuestReminder = currentQuestReminder
            initialReminderTime = currentReminderTime
            initialImageDisplayMode = currentImageDisplayMode

            withContext(Dispatchers.Main) {
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun doExit() {
        if (!hasChanges()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        if (initialDebug) {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.WARN)
                .setTitle(getString(R.string.app_setting_unsaved_title))
                .setMessage(getString(R.string.app_setting_unsaved_msg))
                .setConfirmText("确认退出")
                .setCancelText("继续编辑")
                .onConfirm {
                    setResult(RESULT_CANCELED)
                    finish()
                }
                .show()
        } else {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun doConfirm() {
        if (!hasChanges()) {
            setResult(RESULT_OK)
            finish()
            return
        }

        if (initialDebug) {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.WARN)
                .setTitle(getString(R.string.app_setting_confirm_title))
                .setMessage(getString(R.string.app_setting_confirm_msg) + "\n" + buildChangeSummary())
                .setConfirmText("确认变更")
                .setCancelText("取消")
                .onConfirm { applyAndLog() }
                .show()
        } else {
            applyAndLog()
        }
    }

    private fun bindClickEvents() {
        binding.btnReturn.setOnClickListener { doExit() }
        binding.btnCancel.setOnClickListener { doExit() }
        binding.btnConfirm.setOnClickListener { doConfirm() }

        binding.rowUserSetting.setOnClickListener {
            startActivity(Intent(this, UserSettingActivity::class.java))
        }

        binding.btnDebugToggle.setOnClickListener {
            currentDebug = !currentDebug
            updateDebugButtons()
        }

        binding.btnLeftToggle.setOnClickListener {
            currentLeft = !currentLeft
            updateLeftButtons()
        }

        binding.btnDisplayMonth.setOnClickListener {
            if (currentDayGroup != 0) {
                currentDayGroup = 0
                updateDisplayButtons()
            }
        }
        binding.btnDisplayWeek.setOnClickListener {
            if (currentDayGroup != 1) {
                currentDayGroup = 1
                updateDisplayButtons()
            }
        }
        binding.btnDisplayDay.setOnClickListener {
            if (currentDayGroup != 2) {
                currentDayGroup = 2
                updateDisplayButtons()
            }
        }

        binding.btnPixelFontToggle.setOnClickListener {
            currentPixelFont = !currentPixelFont
            updatePixelFontButton()
        }

        binding.btnQuestReminderToggle.setOnClickListener {
            val newState = !currentQuestReminder
            if (newState && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    enableQuestReminder()
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else if (newState) {
                enableQuestReminder()
            } else {
                currentQuestReminder = false
                updateQuestReminderButton()
            }
        }

        binding.btnImageDisplayToggle.setOnClickListener {
            currentImageDisplayMode = !currentImageDisplayMode
            updateImageDisplayButton()
        }

        binding.rowReminderTime.setOnClickListener {
            NumberPickerDialog(this)
                .setMode(PickerMode.TIME)
                .setTitle("设置提醒时间")
                .setInitialTime(currentReminderTime / 100, currentReminderTime % 100)
                .onConfirm { value ->
                    currentReminderTime = value
                    updateReminderTimeDisplay()
                }
                .show()
        }

        binding.btnBackupSave.setOnClickListener {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.WARN)
                .setTitle(getString(R.string.app_setting_confirm_title))
                .setMessage("确认导出当前日志和用户数据？\n导出成功后可随时恢复。")
                .onConfirm {
                    exportLauncher.launch("PiecesOfLife_${TimeUtil.getTimeInt()}.piecesbackup")
                }
                .show()
        }

        binding.btnBackupLoad.setOnClickListener {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.ERROR)
                .setButtons(PixelDialog.ButtonMode.DUAL_IMPORT_CONFIRM)
                .setTitle(getString(R.string.app_setting_import_title))
                .setMessage(getString(R.string.app_setting_import_msg))
                .onConfirm {
                    importLauncher.launch(arrayOf("application/octet-stream", "application/zip"))
                }
                .show()
        }
    }
}
