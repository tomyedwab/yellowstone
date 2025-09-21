package com.tomyedwab.yellowstone.ui.lists

import com.tomyedwab.yellowstone.ConnectionServiceListener

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tomyedwab.yellowstone.R
import com.tomyedwab.yellowstone.adapters.TaskListAdapter
import com.tomyedwab.yellowstone.adapters.TaskListItemTouchHelper
import com.tomyedwab.yellowstone.models.TaskList
import com.tomyedwab.yellowstone.services.connection.ConnectionService
import com.tomyedwab.yellowstone.MainActivity

class ListsFragment : Fragment(), ConnectionServiceListener {

    private var listsViewModel: ListsViewModel? = null
    private lateinit var adapter: TaskListAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private var isViewModelInitialized = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_task_list_base, container, false)

        recyclerView = root.findViewById(R.id.rv_task_lists)
        fabAdd = root.findViewById(R.id.fab_add)
        progressBar = root.findViewById(R.id.progress_bar)
        errorText = root.findViewById(R.id.error_text)

        if (fabAdd != null) {
            fabAdd.visibility = View.VISIBLE
        }

        setupRecyclerView()
        setupFab()

        // Register for ConnectionService callbacks
        val mainActivity = requireActivity() as MainActivity
        mainActivity.addConnectionServiceListener(this)

        // Check if ConnectionService is already available (for tab switches)
        val connectionService = mainActivity.getConnectionService()
        if (connectionService != null) {
            // ConnectionService is already ready, initialize immediately
            onConnectionServiceReady(connectionService)
        } else {
            // Show loading state while waiting for ConnectionService
            progressBar.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            errorText.visibility = View.GONE
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val mainActivity = activity as? MainActivity
        mainActivity?.removeConnectionServiceListener(this)
        // Reset the initialization flag so ViewModel can be re-observed if needed
        isViewModelInitialized = false
    }

    override fun onConnectionServiceReady(connectionService: ConnectionService) {
        if (!isViewModelInitialized) {
            val factory = ListsViewModelFactory(
                connectionService.getDataViewService(),
                connectionService.getConnectionStateProvider().connectionState,
                connectionService.getConnectionStateProvider()
            )
            listsViewModel = ViewModelProvider(this, factory)[ListsViewModel::class.java]
            isViewModelInitialized = true
            observeViewModel()
        }
    }

    private fun setupRecyclerView() {
        adapter = TaskListAdapter(
            onItemClick = { taskList ->
                // Navigate to task list detail view
                // TODO: Navigate to /list/{listId}
                // This requires:
                // 1. Create TaskListDetailFragment with layout and ViewModel
                // 2. Add navigation action in mobile_navigation.xml
                // 3. Use NavController to navigate: findNavController().navigate(R.id.action_lists_to_detail, bundle)
                // 4. Pass taskList.id as argument to the detail fragment
            },
            onArchiveClick = { taskList ->
                // Archive the list
                listsViewModel?.archiveTaskList(taskList.id)
            },
            onReorderClick = { listId, afterListId ->
                // Reorder the list
                listsViewModel?.reorderTaskList(listId, afterListId)
            }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Set up drag-and-drop
        val itemTouchHelper = ItemTouchHelper(TaskListItemTouchHelper(adapter))
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun setupFab() {
        fabAdd.setOnClickListener {
            showAddTaskListDialog()
        }
    }

    private fun showAddTaskListDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "Enter task list name"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Add New Task List")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val title = editText.text.toString().trim()
                if (title.isNotEmpty()) {
                    listsViewModel?.addTaskList(title)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        val viewModel = listsViewModel ?: return

        viewModel.taskLists.observe(viewLifecycleOwner) { taskLists ->
            adapter.updateTaskLists(taskLists)
        }

        viewModel.taskMetadata.observe(viewLifecycleOwner) { metadata ->
            adapter.updateTaskMetadata(metadata)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                recyclerView.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
                errorText.visibility = View.GONE
            } else {
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                recyclerView.visibility = View.GONE
                progressBar.visibility = View.GONE
                errorText.visibility = View.VISIBLE
                errorText.text = "Error: $error"
            } else {
                errorText.visibility = View.GONE
            }
        }
    }
}
