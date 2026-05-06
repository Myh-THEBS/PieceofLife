package com.archite.piecesoflife

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.archite.piecesoflife.data.AppDatabase
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.data.LogRepository
import com.archite.piecesoflife.data.LogType
import com.archite.piecesoflife.data.UserItem
import com.archite.piecesoflife.data.UserPreferencesRepository
import com.archite.piecesoflife.databinding.ActivityMainBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        setupDatabaseTest()
        setupDataStoreTest()
    }

    private fun setupDatabaseTest() {
        val repo = LogRepository(AppDatabase.getInstance(this).logDao())

        SpriteLoader.setButton(
            binding.testDbButton,
            SpriteDef.B48x24.RES,
            frame = SpriteDef.B48x24.frame(2),
            scale = 5,
        )

        binding.testDbButton.setOnClickListener {
            binding.testResult.visibility = android.view.View.VISIBLE
            binding.testResult.text = "测试中..."

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val now = TimeUtil.getTimeInt()
                    val nowTime = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_SECOND)

                    val entity = LogEntity(
                        logType = LogType.DEFAULT,
                        logText = "数据库测试 - ${TimeUtil.timeInt2String(nowTime)}",
                        buildDate = now,
                        buildTime = nowTime,
                        changeDate = now,
                        changeTime = nowTime,
                    )

                    val id = repo.saveLog(entity)
                    val loaded = repo.getLogById(id)
                    val count = repo.getTotalLogCount()
                    val recentIds = repo.getLogIdsInDateRange(now, now)
                    val searchResult = repo.searchLogs("数据库测试", now)

                    val report = buildString {
                        append("写入 → id=$id\n")
                        append("读取 → ${loaded?.logText ?: "失败"}\n")
                        append("今日日志数=$count\n")
                        append("今日查询 → ${recentIds.size} 条\n")
                        append("关键词搜索 → ${searchResult.size} 条")
                    }

                    withContext(Dispatchers.Main) {
                        binding.testResult.text = report
                        Toast.makeText(
                            this@MainActivity,
                            "数据库测试完成，id=$id",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.testResult.text = "测试失败: ${e.message}"
                        Toast.makeText(
                            this@MainActivity,
                            "数据库测试失败！",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun setupDataStoreTest() {
        val repo = UserPreferencesRepository(this)

        SpriteLoader.setButton(
            binding.testDsButton,
            SpriteDef.B48x32.RES,
            frame = SpriteDef.B48x32.frame(4),
            scale = 5,
        )

        binding.testDsButton.setOnClickListener {
            binding.dsResult.visibility = android.view.View.VISIBLE
            binding.dsResult.text = "测试中..."

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repo.setUserName("测试用户")
                    repo.setDebugMode(true)
                    repo.setLastDate(TimeUtil.getTimeInt())
                    repo.setUserExp(250)
                    repo.setDayGroup(2)
                    repo.setUserAvatar(3)

                    val items = listOf(
                        UserItem("攻击力", UserItem.TYPE_ATTRIBUTES, 0, 42),
                        UserItem("防御力", UserItem.TYPE_ATTRIBUTES, 0, 18),
                        UserItem("治愈术", UserItem.TYPE_SKILL, 0, 0),
                    )
                    repo.setItems(items)

                    val name = repo.getUserName()
                    val exp = repo.getUserExp()
                    val avatar = repo.getUserAvatar()
                    val loadedItems = repo.getItems()
                    val errorCode = repo.validateUserData()

                    val report = buildString {
                        append("name=$name\n")
                        append("exp=$exp  avatar=$avatar\n")
                        append("items(${loadedItems.size}):\n")
                        loadedItems.forEach { append("  [${it.type}] ${it.name}=${it.value}\n") }
                        append("校验错误码=$errorCode")
                    }

                    withContext(Dispatchers.Main) {
                        binding.dsResult.text = report
                        Toast.makeText(
                            this@MainActivity,
                            "DataStore 测试完成",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.dsResult.text = "测试失败: ${e.message}"
                        Toast.makeText(
                            this@MainActivity,
                            "DataStore 测试失败！",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
}
