package com.university.studentixflow.models

import kotlinx.serialization.Serializable

@Serializable
data class CourseRequest(
    val title: String,
    val description: String,
    val startDate: Long,
    val durationWeeks: Int
)

@Serializable
data class CourseResponse(
    val id: Int,
    val title: String,
    val description: String,
    val teacherId: Int,
    val teacherName: String,
    val isActive: Boolean,
    val startDate: Long,
    val durationWeeks: Int
)

@Serializable
data class CourseSectionResponse(
    val id: Int,
    val courseId: Int,
    val weekNumber: Int,
    val title: String,
    val description: String,
    val sortOrder: Int
)