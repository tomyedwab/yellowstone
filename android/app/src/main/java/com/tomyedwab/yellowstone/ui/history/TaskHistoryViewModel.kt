package com.tomyedwab.yellowstone.ui.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.google.gson.reflect.TypeToken
import com.tomyedwab.yellowstone.models.TaskHistoryResponse
import com.tomyedwab.yellowstone.provider.connection.ConnectionAction
import com.tomyedwab.yellowstone.provider.connection.ConnectionStateProvider
import com.tomyedwab.yellowstone.provider.connection.HubConnectionState
import com.tomyedwab.yellowstone.provider.connection.PendingEvent
import com.tomyedwab.yellowstone.services.connection.DataViewResult
import com.tomyedwab.yellowstone.services.connection.DataViewService
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class TaskHistoryViewModel(
    private val dataViewService: DataViewService,
    private val connectionState: LiveData<HubConnectionState>,
    private val connectionStateProvider: ConnectionStateProvider,
    private val taskId: Int
) : ViewModel() {

    private val refreshTrigger = MutableLiveData(0)

    val taskHistory: LiveData<DataViewResult<TaskHistoryResponse>> = refreshTrigger.switchMap {
        dataViewService.createDataView(
            connectionState = connectionState,
            componentName = "yellowstone",
            apiPath = "api/task/history",
            apiParams = mapOf("id" to taskId.toString()),
            typeToken = object : TypeToken<TaskHistoryResponse>() {}
        ).switchMap { result ->
            val transformedResult = MutableLiveData<DataViewResult<TaskHistoryResponse>>()
            transformedResult.value = DataViewResult(
                loading = result.loading,
                data = result.data,
                error = result.error
            )
            transformedResult
        }
    }

    init {
        refreshTrigger.value = 0
    }

    fun addComment(comment: String) {
        viewModelScope.launch {
            try {
                val event = PendingEvent(
                    clientId = UUID.randomUUID().toString(),
                    type = "Task:AddComment",
                    timestamp = Instant.now().toString(),
                    data = mapOf(
                        "taskId" to taskId,
                        "userComment" to comment
                    )
                )
                connectionStateProvider.dispatch(ConnectionAction.PublishEvent(event))

                // Trigger a refresh to reload history after comment is submitted
                refreshTrigger.value = refreshTrigger.value?.plus(1) ?: 1
            } catch (e: Exception) {
                // Error handling would be managed by the data views
            }
        }
    }

}

class TaskHistoryViewModelFactory(
    private val dataViewService: DataViewService,
    private val connectionState: LiveData<HubConnectionState>,
    private val connectionStateProvider: ConnectionStateProvider,
    private val taskId: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskHistoryViewModel::class.java)) {
            return TaskHistoryViewModel(dataViewService, connectionState, connectionStateProvider, taskId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
