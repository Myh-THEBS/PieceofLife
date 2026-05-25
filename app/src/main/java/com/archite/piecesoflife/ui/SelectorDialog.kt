package com.archite.piecesoflife.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import com.archite.piecesoflife.R
import com.archite.piecesoflife.databinding.DialogSelectorBinding
import com.archite.piecesoflife.util.SpriteLoader
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt

class SelectorDialog(context: Context) : Dialog(context) {

    private val binding: DialogSelectorBinding = DialogSelectorBinding.inflate(layoutInflater)

    private var onItemSelectedAction: ((String, Int) -> Unit)? = null

    init {
        setContentView(binding.root)
        setupWindow()
        renderBackgrounds()
    }

    fun setTitle(text: String): SelectorDialog {
        binding.dialogTitle.text = text
        return this
    }

    fun setItems(items: List<String>): SelectorDialog {
        buildItemList(items)
        return this
    }

    fun setCenteredItems(items: List<String>): SelectorDialog {
        buildCenteredItemList(items)
        return this
    }

    fun setColorItems(items: List<String>): SelectorDialog {
        buildColorItemList(items)
        return this
    }

    fun setItemsWithRemark(items: List<Pair<String, String>>): SelectorDialog {
        buildItemListWithRemark(items)
        return this
    }

    fun onItemSelected(callback: (value: String, index: Int) -> Unit): SelectorDialog {
        onItemSelectedAction = callback
        return this
    }

    fun setCancelableOutside(cancelable: Boolean): SelectorDialog {
        setCanceledOnTouchOutside(cancelable)
        return this
    }

    override fun show() {
        renderIcon()
        super.show()
    }

    private fun setupWindow() {
        window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            val displayMetrics = context.resources.displayMetrics
            setLayout(
                (280 * displayMetrics.density).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setGravity(Gravity.CENTER)
            attributes = attributes?.apply {
                width = (280 * displayMetrics.density).toInt()
                dimAmount = 0.6f
            }
        }
    }

    private fun renderBackgrounds() {
        binding.dialogRoot.background = SpriteLoader.background48(index = 0, pixelScale = 5)
    }

    private fun renderIcon() {
        SpriteLoader.setIcon(binding.dialogIcon, index = 4, scale = 5)
    }

    private fun buildItemList(items: List<String>) {
        binding.listContainer.removeAllViews()
        for ((index, item) in items.withIndex()) {
            val itemView = layoutInflater.inflate(R.layout.item_selector_entry, binding.listContainer, false)
            itemView.findViewById<TextView>(R.id.itemTitle).text = item
            itemView.findViewById<TextView>(R.id.itemRemark).visibility = android.view.View.GONE
            itemView.setOnClickListener {
                onItemSelectedAction?.invoke(item, index)
                dismiss()
            }
            binding.listContainer.addView(itemView)
            addDividerIfNeeded(index, items.size)
        }
    }

    private fun buildCenteredItemList(items: List<String>) {
        binding.listContainer.removeAllViews()
        for ((index, item) in items.withIndex()) {
            val itemView = layoutInflater.inflate(R.layout.item_selector_entry, binding.listContainer, false)
            val titleView = itemView.findViewById<TextView>(R.id.itemTitle)
            titleView.text = item
            titleView.gravity = Gravity.CENTER
            itemView.findViewById<TextView>(R.id.itemRemark).visibility = android.view.View.GONE
            itemView.setOnClickListener {
                onItemSelectedAction?.invoke(item, index)
                dismiss()
            }
            binding.listContainer.addView(itemView)
            addDividerIfNeeded(index, items.size)
        }
    }

    private fun buildColorItemList(items: List<String>) {
        binding.listContainer.removeAllViews()
        for ((index, item) in items.withIndex()) {
            val itemView = layoutInflater.inflate(R.layout.item_selector_entry, binding.listContainer, false)
            val titleView = itemView.findViewById<TextView>(R.id.itemTitle)
            titleView.text = item
            titleView.gravity = Gravity.CENTER
            val hashIdx = item.indexOf("#")
            if (hashIdx >= 0) {
                try { titleView.setTextColor(Color.parseColor(item.substring(hashIdx))) } catch (_: Exception) {}
            }
            itemView.findViewById<TextView>(R.id.itemRemark).visibility = android.view.View.GONE
            itemView.setOnClickListener {
                onItemSelectedAction?.invoke(item, index)
                dismiss()
            }
            binding.listContainer.addView(itemView)
            addDividerIfNeeded(index, items.size)
        }
    }

    private fun buildItemListWithRemark(items: List<Pair<String, String>>) {
        binding.listContainer.removeAllViews()
        for ((index, pair) in items.withIndex()) {
            val (title, remark) = pair
            val itemView = layoutInflater.inflate(R.layout.item_selector_entry, binding.listContainer, false)
            itemView.findViewById<TextView>(R.id.itemTitle).text = title
            val remarkView = itemView.findViewById<TextView>(R.id.itemRemark)
            remarkView.text = remark
            remarkView.visibility = android.view.View.VISIBLE
            itemView.setOnClickListener {
                onItemSelectedAction?.invoke(title, index)
                dismiss()
            }
            binding.listContainer.addView(itemView)
            addDividerIfNeeded(index, items.size)
        }
    }

    private fun addDividerIfNeeded(index: Int, total: Int) {
        if (index < total - 1) {
            val divider = android.widget.LinearLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (2 * context.resources.displayMetrics.density).toInt(),
                )
                setBackgroundColor("#C7C7C7".toColorInt())
            }
            binding.listContainer.addView(divider)
        }
    }
}
