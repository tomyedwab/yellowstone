package com.tomyedwab.yellowstone.ui.templates

import com.tomyedwab.yellowstone.ConnectionServiceListener

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
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
import com.tomyedwab.yellowstone.services.connection.ConnectionService
import com.tomyedwab.yellowstone.MainActivity

class TemplatesFragment : Fragment(), ConnectionServiceListener {

    private var templatesViewModel: TemplatesViewModel? = null
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
            val factory = TemplatesViewModelFactory(
                connectionService.getDataViewService(),
                connectionService.getConnectionStateProvider().connectionState,
                connectionService.getConnectionStateProvider()
            )
            templatesViewModel = ViewModelProvider(this, factory)[TemplatesViewModel::class.java]
            isViewModelInitialized = true
            observeViewModel()
        }
    }

    private fun setupRecyclerView() {
        adapter = TaskListAdapter(
            onItemClick = { taskList ->
                // Navigate to task list page activity
                val intent = Intent(requireContext(), com.tomyedwab.yellowstone.ui.tasks.TaskListPageActivity::class.java)
                intent.putExtra(com.tomyedwab.yellowstone.ui.tasks.TaskListPageActivity.EXTRA_LIST_ID, taskList.id)
                intent.putExtra(com.tomyedwab.yellowstone.ui.tasks.TaskListPageActivity.EXTRA_LIST_TITLE, taskList.title)
                startActivity(intent)
            },
            onArchiveClick = { taskList ->
                // Archive the list
                templatesViewModel?.archiveTaskList(taskList.id)
            },
            onReorderClick = { listId, afterListId ->
                // Reorder the template
                templatesViewModel?.reorderTemplate(listId, afterListId)
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
            showAddTemplateDialog()
        }
    }

    private fun showAddTemplateDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "Enter template title"
            requestFocus()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Create New Template")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val title = editText.text.toString().trim()
                if (title.isNotEmpty()) {
                    templatesViewModel?.addTemplate(title)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()

        editText.post {
            editText.requestFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun observeViewModel() {
        val viewModel = templatesViewModel ?: return

        viewModel.templates.observe(viewLifecycleOwner) { templates ->
            adapter.updateTaskLists(templates)
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
