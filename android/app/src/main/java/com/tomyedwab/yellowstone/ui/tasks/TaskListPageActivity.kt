package com.tomyedwab.yellowstone.ui.tasks

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tomyedwab.yellowstone.R
import com.tomyedwab.yellowstone.adapters.TaskAdapter
import com.tomyedwab.yellowstone.adapters.TaskItemTouchHelper
import com.tomyedwab.yellowstone.generated.TaskList
import com.tomyedwab.yellowstone.services.connection.ConnectionService
import com.tomyedwab.yellowstone.ui.history.TaskHistoryActivity
import com.tomyedwab.yellowstone.generated.ApiRoutes

class TaskListPageActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LIST_ID = "list_id"
        const val EXTRA_LIST_TITLE = "list_title"
    }

    private lateinit var viewModel: TaskListPageViewModel
    private lateinit var adapter: TaskAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView

    private var connectionService: ConnectionService? = null
    private var isBound = false
    private var listId: Int = -1
    private var listTitle: String = ""
    private var isSelectionMode = false

    private var availableTaskLists: List<TaskList> = emptyList()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ConnectionService.ConnectionBinder
            connectionService = binder.getService()
            isBound = true
            initializeViewModel()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connectionService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_list_page)

        listId = intent.getIntExtra(EXTRA_LIST_ID, -1)
        listTitle = intent.getStringExtra(EXTRA_LIST_TITLE) ?: "Task List"

        if (listId == -1) {
            finish()
            return
        }

        setupActionBar()
        setupViews()
        setupRecyclerView()
        setupFab()

        val intent = Intent(this, ConnectionService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun setupActionBar() {
        supportActionBar?.apply {
            title = listTitle
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.rv_tasks)
        fabAdd = findViewById(R.id.fab_add_task)
        progressBar = findViewById(R.id.progress_bar)
        errorText = findViewById(R.id.error_text)
    }

    private fun setupRecyclerView() {
        adapter = TaskAdapter(
            onItemClick = { task ->
                if (!isSelectionMode) {
                    val intent = TaskHistoryActivity.createIntent(this@TaskListPageActivity, listId, task.id)
                    startActivity(intent)
                }
            },
            onCheckboxClick = { task ->
                if (isSelectionMode) {
                    viewModel.toggleTaskSelection(task.id)
                } else {
                    viewModel.toggleTaskCompletion(task.id)
                }
            },
            isSelectionMode = { isSelectionMode }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        val itemTouchHelper = ItemTouchHelper(TaskItemTouchHelper(adapter) { fromPos, toPos ->
            if (!isSelectionMode) {
                viewModel.reorderTasks(fromPos, toPos)
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun setupFab() {
        fabAdd.setOnClickListener {
            if (!isSelectionMode) {
                showAddTaskDialog()
            }
        }
    }

    private fun initializeViewModel() {
        val connectionService = this.connectionService ?: return

        val factory = TaskListPageViewModelFactory(
            connectionService.getDataViewService(),
            connectionService.getConnectionStateProvider().connectionState,
            connectionService.getConnectionStateProvider(),
            listId
        )
        viewModel = ViewModelProvider(this, factory)[TaskListPageViewModel::class.java]
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.tasks.observe(this) { tasks ->
            adapter.updateTasks(tasks)
            updateFabVisibility()
        }

        viewModel.selectedTasks.observe(this) { selectedTasks ->
            adapter.updateSelectedTasks(selectedTasks)
            invalidateOptionsMenu()
        }

        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                recyclerView.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
                errorText.visibility = View.GONE
            } else {
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }

        viewModel.error.observe(this) { error ->
            if (error != null) {
                recyclerView.visibility = View.GONE
                progressBar.visibility = View.GONE
                errorText.visibility = View.VISIBLE
                errorText.text = "Error: $error"
            } else {
                errorText.visibility = View.GONE
            }
        }

        loadAvailableTaskLists()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.task_list_page_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val editItem = menu.findItem(R.id.action_edit_list)
        val archiveItem = menu.findItem(R.id.action_archive_list)
        val selectionItem = menu.findItem(R.id.action_toggle_selection)
        val batchSelectItem = menu.findItem(R.id.action_batch_select)
        val addToListItem = menu.findItem(R.id.action_add_to_list)
        val copyItem = menu.findItem(R.id.action_copy)
        val moveItem = menu.findItem(R.id.action_move)
        val exitSelectionItem = menu.findItem(R.id.action_exit_selection)

        if (isSelectionMode) {
            editItem.isVisible = false
            archiveItem.isVisible = false
            selectionItem.isVisible = false

            batchSelectItem.isVisible = true
            addToListItem.isVisible = true
            copyItem.isVisible = true
            moveItem.isVisible = true
            exitSelectionItem.isVisible = true

            val selectedCount = viewModel.selectedTasks.value?.size ?: 0
            addToListItem.isEnabled = selectedCount > 0
            copyItem.isEnabled = selectedCount > 0
            moveItem.isEnabled = selectedCount > 0
        } else {
            editItem.isVisible = true
            archiveItem.isVisible = true
            selectionItem.isVisible = true

            batchSelectItem.isVisible = false
            addToListItem.isVisible = false
            copyItem.isVisible = false
            moveItem.isVisible = false
            exitSelectionItem.isVisible = false
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_edit_list -> {
                showRenameListDialog()
                true
            }
            R.id.action_archive_list -> {
                viewModel.archiveList()
                true
            }
            R.id.action_toggle_selection -> {
                toggleSelectionMode()
                true
            }
            R.id.action_batch_select -> {
                showBatchSelectionDialog()
                true
            }
            R.id.action_add_to_list -> {
                showTargetListDialog(TaskListPageViewModel.BatchOperation.ADD_TO_LIST)
                true
            }
            R.id.action_copy -> {
                showTargetListDialog(TaskListPageViewModel.BatchOperation.COPY)
                true
            }
            R.id.action_move -> {
                showTargetListDialog(TaskListPageViewModel.BatchOperation.MOVE)
                true
            }
            R.id.action_exit_selection -> {
                exitSelectionMode()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleSelectionMode() {
        isSelectionMode = !isSelectionMode
        adapter.setSelectionMode(isSelectionMode)
        updateFabVisibility()
        invalidateOptionsMenu()

        if (!isSelectionMode) {
            viewModel.clearSelection()
        }
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        adapter.setSelectionMode(false)
        viewModel.clearSelection()
        updateFabVisibility()
        invalidateOptionsMenu()
    }

    private fun updateFabVisibility() {
        fabAdd.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
    }

    private fun showAddTaskDialog() {
        val editText = EditText(this).apply {
            hint = "Enter task title"
        }

        AlertDialog.Builder(this)
            .setTitle("Add New Task")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val title = editText.text.toString().trim()
                if (title.isNotEmpty()) {
                    viewModel.addTask(title)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameListDialog() {
        val editText = EditText(this).apply {
            hint = "Enter list title"
            setText(listTitle)
            selectAll()
        }

        AlertDialog.Builder(this)
            .setTitle("Rename List")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val newTitle = editText.text.toString().trim()
                if (newTitle.isNotEmpty() && newTitle != listTitle) {
                    viewModel.renameList(newTitle)
                    listTitle = newTitle
                    supportActionBar?.title = listTitle
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBatchSelectionDialog() {
        val options = arrayOf(
            "Select All tasks",
            "Select Completed tasks only",
            "Select Uncompleted tasks only",
            "Clear Selection"
        )

        AlertDialog.Builder(this)
            .setTitle("Batch Selection")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.selectAllTasks()
                    1 -> viewModel.selectCompletedTasks()
                    2 -> viewModel.selectUncompletedTasks()
                    3 -> viewModel.clearSelection()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadAvailableTaskLists() {
        val connectionService = this.connectionService ?: return
        val apiRoutes = ApiRoutes(connectionService.getDataViewService(), connectionService.getConnectionStateProvider().connectionState)
        val taskListDataView = apiRoutes.getTasklistAll()

        taskListDataView.observe(this) { result ->
            if (result.data != null) {
                availableTaskLists = result.data.taskLists.filter { it.id != listId }
            }
        }
    }

    private fun showTargetListDialog(operation: TaskListPageViewModel.BatchOperation) {
        if (availableTaskLists.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No Available Lists")
                .setMessage("There are no other lists available to ${getOperationName(operation)} tasks to.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val listTitles = availableTaskLists.map { it.title }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Target List")
            .setItems(listTitles) { _, which ->
                val targetList = availableTaskLists[which]
                viewModel.performBatchOperation(operation, targetList.id)
                exitSelectionMode()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getOperationName(operation: TaskListPageViewModel.BatchOperation): String {
        return when (operation) {
            TaskListPageViewModel.BatchOperation.ADD_TO_LIST -> "add"
            TaskListPageViewModel.BatchOperation.COPY -> "copy"
            TaskListPageViewModel.BatchOperation.MOVE -> "move"
        }
    }
}
