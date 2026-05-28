package com.archite.piecesoflife.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 单条属性变更记录。
 *
 * 格式：{"abbr":"STP","inc":5,"dec":0}
 * 语义：inc 为该属性在文本中的正增量总和，dec 为负增量绝对值和（非负数）。
 * inc-dec 即为该属性在该日志中的净变化值。
 */
data class ItemDelta(
    val abbr: String,
    val inc: Int,
    val dec: Int,
)

/**
 * 封装日志文本中属性变更的解析和属性生效逻辑。
 *
 * 核心流程：
 *   解析：logText → List<ItemDelta> + JSON 序列化
 *   生效：List<ItemDelta> + ApplyMode → 修改 UserItem.value
 *   撤销：List<ItemDelta> × (-1) + ApplyMode → 还原 UserItem.value
 *
 * itemsJson 格式：[{"abbr":"STP","inc":5,"dec":0},...]
 */
object LogItemChange {

    /**
     * 从日志文本中解析属性变更，返回合并后的 ItemDelta 列表。
     * 仅匹配 knownAbbrs 中定义的缩写，其他文本中的字母组合不解析。
     * 同一属性在文本中出现多次则 inc/dec 分别累加。
     */
    fun parseLogText(logText: String, knownAbbrs: List<String> = emptyList()): List<ItemDelta> {
        if (knownAbbrs.isEmpty()) return emptyList()
        val groups = linkedMapOf<String, Pair<Int, Int>>()
        val pattern = knownAbbrs.map { Regex.escape(it) }.joinToString("|")
        val regex = Regex("""($pattern)([+-]\d{1,9})""")
        for (match in regex.findAll(logText)) {
            val abbr = match.groupValues[1]
            val delta = match.groupValues[2].toIntOrNull() ?: continue
            val (inc, dec) = groups.getOrPut(abbr) { 0 to 0 }
            if (delta >= 0) groups[abbr] = inc + delta to dec else groups[abbr] = inc to dec + (-delta)
        }
        return groups.map { (abbr, pair) -> ItemDelta(abbr, pair.first, pair.second) }
    }

    /**
     * 将 ItemDelta 列表序列化为 JSON 字符串。
     */
    fun toJson(deltas: List<ItemDelta>): String {
        val arr = JSONArray()
        for (d in deltas) {
            arr.put(JSONObject().apply {
                put("abbr", d.abbr)
                put("inc", d.inc)
                put("dec", d.dec)
            })
        }
        return arr.toString()
    }

    /**
     * 从 JSON 字符串反序列化为 ItemDelta 列表。
     */
    fun fromJson(json: String): List<ItemDelta> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                ItemDelta(
                    abbr = obj.getString("abbr"),
                    inc = obj.optInt("inc", 0),
                    dec = obj.optInt("dec", 0),
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    /**
     * 将属性变更列表应用到 UserItem 列表中。
     * 直接修改传入的 items（MutableList）。
     * 不包含具体任务的 UserItem 则跳过。
     */
    fun apply(
        deltas: List<ItemDelta>,
        mode: ApplyMode,
        items: MutableList<UserItem>,
    ) {
        for (d in deltas) {
            val idx = items.indexOfFirst { it.abbr == d.abbr }
            if (idx < 0) continue
            val delta = when (mode) {
                ApplyMode.ALL -> d.inc - d.dec
                ApplyMode.DEFAULT_SUCCESS -> d.inc - d.dec
                ApplyMode.DEFAULT_FAILURE -> 0
                ApplyMode.PENALTY_SUCCESS -> d.inc
                ApplyMode.PENALTY_FAILURE -> -d.dec
                ApplyMode.DELETE_ALL -> -d.inc + d.dec
                ApplyMode.UNFINISH_QUEST -> 0
            }
            if (delta != 0) {
                items[idx] = items[idx].copy(value = items[idx].value + delta)
            }
        }
    }

    /**
     * 撤销属性变更（取反后 apply）。
     */
    fun cancel(
        deltas: List<ItemDelta>,
        mode: ApplyMode,
        items: MutableList<UserItem>,
    ) {
        apply(deltas, mode.inverse(), items)
    }
}

/**
 * 属性生效模式。
 * 与 QuestFlag 的六种状态对应。
 */
enum class ApplyMode {
    ALL,
    DELETE_ALL,
    DEFAULT_SUCCESS,
    DEFAULT_FAILURE,
    PENALTY_SUCCESS,
    PENALTY_FAILURE,
    UNFINISH_QUEST;

    fun inverse(): ApplyMode = when (this) {
        ALL -> DELETE_ALL
        DEFAULT_SUCCESS -> DELETE_ALL
        DEFAULT_FAILURE -> DEFAULT_SUCCESS
        PENALTY_SUCCESS -> PENALTY_FAILURE
        PENALTY_FAILURE -> PENALTY_SUCCESS
        DELETE_ALL -> DELETE_ALL
        UNFINISH_QUEST -> UNFINISH_QUEST
    }

    companion object {
        fun fromQuest(logType: Int, flag0: Int): ApplyMode {
            val isQuest = logType == LogType.QUEST
            if (!isQuest) return ALL
            val finished = QuestFlag.isFinished(flag0)
            val failed = QuestFlag.isFailed(flag0)
            if (!finished && !failed) return UNFINISH_QUEST
            val isMinus = QuestFlag.isMinusType(flag0)
            return when {
                finished && !isMinus -> DEFAULT_SUCCESS
                finished && isMinus -> PENALTY_SUCCESS
                failed && !isMinus -> DEFAULT_FAILURE
                failed && isMinus -> PENALTY_FAILURE
                else -> ALL
            }
        }
    }
}
