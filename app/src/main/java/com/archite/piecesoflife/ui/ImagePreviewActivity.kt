package com.archite.piecesoflife.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.archite.piecesoflife.R
import com.archite.piecesoflife.databinding.ActivityImagePreviewBinding
import com.archite.piecesoflife.util.ImageUtil
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader

class ImagePreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_FILE_NAME = "imageFileName"
        const val EXTRA_LOG_ID = "logId"
    }

    private lateinit var binding: ActivityImagePreviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imageFileName = intent.getStringExtra(EXTRA_IMAGE_FILE_NAME) ?: run {
            finish(); return
        }
        val logId = intent.getLongExtra(EXTRA_LOG_ID, -1L)

        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.black)
        SpriteLoader.setButton(binding.btnClose, SpriteDef.B24.RES, SpriteDef.B24.frame(12), scale = 5)
        SpriteLoader.setButton(binding.btnEdit, SpriteDef.B24.RES, SpriteDef.B24.frame(20),  scale = 5)

        val imageFile = java.io.File(ImageUtil.getImagesDir(this), imageFileName)
        if (imageFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            if (bitmap != null) {
                binding.photoView.setImageBitmap(bitmap)
            }
        }

        if (logId > 0) {
            binding.btnEdit.visibility = android.view.View.VISIBLE
            binding.btnEdit.setOnClickListener {
                val intent = Intent(this, LogEditorActivity::class.java).apply {
                    putExtra(LogEditorActivity.EXTRA_LOG_ID, logId)
                }
                startActivity(intent)
            }
        }

        binding.btnClose.setOnClickListener { finish() }
    }
}
