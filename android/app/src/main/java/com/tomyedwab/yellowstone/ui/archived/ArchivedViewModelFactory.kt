package com.tomyedwab.yellowstone.ui.archived

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tomyedwab.yellowstone.provider.connection.HubConnectionState
import com.tomyedwab.yellowstone.services.connection.DataViewService
import com.tomyedwab.yellowstone.provider.connection.ConnectionStateProvider

class ArchivedViewModelFactory(
    private val dataViewService: DataViewService,
    private val connectionState: LiveData<HubConnectionState>,
    private val connectionStateProvider: ConnectionStateProvider
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ArchivedViewModel::class.java)) {
            return ArchivedViewModel(dataViewService, connectionState, connectionStateProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}