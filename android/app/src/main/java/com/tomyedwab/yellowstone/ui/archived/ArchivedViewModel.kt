package com.tomyedwab.yellowstone.ui.archived

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.tomyedwab.yellowstone.generated.ApiRoutes
import com.tomyedwab.yellowstone.generated.Events
import com.tomyedwab.yellowstone.generated.TaskList
import com.tomyedwab.yellowstone.provider.connection.HubConnectionState
import com.tomyedwab.yellowstone.provider.connection.ConnectionStateProvider
import com.tomyedwab.yellowstone.services.connection.DataViewService

class ArchivedViewModel(
    dataViewService: DataViewService,
    connectionState: LiveData<HubConnectionState>,
    connectionStateProvider: ConnectionStateProvider
) : ViewModel() {

    private val apiRoutes = ApiRoutes(dataViewService, connectionState)
    private val events = Events(connectionStateProvider)

    private val allTaskListsDataView = apiRoutes.getTasklistAll()
    private val taskMetadataDataView = apiRoutes.getTasklistMetadata()

    val archivedItems: LiveData<List<TaskList>> = allTaskListsDataView.map { result ->
        result.data?.taskLists?.filter { it.archived } ?: emptyList()
    }

    val taskMetadata: LiveData<Map<Int, Pair<Int, Int>>> = taskMetadataDataView.map { result ->
        val metadata = result.data?.metadata ?: emptyList()
        metadata.associate { it.listId to (it.total to it.completed) }
    }

    val isLoading: LiveData<Boolean> = allTaskListsDataView.map { result ->
        result.loading
    }

    val error: LiveData<String?> = allTaskListsDataView.map { result ->
        result.error
    }

    fun unarchiveTaskList(listId: Int) {
        events.taskListUpdateArchived(false, listId)
    }
}
