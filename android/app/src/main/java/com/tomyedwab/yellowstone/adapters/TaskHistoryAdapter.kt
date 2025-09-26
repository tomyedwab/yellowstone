package com.tomyedwab.yellowstone.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tomyedwab.yellowstone.R
import com.tomyedwab.yellowstone.generated.TaskHistory
import java.text.SimpleDateFormat
import java.util.*

class TaskHistoryAdapter : RecyclerView.Adapter<TaskHistoryAdapter.HistoryViewHolder>() {

    private var historyEntries: List<TaskHistory> = emptyList()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun updateHistory(entries: List<TaskHistory>) {
        historyEntries = entries.reversed() // Reverse chronological order
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_entry, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(historyEntries[position])
    }

    override fun getItemCount(): Int = historyEntries.size

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timestampText: TextView = itemView.findViewById(R.id.tv_timestamp)
        private val systemCommentText: TextView = itemView.findViewById(R.id.tv_system_comment)
        private val userCommentText: TextView = itemView.findViewById(R.id.tv_user_comment)

        fun bind(entry: TaskHistory) {
            // Format and display timestamp
            try {
                val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    .parse(entry.createdAt)
                timestampText.text = if (date != null) {
                    dateFormat.format(date)
                } else {
                    entry.createdAt
                }
            } catch (e: Exception) {
                timestampText.text = entry.createdAt
            }

            // Display system comment if present
            if (entry.systemComment?.isNotEmpty() == true) {
                systemCommentText.text = entry.systemComment
                systemCommentText.visibility = View.VISIBLE
            } else {
                systemCommentText.visibility = View.GONE
            }

            // Display user comment if present
            if (entry.userComment?.isNotEmpty() == true) {
                userCommentText.text = entry.userComment
                userCommentText.visibility = View.VISIBLE
            } else {
                userCommentText.visibility = View.GONE
            }
        }
    }
}