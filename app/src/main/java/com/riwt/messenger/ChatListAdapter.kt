package com.riwt.messenger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatListAdapter(
    private val onClick: (partnerId: String, partnerName: String) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.VH>() {

    private val items = mutableListOf<ChatRow>()

    fun submit(list: List<ChatRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        holder.title.text = row.partnerName
        holder.sub.text = row.lastMessage
        holder.itemView.setOnClickListener {
            onClick(row.partnerId, row.partnerName)
        }
    }

    override fun getItemCount() = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(android.R.id.text1)
        val sub: TextView = v.findViewById(android.R.id.text2)
    }
}
