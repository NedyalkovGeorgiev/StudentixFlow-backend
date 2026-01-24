package com.university.studentixflow.models

import kotlinx.serialization.Serializable

@Serializable
data class AdminUserUpdateRequest(
    val fullName: String? = null,
    val email: String? = null,
    val role: UserRole? = null,
    val isActive: Boolean? = null
)