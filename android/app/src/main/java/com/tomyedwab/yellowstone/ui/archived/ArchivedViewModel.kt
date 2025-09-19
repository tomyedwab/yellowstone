package com.tomyedwab.yellowstone.ui.archived

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.google.gson.reflect.TypeToken
import com.tomyedwab.yellowstone.models.TaskList
import com.tomyedwab.yellowstone.models.TaskListResponse
import com.tomyedwab.yellowstone.provider.connection.HubConnectionState
import com.tomyedwab.yellowstone.services.connection.DataViewResult
import com.tomyedwab.yellowstone.services.connection.DataViewService

class ArchivedViewModel(
    private val dataViewService: DataViewService,
    private val connectionState: LiveData<HubConnectionState>
) : ViewModel() {

    private val archivedDataView: LiveData<DataViewResult<TaskListResponse>> = 
        dataViewService.createDataView(
            connectionState = connectionState,
            componentName = "yellowstone",
            apiPath = "api/tasklist/archived",
            apiParams = emptyMap(),
            typeToken = object : TypeToken<TaskListResponse>() {}
        )

    val archivedItems: LiveData<List<TaskList>> = archivedDataView.map { result ->
        result.data?.taskLists ?: emptyList()
    }

    val isLoading: LiveData<Boolean> = archivedDataView.map { result ->
        result.loading
    }

    val error: LiveData<String?> = archivedDataView.map { result ->
        result.error
    }
}
