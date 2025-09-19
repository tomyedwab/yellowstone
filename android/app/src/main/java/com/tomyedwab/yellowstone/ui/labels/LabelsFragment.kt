package com.tomyedwab.yellowstone.ui.labels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tomyedwab.yellowstone.R
import com.tomyedwab.yellowstone.adapters.TaskListAdapter
import com.tomyedwab.yellowstone.models.TaskList

class LabelsFragment : Fragment() {

    private lateinit var labelsViewModel: LabelsViewModel
    private lateinit var adapter: TaskListAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_task_list_base, container, false)

        labelsViewModel = ViewModelProvider(this)[LabelsViewModel::class.java]

        recyclerView = root.findViewById(R.id.rv_task_lists)
        fabAdd = root.findViewById(R.id.fab_add)

        setupRecyclerView()
        setupFab()
        observeViewModel()

        return root
    }

    private fun setupRecyclerView() {
        adapter = TaskListAdapter { taskList ->
            // Handle item click - navigate to label detail
            // TODO: Implement navigation to label detail
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun setupFab() {
        fabAdd.setOnClickListener {
            // Handle create new label
            // TODO: Implement create new label dialog/activity
        }
    }

    private fun observeViewModel() {
        labelsViewModel.labels.observe(viewLifecycleOwner) { labels ->
            adapter.updateTaskLists(labels)
        }
    }
}