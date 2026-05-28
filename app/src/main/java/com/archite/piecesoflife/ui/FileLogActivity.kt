package com.archite.piecesoflife.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
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
import com.archite.piecesoflife.databinding.ActivityFileLogBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileLogActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LOG_ID = "logId"
        const val EXTRA_LOG_MODE = "logMode"
        const val EXTRA_RESULT_ID = "resultId"

        const val MODE_IMAGE = 0
        const val MODE_DOCUMENT = 1

        const val NEW_LOG_FILE = -3L
        const val RESULT_SAVED = 1
    }

    private lateinit var binding: ActivityFileLogBinding
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private lateinit var logRepo: LogRepository

    private var currentLogId = NEW_LOG_FILE
    private var currentMode = MODE_IMAGE
    private var isLargeMode = true
    private var hasAttachment = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentLogId = intent.getLongExtra(EXTRA_LOG_ID, NEW_LOG_FILE)
        currentMode = intent.getIntExtra(EXTRA_LOG_MODE, MODE_IMAGE)

        setupSystemBars()
        renderBackgrounds()
        renderSprites()
        initData()
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

        binding.iconDesc.setImageBitmap(SpriteLoader.button16(4, scale = 5))
        binding.iconPreview.setImageBitmap(SpriteLoader.button16(23, scale = 5))
        binding.iconMode.setImageBitmap(SpriteLoader.button16(14, scale = 5))

        SpriteLoader.setButton(binding.btnModeLarge, SpriteDef.B32.RES, SpriteDef.B32.frame(7), scale = 5)
        SpriteLoader.setButton(binding.btnModeSmall, SpriteDef.B32.RES, SpriteDef.B32.frame(8), scale = 5)

        SpriteLoader.setButton(binding.btnDelete, SpriteDef.B24.RES, SpriteDef.B24.frame(6), scale = 2)

        if (currentMode == MODE_DOCUMENT) {
            binding.rowMode.visibility = android.view.View.GONE
            binding.ivPreview.visibility = android.view.View.GONE
            binding.tvUploadHint.visibility = android.view.View.GONE
            binding.tvDocFilename.visibility = android.view.View.VISIBLE
            binding.tvDocPreview.visibility = android.view.View.VISIBLE
            binding.labelDelete.setText(R.string.document_log_delete)
        } else {
            binding.rowMode.visibility = android.view.View.VISIBLE
            binding.ivPreview.visibility = android.view.View.VISIBLE
            binding.tvDocFilename.visibility = android.view.View.GONE
            binding.tvDocPreview.visibility = android.view.View.GONE
            binding.labelDelete.setText(R.string.picture_log_delete)
        }
    }

    private fun initData() {
        logRepo = LogRepository(AppDatabase.getInstance(this).logDao())

        if (currentMode == MODE_DOCUMENT) {
            binding.titleTextView.setText(R.string.document_log_title)
            binding.iconPreview.setImageBitmap(SpriteLoader.button16(4, scale = 5))
        }

        if (currentLogId > 0) {
            ioScope.launch {
                val entity = logRepo.getLogById(currentLogId)
                if (entity != null) {
                    hasAttachment = entity.remark.isNotEmpty()
                    isLargeMode = entity.flag1 != 1
                    withContext(Dispatchers.Main) {
                        binding.etDescription.setText(entity.logText)
                        if (currentMode == MODE_IMAGE) {
                            updateModeButtons()
                        }
                        updateDeleteVisibility()
                        updatePreviewPlaceholder()
                    }
                }
            }
        }
    }

    private fun bindClickEvents() {
        binding.btnReturn.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }

        binding.btnConfirm.setOnClickListener { doSave() }

        if (currentMode == MODE_IMAGE) {
            binding.btnModeLarge.setOnClickListener {
                isLargeMode = true
                updateModeButtons()
            }

            binding.btnModeSmall.setOnClickListener {
                isLargeMode = false
                updateModeButtons()
            }
        }

        binding.btnDelete.setOnClickListener {
            hasAttachment = false
            updateDeleteVisibility()
            updatePreviewPlaceholder()
        }

        binding.previewContainer.setOnClickListener {
            // Placeholder: file selection will be handled in Phase 1.4
        }
    }

    private fun updateModeButtons() {
        binding.btnModeLarge.setImageBitmap(SpriteLoader.button32(if (isLargeMode) 7 else 6))
        binding.btnModeSmall.setImageBitmap(SpriteLoader.button32(if (isLargeMode) 8 else 9))
    }

    private fun updateDeleteVisibility() {
        binding.rowDelete.visibility = if (hasAttachment) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun updatePreviewPlaceholder() {
        if (hasAttachment) {
            if (currentMode == MODE_IMAGE) {
                binding.ivPreview.visibility = android.view.View.VISIBLE
                binding.tvUploadHint.visibility = android.view.View.GONE
                binding.ivPreview.setImageDrawable(null)
                binding.ivPreview.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            } else {
                binding.tvDocFilename.visibility = android.view.View.VISIBLE
                binding.tvDocPreview.visibility = android.view.View.VISIBLE
                binding.tvUploadHint.visibility = android.view.View.GONE
            }
        } else {
            binding.tvUploadHint.visibility = android.view.View.VISIBLE
            binding.tvUploadHint.setText(
                if (currentMode == MODE_IMAGE) R.string.picture_log_upload_hint
                else R.string.document_log_upload_hint
            )
            if (currentMode == MODE_IMAGE) {
                binding.ivPreview.setImageDrawable(null)
                binding.ivPreview.setBackgroundColor(android.graphics.Color.WHITE)
            } else {
                binding.tvDocFilename.visibility = android.view.View.GONE
                binding.tvDocPreview.visibility = android.view.View.GONE
            }
        }
    }

    private fun doSave() {
        val description = binding.etDescription.text.toString().trim()

        ioScope.launch {
            val now = TimeUtil.getTimeInt()
            val nowTime = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_SECOND)

            val entity = LogEntity(
                id = if (currentLogId > 0) currentLogId else 0,
                logType = if (currentMode == MODE_IMAGE) LogType.PICTURE else LogType.DOCUMENT,
                logText = description,
                remark = "",
                buildDate = now,
                buildTime = nowTime,
                changeDate = now,
                changeTime = nowTime,
                flag1 = if (currentMode == MODE_IMAGE && isLargeMode) -1 else if (currentMode == MODE_IMAGE) 1 else -1,
                itemsJson = "[]",
            )

            val savedId = logRepo.saveLog(entity)

            withContext(Dispatchers.Main) {
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_RESULT_ID, savedId)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
    }
}
