package com.tomyedwab.yellowstone.ui.archived

import com.tomyedwab.yellowstone.ConnectionServiceListener

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tomyedwab.yellowstone.R
import com.tomyedwab.yellowstone.adapters.ArchivedTaskListAdapter
import com.tomyedwab.yellowstone.models.TaskList
import com.tomyedwab.yellowstone.services.connection.ConnectionService
import com.tomyedwab.yellowstone.MainActivity

class ArchivedFragment : Fragment(), ConnectionServiceListener {

    private var archivedViewModel: ArchivedViewModel? = null
    private lateinit var adapter: ArchivedTaskListAdapter
    private lateinit var recyclerView: RecyclerView
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
        progressBar = root.findViewById(R.id.progress_bar)
        errorText = root.findViewById(R.id.error_text)
        
        // Hide FAB for archived items since we don't create new archived items directly
        val fabAdd = root.findViewById<View>(R.id.fab_add)
        fabAdd.visibility = View.GONE

        setupRecyclerView()

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
            val factory = ArchivedViewModelFactory(
                connectionService.getDataViewService(),
                connectionService.getConnectionStateProvider().connectionState,
                connectionService.getConnectionStateProvider()
            )
            archivedViewModel = ViewModelProvider(this, factory)[ArchivedViewModel::class.java]
            isViewModelInitialized = true
            observeViewModel()
        }
    }

    private fun setupRecyclerView() {
        adapter = ArchivedTaskListAdapter(
            onItemClick = { taskList ->
                // Navigate to archived item detail view
                // TODO: Navigate to /archived/list/{listId}
                // This requires:
                // 1. Create ArchivedListDetailFragment with layout and ViewModel
                // 2. Add navigation action in mobile_navigation.xml
                // 3. Use NavController to navigate: findNavController().navigate(R.id.action_archived_to_detail, bundle)
                // 4. Pass taskList.id as argument to the detail fragment
            },
            onUnarchiveClick = { taskList ->
                // Unarchive the list - restore to active state
                archivedViewModel?.unarchiveTaskList(taskList.id)
            }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun observeViewModel() {
        val viewModel = archivedViewModel ?: return

        viewModel.archivedItems.observe(viewLifecycleOwner) { archivedItems ->
            adapter.updateTaskLists(archivedItems)
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