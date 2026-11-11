package com.example.applock

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch

data class AppItem(
    val pkg: String,
    val label: String,
    val icon: Drawable,
    var locked: Boolean
)

class AppAdapter(
    private val onToggle: (AppItem, Boolean) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var items: List<AppItem> = emptyList()

    fun submit(list: List<AppItem>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.ivIcon)
        val label: TextView = v.findViewById(R.id.tvLabel)
        val sw: MaterialSwitch = v.findViewById(R.id.sw)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.icon.setImageDrawable(item.icon)
        h.label.text = item.label
        h.sw.setOnCheckedChangeListener(null)
        h.sw.isChecked = item.locked
        h.sw.setOnCheckedChangeListener { _, checked ->
            item.locked = checked
            onToggle(item, checked)
        }
        h.itemView.setOnClickListener { h.sw.toggle() }
    }
}
