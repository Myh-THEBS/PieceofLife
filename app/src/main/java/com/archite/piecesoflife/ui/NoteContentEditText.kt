package com.archite.piecesoflife.ui

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.util.SpannableUtil

class NoteContentEditText : androidx.appcompat.widget.AppCompatEditText {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    companion object {
        const val EDIT_OP_UNDO = 'U'
        const val EDIT_OP_REDO = 'R'
        const val EDIT_OP_SIZE = 'S'
        const val EDIT_OP_BOLD = 'B'
        const val EDIT_OP_UNDER = 'L'
        const val EDIT_OP_STRIKE = 'T'
        const val EDIT_OP_COLOR = 'C'

        private val FORMAT_REGEX = Regex("""(\d+)\+(\d+)\+(\w)\+?(.*)""")
        private const val MAX_HISTORY = 10
        private const val SNAPSHOT_DEBOUNCE_MS = 300L

        fun applyRemarkFormats(sb: SpannableStringBuilder, bodyOffset: Int, remark: String) {
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
                    "B" -> sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
                    "U" -> sb.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
                    "T" -> sb.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
                    "C" -> {
                        val color = value.toIntOrNull()
                        if (color != null) sb.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
                    }
                    "S" -> {
                        val size = value.toIntOrNull()
                        if (size != null) sb.setSpan(RelativeSizeSpan(size / 20f), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
                    }
                }
            }
        }
    }

    fun interface OnSelectionChangedListener {
        fun onSelectionChanged(selStart: Int, selEnd: Int)
    }

    private var selectionListener: OnSelectionChangedListener? = null

    fun setOnSelectionChangedListener(listener: OnSelectionChangedListener) {
        this.selectionListener = listener
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        selectionListener?.onSelectionChanged(selStart, selEnd)
    }

    fun interface OnTriggerDetectedListener {
        fun onTriggerDetected(trigger: String)
    }

    private var triggerListener: OnTriggerDetectedListener? = null

    fun setOnTriggerDetectedListener(listener: OnTriggerDetectedListener) {
        this.triggerListener = listener
    }

    private data class EditState(
        val spanText: SpannableStringBuilder,
        val selStart: Int,
        val selEnd: Int,
    )

    private lateinit var historySpan: MutableList<EditState>
    private var historyPointer = -1
    private lateinit var currentSpan: SpannableStringBuilder
    private var lastSnapshotTime = 0L
    private var isProgrammaticChange = false
    private var triggerActive = false
    var triggerInsertPos = -1
    fun resetTrigger() { triggerActive = false; triggerInsertPos = -1 }

    init {
        historySpan = mutableListOf()
        currentSpan = SpannableStringBuilder("")
    }

    fun loadFromLog(logText: String, remark: String) {
        val sb = SpannableStringBuilder(logText)
        applyRemarkFormats(sb, 0, remark)
        historySpan.clear()
        historyPointer = -1
        lastSnapshotTime = 0
        setText(sb)
        currentSpan = (text as? SpannableStringBuilder) ?: SpannableStringBuilder(text ?: "")
    }

    fun saveToLog(logEntity: LogEntity): LogEntity {
        return logEntity.copy(
            logText = text?.toString() ?: "",
            remark = getSpanFormatString(),
        )
    }

    fun getSpanFormatString(): String {
        if (currentSpan.isEmpty()) return ""
        val sb = StringBuilder()
        for (span in currentSpan.getSpans(0, currentSpan.length, StyleSpan::class.java)) {
            if (span.style and Typeface.BOLD != 0) {
                sb.append("${currentSpan.getSpanStart(span)}+${currentSpan.getSpanEnd(span)}+$EDIT_OP_BOLD ")
            }
        }
        for (span in currentSpan.getSpans(0, currentSpan.length, UnderlineSpan::class.java)) {
            sb.append("${currentSpan.getSpanStart(span)}+${currentSpan.getSpanEnd(span)}+$EDIT_OP_UNDER ")
        }
        for (span in currentSpan.getSpans(0, currentSpan.length, StrikethroughSpan::class.java)) {
            sb.append("${currentSpan.getSpanStart(span)}+${currentSpan.getSpanEnd(span)}+$EDIT_OP_STRIKE ")
        }
        for (span in currentSpan.getSpans(0, currentSpan.length, ForegroundColorSpan::class.java)) {
            sb.append("${currentSpan.getSpanStart(span)}+${currentSpan.getSpanEnd(span)}+$EDIT_OP_COLOR+${span.foregroundColor} ")
        }
        for (span in currentSpan.getSpans(0, currentSpan.length, RelativeSizeSpan::class.java)) {
            sb.append("${currentSpan.getSpanStart(span)}+${currentSpan.getSpanEnd(span)}+$EDIT_OP_SIZE+${(span.sizeChange * 20).toInt()} ")
        }
        return sb.toString().trimEnd()
    }

    fun applyCommand(operator: Char, value: Int = 0) {
        val selStart = selectionStart
        val selEnd = selectionEnd
        when (operator) {
            EDIT_OP_BOLD -> {
                if (!isBold())
                    SpannableUtil.toggleSpan<StyleSpan>(currentSpan, selStart, selEnd, StyleSpan::class.java, StyleSpan(Typeface.BOLD))
                else
                    SpannableUtil.toggleSpan<StyleSpan>(currentSpan, selStart, selEnd, StyleSpan::class.java, null)
            }
            EDIT_OP_UNDER -> {
                if (!isUnderline())
                    SpannableUtil.toggleSpan<UnderlineSpan>(currentSpan, selStart, selEnd, UnderlineSpan::class.java, UnderlineSpan())
                else
                    SpannableUtil.toggleSpan<UnderlineSpan>(currentSpan, selStart, selEnd, UnderlineSpan::class.java, null)
            }
            EDIT_OP_STRIKE -> {
                if (!isStrikethrough())
                    SpannableUtil.toggleSpan<StrikethroughSpan>(currentSpan, selStart, selEnd, StrikethroughSpan::class.java, StrikethroughSpan())
                else
                    SpannableUtil.toggleSpan<StrikethroughSpan>(currentSpan, selStart, selEnd, StrikethroughSpan::class.java, null)
            }
            EDIT_OP_COLOR -> {
                SpannableUtil.toggleSpan(currentSpan, selStart, selEnd, ForegroundColorSpan::class.java, ForegroundColorSpan(value))
            }
            EDIT_OP_SIZE -> {
                val proportion = value / 20f
                SpannableUtil.toggleSpan(currentSpan, selStart, selEnd, RelativeSizeSpan::class.java, RelativeSizeSpan(proportion))
            }
            EDIT_OP_UNDO -> {
                if (historyPointer > 0) {
                    historyPointer--
                    restoreSnapshot(historySpan[historyPointer])
                }
                triggerActive = false
                return
            }
            EDIT_OP_REDO -> {
                if (historyPointer < historySpan.size - 1) {
                    historyPointer++
                    restoreSnapshot(historySpan[historyPointer])
                }
                triggerActive = false
                return
            }
            else -> return
        }
        updateHistoryList()
    }

    private fun restoreSnapshot(state: EditState) {
        isProgrammaticChange = true
        setText(SpannableStringBuilder(state.spanText))
        isProgrammaticChange = false
        val len = state.spanText.length
        val safeStart = state.selStart.coerceIn(0, len)
        val safeEnd = state.selEnd.coerceIn(0, len)
        setSelection(safeStart, safeEnd)
        currentSpan = (text as? SpannableStringBuilder) ?: SpannableStringBuilder(text ?: "")
    }

    fun insertAtCursor(text: String) {
        val selStart: Int
        val selEnd: Int
        if (triggerInsertPos >= 0) {
            selStart = triggerInsertPos
            selEnd = selectionStart
            triggerInsertPos = -1
            triggerActive = false
        } else {
            selStart = selectionStart
            selEnd = selectionEnd
        }
        val sb = SpannableStringBuilder(currentSpan)
        sb.replace(selStart, selEnd, text)
        currentSpan = sb
        isProgrammaticChange = true
        setText(SpannableStringBuilder(sb))
        isProgrammaticChange = false
        setSelection(selStart + text.length)
        updateHistoryList()
    }

    fun isBold(): Boolean = SpannableUtil.hasSpan(currentSpan, selectionStart, selectionEnd, StyleSpan::class.java)
    fun isUnderline(): Boolean = SpannableUtil.hasSpan(currentSpan, selectionStart, selectionEnd, UnderlineSpan::class.java)
    fun isStrikethrough(): Boolean = SpannableUtil.hasSpan(currentSpan, selectionStart, selectionEnd, StrikethroughSpan::class.java)
    fun canUndo(): Boolean = historyPointer > 0
    fun canRedo(): Boolean = historyPointer < historySpan.size - 1
    fun isEmpty(): Boolean = currentSpan.isEmpty()

    private fun updateHistoryList() {
        while (historySpan.size > historyPointer + 1) {
            historySpan.removeAt(historySpan.lastIndex)
        }
        historySpan.add(EditState(SpannableStringBuilder(currentSpan), selectionStart, selectionEnd))
        if (historySpan.size > MAX_HISTORY) historySpan.removeAt(0)
        historyPointer = historySpan.size - 1
        lastSnapshotTime = System.currentTimeMillis()
        android.util.Log.d("NoteContent", "updateHistoryList done ptr=$historyPointer size=${historySpan.size}")
    }

    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int,
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)

        currentSpan = if (text is SpannableStringBuilder) text else SpannableStringBuilder(text ?: "")

        if (!::historySpan.isInitialized || isProgrammaticChange) return

        val now = System.currentTimeMillis()
        android.util.Log.d("NoteContent", "onTextChanged ptr=$historyPointer text='${text?.toString()}' start=$start lenB=$lengthBefore lenA=$lengthAfter spanSize=${historySpan.size}")
        if (now - lastSnapshotTime >= SNAPSHOT_DEBOUNCE_MS) {
            while (historySpan.size > historyPointer + 1) {
                historySpan.removeAt(historySpan.lastIndex)
            }
            historySpan.add(EditState(SpannableStringBuilder(currentSpan), selectionStart, selectionEnd))
            if (historySpan.size > MAX_HISTORY) historySpan.removeAt(0)
            historyPointer = historySpan.size - 1
            lastSnapshotTime = now
        } else {
            historySpan[historyPointer] = EditState(SpannableStringBuilder(currentSpan), selectionStart, selectionEnd)
        }

        if (triggerActive) return
        if (lengthAfter != 1 || lengthBefore >= 1) return
        val inserted = text?.get(start) ?: return
        if (inserted !in listOf('@', '#', '$')) return
        if (start < 1) return
        val prevChar = text?.get(start - 1) ?: return
        if (prevChar != inserted) return

        val trigger = "$inserted$inserted"
        triggerInsertPos = start - 1
        triggerActive = true
        triggerListener?.onTriggerDetected(trigger)
    }
}
