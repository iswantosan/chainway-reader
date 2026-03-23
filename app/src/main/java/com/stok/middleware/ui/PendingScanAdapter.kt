package com.stok.middleware.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.stok.middleware.R
import com.stok.middleware.data.model.PendingScanRow
import com.stok.middleware.data.model.PendingScanState
import com.stok.middleware.data.model.ScanMode
import com.stok.middleware.databinding.ItemPendingScanBinding

class PendingScanAdapter : ListAdapter<PendingScanRow, PendingScanAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPendingScanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(private val binding: ItemPendingScanBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: PendingScanRow) {
            binding.itemScanId.text = "#${row.shortId()}"
            binding.itemScanTime.text = row.createdAt
            binding.itemScanValue.text = row.value
            binding.itemScanMode.text = when (row.mode) {
                ScanMode.KEYBOARD -> "KEYBOARD"
                ScanMode.RFID -> "RFID"
            }
            val (stateText, colorRes) = when (row.state) {
                PendingScanState.PENDING -> itemView.context.getString(R.string.pending_state_pending) to R.color.status_local
                PendingScanState.SENDING -> itemView.context.getString(R.string.pending_state_sending) to R.color.primary_dark
                PendingScanState.SENT -> itemView.context.getString(R.string.pending_state_sent) to R.color.status_sent
                PendingScanState.FAILED -> {
                    val d = row.serverMessage?.let { " — $it" } ?: ""
                    (itemView.context.getString(R.string.pending_state_failed) + d) to R.color.status_failed
                }
            }
            binding.itemScanState.text = stateText
            binding.itemScanState.setTextColor(
                ContextCompat.getColor(itemView.context, colorRes)
            )
        }
    }

    private object Diff : DiffUtil.ItemCallback<PendingScanRow>() {
        override fun areItemsTheSame(a: PendingScanRow, b: PendingScanRow) = a.localId == b.localId
        override fun areContentsTheSame(a: PendingScanRow, b: PendingScanRow) = a == b
    }
}
