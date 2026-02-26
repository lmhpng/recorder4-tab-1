package com.voicerecorder.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val layoutUser: LinearLayout = view.findViewById(R.id.layoutUser)
        val layoutAi: LinearLayout = view.findViewById(R.id.layoutAi)
        val tvUserMsg: TextView = view.findViewById(R.id.tvUserMsg)
        val tvAiMsg: TextView = view.findViewById(R.id.tvAiMsg)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = messages[position]
        if (msg.role == "user") {
            holder.layoutUser.visibility = View.VISIBLE
            holder.layoutAi.visibility = View.GONE
            holder.tvUserMsg.text = msg.content
        } else {
            holder.layoutUser.visibility = View.GONE
            holder.layoutAi.visibility = View.VISIBLE
            holder.tvAiMsg.text = msg.content
        }
    }

    override fun getItemCount() = messages.size
}
