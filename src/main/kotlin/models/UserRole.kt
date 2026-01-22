package com.university.studentixflow.models

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    ADMIN, TEACHER, STUDENT
}