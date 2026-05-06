package com.archite.piecesoflife

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.archite.piecesoflife.databinding.ActivityMainBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SpriteLoader.setIcon(binding.demoSprite, index = 5, scale = 5)
        SpriteLoader.setButton(
            binding.demoButton,
            SpriteDef.B24.RES,
            frame = SpriteDef.B24.frame(0),
            scale = 5,
        )

        binding.demoNinePatch.background = SpriteLoader.background48(index = 2, pixelScale = 5)
    }
}
