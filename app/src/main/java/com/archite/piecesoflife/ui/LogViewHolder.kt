package com.archite.piecesoflife.ui

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.util.SpanTextBuilder
import com.archite.piecesoflife.util.TimeUtil

class LogViewHolder(
    itemView: View,
    private val onItemLongClick: (LogEntity) -> Unit,
) : RecyclerView.ViewHolder(itemView) {

    private val tvLogText: TextView = itemView.findViewById(R.id.tvLogText)

    fun bind(log: LogEntity, itemAbbrMap: Map<String, String> = emptyMap()) {
        val lastDate = TimeUtil.getTimeInt()
        val spannable = SpanTextBuilder.buildDisplayText(log, lastDate, itemAbbrMap)
        tvLogText.text = spannable
        itemView.setOnLongClickListener {
            onItemLongClick(log)
            true
        }
    }
}
