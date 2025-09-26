package com.tomyedwab.yellowstone.ui.lists

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.tomyedwab.yellowstone.generated.ApiRoutes
import com.tomyedwab.yellowstone.generated.Events
import com.tomyedwab.yellowstone.generated.TaskList
import com.tomyedwab.yellowstone.provider.connection.ConnectionStateProvider
import com.tomyedwab.yellowstone.provider.connection.HubConnectionState
import com.tomyedwab.yellowstone.services.connection.DataViewService

class ListsViewModel(
        dataViewService: DataViewService,
        connectionState: LiveData<HubConnectionState>,
        connectionStateProvider: ConnectionStateProvider
) : ViewModel() {

    private val apiRoutes = ApiRoutes(dataViewService, connectionState)
    private val events = Events(connectionStateProvider)

    private val taskListDataView = apiRoutes.getTasklistTodo()
    private val taskMetadataDataView = apiRoutes.getTasklistMetadata()

    val taskLists: LiveData<List<TaskList>> =
            taskListDataView.map { result -> result.data?.taskLists ?: emptyList() }

    val taskMetadata: LiveData<Map<Int, Pair<Int, Int>>> =
            taskMetadataDataView.map { result ->
                val metadata = result.data?.metadata ?: emptyList()
                metadata.associate { it.listId to (it.total to it.completed) }
            }

    val isLoading: LiveData<Boolean> = taskListDataView.map { result -> result.loading }

    val error: LiveData<String?> = taskListDataView.map { result -> result.error }

    fun addTaskList(title: String) {
        events.taskListAdd(false, "toDoList", title)
    }

    fun archiveTaskList(listId: Int) {
        events.taskListUpdateArchived(true, listId)
    }

    fun reorderTaskList(listId: Int, afterListId: Int?) {
        events.taskListReorder(afterListId, listId)
    }
}
