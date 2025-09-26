package com.tomyedwab.yellowstone.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tomyedwab.yellowstone.R
import com.tomyedwab.yellowstone.generated.Task
import java.util.Collections

class TaskAdapter(
    private val onItemClick: (Task) -> Unit = {},
    private val onCheckboxClick: (Task) -> Unit = {},
    private val isSelectionMode: () -> Boolean = { false }
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private var tasks: MutableList<Task> = mutableListOf()
    private var selectedTasks: Set<Int> = emptySet()
    private var selectionMode: Boolean = false

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_task_title)
        private val cbCompleted: CheckBox = itemView.findViewById(R.id.cb_task_completed)
        private val tvDueDate: TextView = itemView.findViewById(R.id.tv_due_date)
        private val cbSelection: CheckBox = itemView.findViewById(R.id.cb_task_selection)

        fun bind(task: Task) {
            tvTitle.text = task.title

            if (selectionMode) {
                cbCompleted.visibility = View.GONE
                cbSelection.visibility = View.VISIBLE
                cbSelection.isChecked = selectedTasks.contains(task.id)
                cbSelection.setOnClickListener { onCheckboxClick(task) }
            } else {
                cbCompleted.visibility = View.VISIBLE
                cbSelection.visibility = View.GONE
                cbCompleted.isChecked = task.completedAt != null
                cbCompleted.setOnClickListener { onCheckboxClick(task) }
            }

            if (!task.dueDate.isNullOrEmpty()) {
                tvDueDate.text = task.dueDate
                tvDueDate.visibility = View.VISIBLE
            } else {
                tvDueDate.visibility = View.GONE
            }

            if (task.completedAt != null && !selectionMode) {
                tvTitle.alpha = 0.6f
                itemView.alpha = 0.6f
            } else {
                tvTitle.alpha = 1.0f
                itemView.alpha = 1.0f
            }

            itemView.setOnClickListener { onItemClick(task) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position])
    }

    override fun getItemCount(): Int = tasks.size

    fun updateTasks(newTasks: List<Task>) {
        tasks.clear()
        tasks.addAll(newTasks)
        notifyDataSetChanged()
    }

    fun updateSelectedTasks(newSelectedTasks: Set<Int>) {
        selectedTasks = newSelectedTasks
        notifyDataSetChanged()
    }

    fun setSelectionMode(selectionMode: Boolean) {
        this.selectionMode = selectionMode
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(tasks, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(tasks, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    fun getTasks(): List<Task> = tasks.toList()
}
