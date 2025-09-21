package com.tomyedwab.yellowstone.ui.lists

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.google.gson.reflect.TypeToken
import com.tomyedwab.yellowstone.models.TaskList
import com.tomyedwab.yellowstone.models.TaskListResponse
import com.tomyedwab.yellowstone.models.TaskMetadataResponse
import com.tomyedwab.yellowstone.provider.connection.ConnectionAction
import com.tomyedwab.yellowstone.provider.connection.ConnectionStateProvider
import com.tomyedwab.yellowstone.provider.connection.HubConnectionState
import com.tomyedwab.yellowstone.provider.connection.PendingEvent
import com.tomyedwab.yellowstone.services.connection.DataViewResult
import com.tomyedwab.yellowstone.services.connection.DataViewService
import java.time.Instant
import java.util.UUID

class ListsViewModel(
        private val dataViewService: DataViewService,
        private val connectionState: LiveData<HubConnectionState>,
        private val connectionStateProvider: ConnectionStateProvider
) : ViewModel() {

    private val taskListDataView: LiveData<DataViewResult<TaskListResponse>> =
            dataViewService.createDataView(
                    connectionState = connectionState,
                    componentName = "yellowstone",
                    apiPath = "api/tasklist/todo",
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

    val taskLists: LiveData<List<TaskList>> =
            taskListDataView.map { result -> result.data?.taskLists ?: emptyList() }

    val taskMetadata: LiveData<Map<Int, Pair<Int, Int>>> =
            taskMetadataDataView.map { result ->
                val metadata = result.data?.metadata ?: emptyList()
                metadata.associate { it.listId to (it.totalTasks to it.completedTasks) }
            }

    val isLoading: LiveData<Boolean> = taskListDataView.map { result -> result.loading }

    val error: LiveData<String?> = taskListDataView.map { result -> result.error }

    fun addTaskList(title: String) {
        val event =
                PendingEvent(
                        clientId = UUID.randomUUID().toString(),
                        type = "TaskList:Add",
                        timestamp = Instant.now().toString(),
                        data =
                                mapOf(
                                        "title" to title,
                                        "category" to "toDoList",
                                        "archived" to false
                                )
                )
        connectionStateProvider.dispatch(ConnectionAction.PublishEvent(event))
    }

    fun archiveTaskList(listId: Int) {
        val event =
                PendingEvent(
                        clientId = UUID.randomUUID().toString(),
                        type = "TaskList:UpdateArchived",
                        timestamp = Instant.now().toString(),
                        data = mapOf("listId" to listId, "archived" to true)
                )
        connectionStateProvider.dispatch(ConnectionAction.PublishEvent(event))
    }

    fun reorderTaskList(listId: Int, afterListId: Int?) {
        val event =
                PendingEvent(
                        clientId = UUID.randomUUID().toString(),
                        type = "TaskList:Reorder",
                        timestamp = Instant.now().toString(),
                        data = mapOf("listId" to listId, "afterListId" to (afterListId ?: ""))
                )
        connectionStateProvider.dispatch(ConnectionAction.PublishEvent(event))
    }
}
