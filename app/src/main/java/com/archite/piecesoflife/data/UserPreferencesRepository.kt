package com.archite.piecesoflife.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_DEBUG_MODE = booleanPreferencesKey("debug_mode")
        private val KEY_LAST_DATE = intPreferencesKey("last_date")
        private val KEY_DAY_GROUP = intPreferencesKey("day_group")
        private val KEY_ITEMS_JSON = stringPreferencesKey("items_json")
        private val KEY_USER_AVATAR = intPreferencesKey("user_avatar")
        private val KEY_USER_UUID = stringPreferencesKey("user_uuid")
        private val KEY_LEFT_MODE = booleanPreferencesKey("left_mode")
        private val KEY_SIZE_LIST = stringPreferencesKey("size_list")
        private val KEY_COLOR_LIST = stringPreferencesKey("color_list")
        private val KEY_QUEST_REMINDER = booleanPreferencesKey("quest_reminder")
        private val KEY_REMINDER_TIME = intPreferencesKey("reminder_time")
        private val KEY_PIXEL_FONT = booleanPreferencesKey("pixel_font")
        private val KEY_IMAGE_DISPLAY_MODE = booleanPreferencesKey("image_display_mode")

        // 默认字号列表（相对比例 * 20）
        private val DEFAULT_SIZE_LIST = listOf(10, 12, 16, 18, 20, 24, 36, 48)

        // 默认颜色列表（ARGB 格式字符串）
        val DEFAULT_COLOR_LIST = listOf(
            "#FF000000", "#FFFFFFFF", "#FFED6464", "#FFF3994B", "#FFFBD92A",
            "#FF6CEA3E", "#FF3BCDAB", "#FF2391C5", "#FF954ADF", "#FFFD8BDD",
        )

        private val DEFAULT_ITEMS_JSON = """
            [
                {"name": "角色等级", "type": "Skill", "iconEmoji": "💠", "value": 9235, "abbr": "EXP", "levelExp": 1000, "priority": 99, "rule": "默认的等级和经验，代表一段时间以来的成长和经历。"},
                {"name": "点数", "type": "Attributes", "iconEmoji": "💎", "value": 50, "abbr": "STP", "priority": 3, "rule": "学习和完成任务积累的奖励，可以与货币1:1兑换，购买自己想要的东西。"},
                {"name": "健康", "type": "Attributes", "iconEmoji": "🍖", "value": 10, "abbr": "HP", "priority": 2, "rule": "健康指数，通过完成运动、早睡早起、健康饮食等获得，可以消费用于购买网络增值产品或零食。"},
                {"name": "幸运", "type": "Attributes", "iconEmoji": "🎲", "value": -3, "abbr": "LUK", "priority": 1, "rule": "运气指数，LUK>0时代表近期处于好运，反之代表厄运。发生好运的事情时，LUK会增加，否则会减少。\nLUK=0 代表平凡的一天，运气应该是守恒的。"},
                {"name": "点数惩罚", "type": "Phrases", "rule": "惩罚，STP-"},
                {"name": "学习奖励", "type": "Phrases", "rule": "完成学习，STP+"}
            ]
        """.trimIndent()
    }

    val userNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_NAME] ?: "默认用户"
    }

    val debugModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEBUG_MODE] ?: false
    }

    val lastDateFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_DATE] ?: 19981228
    }

    val dayGroupFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_DAY_GROUP] ?: 2
    }

    val userAvatarFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_AVATAR] ?: 0
    }

    val pixelFontFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PIXEL_FONT] ?: true
    }

    val questReminderFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_QUEST_REMINDER] ?: false
    }

    val imageDisplayModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IMAGE_DISPLAY_MODE] ?: true
    }

    /**
     * 观察设备唯一标识。首次启动时由 [getOrCreateUserId] 自动生成。
     * 用户也可通过 [setUserUuid] 手动配置（例如服务端分发的固定 UUID）。
     */
    val userUuidFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_UUID] ?: ""
    }

    val leftModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_LEFT_MODE] ?: false
    }

    val itemsFlow: Flow<List<UserItem>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_ITEMS_JSON] ?: DEFAULT_ITEMS_JSON
        parseItemsJson(json)
    }

    suspend fun getUserName(): String = context.dataStore.data.first()[KEY_USER_NAME] ?: "默认用户"
    suspend fun getDebugMode(): Boolean = context.dataStore.data.first()[KEY_DEBUG_MODE] ?: false
    suspend fun getLastDate(): Int = context.dataStore.data.first()[KEY_LAST_DATE] ?: 19981228
    suspend fun getDayGroup(): Int = context.dataStore.data.first()[KEY_DAY_GROUP] ?: 2
    suspend fun getUserAvatar(): Int = context.dataStore.data.first()[KEY_USER_AVATAR] ?: 0
    suspend fun getLeftMode(): Boolean = context.dataStore.data.first()[KEY_LEFT_MODE] ?: false
    suspend fun getQuestReminderEnabled(): Boolean = context.dataStore.data.first()[KEY_QUEST_REMINDER] ?: false
    suspend fun getReminderTime(): Int = context.dataStore.data.first()[KEY_REMINDER_TIME] ?: 2200
    suspend fun getPixelFont(): Boolean = context.dataStore.data.first()[KEY_PIXEL_FONT] ?: true
    suspend fun getImageDisplayMode(): Boolean = context.dataStore.data.first()[KEY_IMAGE_DISPLAY_MODE] ?: true

    /**
     * 获取设备唯一标识。首次调用时自动生成一个随机 UUID 并持久化，
     * 后续调用始终返回同一个值。用户也可通过 [setUserUuid] 手动配置。
     *
     * 可用于：备份包 manifest.json 标识数据归属、未来自托管云同步的用户隔离。
     */
    suspend fun getOrCreateUserId(): String {
        val existing = context.dataStore.data.first()[KEY_USER_UUID]
        if (existing != null) return existing
        val newId = UUID.randomUUID().toString()
        context.dataStore.edit { it[KEY_USER_UUID] = newId }
        return newId
    }

    /**
     * 手动设置设备唯一标识（例如从服务端管理员处获取的固定 UUID）。
     * 设置后将覆盖之前自动生成的值。
     */
    suspend fun setUserUuid(uuid: String) = context.dataStore.edit { it[KEY_USER_UUID] = uuid }

    suspend fun getItems(): List<UserItem> {
        val json = context.dataStore.data.first()[KEY_ITEMS_JSON] ?: DEFAULT_ITEMS_JSON
        return parseItemsJson(json)
    }

    suspend fun setUserName(name: String) = context.dataStore.edit { it[KEY_USER_NAME] = name }
    suspend fun setDebugMode(enabled: Boolean) = context.dataStore.edit { it[KEY_DEBUG_MODE] = enabled }
    suspend fun setLastDate(date: Int) = context.dataStore.edit { it[KEY_LAST_DATE] = date }
    suspend fun setDayGroup(group: Int) = context.dataStore.edit { it[KEY_DAY_GROUP] = group }
    suspend fun setUserAvatar(avatarIndex: Int) = context.dataStore.edit { it[KEY_USER_AVATAR] = avatarIndex }
    suspend fun setLeftMode(enabled: Boolean) = context.dataStore.edit { it[KEY_LEFT_MODE] = enabled }
    suspend fun setQuestReminderEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_QUEST_REMINDER] = enabled }
    suspend fun setReminderTime(time: Int) = context.dataStore.edit { it[KEY_REMINDER_TIME] = time }
    suspend fun setPixelFont(enabled: Boolean) = context.dataStore.edit { it[KEY_PIXEL_FONT] = enabled }
    suspend fun setImageDisplayMode(largeMode: Boolean) = context.dataStore.edit { it[KEY_IMAGE_DISPLAY_MODE] = largeMode }

    suspend fun setItems(items: List<UserItem>) = context.dataStore.edit {
        it[KEY_ITEMS_JSON] = itemsToJson(items)
    }

    suspend fun getSizeList(): List<Int> {
        val json = context.dataStore.data.first()[KEY_SIZE_LIST] ?: return DEFAULT_SIZE_LIST
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { arr.getInt(it) }
        } catch (_: Exception) { DEFAULT_SIZE_LIST }
    }

    suspend fun getColorList(): List<String> {
        val json = context.dataStore.data.first()[KEY_COLOR_LIST] ?: return DEFAULT_COLOR_LIST
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { arr.getString(it) }
        } catch (_: Exception) { DEFAULT_COLOR_LIST }
    }

    suspend fun setSizeList(sizes: List<Int>) = context.dataStore.edit {
        it[KEY_SIZE_LIST] = JSONArray(sizes).toString()
    }

    suspend fun setColorList(colors: List<String>) = context.dataStore.edit {
        it[KEY_COLOR_LIST] = JSONArray(colors).toString()
    }

    suspend fun updateAll(
        userName: String? = null,
        debugMode: Boolean? = null,
        lastDate: Int? = null,
        dayGroup: Int? = null,
        userAvatar: Int? = null,
        items: List<UserItem>? = null
    ) = context.dataStore.edit { prefs ->
        userName?.let { prefs[KEY_USER_NAME] = it }
        debugMode?.let { prefs[KEY_DEBUG_MODE] = it }
        lastDate?.let { prefs[KEY_LAST_DATE] = it }
        dayGroup?.let { prefs[KEY_DAY_GROUP] = it }
        userAvatar?.let { prefs[KEY_USER_AVATAR] = it }
        items?.let { prefs[KEY_ITEMS_JSON] = itemsToJson(it) }
    }

    suspend fun resetToDefaults() = context.dataStore.edit { prefs ->
        prefs[KEY_USER_NAME] = "新用户"
        prefs[KEY_DEBUG_MODE] = true
        prefs[KEY_LAST_DATE] = 20250101
        prefs[KEY_DAY_GROUP] = 1
        prefs[KEY_USER_AVATAR] = 0
        prefs[KEY_LEFT_MODE] = false
        prefs[KEY_USER_UUID] = UUID.randomUUID().toString()
        prefs[KEY_ITEMS_JSON] = DEFAULT_ITEMS_JSON
        prefs[KEY_QUEST_REMINDER] = false
        prefs[KEY_REMINDER_TIME] = 2200
        prefs[KEY_PIXEL_FONT] = true
        prefs[KEY_IMAGE_DISPLAY_MODE] = true
    }

    suspend fun validateUserData(): Int {
        val name = getUserName()
        val lastDate = getLastDate()
        val dayGroup = getDayGroup()

        if (name.isEmpty() && lastDate == 19981228) return 1
        if (name.isEmpty()) return 2
        if (dayGroup < 0 || dayGroup > 365) return 3
        return 0
    }

    private fun parseItemsJson(json: String): List<UserItem> {
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                UserItem(
                    name = obj.getString("name"),
                    type = obj.optString("type", "Attributes"),
                    iconEmoji = obj.optString("iconEmoji", if (obj.has("icon")) "\u2699" else "⚙"),
                    value = obj.optInt("value", 0),
                    abbr = obj.optString("abbr", ""),
                    rule = obj.optString("rule", ""),
                    levelExp = obj.optInt("levelExp", 1000),
                    priority = obj.optInt("priority", 0),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun itemsToJson(items: List<UserItem>): String {
        val arr = JSONArray()
        for (item in items) {
            arr.put(JSONObject().apply {
                put("name", item.name)
                put("type", item.type)
                put("iconEmoji", item.iconEmoji)
                put("value", item.value)
                put("abbr", item.abbr)
                put("rule", item.rule)
                put("levelExp", item.levelExp)
                put("priority", item.priority)
            })
        }
        return arr.toString()
    }
}
