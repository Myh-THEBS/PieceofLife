package com.archite.piecesoflife.ui

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.data.QuestFlag
import com.archite.piecesoflife.util.SpanTextBuilder
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil

enum class QuestItemMode { HOME, PLAN }

class QuestViewHolder(
    itemView: View,
    private val onQuestComplete: (LogEntity) -> Unit,
    private val onQuestFail: (LogEntity) -> Unit,
    private val onItemLongClick: (LogEntity) -> Unit,
) : RecyclerView.ViewHolder(itemView) {

    companion object {
        private const val FRAME_UNFINISHED = 34
        private const val FRAME_FINISHED = 35
        private const val FRAME_FAILED = 36
        private const val SETTLED_ALPHA = 0.45f
    }

    private val btnConfirm: ImageView = itemView.findViewById(R.id.btnQuestConfirm)
    private val btnColumn: LinearLayout = itemView.findViewById(R.id.questButtonColumn)
    private val tvQuestText: TextView = itemView.findViewById(R.id.tvQuestText)
    private var currentLog: LogEntity? = null
    private var currentMode = QuestItemMode.HOME

    init {
        btnConfirm.setOnClickListener {
            currentLog?.let { log ->
                if (currentMode == QuestItemMode.PLAN &&
                    (QuestFlag.isFinished(log.flag0) || QuestFlag.isFailed(log.flag0))) {
                    return@let
                }
                PixelDialog(itemView.context)
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

    fun bind(
        log: LogEntity,
        itemAbbrMap: Map<String, String> = emptyMap(),
        labelNames: Set<String> = emptySet(),
        pixelFont: Boolean = false,
        mode: QuestItemMode = QuestItemMode.HOME,
    ) {
        currentLog = log
        currentMode = mode
        val lastDate = TimeUtil.getTimeInt()
        val spannable = SpanTextBuilder.buildDisplayText(
            log, lastDate, itemAbbrMap, labelNames,
            plainText = mode == QuestItemMode.PLAN,
        )
        tvQuestText.text = spannable

        val pixelTypeface = if (pixelFont) ResourcesCompat.getFont(itemView.context, R.font.wqy_12px) else null
        tvQuestText.typeface = pixelTypeface
        tvQuestText.textSize = if (pixelFont) 20f else 18f
        tvQuestText.setTextColor(
            ContextCompat.getColor(
                itemView.context,
                if (mode == QuestItemMode.PLAN) R.color.TEXT_black else R.color.white,
            )
        )

        val settled = QuestFlag.isFinished(log.flag0) || QuestFlag.isFailed(log.flag0)

        if (mode == QuestItemMode.PLAN) {
            itemView.alpha = if (settled) SETTLED_ALPHA else 1f
            btnColumn.visibility = View.VISIBLE
            btnConfirm.visibility = View.VISIBLE
            btnConfirm.isEnabled = !settled
            val frame = when {
                QuestFlag.isFinished(log.flag0) -> FRAME_FINISHED
                QuestFlag.isFailed(log.flag0) -> FRAME_FAILED
                else -> FRAME_UNFINISHED
            }
            val sprite = SpriteDef.B24.frame(frame)
            SpriteLoader.setButton(
                btnConfirm, SpriteDef.B24.RES, sprite,
                scale = 5, downFrame = sprite,
            )
            btnConfirm.background =
                ContextCompat.getDrawable(itemView.context, R.drawable.click_background)
        } else {
            itemView.alpha = 1f
            btnConfirm.isEnabled = true
            if (settled) {
                btnColumn.visibility = View.GONE
            } else {
                btnColumn.visibility = View.VISIBLE
                btnConfirm.visibility = View.VISIBLE
                SpriteLoader.setButton(btnConfirm, SpriteDef.B24.RES, SpriteDef.B24.frame(8), 5)
            }
        }

        itemView.setOnLongClickListener {
            onItemLongClick(log)
            true
        }
    }
}
