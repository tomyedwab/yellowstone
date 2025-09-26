package com.tomyedwab.yellowstone.ui.templates

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.tomyedwab.yellowstone.generated.ApiRoutes
import com.tomyedwab.yellowstone.generated.Events
import com.tomyedwab.yellowstone.generated.TaskList
import com.tomyedwab.yellowstone.generated.TaskListMetadataResponse
import com.tomyedwab.yellowstone.provider.connection.ConnectionStateProvider
import com.tomyedwab.yellowstone.provider.connection.HubConnectionState
import com.tomyedwab.yellowstone.services.connection.DataViewService

class TemplatesViewModel(
    dataViewService: DataViewService,
    connectionState: LiveData<HubConnectionState>,
    connectionStateProvider: ConnectionStateProvider
) : ViewModel() {

    private val apiRoutes = ApiRoutes(dataViewService, connectionState)
    private val events = Events(connectionStateProvider)

    private val templateDataView = apiRoutes.getTasklistTemplate()
    private val taskMetadataDataView = apiRoutes.getTasklistMetadata()

    val templates: LiveData<List<TaskList>> = templateDataView.map { result ->
        result.data?.taskLists ?: emptyList()
    }

    val taskMetadata: LiveData<Map<Int, Pair<Int, Int>>> =
        taskMetadataDataView.map { result ->
            val metadata = result.data?.metadata ?: emptyList()
            metadata.associate { it.listId to (it.total to it.completed) }
        }

    val isLoading: LiveData<Boolean> = templateDataView.map { result ->
        result.loading
    }

    val error: LiveData<String?> = templateDataView.map { result ->
        result.error
    }

    fun addTemplate(title: String) {
        events.taskListAdd(false, "template", title)
    }

    fun reorderTemplate(listId: Int, afterListId: Int?) {
        events.taskListReorder(afterListId, listId)
    }

    fun archiveTaskList(listId: Int) {
        events.taskListUpdateArchived(true, listId)
    }
}
