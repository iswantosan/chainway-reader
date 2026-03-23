package com.stok.middleware.ui

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Dipakai untuk [RecyclerView] ber-tinggi `wrap_content` di dalam [ScrollView]/NestedScrollView.
 * Tanpa ini, RV sering hanya mengukur ±1 layar sehingga konten terpotong dan parent tidak bisa scroll.
 */
class FullyExpandedLinearLayoutManager(context: Context) : LinearLayoutManager(context) {

    override fun onMeasure(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        widthSpec: Int,
        heightSpec: Int
    ) {
        val mode = View.MeasureSpec.getMode(heightSpec)
        if (mode == View.MeasureSpec.UNSPECIFIED || mode == View.MeasureSpec.AT_MOST) {
            val expandedHeight = View.MeasureSpec.makeMeasureSpec(
                Int.MAX_VALUE shr 2,
                View.MeasureSpec.AT_MOST
            )
            super.onMeasure(recycler, state, widthSpec, expandedHeight)
        } else {
            super.onMeasure(recycler, state, widthSpec, heightSpec)
        }
    }
}
