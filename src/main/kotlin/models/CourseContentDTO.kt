package com.university.studentixflow.models

import kotlinx.serialization.Serializable

@Serializable
enum class MaterialType {
    VIDEO,
    PDF,
    LINK,
    DOCUMENT,
    OTHER
}

@Serializable
data class SectionRequest(
    val weekNumber: Int,
    val title: String,
    val description: String,
    val url: String? = null,
    val sortOrder: Int = 0
)

@Serializable
data class SectionResponse(
    val id: Int,
    val courseId: Int,
    val weekNumber: Int,
    val title: String,
    val description: String,
    val url: String?,
    val sortOrder: Int
)

@Serializable
data class TaskRequest(
    val title: String,
    val description: String,
    val dueDate: Long
)

@Serializable
data class TaskResponse(
    val id: Int,
    val sectionId: Int,
    val title: String,
    val description: String,
    val dueDate: Long
)

@Serializable
data class MaterialRequest(
    val title: String,
    val url: String,
    val type: MaterialType,
    val isVisible: Boolean = true
)

@Serializable
data class MaterialResponse(
    val id: Int,
    val sectionId: Int,
    val title: String,
    val url: String,
    val type: MaterialType,
    val isVisible: Boolean
)

@Serializable
data class SectionWithContentResponse(
    val id: Int,
    val courseId: Int,
    val weekNumber: Int,
    val title: String,
    val description: String,
    val url: String?,
    val sortOrder: Int,
    val tasks: List<TaskResponse>,
    val materials: List<MaterialResponse>,
    val tests: List<TestSummaryResponse> = emptyList()
)

@Serializable
data class CourseWithContentResponse(
    val id: Int,
    val title: String,
    val description: String,
    val teacherId: Int,
    val teacherName: String,
    val isActive: Boolean,
    val startDate: Long,
    val durationWeeks: Int,
    val sections: List<SectionWithContentResponse>
)