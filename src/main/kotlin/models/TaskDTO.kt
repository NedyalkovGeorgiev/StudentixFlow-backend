package com.university.studentixflow.models

import kotlinx.serialization.Serializable

@Serializable
data class TaskDTO(
    val id: Int,
    val courseId: Int,
    val title: String,
    val description: String,
    val dueDate: Long
)