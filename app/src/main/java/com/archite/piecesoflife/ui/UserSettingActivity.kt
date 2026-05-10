package com.archite.piecesoflife.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.archite.piecesoflife.R
import com.archite.piecesoflife.databinding.ActivityUserSettingBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader

class UserSettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserSettingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserSettingBinding.inflate(layoutInflater)
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
        SpriteLoader.setButton(binding.btnReturn, SpriteDef.B16.RES, SpriteDef.B16.frame(17), downFrame = SpriteDef.B16.frame(17), scale = 4)
        SpriteLoader.setButton(binding.btnCancel, SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(2), scale = 4)
        SpriteLoader.setButton(binding.btnConfirm, SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(0), scale = 4)
    }

    private fun bindClickEvents() {
        binding.btnReturn.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnConfirm.setOnClickListener { finish() }
    }
}
