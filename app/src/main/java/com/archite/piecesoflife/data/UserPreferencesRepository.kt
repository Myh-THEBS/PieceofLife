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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_DEBUG_MODE = booleanPreferencesKey("debug_mode")
        private val KEY_LAST_DATE = intPreferencesKey("last_date")
        private val KEY_USER_EXP = intPreferencesKey("user_exp")
        private val KEY_DAY_GROUP = intPreferencesKey("day_group")
        private val KEY_ITEMS_JSON = stringPreferencesKey("items_json")
        private val KEY_USER_AVATAR = intPreferencesKey("user_avatar")

        private val DEFAULT_ITEMS_JSON = """
            [
                {"name": "STP", "type": "Attributes", "icon": 0, "value": 0},
                {"name": "HP", "type": "Attributes", "icon": 0, "value": 0},
                {"name": "LUK", "type": "Attributes", "icon": 0, "value": 0},
                {"name": "惩罚，STP-", "type": "Phrases", "icon": 0, "value": 0},
                {"name": "学习，STP+", "type": "Phrases", "icon": 0, "value": 0}
            ]
        """.trimIndent()
    }

    val userNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_NAME] ?: ""
    }

    val debugModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEBUG_MODE] ?: false
    }

    val lastDateFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_DATE] ?: 19981228
    }

    val userExpFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_EXP] ?: 0
    }

    val dayGroupFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_DAY_GROUP] ?: 2
    }

    val userAvatarFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_AVATAR] ?: 0
    }

    val itemsFlow: Flow<List<UserItem>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_ITEMS_JSON] ?: DEFAULT_ITEMS_JSON
        parseItemsJson(json)
    }

    suspend fun getUserName(): String = context.dataStore.data.first()[KEY_USER_NAME] ?: ""
    suspend fun getDebugMode(): Boolean = context.dataStore.data.first()[KEY_DEBUG_MODE] ?: false
    suspend fun getLastDate(): Int = context.dataStore.data.first()[KEY_LAST_DATE] ?: 19981228
    suspend fun getUserExp(): Int = context.dataStore.data.first()[KEY_USER_EXP] ?: 0
    suspend fun getDayGroup(): Int = context.dataStore.data.first()[KEY_DAY_GROUP] ?: 2
    suspend fun getUserAvatar(): Int = context.dataStore.data.first()[KEY_USER_AVATAR] ?: 0

    suspend fun getItems(): List<UserItem> {
        val json = context.dataStore.data.first()[KEY_ITEMS_JSON] ?: DEFAULT_ITEMS_JSON
        return parseItemsJson(json)
    }

    suspend fun setUserName(name: String) = context.dataStore.edit { it[KEY_USER_NAME] = name }
    suspend fun setDebugMode(enabled: Boolean) = context.dataStore.edit { it[KEY_DEBUG_MODE] = enabled }
    suspend fun setLastDate(date: Int) = context.dataStore.edit { it[KEY_LAST_DATE] = date }
    suspend fun setUserExp(exp: Int) = context.dataStore.edit { it[KEY_USER_EXP] = exp }
    suspend fun setDayGroup(group: Int) = context.dataStore.edit { it[KEY_DAY_GROUP] = group }
    suspend fun setUserAvatar(avatarIndex: Int) = context.dataStore.edit { it[KEY_USER_AVATAR] = avatarIndex }

    suspend fun setItems(items: List<UserItem>) = context.dataStore.edit {
        it[KEY_ITEMS_JSON] = itemsToJson(items)
    }

    suspend fun updateAll(
        userName: String? = null,
        debugMode: Boolean? = null,
        lastDate: Int? = null,
        userExp: Int? = null,
        dayGroup: Int? = null,
        userAvatar: Int? = null,
        items: List<UserItem>? = null
    ) = context.dataStore.edit { prefs ->
        userName?.let { prefs[KEY_USER_NAME] = it }
        debugMode?.let { prefs[KEY_DEBUG_MODE] = it }
        lastDate?.let { prefs[KEY_LAST_DATE] = it }
        userExp?.let { prefs[KEY_USER_EXP] = it }
        dayGroup?.let { prefs[KEY_DAY_GROUP] = it }
        userAvatar?.let { prefs[KEY_USER_AVATAR] = it }
        items?.let { prefs[KEY_ITEMS_JSON] = itemsToJson(it) }
    }

    suspend fun resetToDefaults() = context.dataStore.edit { prefs ->
        prefs[KEY_USER_NAME] = "新用户"
        prefs[KEY_DEBUG_MODE] = true
        prefs[KEY_LAST_DATE] = 20250101
        prefs[KEY_USER_EXP] = 0
        prefs[KEY_DAY_GROUP] = 1
        prefs[KEY_USER_AVATAR] = 0
        prefs[KEY_ITEMS_JSON] = DEFAULT_ITEMS_JSON
    }

    suspend fun validateUserData(): Int {
        val name = getUserName()
        val lastDate = getLastDate()
        val userExp = getUserExp()
        val dayGroup = getDayGroup()

        if (userExp == -114514 && name.isEmpty() && lastDate == 19981228) return 1
        if (name.isEmpty()) return 2
        if (dayGroup < 0 || dayGroup > 365) return 3
        if (userExp < 0) return 4
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
                    icon = obj.optInt("icon", 0),
                    value = obj.optInt("value", 0)
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
                put("icon", item.icon)
                put("value", item.value)
            })
        }
        return arr.toString()
    }
}
