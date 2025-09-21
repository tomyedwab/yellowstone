package com.tomyedwab.yellowstone.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tomyedwab.yellowstone.R
import com.tomyedwab.yellowstone.models.TaskList
import java.util.Collections

class TaskListAdapter : RecyclerView.Adapter<TaskListAdapter.TaskListViewHolder> {

    private var taskLists: MutableList<TaskList> = mutableListOf()
    private var taskMetadata: Map<Int, Pair<Int, Int>> = emptyMap()
    private val onItemClick: (TaskList) -> Unit
    private val onArchiveClick: (TaskList) -> Unit
    private val onReorderClick: ((Int, Int?) -> Unit)?

    // Primary constructor for lists fragment with all features
    constructor(
        taskLists: MutableList<TaskList> = mutableListOf(),
        taskMetadata: Map<Int, Pair<Int, Int>> = emptyMap(),
        onItemClick: (TaskList) -> Unit = {},
        onArchiveClick: (TaskList) -> Unit = {},
        onReorderClick: ((Int, Int?) -> Unit)? = null
    ) {
        this.taskLists = taskLists
        this.taskMetadata = taskMetadata
        this.onItemClick = onItemClick
        this.onArchiveClick = onArchiveClick
        this.onReorderClick = onReorderClick
    }

    // Secondary constructor for backward compatibility
    constructor(onItemClick: (TaskList) -> Unit) {
        this.onItemClick = onItemClick
        this.onArchiveClick = {}
        this.onReorderClick = null
    }

    inner class TaskListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_name)
        private val tvItemCount: TextView = itemView.findViewById(R.id.tv_item_count)
        private val btnArchive: ImageButton = itemView.findViewById(R.id.btn_archive)

        fun bind(taskList: TaskList) {
            tvName.text = taskList.title

            // Set task count metadata if available
            val metadata = taskMetadata[taskList.id]
            if (metadata != null) {
                val (totalTasks, completedTasks) = metadata
                // For templates, only show task count, not completion status
                if (taskList.category == "template") {
                    tvItemCount.text = "$totalTasks tasks"
                } else {
                    tvItemCount.text = "$totalTasks tasks, $completedTasks completed"
                }
                tvItemCount.visibility = View.VISIBLE
            } else {
                tvItemCount.visibility = View.GONE
            }

            // Hide archive button for templates
            if (taskList.category == "template") {
                btnArchive.visibility = View.GONE
            } else {
                btnArchive.visibility = View.VISIBLE
                btnArchive.setOnClickListener { onArchiveClick(taskList) }
            }

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

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(taskLists, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(taskLists, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    fun onItemMoved(fromPosition: Int, toPosition: Int) {
        val movedItem = taskLists[toPosition]
        val afterItem = if (toPosition > 0) taskLists[toPosition - 1] else null
        onReorderClick?.invoke(movedItem.id, afterItem?.id)
    }
}