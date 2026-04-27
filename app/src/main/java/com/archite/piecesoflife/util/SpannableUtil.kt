package com.archite.piecesoflife.util

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan

object SpannableUtil {

    fun <T> toggleSpan(
        editable: SpannableStringBuilder,
        selStart: Int,
        selEnd: Int,
        clazz: Class<T>,
        newSpan: T?
    ) {
        require(selStart <= selEnd) { "selStart > selEnd" }

        val oldSpans = editable.getSpans(selStart, selEnd, clazz)
        for (s in oldSpans) {
            val sStart = editable.getSpanStart(s)
            val sEnd = editable.getSpanEnd(s)
            editable.removeSpan(s)
            if (sStart < selStart) {
                editable.setSpan(cloneSpan(s), sStart, selStart, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
            }
            if (sEnd > selEnd) {
                editable.setSpan(cloneSpan(s), selEnd, sEnd, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
            }
        }

        if (newSpan != null) {
            editable.setSpan(newSpan, selStart, selEnd, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        }
    }

    fun <T> hasSpan(e: SpannableStringBuilder, start: Int, end: Int, clazz: Class<T>): Boolean {
        val spans = e.getSpans(start, end, clazz)
        return spans.isNotEmpty()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> cloneSpan(span: T): T {
        return when (span) {
            is StyleSpan -> StyleSpan(span.style) as T
            is ForegroundColorSpan -> ForegroundColorSpan(span.foregroundColor) as T
            is RelativeSizeSpan -> RelativeSizeSpan(span.sizeChange) as T
            is StrikethroughSpan -> StrikethroughSpan() as T
            is UnderlineSpan -> UnderlineSpan() as T
            else -> throw IllegalArgumentException("请给 ${span!!::class.java.simpleName} 写克隆逻辑")
        }
    }
}
