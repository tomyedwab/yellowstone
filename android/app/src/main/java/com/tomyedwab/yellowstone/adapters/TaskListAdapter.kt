package com.tomyedwab.yellowstone.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tomyedwab.yellowstone.R
import com.tomyedwab.yellowstone.models.TaskList

class TaskListAdapter(
    private var taskLists: List<TaskList> = emptyList(),
    private val onItemClick: (TaskList) -> Unit = {}
) : RecyclerView.Adapter<TaskListAdapter.TaskListViewHolder>() {

    inner class TaskListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_name)
        private val tvDescription: TextView = itemView.findViewById(R.id.tv_description)
        private val tvItemCount: TextView = itemView.findViewById(R.id.tv_item_count)

        fun bind(taskList: TaskList) {
            tvName.text = taskList.title
            
            // Hide description for now since backend doesn't provide it
            tvDescription.visibility = View.GONE
            
            // Hide item count for now since backend doesn't provide it
            tvItemCount.visibility = View.GONE
            
            itemView.setOnClickListener { onItemClick(taskList) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskListViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task_list, parent, false)
        return TaskListViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskListViewHolder, position: Int) {
        holder.bind(taskLists[position])
    }

    override fun getItemCount(): Int = taskLists.size

    fun updateTaskLists(newTaskLists: List<TaskList>) {
        taskLists = newTaskLists
        notifyDataSetChanged()
    }
}