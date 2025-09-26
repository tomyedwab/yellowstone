package com.tomyedwab.yellowstone.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tomyedwab.yellowstone.R
import com.tomyedwab.yellowstone.generated.TaskList

class ArchivedTaskListAdapter(
    private val onItemClick: (TaskList) -> Unit,
    private val onUnarchiveClick: (TaskList) -> Unit
) : RecyclerView.Adapter<ArchivedTaskListAdapter.ArchivedTaskListViewHolder>() {

    private var taskLists: MutableList<TaskList> = mutableListOf()
    private var taskMetadata: Map<Int, Pair<Int, Int>> = emptyMap()

    inner class ArchivedTaskListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_name)
        private val tvItemCount: TextView = itemView.findViewById(R.id.tv_item_count)
        private val btnUnarchive: ImageButton = itemView.findViewById(R.id.btn_unarchive)

        fun bind(taskList: TaskList) {
            tvName.text = taskList.title

            // Set task count in "X tasks" format as per spec using metadata
            val metadata = taskMetadata[taskList.id]
            if (metadata != null) {
                val (totalTasks, _) = metadata
                tvItemCount.text = "$totalTasks tasks"
                tvItemCount.visibility = View.VISIBLE
            } else {
                // Fallback if no metadata available
                tvItemCount.text = "0 tasks"
                tvItemCount.visibility = View.VISIBLE
            }

            itemView.setOnClickListener { onItemClick(taskList) }
            btnUnarchive.setOnClickListener { onUnarchiveClick(taskList) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArchivedTaskListViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_archived_task_list, parent, false)
        return ArchivedTaskListViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArchivedTaskListViewHolder, position: Int) {
        holder.bind(taskLists[position])
    }

    override fun getItemCount(): Int = taskLists.size

    fun updateTaskLists(newTaskLists: List<TaskList>) {
        taskLists.clear()
        taskLists.addAll(newTaskLists)
        notifyDataSetChanged()
    }

    fun updateTaskMetadata(newTaskMetadata: Map<Int, Pair<Int, Int>>) {
        taskMetadata = newTaskMetadata
        notifyDataSetChanged()
    }

    fun updateTaskListsAndMetadata(newTaskLists: List<TaskList>, newTaskMetadata: Map<Int, Pair<Int, Int>>) {
        taskLists.clear()
        taskLists.addAll(newTaskLists)
        taskMetadata = newTaskMetadata
        notifyDataSetChanged()
    }
}