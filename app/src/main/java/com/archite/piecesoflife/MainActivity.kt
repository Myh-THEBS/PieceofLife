package com.archite.piecesoflife

import android.annotation.SuppressLint
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
import com.archite.piecesoflife.data.UserItem
import com.archite.piecesoflife.data.UserPreferencesRepository
import com.archite.piecesoflife.databinding.ActivityMainBinding
import com.archite.piecesoflife.ui.AddonToolActivity
import com.archite.piecesoflife.ui.PixelDialog
import com.archite.piecesoflife.util.FileUtil
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        showStatus(binding.tvExport, "导出中...")
        ioScope.launch {
            FileUtil.exportToUri(this@MainActivity, uri).let { result ->
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        showStatus(binding.tvExport, "导出成功")
                        Toast.makeText(this@MainActivity, "导出成功", Toast.LENGTH_SHORT).show()
                    } else {
                        showStatus(binding.tvExport, "导出失败: ${result.exceptionOrNull()?.message}")
                        Toast.makeText(this@MainActivity, "导出失败", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        showStatus(binding.tvImport, "恢复中...")
        ioScope.launch {
            FileUtil.importFromUri(this@MainActivity, uri).let { result ->
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        showStatus(binding.tvImport, "恢复成功，即将关闭APP")
                        Toast.makeText(this@MainActivity, "恢复成功，正在重启...", Toast.LENGTH_SHORT).show()
                        // 延迟一小段时间让 Toast 显示后关闭进程
                        android.os.Handler(mainLooper).postDelayed({
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }, 500)
                    } else {
                        showStatus(binding.tvImport, "恢复失败: ${result.exceptionOrNull()?.message}")
                        Toast.makeText(this@MainActivity, "恢复失败", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()

        val logRepo = LogRepository(AppDatabase.getInstance(this).logDao())
        val userRepo = UserPreferencesRepository(this)

        SpriteLoader.setButton(binding.btnSaveLog, SpriteDef.B48x32.RES, frame = SpriteDef.B48x32.frame(0), scale = 5)
        SpriteLoader.setButton(binding.btnUpdateUser, SpriteDef.B48x32.RES, frame = SpriteDef.B48x32.frame(2), scale = 5)
        SpriteLoader.setButton(binding.btnExport, SpriteDef.B48x32.RES, frame = SpriteDef.B48x32.frame(4), scale = 5)
        SpriteLoader.setButton(binding.btnImport, SpriteDef.B48x32.RES, frame = SpriteDef.B48x32.frame(6), scale = 5)
        SpriteLoader.setButton(binding.btnAddonTool, SpriteDef.B48x32.RES, frame = SpriteDef.B48x32.frame(4), scale = 5)
        SpriteLoader.setButton(binding.btnDialogInfo, SpriteDef.B48x24.RES, frame = SpriteDef.B48x24.frame(2), scale = 5)
        SpriteLoader.setButton(binding.btnDialogWarn, SpriteDef.B48x24.RES, frame = SpriteDef.B48x24.frame(4), scale = 5)
        SpriteLoader.setButton(binding.btnDialogError, SpriteDef.B48x24.RES, frame = SpriteDef.B48x24.frame(6), scale = 5)

        binding.btnSaveLog.setOnClickListener {
            showStatus(binding.tvSaveLog, "保存中...")
            ioScope.launch {
                try {
                    val now = TimeUtil.getTimeInt()
                    val nowTime = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_SECOND)
                    val entity = LogEntity(
                        logType = LogType.DEFAULT,
                        logText = "测试日志 - ${TimeUtil.timeInt2String(nowTime)}",
                        buildDate = now,
                        buildTime = nowTime,
                        changeDate = now,
                        changeTime = nowTime,
                    )
                    val id = logRepo.saveLog(entity)
                    val count = logRepo.getTotalLogCount()
                    withContext(Dispatchers.Main) {
                        showStatus(binding.tvSaveLog, "已保存 id=$id，共 $count 条")
                        Toast.makeText(this@MainActivity, "日志已保存，id=$id", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showStatus(binding.tvSaveLog, "保存失败: ${e.message}")
                    }
                }
            }
        }

        binding.btnUpdateUser.setOnClickListener {
            showStatus(binding.tvUpdateUser, "更新中...")
            ioScope.launch {
                try {
                    userRepo.setUserName("测试用户")
                    userRepo.setUserExp(250)
                    userRepo.setDayGroup(2)
                    userRepo.setUserAvatar(3)
                    val items = listOf(
                        UserItem("攻击力", UserItem.TYPE_ATTRIBUTES, 0, 42),
                        UserItem("防御力", UserItem.TYPE_ATTRIBUTES, 0, 18),
                    )
                    userRepo.setItems(items)
                    val uuid = userRepo.getOrCreateUserId()
                    withContext(Dispatchers.Main) {
                        showStatus(binding.tvUpdateUser, "已更新，UUID=$uuid")
                        Toast.makeText(this@MainActivity, "用户信息已更新", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showStatus(binding.tvUpdateUser, "更新失败: ${e.message}")
                    }
                }
            }
        }

        binding.btnExport.setOnClickListener {
            exportLauncher.launch("PiecesOfLife_${TimeUtil.getTimeInt()}.piecesbackup")
        }

        binding.btnImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/octet-stream", "application/zip"))
        }

        binding.btnAddonTool.setOnClickListener {
            showStatus(binding.tvAddonTool, "打开 AddonTool...")
            val intent = Intent(this, AddonToolActivity::class.java)
            startActivity(intent)
        }

        binding.btnDialogInfo.setOnClickListener {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.INFO)
                .setTitle("信息")
                .setMessage("这是一条信息提示。\nPixelDialog 组件测试。")
                .setConfirmText("知道了")
                .show()
        }

        binding.btnDialogWarn.setOnClickListener {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.WARN)
                .setTitle("警告")
                .setMessage("操作前请确认当前数据已备份！\n此操作不可撤销。")
                .setConfirmText("确认执行")
                .setCancelText("取消")
                .onConfirm { /* 执行操作 */ }
                .show()
        }

        binding.btnDialogError.setOnClickListener {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.ERROR)
                .setTitle("重要选择")
                .setMessage("文件格式不兼容，无法读取。\n请检查文件格式后重试。")
                .setConfirmText("重试")
                .setCancelText("关闭")
                .onConfirm { /* 执行操作 */ }
                .show()
        }
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

    private fun showStatus(textView: android.widget.TextView, text: String) {
        textView.visibility = android.view.View.VISIBLE
        textView.text = text
    }
}
