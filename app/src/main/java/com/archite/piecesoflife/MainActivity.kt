package com.archite.piecesoflife

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.archite.piecesoflife.R
import com.archite.piecesoflife.databinding.ActivityMainBinding
import com.archite.piecesoflife.ui.AddonToolActivity
import com.archite.piecesoflife.ui.AppSettingActivity
import com.archite.piecesoflife.ui.LogQueryActivity
import com.archite.piecesoflife.ui.PixelDialog
import com.archite.piecesoflife.ui.QuestSettingActivity
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val logQueryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val focusDate = data?.getIntExtra(LogQueryActivity.EXTRA_FOCUS_DATE, 0) ?: 0
            val keyword = data?.getStringExtra(LogQueryActivity.EXTRA_KEYWORD) ?: ""
            val dateStr = TimeUtil.dateInt2String(focusDate)
            binding.tvLogQueryResult.text = if (keyword.isEmpty()) {
                "选中日期：$dateStr"
            } else {
                "搜索：$keyword（日期：$dateStr）"
            }
        } else {
            binding.tvLogQueryResult.text = "已取消"
        }
    }

    private val questSettingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val deadline = data?.getIntExtra(QuestSettingActivity.EXTRA_DEADLINE, 0) ?: 0
            val questTypeFlag = data?.getIntExtra(QuestSettingActivity.EXTRA_QUEST_TYPE_FLAG, 0) ?: 0
            val repeatMode = data?.getIntExtra(QuestSettingActivity.EXTRA_REPEAT_MODE, -1) ?: -1
            val deadlineStr = if (deadline == QuestSettingActivity.UNLIMITED_DATE) "无期限" else TimeUtil.dateInt2String(deadline)
            val typeStr = if (questTypeFlag == 1) "惩罚类型" else "默认类型"
            val repeatStr = when (repeatMode) {
                0 -> "每月"
                1 -> "每周"
                2 -> "每天"
                else -> "不重复"
            }
            binding.tvQuestSettingResult.text = "期限：$deadlineStr | $typeStr | $repeatStr"
        } else {
            binding.tvQuestSettingResult.text = "已取消"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()
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

    private fun renderSprites() {
        SpriteLoader.setButton(binding.btnAddonTool, SpriteDef.B48x32.RES, frame = SpriteDef.B48x32.frame(4), scale = 5)
        SpriteLoader.setButton(binding.btnDialogInfo, SpriteDef.B48x24.RES, frame = SpriteDef.B48x24.frame(2), scale = 5)
        SpriteLoader.setButton(binding.btnDialogWarn, SpriteDef.B48x24.RES, frame = SpriteDef.B48x24.frame(4), scale = 5)
        SpriteLoader.setButton(binding.btnDialogError, SpriteDef.B48x24.RES, frame = SpriteDef.B48x24.frame(6), scale = 5)
        SpriteLoader.setButton(binding.btnAppSetting, SpriteDef.B72x32.RES, frame = SpriteDef.B72x32.frame(0), scale = 5)
        SpriteLoader.setButton(binding.btnLogQuery, SpriteDef.B72x32.RES, frame = SpriteDef.B72x32.frame(4), scale = 5)
        SpriteLoader.setButton(binding.btnQuestSetting, SpriteDef.B48x32.RES, frame = SpriteDef.B48x32.frame(2), scale = 5)
    }

    private fun bindClickEvents() {
        binding.btnAddonTool.setOnClickListener {
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
                .onConfirm { }
                .show()
        }

        binding.btnDialogError.setOnClickListener {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.ERROR)
                .setTitle("重要选择")
                .setMessage("文件格式不兼容，无法读取。\n请检查文件格式后重试。")
                .setConfirmText("重试")
                .setCancelText("关闭")
                .onConfirm { }
                .show()
        }

        binding.btnAppSetting.setOnClickListener {
            val intent = Intent(this, AppSettingActivity::class.java)
            startActivity(intent)
        }

        binding.btnLogQuery.setOnClickListener {
            val intent = Intent(this, LogQueryActivity::class.java)
            intent.putExtra(LogQueryActivity.EXTRA_FOCUS_DATE, TimeUtil.getTimeInt())
            logQueryLauncher.launch(intent)
        }

        binding.btnQuestSetting.setOnClickListener {
            val intent = Intent(this, QuestSettingActivity::class.java)
            questSettingLauncher.launch(intent)
        }
    }
}
