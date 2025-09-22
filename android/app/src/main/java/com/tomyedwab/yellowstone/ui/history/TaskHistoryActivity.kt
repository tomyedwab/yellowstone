package com.tomyedwab.yellowstone.ui.history

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.ComponentName
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tomyedwab.yellowstone.R
import com.tomyedwab.yellowstone.adapters.TaskHistoryAdapter
import com.tomyedwab.yellowstone.services.connection.ConnectionService

class TaskHistoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LIST_ID = "list_id"
        const val EXTRA_TASK_ID = "task_id"

        fun createIntent(context: Context, listId: Int, taskId: Int): Intent {
            return Intent(context, TaskHistoryActivity::class.java).apply {
                putExtra(EXTRA_LIST_ID, listId)
                putExtra(EXTRA_TASK_ID, taskId)
            }
        }
    }

    private lateinit var viewModel: TaskHistoryViewModel
    private lateinit var adapter: TaskHistoryAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var commentInput: EditText
    private lateinit var sendButton: ImageButton

    private var connectionService: ConnectionService? = null
    private var isBound = false
    private var listId: Int = -1
    private var taskId: Int = -1

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
        setContentView(R.layout.activity_task_history)

        listId = intent.getIntExtra(EXTRA_LIST_ID, -1)
        taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)

        if (listId == -1 || taskId == -1) {
            finish()
            return
        }

        setupActionBar()
        setupViews()
        setupRecyclerView()
        setupCommentInput()

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
            setDisplayHomeAsUpEnabled(true)
            title = "Task History"
        }
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.rv_history)
        progressBar = findViewById(R.id.progress_bar)
        errorText = findViewById(R.id.error_text)
        commentInput = findViewById(R.id.et_comment)
        sendButton = findViewById(R.id.btn_send)
    }

    private fun setupRecyclerView() {
        adapter = TaskHistoryAdapter()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupCommentInput() {
        sendButton.setOnClickListener {
            val comment = commentInput.text.toString().trim()
            if (comment.isNotEmpty()) {
                viewModel.addComment(comment)
                commentInput.text.clear()
            }
        }
    }

    private fun initializeViewModel() {
        val connectionService = this.connectionService ?: return

        val factory = TaskHistoryViewModelFactory(
            connectionService.getDataViewService(),
            connectionService.getConnectionStateProvider().connectionState,
            connectionService.getConnectionStateProvider(),
            taskId
        )
        viewModel = ViewModelProvider(this, factory)[TaskHistoryViewModel::class.java]
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.taskHistory.observe(this) { result ->
            when {
                result.loading -> {
                    recyclerView.visibility = View.GONE
                    progressBar.visibility = View.VISIBLE
                    errorText.visibility = View.GONE
                }
                result.error != null -> {
                    recyclerView.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    errorText.visibility = View.VISIBLE
                    errorText.text = "Error: ${result.error}"
                }
                result.data != null -> {
                    progressBar.visibility = View.GONE
                    errorText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE

                    supportActionBar?.title = result.data.taskTitle
                    adapter.updateHistory(result.data.history)
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}