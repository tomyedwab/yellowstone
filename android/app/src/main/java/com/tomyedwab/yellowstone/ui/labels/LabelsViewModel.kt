package com.tomyedwab.yellowstone.ui.labels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tomyedwab.yellowstone.models.TaskList

class LabelsViewModel : ViewModel() {

    private val _labels = MutableLiveData<List<TaskList>>().apply {
        // Sample data for now
        value = listOf(
            TaskList(1, "Urgent", "test", false),
            TaskList(2, "Personal", "test", false),
            TaskList(3, "Work", "test", false),
            TaskList(4, "Later", "test", false)
        )
    }
    val labels: LiveData<List<TaskList>> = _labels

    fun addLabel(label: TaskList) {
        val currentLabels = _labels.value?.toMutableList() ?: mutableListOf()
        currentLabels.add(label)
        _labels.value = currentLabels
    }

    fun removeLabel(label: TaskList) {
        val currentLabels = _labels.value?.toMutableList() ?: return
        currentLabels.remove(label)
        _labels.value = currentLabels
    }
}
