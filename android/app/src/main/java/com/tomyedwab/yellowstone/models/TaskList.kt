package com.tomyedwab.yellowstone.models

import com.google.gson.annotations.SerializedName

data class TaskList(
        @SerializedName("Id") val id: Int,
        @SerializedName("Title") val title: String,
        @SerializedName("Category") val category: String,
        @SerializedName("Archived") val archived: Boolean
)

data class TaskListResponse(@SerializedName("TaskLists") val taskLists: List<TaskList>)

data class TaskMetadata(
        @SerializedName("ListId") val listId: Int,
        @SerializedName("TotalTasks") val totalTasks: Int,
        @SerializedName("CompletedTasks") val completedTasks: Int
)

data class TaskMetadataResponse(@SerializedName("Metadata") val metadata: List<TaskMetadata>)
