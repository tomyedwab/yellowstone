package com.tomyedwab.yellowstone.ui.tasks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.map
import com.google.gson.reflect.TypeToken
import com.tomyedwab.yellowstone.models.Task
import com.tomyedwab.yellowstone.models.TaskComment
import com.tomyedwab.yellowstone.models.TaskLabel
import com.tomyedwab.yellowstone.models.TaskResponse
import com.tomyedwab.yellowstone.models.TaskCommentsResponse
import com.tomyedwab.yellowstone.models.TaskLabelsResponse
import com.tomyedwab.yellowstone.provider.connection.ConnectionAction
import com.tomyedwab.yellowstone.provider.connection.ConnectionStateProvider
import com.tomyedwab.yellowstone.provider.connection.PendingEvent
import com.tomyedwab.yellowstone.provider.connection.HubConnectionState
import com.tomyedwab.yellowstone.services.connection.DataViewService
import com.tomyedwab.yellowstone.services.connection.DataViewResult
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class TaskListPageViewModel(
    private val dataViewService: DataViewService,
    private val connectionState: LiveData<HubConnectionState>,
    private val connectionStateProvider: ConnectionStateProvider,
    private val listId: Int
) : ViewModel() {

    enum class BatchOperation {
        ADD_TO_LIST, COPY, MOVE
    }

    private val tasksDataView: LiveData<DataViewResult<TaskResponse>> =
        dataViewService.createDataView(
            connectionState = connectionState,
            componentName = "yellowstone",
            apiPath = "api/task/list",
            apiParams = mapOf("listId" to listId.toString()),
            typeToken = object : TypeToken<TaskResponse>() {}
        )

    private val commentsDataView: LiveData<DataViewResult<TaskCommentsResponse>> =
        dataViewService.createDataView(
            connectionState = connectionState,
            componentName = "yellowstone",
            apiPath = "api/tasklist/recentcomments",
            apiParams = mapOf("listId" to listId.toString()),
            typeToken = object : TypeToken<TaskCommentsResponse>() {}
        )

    private val labelsDataView: LiveData<DataViewResult<TaskLabelsResponse>> =
        dataViewService.createDataView(
            connectionState = connectionState,
            componentName = "yellowstone",
            apiPath = "api/task/labels",
            apiParams = mapOf("listId" to listId.toString()),
            typeToken = object : TypeToken<TaskLabelsResponse>() {}
        )

    val tasks: LiveData<List<Task>> =
        tasksDataView.map { result -> result.data?.tasks ?: emptyList() }

    val recentComments: LiveData<List<TaskComment>> =
        commentsDataView.map { result -> result.data?.comments ?: emptyList() }

    val labels: LiveData<List<TaskLabel>> =
        labelsDataView.map { result -> result.data?.labels ?: emptyList() }

    private val _selectedTasks = MutableLiveData<Set<Int>>()
    val selectedTasks: LiveData<Set<Int>> = _selectedTasks

    val isLoading: LiveData<Boolean> = tasksDataView.map { result -> result.loading }

    val error: LiveData<String?> = tasksDataView.map { result -> result.error }

    init {
        _selectedTasks.value = emptySet()
    }

    fun addTask(title: String) {
        viewModelScope.launch {
            try {
                val event = PendingEvent(
                    clientId = UUID.randomUUID().toString(),
                    type = "Task:Add",
                    timestamp = Instant.now().toString(),
                    data = mapOf(
                        "listId" to listId,
                        "title" to title
                    )
                )
                connectionStateProvider.dispatch(ConnectionAction.PublishEvent(event))
            } catch (e: Exception) {
                // Error handling would be managed by the data views
            }
        }
    }

    fun toggleTaskCompletion(taskId: Int) {
        viewModelScope.launch {
            try {
                val task = tasks.value?.find { it.id == taskId } ?: return@launch
                val completionTime: Any? = if (task.completedAt != null) {
                    null
                } else {
                    Instant.now().toString()
                }
                val event = PendingEvent(
                    clientId = UUID.randomUUID().toString(),
                    type = "Task:UpdateCompleted",
                    timestamp = Instant.now().toString(),
                    data = mapOf(
                        "taskId" to taskId,
                        "completedAt" to completionTime,
                    )
                )
                connectionStateProvider.dispatch(ConnectionAction.PublishEvent(event))
            } catch (e: Exception) {
                // Error handling would be managed by the data views
            }
        }
    }

    fun reorderTasks(fromPosition: Int, toPosition: Int) {
        val currentTasks = tasks.value?.toMutableList() ?: return
        if (fromPosition >= currentTasks.size || toPosition >= currentTasks.size) return

        val task = currentTasks.removeAt(fromPosition)
        val afterTask = if (toPosition > 0) currentTasks[toPosition - 1] else null

        viewModelScope.launch {
            try {
                val event = PendingEvent(
                    clientId = UUID.randomUUID().toString(),
                    type = "TaskList:ReorderTasks",
                    timestamp = Instant.now().toString(),
                    data = mapOf(
                        "taskId" to task.id,
                        "afterTaskId" to (afterTask?.id ?: null)
                    )
                )
                connectionStateProvider.dispatch(ConnectionAction.PublishEvent(event))
            } catch (e: Exception) {
                // Error handling would be managed by the data views
            }
        }
    }

    fun renameList(newTitle: String) {
        viewModelScope.launch {
            try {
                val event = PendingEvent(
                    clientId = UUID.randomUUID().toString(),
                    type = "TaskList:UpdateTitle",
                    timestamp = Instant.now().toString(),
                    data = mapOf(
                        "listId" to listId,
                        "title" to newTitle
                    )
                )
                connectionStateProvider.dispatch(ConnectionAction.PublishEvent(event))
            } catch (e: Exception) {
                // Error handling would be managed by the data views
            }
        }
    }

    fun archiveList() {
        viewModelScope.launch {
            try {
                val event = PendingEvent(
                    clientId = UUID.randomUUID().toString(),
                    type = "TaskList:UpdateArchived",
                    timestamp = Instant.now().toString(),
                    data = mapOf(
                        "listId" to listId,
                        "archived" to true
                    )
                )
                connectionStateProvider.dispatch(ConnectionAction.PublishEvent(event))
            } catch (e: Exception) {
                // Error handling would be managed by the data views
            }
        }
    }

    fun toggleTaskSelection(taskId: Int) {
        val currentSelection = _selectedTasks.value ?: emptySet()
        val newSelection = if (currentSelection.contains(taskId)) {
            currentSelection - taskId
        } else {
            currentSelection + taskId
        }
        _selectedTasks.value = newSelection
    }

    fun selectAllTasks() {
        val allTaskIds = tasks.value?.map { it.id }?.toSet() ?: emptySet()
        _selectedTasks.value = allTaskIds
    }

    fun selectCompletedTasks() {
        val completedTaskIds = tasks.value
            ?.filter { it.completedAt != null }
            ?.map { it.id }
            ?.toSet() ?: emptySet()
        _selectedTasks.value = completedTaskIds
    }

    fun selectUncompletedTasks() {
        val uncompletedTaskIds = tasks.value
            ?.filter { it.completedAt == null }
            ?.map { it.id }
            ?.toSet() ?: emptySet()
        _selectedTasks.value = uncompletedTaskIds
    }

    fun clearSelection() {
        _selectedTasks.value = emptySet()
    }

    fun performBatchOperation(operation: BatchOperation, targetListId: Int? = null) {
        val selectedTaskIds = _selectedTasks.value?.toList() ?: return
        if (selectedTaskIds.isEmpty()) return

        viewModelScope.launch {
            try {
                when (operation) {
                    BatchOperation.MOVE -> {
                        if (targetListId != null) {
                            val event = PendingEvent(
                                clientId = UUID.randomUUID().toString(),
                                type = "TaskList:MoveTasks",
                                timestamp = Instant.now().toString(),
                                data = mapOf(
                                    "taskIds" to selectedTaskIds,
                                    "oldListId" to listId,
                                    "newListId" to targetListId
                                )
                            )
                            connectionStateProvider.dispatch(ConnectionAction.PublishEvent(event))
                        }
                    }
                    BatchOperation.ADD_TO_LIST -> {
                        if (targetListId != null) {
                            val event = PendingEvent(
                                clientId = UUID.randomUUID().toString(),
                                type = "TaskList:CopyTasks",
                                timestamp = Instant.now().toString(),
                                data = mapOf(
                                    "taskIds" to selectedTaskIds,
                                    "newListId" to targetListId
                                )
                            )
                            connectionStateProvider.dispatch(ConnectionAction.PublishEvent(event))
                        }
                    }
                    BatchOperation.COPY -> {
                        if (targetListId != null) {
                            val event = PendingEvent(
                                clientId = UUID.randomUUID().toString(),
                                type = "TaskList:DuplicateTasks",
                                timestamp = Instant.now().toString(),
                                data = mapOf(
                                    "taskIds" to selectedTaskIds,
                                    "newListId" to targetListId
                                )
                            )
                            connectionStateProvider.dispatch(ConnectionAction.PublishEvent(event))
                        }
                    }
                }
                clearSelection()
            } catch (e: Exception) {
                // Error handling would be managed by the data views
            }
        }
    }
}
