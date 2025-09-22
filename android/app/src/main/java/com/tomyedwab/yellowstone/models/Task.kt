package com.tomyedwab.yellowstone.models

import com.google.gson.annotations.SerializedName

data class Task(
    @SerializedName("Id") val id: Int,
    @SerializedName("Title") val title: String,
    @SerializedName("CompletedAt") val completedAt: String?,
    @SerializedName("DueDate") val dueDate: String?,
    @SerializedName("ListId") val listId: Int,
    var isSelected: Boolean = false
)

data class TaskResponse(@SerializedName("Tasks") val tasks: List<Task>)

data class TaskComment(
    @SerializedName("TaskId") val taskId: Int,
    @SerializedName("Comment") val comment: String
)

data class TaskCommentsResponse(@SerializedName("Comments") val comments: List<TaskComment>)

data class TaskLabel(
    @SerializedName("Id") val id: Int,
    @SerializedName("Name") val name: String,
    @SerializedName("Color") val color: String?
)

data class TaskLabelsResponse(@SerializedName("Labels") val labels: List<TaskLabel>)
