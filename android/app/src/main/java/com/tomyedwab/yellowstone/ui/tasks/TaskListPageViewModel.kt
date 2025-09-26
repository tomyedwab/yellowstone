package com.tomyedwab.yellowstone.ui.tasks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.map
import com.tomyedwab.yellowstone.generated.ApiRoutes
import com.tomyedwab.yellowstone.generated.Events
import com.tomyedwab.yellowstone.generated.Task
import com.tomyedwab.yellowstone.generated.TaskRecentComment
import com.tomyedwab.yellowstone.generated.TaskLabels
import com.tomyedwab.yellowstone.provider.connection.ConnectionStateProvider
import com.tomyedwab.yellowstone.provider.connection.HubConnectionState
import com.tomyedwab.yellowstone.services.connection.DataViewService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TaskListPageViewModel(
    dataViewService: DataViewService,
    connectionState: LiveData<HubConnectionState>,
    connectionStateProvider: ConnectionStateProvider,
    private val listId: Int
) : ViewModel() {

    enum class BatchOperation {
        ADD_TO_LIST, COPY, MOVE
    }

    private val apiRoutes = ApiRoutes(dataViewService, connectionState)
    private val events = Events(connectionStateProvider)

    private val tasksDataView = apiRoutes.getTaskList(listId)
    private val commentsDataView = apiRoutes.getTasklistRecentComments(listId)
    private val labelsDataView = apiRoutes.getTasklistLabels(listId)

    val tasks: LiveData<List<Task>> =
        tasksDataView.map { result -> result.data?.tasks ?: emptyList() }

    val recentComments: LiveData<List<TaskRecentComment>> =
        commentsDataView.map { result -> result.data?.comments ?: emptyList() }

    val labels: LiveData<List<TaskLabels>> =
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
                events.taskAdd(null, listId, title)
            } catch (e: Exception) {
                // Error handling would be managed by the data views
            }
        }
    }

    fun toggleTaskCompletion(taskId: Int) {
        viewModelScope.launch {
            try {
                val task = tasks.value?.find { it.id == taskId } ?: return@launch
                val completionTime = if (task.completedAt != null) {
                    null
                } else {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
                }
                events.taskUpdateCompleted(completionTime, taskId)
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
                events.taskListReorderTasks(afterTask?.id, task.id, listId)
            } catch (e: Exception) {
                // Error handling would be managed by the data views
            }
        }
    }

    fun renameList(newTitle: String) {
        viewModelScope.launch {
            try {
                events.taskListUpdateTitle(listId, newTitle)
            } catch (e: Exception) {
                // Error handling would be managed by the data views
            }
        }
    }

    fun archiveList() {
        viewModelScope.launch {
            try {
                events.taskListUpdateArchived(true, listId)
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
                            events.taskListMoveTasks(targetListId, listId, selectedTaskIds)
                        }
                    }
                    BatchOperation.ADD_TO_LIST -> {
                        if (targetListId != null) {
                            events.taskListCopyTasks(targetListId, selectedTaskIds)
                        }
                    }
                    BatchOperation.COPY -> {
                        if (targetListId != null) {
                            events.taskListDuplicateTasks(targetListId, selectedTaskIds)
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
