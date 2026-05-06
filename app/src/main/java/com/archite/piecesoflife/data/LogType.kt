package com.archite.piecesoflife.data

object LogType {
    const val ERROR = -2
    const val DEBUG = -1
    const val HINT = 0
    const val DEFAULT = 1
    const val QUEST = 2
    const val TEMPLATE = 3
}

object QuestType {
    const val DEFAULT = -1
    const val MONTH = 0
    const val WEEK = 1
    const val DAY = 2
}

object QuestFlag {

    const val DEFAULT_UNFINISHED = 0
    const val MINUS_UNFINISHED = 1

    const val DEFAULT_FINISHED = 50
    const val MINUS_FINISHED = 51

    const val DEFAULT_FAILED = -50
    const val MINUS_FAILED = -49

    fun isFinished(flag0: Int): Boolean = flag0 in FINISHED_SET
    fun isFailed(flag0: Int): Boolean = flag0 in FAILED_SET
    fun isUnfinished(flag0: Int): Boolean = flag0 in UNFINISHED_SET
    fun isDefaultType(flag0: Int): Boolean = flag0 % 2 == 0
    fun isMinusType(flag0: Int): Boolean = !isDefaultType(flag0)

    fun reverseQuestState(flag0: Int): Int {
        return when (flag0) {
            DEFAULT_FINISHED -> DEFAULT_FAILED
            MINUS_FINISHED -> MINUS_FAILED
            DEFAULT_FAILED -> DEFAULT_FINISHED
            MINUS_FAILED -> MINUS_FINISHED
            else -> flag0
        }
    }

    private val UNFINISHED_SET = setOf(DEFAULT_UNFINISHED, MINUS_UNFINISHED)
    private val FINISHED_SET = setOf(DEFAULT_FINISHED, MINUS_FINISHED)
    private val FAILED_SET = setOf(DEFAULT_FAILED, MINUS_FAILED)
}
