package com.tomyedwab.yellowstone.models

import com.google.gson.annotations.SerializedName

data class TaskHistoryEntry(
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("systemComment") val systemComment: String?,
    @SerializedName("userComment") val userComment: String?
)

data class TaskHistoryResponse(
    @SerializedName("title") val taskTitle: String,
    @SerializedName("history") val history: List<TaskHistoryEntry>
)

data class AddCommentRequest(
    @SerializedName("taskId") val taskId: Int,
    @SerializedName("userComment") val userComment: String
)
