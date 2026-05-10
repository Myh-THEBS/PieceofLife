package com.archite.piecesoflife.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.archite.piecesoflife.R
import com.archite.piecesoflife.databinding.ActivityAddonToolBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader

class AddonToolActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddonToolBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddonToolBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()
        renderBackgrounds()
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

    private fun renderSprites() {
        // 顶部返回按钮：B24 帧 0/1（偶弹起，奇按下）
        SpriteLoader.setButton(binding.btnReturn, SpriteDef.B16.RES, SpriteDef.B16.frame(17), downFrame = SpriteDef.B16.frame(17), scale = 4)

        // 底部取消按钮：B72x32 帧 0/1
        SpriteLoader.setButton(binding.btnCancel, SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(2), scale = 4)

        // 底部确认按钮：B72x32 帧 2/3
        SpriteLoader.setButton(binding.btnConfirm, SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(0), scale = 4)

        // 7 个工具图标（Icons 24x24，scale=2 → 48x48dp）
        val toolIcons = listOf(
            binding.toolIcon1, binding.toolIcon2, binding.toolIcon3,
            binding.toolIcon4, binding.toolIcon5, binding.toolIcon6, binding.toolIcon7,
        )
        val iconIndices = listOf(20, 22, 23, 24, 3, 5, 17)
        for (i in toolIcons.indices) {
            SpriteLoader.setIcon(toolIcons[i], index = iconIndices[i], scale = 4)
        }
    }

    private fun bindClickEvents() {
        // 返回、取消、确认 — 均关闭页面
        binding.btnReturn.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnConfirm.setOnClickListener { finish() }

        // PixelDialog 集成测试：工具 1~3 分别展示信息/警告/错误弹窗
        binding.toolPanel1.setOnClickListener {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.INFO)
                .setTitle("信息")
                .setMessage("这是一条信息提示。\n可以包含多行文本。")
                .setConfirmText("知道了")
                .onConfirm { /* 可执行操作 */ }
                .show()
        }
        binding.toolPanel2.setOnClickListener {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.WARN)
                .setTitle("警告")
                .setMessage("操作前请确认当前数据已备份！\n此操作不可撤销。")
                .setConfirmText("确认执行")
                .setCancelText("取消")
                .onConfirm { /* 执行操作 */ }
                .show()
        }
        binding.toolPanel3.setOnClickListener {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.ERROR)
                .setTitle("错误")
                .setMessage("文件格式不兼容，无法读取。\n请检查文件格式后重试。")
                .setConfirmText("重试")
                .setCancelText("关闭")
                .onConfirm { /* 执行操作 */ }
                .show()
        }
    }
}
