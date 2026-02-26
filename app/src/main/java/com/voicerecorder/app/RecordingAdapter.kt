package com.voicerecorder.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecordingAdapter(
    private val recordings: List<Recording>,
    private val onPlay: (Recording) -> Unit,
    private val onDelete: (Recording) -> Unit,
    private val onTranscribe: (Recording) -> Unit,
    private val onSummarize: (Recording) -> Unit,
    private val onLongPress: (Recording) -> Unit
) : RecyclerView.Adapter<RecordingAdapter.ViewHolder>() {

    private var playingId: String? = null

    fun setPlaying(id: String?) { playingId = id; notifyDataSetChanged() }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        val tvTranscriptPreview: TextView = view.findViewById(R.id.tvTranscriptPreview)
        val layoutTodos: LinearLayout = view.findViewById(R.id.layoutTodos)
        val tvTodosPreview: TextView = view.findViewById(R.id.tvTodosPreview)
        val btnPlay: LinearLayout = view.findViewById(R.id.btnPlay)
        val ivPlayIcon: ImageView = view.findViewById(R.id.ivPlayIcon)
        val tvPlayText: TextView = view.findViewById(R.id.tvPlayText)
        val btnTranscribe: LinearLayout = view.findViewById(R.id.btnTranscribe)
        val btnSummarize: LinearLayout = view.findViewById(R.id.btnSummarize)
        val btnDelete: LinearLayout = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_recording, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val r = recordings[position]
        holder.tvName.text = r.name
        holder.tvDate.text = r.date
        holder.tvDuration.text = r.duration

        // 转写预览
        if (!r.transcript.isNullOrEmpty()) {
            holder.tvTranscriptPreview.visibility = View.VISIBLE
            holder.tvTranscriptPreview.text = r.transcript
        } else {
            holder.tvTranscriptPreview.visibility = View.GONE
        }

        // 待办事项预览
        if (!r.todos.isNullOrEmpty()) {
            holder.layoutTodos.visibility = View.VISIBLE
            holder.tvTodosPreview.text = r.todos
        } else {
            holder.layoutTodos.visibility = View.GONE
        }

        // 播放状态
        val isPlaying = r.id == playingId
        holder.ivPlayIcon.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        holder.tvPlayText.text = if (isPlaying) " 暂停" else " 播放"

        holder.btnPlay.setOnClickListener { onPlay(r) }
        holder.btnTranscribe.setOnClickListener { onTranscribe(r) }
        holder.btnSummarize.setOnClickListener { onSummarize(r) }
        holder.btnDelete.setOnClickListener { onDelete(r) }
        holder.itemView.setOnLongClickListener { onLongPress(r); true }
    }

    override fun getItemCount() = recordings.size
}
