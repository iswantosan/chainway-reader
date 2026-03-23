package com.stok.middleware.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.stok.middleware.R
import com.stok.middleware.data.model.LogStatus
import com.stok.middleware.data.model.ScanLogItem
import androidx.core.content.ContextCompat
import com.stok.middleware.databinding.ItemScanLogBinding

class ScanLogAdapter(
    private val onCopyItem: ((ScanLogItem) -> Unit)? = null
) : ListAdapter<ScanLogItem, ScanLogAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScanLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onCopyItem)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemScanLogBinding,
        private val onCopyItem: ((ScanLogItem) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScanLogItem) {
            binding.itemTimestamp.text = item.timestamp
            binding.itemMode.text = item.mode.name
            binding.itemValue.text = item.value
            val statusText = item.status.name + (item.detail?.let { " - $it" } ?: "")
            binding.itemStatus.text = statusText
            binding.itemStatus.setTextColor(
                when (item.status) {
                    LogStatus.SENT -> ContextCompat.getColor(binding.root.context, R.color.status_sent)
                    LogStatus.FAILED -> ContextCompat.getColor(binding.root.context, R.color.status_failed)
                    LogStatus.LOCAL_ONLY -> ContextCompat.getColor(binding.root.context, R.color.status_local)
                    else -> binding.itemTimestamp.currentTextColor
                }
            )
            binding.root.setOnLongClickListener {
                onCopyItem?.invoke(item)
                true
            }
        }
    }

    companion object {
        fun formatLogLine(item: ScanLogItem): String {
            val detail = item.detail?.let { " - $it" } ?: ""
            return "${item.timestamp}\t${item.mode.name}\t${item.value}\t${item.status.name}$detail"
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ScanLogItem>() {
        override fun areItemsTheSame(a: ScanLogItem, b: ScanLogItem) = a.id == b.id
        override fun areContentsTheSame(a: ScanLogItem, b: ScanLogItem) = a == b
    }
}
