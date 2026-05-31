package com.archite.piecesoflife.ui

import android.content.Intent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.data.LogType
import com.archite.piecesoflife.util.ImageUtil
import com.archite.piecesoflife.util.SpanTextBuilder
import com.archite.piecesoflife.util.TimeUtil
import java.io.File

class LogViewHolder(
    itemView: View,
    private val onItemLongClick: (LogEntity) -> Unit,
) : RecyclerView.ViewHolder(itemView) {

    private val tvLogText: TextView = itemView.findViewById(R.id.tvLogText)
    private val containerPicture: View = itemView.findViewById(R.id.containerPicture)
    private val ivPictureLarge: ImageView = itemView.findViewById(R.id.ivPictureLarge)
    private val ivPictureSmall: ImageView = itemView.findViewById(R.id.ivPictureSmall)
    private val tvDocPreview: TextView = itemView.findViewById(R.id.tvDocPreview)
    private var currentLog: LogEntity? = null

    fun bind(log: LogEntity, itemAbbrMap: Map<String, String> = emptyMap()) {
        currentLog = log
        val lastDate = TimeUtil.getTimeInt()

        containerPicture.visibility = View.GONE
        tvDocPreview.visibility = View.GONE
        ivPictureLarge.visibility = View.GONE
        ivPictureSmall.visibility = View.GONE

        when (log.logType) {
            LogType.PICTURE -> {
                val spannable = SpanTextBuilder.buildDisplayText(log, lastDate, itemAbbrMap)
                tvLogText.text = spannable
                containerPicture.visibility = View.VISIBLE
                loadPictureThumbnail(log.remark)
            }
            LogType.DOCUMENT -> {
                val displayName = extractDisplayName(log.remark)
                val displayLog = log.copy(logText = "📄 $displayName")
                val spannable = SpanTextBuilder.buildDisplayText(displayLog, lastDate, itemAbbrMap)
                tvLogText.text = spannable
                tvDocPreview.visibility = View.VISIBLE
                tvDocPreview.text = log.logText.ifEmpty { "(空文档)" }
                tvDocPreview.setOnClickListener { openDocEditor(log) }
            }
            else -> {
                val spannable = SpanTextBuilder.buildDisplayText(log, lastDate, itemAbbrMap)
                tvLogText.text = spannable
            }
        }

        itemView.setOnLongClickListener {
            onItemLongClick(log)
            true
        }
    }

    private fun loadPictureThumbnail(remark: String) {
        if (remark.isEmpty()) return
        val imageFileName = File(remark).name
        val context = itemView.context
        val bitmap = ImageUtil.loadThumbnail(context, imageFileName)
        if (bitmap != null) {
            val rounded = ImageUtil.drawRoundCornerBitmap(bitmap, 16f)
            ivPictureLarge.setImageBitmap(rounded)
            ivPictureSmall.setImageBitmap(rounded)
            ivPictureLarge.visibility = View.VISIBLE
            ivPictureLarge.setOnClickListener { openImagePreview(imageFileName, currentLog?.id ?: -1L) }
            ivPictureSmall.setOnClickListener { openImagePreview(imageFileName, currentLog?.id ?: -1L) }
        }
    }

    private fun openImagePreview(imageFileName: String, logId: Long = -1L) {
        val context = itemView.context
        val intent = Intent(context, ImagePreviewActivity::class.java).apply {
            putExtra(ImagePreviewActivity.EXTRA_IMAGE_FILE_NAME, imageFileName)
            if (logId > 0) putExtra(ImagePreviewActivity.EXTRA_LOG_ID, logId)
        }
        context.startActivity(intent)
    }

    private fun openDocEditor(log: LogEntity) {
        val context = itemView.context
        val intent = Intent(context, LogEditorActivity::class.java).apply {
            putExtra(LogEditorActivity.EXTRA_LOG_ID, log.id)
        }
        context.startActivity(intent)
    }

    private fun extractDisplayName(remark: String): String {
        if (remark.isEmpty()) return "文档"
        val fileName = File(remark).name
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val parts = nameWithoutExt.split("_")
        return if (parts.size >= 3) {
            parts.drop(2).joinToString("_") + "." + fileName.substringAfterLast(".")
        } else {
            fileName
        }
    }
}
