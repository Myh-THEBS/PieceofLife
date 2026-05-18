package com.archite.piecesoflife.data

data class UserItem(
    val name: String,
    val type: String,
    val iconEmoji: String = "",
    val value: Int = 0,
    val abbr: String = "",
    val rule: String = "",
    val levelExp: Int = 1000,
    val priority: Int = 0,
) {
    companion object {
        const val TYPE_ATTRIBUTES = "Attributes"
        const val TYPE_SKILL = "Skill"
        const val TYPE_PHRASES = "Phrases"
    }
}
