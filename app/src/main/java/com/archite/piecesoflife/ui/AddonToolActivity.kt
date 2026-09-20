package com.archite.piecesoflife.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import com.archite.piecesoflife.databinding.ActivityAddonToolBinding
import com.archite.piecesoflife.util.ImageUtil
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddonToolActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EDIT_RESULT = "editResult"
        const val RESULT_LOGS_CHANGED = 1
        const val RESULT_SETTINGS_CHANGED = 2
        const val RESULT_SHOW_DELETED = 3
    }

    private lateinit var binding: ActivityAddonToolBinding
    private lateinit var logRepo: LogRepository

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            handleImagePicked(uri)
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            handleDocumentPicked(uri)
        }
    }

    private val editorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK, Intent().apply { putExtra(EXTRA_EDIT_RESULT, RESULT_LOGS_CHANGED) })
            finish()
        }
    }

    private val questViewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK, Intent().apply { putExtra(EXTRA_EDIT_RESULT, RESULT_LOGS_CHANGED) })
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddonToolBinding.inflate(layoutInflater)
        setContentView(binding.root)
        logRepo = LogRepository(AppDatabase.getInstance(this).logDao())

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
        SpriteLoader.setButton(binding.btnReturn, SpriteDef.B16.RES, SpriteDef.B16.frame(17), downFrame = SpriteDef.B16.frame(17), scale = 5)
        SpriteLoader.setButton(binding.btnCancel, SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(2), scale = 5)
        SpriteLoader.setButton(binding.btnConfirm, SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(0), scale = 5)

        val toolIcons = listOf(
            binding.toolIcon1, binding.toolIcon2, binding.toolIcon3,
            binding.toolIcon4, binding.toolIcon5, binding.toolIcon6, binding.toolIcon7,
            binding.toolIcon8,
        )
        val iconIndices = listOf(20, 22, 23, 24, 46, 44, 17, 47)
        for (i in toolIcons.indices) {
            SpriteLoader.setIcon(toolIcons[i], index = iconIndices[i], scale = 5)
        }
    }

    private fun bindClickEvents() {
        binding.btnReturn.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener {
            setResult(RESULT_OK, Intent().apply { putExtra(EXTRA_EDIT_RESULT, RESULT_SETTINGS_CHANGED) })
            finish()
        }
        binding.btnConfirm.setOnClickListener {
            setResult(RESULT_OK, Intent().apply { putExtra(EXTRA_EDIT_RESULT, RESULT_LOGS_CHANGED) })
            finish()
        }

        binding.toolPanel1.setOnClickListener {
            editorLauncher.launch(
                Intent(this, LogEditorActivity::class.java).apply {
                    putExtra(LogEditorActivity.EXTRA_LOG_ID, LogEditorActivity.NEW_LOG_QUEST)
                }
            )
        }

        binding.toolPanel2.setOnClickListener {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.INFO)
                .setTitle("添加文档")
                .setMessage("请选择文档创建方式：")
                .setButtons(PixelDialog.ButtonMode.TRIPLE_RETURN_NEW_UPLOAD)
                .onCancel { }
                .onFail {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val fileName = ImageUtil.createEmptyDocument(this@AddonToolActivity)
                        if (fileName != null) {
                            saveAndOpenEditor(LogType.DOCUMENT, fileName)
                        }
                    }
                }
                .onConfirm {
                    filePickerLauncher.launch("text/*")
                }
                .show()
        }

        binding.toolPanel3.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.toolPanel4.setOnClickListener {
            lifecycleScope.launch {
                val now = TimeUtil.getTimeInt()
                val nowTime = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_SECOND)
                val randomPoints = (0..99).random()
                logRepo.saveLog(LogEntity(
                    logType = LogType.HINT,
                    logText = "🎲 随机点数：$randomPoints",
                    buildDate = now,
                    buildTime = nowTime,
                    changeDate = now,
                    changeTime = nowTime,
                ))
                PixelDialog(this@AddonToolActivity)
                    .setType(PixelDialog.DialogType.INFO)
                    .setButtons(PixelDialog.ButtonMode.SINGLE_CLOSE)
                    .setTitle("随机结果")
                    .setMessage("🎲 本次随机点数为：$randomPoints")
                    .show()
            }
        }

        binding.toolPanel6.setOnClickListener {
            setResult(RESULT_OK, Intent().apply { putExtra(EXTRA_EDIT_RESULT, RESULT_SHOW_DELETED) })
            finish()
        }

        binding.toolPanel5.setOnClickListener {
            startActivity(Intent(this, DataStatsActivity::class.java))
        }

        binding.toolPanel8.setOnClickListener {
            questViewLauncher.launch(Intent(this, QuestViewActivity::class.java))
        }
    }

    private fun handleImagePicked(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val fileName = ImageUtil.copyImageFromUri(this@AddonToolActivity, uri)
            if (fileName != null) {
                ImageUtil.generateThumbnail(this@AddonToolActivity, fileName)
                saveAndOpenEditor(LogType.PICTURE, fileName)
            }
        }
    }

    private fun handleDocumentPicked(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val fileName = ImageUtil.copyDocumentFromUri(this@AddonToolActivity, uri)
            if (fileName != null) {
                val content = ImageUtil.readDocumentContent(this@AddonToolActivity, fileName) ?: ""
                val preview = ImageUtil.extractPreview(content)
                saveAndOpenEditor(LogType.DOCUMENT, fileName, preview)
            }
        }
    }

    private suspend fun saveAndOpenEditor(logType: Int, fileName: String, previewText: String = "") {
        val now = TimeUtil.getTimeInt()
        val nowTime = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_SECOND)
        val subDir = if (logType == LogType.PICTURE) "images" else "documents"
        val remark = "$subDir/$fileName"
        val logText = if (logType == LogType.PICTURE) "" else previewText

        val entity = LogEntity(
            logType = logType,
            logText = logText,
            remark = remark,
            buildDate = now,
            buildTime = nowTime,
            changeDate = now,
            changeTime = nowTime,
            flag1 = -1,
            itemsJson = "[]",
        )
        val savedId = logRepo.saveLog(entity)

        withContext(Dispatchers.Main) {
            val intent = Intent(this@AddonToolActivity, LogEditorActivity::class.java).apply {
                putExtra(LogEditorActivity.EXTRA_LOG_ID, savedId)
            }
            editorLauncher.launch(intent)
        }
    }
}
