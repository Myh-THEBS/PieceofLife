package com.archite.piecesoflife.ui

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.data.QuestFlag
import com.archite.piecesoflife.util.SpanTextBuilder
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil

class QuestViewHolder(
    itemView: View,
    private val onQuestComplete: (LogEntity) -> Unit,
    private val onQuestFail: (LogEntity) -> Unit,
) : RecyclerView.ViewHolder(itemView) {

    private val btnConfirm: ImageView = itemView.findViewById(R.id.btnQuestConfirm)
    private val btnColumn: LinearLayout = itemView.findViewById(R.id.questButtonColumn)
    private val tvQuestText: TextView = itemView.findViewById(R.id.tvQuestText)
    private var currentLog: LogEntity? = null

    init {
        btnConfirm.setOnClickListener {
            currentLog?.let { log ->
                val context = itemView.context
                PixelDialog(context)
                    .setType(PixelDialog.DialogType.WARN)
                    .setButtons(PixelDialog.ButtonMode.TRIPLE_RETURN_FAIL_SUCCESS)
                    .setTitle("任务完成确认")
                    .setMessage("请确认任务是否完成？")
                    .onConfirm { onQuestComplete(log) }
                    .onCancel { }
                    .onFail { onQuestFail(log) }
                    .show()
            }
        }
    }

    fun bind(log: LogEntity, itemAbbrMap: Map<String, String> = emptyMap()) {
        currentLog = log
        val lastDate = TimeUtil.getTimeInt()
        val spannable = SpanTextBuilder.buildDisplayText(log, lastDate, itemAbbrMap)
        tvQuestText.text = spannable

        if (QuestFlag.isFinished(log.flag0) || QuestFlag.isFailed(log.flag0)) {
            btnColumn.visibility = View.GONE
        } else {
            btnConfirm.visibility = View.VISIBLE
            SpriteLoader.setButton(btnConfirm,
                SpriteDef.B24.RES, SpriteDef.B24.frame(8), 5)
        }
    }
}
