package com.tomyedwab.yellowstone.ui.templates

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.google.gson.reflect.TypeToken
import com.tomyedwab.yellowstone.models.TaskList
import com.tomyedwab.yellowstone.models.TaskListResponse
import com.tomyedwab.yellowstone.provider.connection.ConnectionAction
import com.tomyedwab.yellowstone.provider.connection.ConnectionStateProvider
import com.tomyedwab.yellowstone.provider.connection.HubConnectionState
import com.tomyedwab.yellowstone.provider.connection.PendingEvent
import com.tomyedwab.yellowstone.services.connection.DataViewResult
import com.tomyedwab.yellowstone.services.connection.DataViewService
import java.time.Instant
import java.util.UUID

class TemplatesViewModel(
    private val dataViewService: DataViewService,
    private val connectionState: LiveData<HubConnectionState>,
    private val connectionStateProvider: ConnectionStateProvider
) : ViewModel() {

    private val templateDataView: LiveData<DataViewResult<TaskListResponse>> = 
        dataViewService.createDataView(
            connectionState = connectionState,
            componentName = "yellowstone",
            apiPath = "api/tasklist/template",
            apiParams = emptyMap(),
            typeToken = object : TypeToken<TaskListResponse>() {}
        )

    val templates: LiveData<List<TaskList>> = templateDataView.map { result ->
        result.data?.taskLists ?: emptyList()
    }

    val isLoading: LiveData<Boolean> = templateDataView.map { result ->
        result.loading
    }

    val error: LiveData<String?> = templateDataView.map { result ->
        result.error
    }

    fun addTemplate(title: String) {
        val event = PendingEvent(
            clientId = UUID.randomUUID().toString(),
            type = "TaskList:Add",
            timestamp = Instant.now().toString(),
            data = mapOf(
                "title" to title,
                "category" to "template",
                "archived" to false
            )
        )
        connectionStateProvider.dispatch(ConnectionAction.PublishEvent(event))
    }
}
