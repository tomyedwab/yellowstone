package com.tomyedwab.yellowstone.ui.archived

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.google.gson.reflect.TypeToken
import com.tomyedwab.yellowstone.models.TaskList
import com.tomyedwab.yellowstone.models.TaskListResponse
import com.tomyedwab.yellowstone.models.TaskMetadataResponse
import com.tomyedwab.yellowstone.provider.connection.HubConnectionState
import com.tomyedwab.yellowstone.services.connection.DataViewResult
import com.tomyedwab.yellowstone.services.connection.DataViewService
import com.tomyedwab.yellowstone.provider.connection.ConnectionAction
import com.tomyedwab.yellowstone.provider.connection.ConnectionStateProvider
import com.tomyedwab.yellowstone.provider.connection.PendingEvent
import java.time.Instant
import java.util.UUID

class ArchivedViewModel(
    private val dataViewService: DataViewService,
    private val connectionState: LiveData<HubConnectionState>,
    private val connectionStateProvider: ConnectionStateProvider
) : ViewModel() {

    private val allTaskListsDataView: LiveData<DataViewResult<TaskListResponse>> =
        dataViewService.createDataView(
            connectionState = connectionState,
            componentName = "yellowstone",
            apiPath = "api/tasklist/all",
            apiParams = emptyMap(),
            typeToken = object : TypeToken<TaskListResponse>() {}
        )

    private val taskMetadataDataView: LiveData<DataViewResult<TaskMetadataResponse>> =
        dataViewService.createDataView(
            connectionState = connectionState,
            componentName = "yellowstone",
            apiPath = "api/tasklist/metadata",
            apiParams = emptyMap(),
            typeToken = object : TypeToken<TaskMetadataResponse>() {}
        )

    val archivedItems: LiveData<List<TaskList>> = allTaskListsDataView.map { result ->
        result.data?.taskLists?.filter { it.archived } ?: emptyList()
    }

    val taskMetadata: LiveData<Map<Int, Pair<Int, Int>>> = taskMetadataDataView.map { result ->
        val metadata = result.data?.metadata ?: emptyList()
        metadata.associate { it.listId to (it.totalTasks to it.completedTasks) }
    }

    val isLoading: LiveData<Boolean> = allTaskListsDataView.map { result ->
        result.loading
    }

    val error: LiveData<String?> = allTaskListsDataView.map { result ->
        result.error
    }

    fun unarchiveTaskList(listId: Int) {
        val event = PendingEvent(
            clientId = UUID.randomUUID().toString(),
            type = "TaskList:UpdateArchived",
            timestamp = Instant.now().toString(),
            data = mapOf(
                "listId" to listId,
                "archived" to false
            )
        )
        connectionStateProvider.dispatch(ConnectionAction.PublishEvent(event))
    }
}
