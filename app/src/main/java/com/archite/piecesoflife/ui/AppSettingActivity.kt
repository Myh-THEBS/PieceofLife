package com.archite.piecesoflife.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.AppDatabase
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.data.LogRepository
import com.archite.piecesoflife.data.LogType
import com.archite.piecesoflife.data.UserPreferencesRepository
import com.archite.piecesoflife.databinding.ActivityAppSettingBinding
import com.archite.piecesoflife.ui.UserSettingActivity
import com.archite.piecesoflife.util.FileUtil
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppSettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppSettingBinding
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private lateinit var userRepo: UserPreferencesRepository
    private lateinit var logRepo: LogRepository

    private var currentDebug = false
    private var currentLeft = false
    private var currentDayGroup = 2

    private var initialDebug = false
    private var initialLeft = false
    private var initialDayGroup = 2

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        ioScope.launch {
            FileUtil.exportToUri(this@AppSettingActivity, uri).let { result ->
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        PixelDialog(this@AppSettingActivity)
                            .setType(PixelDialog.DialogType.INFO)
                            .setTitle("导出成功")
                            .setMessage("数据已成功导出到所选位置。")
                            .setConfirmText("知道了")
                            .show()
                    } else {
                        PixelDialog(this@AppSettingActivity)
                            .setType(PixelDialog.DialogType.ERROR)
                            .setTitle("导出失败")
                            .setMessage(result.exceptionOrNull()?.message ?: "未知错误")
                            .setConfirmText("关闭")
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
        ioScope.launch {
            FileUtil.importFromUri(this@AppSettingActivity, uri).let { result ->
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        Toast.makeText(this@AppSettingActivity, "恢复成功，正在重启...", Toast.LENGTH_SHORT).show()
                        android.os.Handler(mainLooper).postDelayed({
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }, 500)
                    } else {
                        PixelDialog(this@AppSettingActivity)
                            .setType(PixelDialog.DialogType.ERROR)
                            .setTitle("导入失败")
                            .setMessage(result.exceptionOrNull()?.message ?: "未知错误")
                            .setConfirmText("关闭")
                            .show()
                    }
                }
            }
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

        ioScope.launch {
            initialDebug = userRepo.getDebugMode()
            initialLeft = userRepo.getLeftMode()
            initialDayGroup = userRepo.getDayGroup()

            currentDebug = initialDebug
            currentLeft = initialLeft
            currentDayGroup = initialDayGroup

            withContext(Dispatchers.Main) {
                updateDebugButtons()
                updateLeftButtons()
                updateDisplayButtons()
            }
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

    private fun hasChanges(): Boolean {
        return currentDebug != initialDebug ||
                currentLeft != initialLeft ||
                currentDayGroup != initialDayGroup
    }

    private fun buildChangeLog(): String {
        val lines = mutableListOf<String>()
        if (currentDebug != initialDebug) {
            lines.add("调试模式：${if (initialDebug) "开" else "关"} -> ${if (currentDebug) "开" else "关"}")
        }
        if (currentLeft != initialLeft) {
            lines.add("左手主键：${if (initialLeft) "开" else "关"} -> ${if (currentLeft) "开" else "关"}")
        }
        if (currentDayGroup != initialDayGroup) {
            val names = mapOf(0 to "月", 1 to "周", 2 to "日")
            lines.add("显示模式：${names[initialDayGroup]} -> ${names[currentDayGroup]}")
        }
        return "APP属性调整：\n" + lines.joinToString("\n")
    }

    private fun buildChangeSummary(): String {
        val lines = mutableListOf<String>()
        if (currentDebug != initialDebug) lines.add(" · 调试模式")
        if (currentLeft != initialLeft) lines.add(" · 左手主键")
        if (currentDayGroup != initialDayGroup) lines.add(" · 显示模式")
        val showLines = if (lines.size > 3) lines.take(3) + listOf(" · …") else lines
        return showLines.joinToString("\n")
    }

    private fun applyAndLog() {
        ioScope.launch {
            userRepo.setDebugMode(currentDebug)
            userRepo.setLeftMode(currentLeft)
            userRepo.setDayGroup(currentDayGroup)

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

        binding.btnBackupSave.setOnClickListener {
            exportLauncher.launch("PiecesOfLife_${TimeUtil.getTimeInt()}.piecesbackup")
        }

        binding.btnBackupLoad.setOnClickListener {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.ERROR)
                .setTitle(getString(R.string.app_setting_import_title))
                .setMessage(getString(R.string.app_setting_import_msg))
                .setConfirmText("确认导入")
                .setCancelText("取消")
                .onConfirm {
                    importLauncher.launch(arrayOf("application/octet-stream", "application/zip"))
                }
                .show()
        }
    }
}
