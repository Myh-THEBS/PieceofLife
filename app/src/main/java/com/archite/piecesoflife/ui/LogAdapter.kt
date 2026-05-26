package com.archite.piecesoflife.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.data.LogType

class LogAdapter(
    private val onQuestComplete: (LogEntity) -> Unit,
    private val onQuestFail: (LogEntity) -> Unit,
    private val onItemLongClick: (LogEntity) -> Unit,
    var itemAbbrMap: Map<String, String> = emptyMap(),
) : ListAdapter<LogEntity, RecyclerView.ViewHolder>(LogDiffCallback()) {

    companion object {
        const val VIEW_TYPE_LOG = 0
        const val VIEW_TYPE_QUEST = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).logType) {
            LogType.QUEST -> VIEW_TYPE_QUEST
            else -> VIEW_TYPE_LOG
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_QUEST -> {
                val view = inflater.inflate(R.layout.item_quest_entry, parent, false)
                QuestViewHolder(view, onQuestComplete, onQuestFail, onItemLongClick)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_log_entry, parent, false)
                LogViewHolder(view, onItemLongClick)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is LogViewHolder -> holder.bind(item, itemAbbrMap)
            is QuestViewHolder -> holder.bind(item, itemAbbrMap)
        }
    }
}

class LogDiffCallback : DiffUtil.ItemCallback<LogEntity>() {
    override fun areItemsTheSame(oldItem: LogEntity, newItem: LogEntity): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: LogEntity, newItem: LogEntity): Boolean =
        oldItem == newItem
}
