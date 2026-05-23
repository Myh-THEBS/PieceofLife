package com.archite.piecesoflife.util

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.data.LogType
import com.archite.piecesoflife.data.QuestFlag
import androidx.core.graphics.toColorInt

object SpanTextBuilder {

    private val FORMAT_REGEX = Regex("""(\d+)\+(\d+)\+(\w+)\+?(.*)""")

    fun buildDisplayText(log: LogEntity, lastDate: Int, itemAbbrMap: Map<String, String> = emptyMap()): SpannableStringBuilder {
        val sb = SpannableStringBuilder()

        if (log.logType == LogType.HINT && log.flag0 == 999) {
            val dateStr = TimeUtil.dateInt2String(log.buildDate)
            sb.append(dateStr)
            sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, sb.length, 0)
            sb.setSpan(RelativeSizeSpan(1.2f), 0, sb.length, 0)
            return sb
        }

        val timeStr = when (log.logType) {
            LogType.DEBUG, LogType.ERROR -> ""
            LogType.QUEST -> buildQuestPrefix(log, lastDate)
            LogType.HINT -> ""
            else -> TimeUtil.timeInt2String(log.buildTime)
        }

        if (timeStr.isNotEmpty()) {
            val prefix = "$timeStr"
            sb.append(prefix)
            when (log.logType) {
                LogType.DEFAULT -> sb.setSpan(ForegroundColorSpan(Color.LTGRAY), 0, sb.length, 0)
                LogType.QUEST -> {
                    if (QuestFlag.isFinished(log.flag0)) sb.setSpan(ForegroundColorSpan("#2E7D32".toColorInt()), 0, sb.length, 0)
                    else if (QuestFlag.isFailed(log.flag0)) sb.setSpan(ForegroundColorSpan("#C62828".toColorInt()), 0, sb.length, 0)
                }
            }
        }

        val bodyStart = sb.length
        val bodyText = if (itemAbbrMap.isNotEmpty()) {
            var text = log.logText
            for ((abbr, emoji) in itemAbbrMap) {
                text = text.replace(Regex("""(?<![a-zA-Z])${abbr}(?=[+\-*/=\s]|$)""")) { "${emoji}${it.value}" }
            }
            text
        } else {
            log.logText
        }
        sb.append(bodyText)
        applyRemarkFormats(sb, bodyStart, log.remark)
        return sb
    }

    private fun buildQuestPrefix(log: LogEntity, lastDate: Int): String {
        return when {
            QuestFlag.isFinished(log.flag0) -> "【任务完成】"
            QuestFlag.isFailed(log.flag0) -> "【任务失败】"
            log.changeDate >= 30000000 -> "【长期任务】"
            else -> {
                val daysLeft = TimeUtil.dateIntMinus(lastDate, log.changeDate)
                if (daysLeft == 0) "【最后一天】"
                else "【剩余${daysLeft}天】"
            }
        }
    }

    private fun applyRemarkFormats(sb: SpannableStringBuilder, bodyOffset: Int, remark: String) {
        if (remark.isBlank()) return
        val parts = remark.trim().split(" ")
        for (part in parts) {
            val match = FORMAT_REGEX.find(part) ?: continue
            val s = match.groupValues[1].toIntOrNull() ?: continue
            val e = match.groupValues[2].toIntOrNull() ?: continue
            val start = bodyOffset + s
            val end = bodyOffset + e
            if (start < 0 || end > sb.length || start >= end) continue
            val type = match.groupValues[3]
            val value = match.groupValues[4]
            when (type) {
                "B" -> sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, end, 0)
                "U" -> sb.setSpan(UnderlineSpan(), start, end, 0)
                "S" -> sb.setSpan(StrikethroughSpan(), start, end, 0)
                "C" -> {
                    val color = value.toIntOrNull()
                    if (color != null) sb.setSpan(ForegroundColorSpan(color), start, end, 0)
                }
                "SIZE" -> {
                    val size = value.toFloatOrNull()
                    if (size != null) sb.setSpan(RelativeSizeSpan(size), start, end, 0)
                }
            }
        }
    }
}
