package com.stok.middleware.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.stok.middleware.databinding.ItemPendingRowBinding

data class PendingRow(val rfid: String, val count: Int)

class PendingRowAdapter : ListAdapter<PendingRow, PendingRowAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPendingRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(private val b: ItemPendingRowBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: PendingRow) {
            b.textRowRfid.text = item.rfid
            b.textRowCount.text = item.count.toString()
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PendingRow>() {
            override fun areItemsTheSame(a: PendingRow, b: PendingRow) = a.rfid == b.rfid
            override fun areContentsTheSame(a: PendingRow, b: PendingRow) = a == b
        }
    }
}
