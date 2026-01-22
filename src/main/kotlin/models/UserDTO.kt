package com.university.studentixflow.models

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val fullName: String,
    val role: UserRole
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class UserResponse(
    val id: Int,
    val email: String,
    val fullName: String,
    val role: UserRole,
    val isActive: Boolean
)

@Serializable
data class AuthResponse(
    val user:UserResponse,
    val token: String
)