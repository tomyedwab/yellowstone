package com.tomyedwab.yellowstone.ui.labels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.tomyedwab.yellowstone.generated.ApiRoutes
import com.tomyedwab.yellowstone.generated.Events
import com.tomyedwab.yellowstone.generated.TaskList
import com.tomyedwab.yellowstone.provider.connection.ConnectionStateProvider
import com.tomyedwab.yellowstone.provider.connection.HubConnectionState
import com.tomyedwab.yellowstone.services.connection.DataViewService

class LabelsViewModel(
        dataViewService: DataViewService,
        connectionState: LiveData<HubConnectionState>,
        connectionStateProvider: ConnectionStateProvider
) : ViewModel() {

    private val apiRoutes = ApiRoutes(dataViewService, connectionState)
    private val events = Events(connectionStateProvider)

    private val taskListDataView = apiRoutes.getTasklistAll()
    private val taskMetadataDataView = apiRoutes.getTasklistMetadata()

    // Filter for label category and not archived as per spec
    val labels: LiveData<List<TaskList>> =
            taskListDataView.map { result ->
                result.data?.taskLists?.filter {
                    it.category == "label" && !it.archived
                } ?: emptyList()
            }

    val taskMetadata: LiveData<Map<Int, Pair<Int, Int>>> =
            taskMetadataDataView.map { result ->
                val metadata = result.data?.metadata ?: emptyList()
                metadata.associate { it.listId to (it.total to it.completed) }
            }

    val isLoading: LiveData<Boolean> = taskListDataView.map { result -> result.loading }

    val error: LiveData<String?> = taskListDataView.map { result -> result.error }

    fun addLabel(title: String) {
        events.taskListAdd(false, "label", title)
    }

    fun reorderLabel(listId: Int, afterListId: Int?) {
        events.taskListReorder(afterListId, listId)
    }

    fun archiveTaskList(listId: Int) {
        events.taskListUpdateArchived(true, listId)
    }

}
