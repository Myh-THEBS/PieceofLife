package com.archite.piecesoflife.data

data class UserItem(
    val name: String,
    val type: String,
    val icon: Int,
    val value: Int
) {
    companion object {
        const val TYPE_ATTRIBUTES = "Attributes"
        const val TYPE_SKILL = "Skill"
        const val TYPE_PHRASES = "Phrases"
    }
}
