package com.tomyedwab.yellowstone.ui.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.tomyedwab.yellowstone.generated.ApiRoutes
import com.tomyedwab.yellowstone.generated.Events
import com.tomyedwab.yellowstone.generated.TaskHistoryResponse
import com.tomyedwab.yellowstone.provider.connection.ConnectionStateProvider
import com.tomyedwab.yellowstone.provider.connection.HubConnectionState
import com.tomyedwab.yellowstone.services.connection.DataViewResult
import com.tomyedwab.yellowstone.services.connection.DataViewService
import kotlinx.coroutines.launch

class TaskHistoryViewModel(
    dataViewService: DataViewService,
    connectionState: LiveData<HubConnectionState>,
    connectionStateProvider: ConnectionStateProvider,
    private val taskId: Int
) : ViewModel() {

    private val apiRoutes = ApiRoutes(dataViewService, connectionState)
    private val events = Events(connectionStateProvider)
    private val refreshTrigger = MutableLiveData(0)

    val taskHistory: LiveData<DataViewResult<TaskHistoryResponse>> = refreshTrigger.switchMap {
        apiRoutes.getTaskHistory(taskId).switchMap { result ->
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
                events.taskAddComment(taskId, comment)

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
