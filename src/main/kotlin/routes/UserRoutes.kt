package com.university.studentixflow.routes

import com.university.studentixflow.models.AdminUserUpdateRequest
import com.university.studentixflow.models.UserResponse
import com.university.studentixflow.repository.UserRepository
import com.university.studentixflow.repository.UserData
import com.university.studentixflow.routes.RouteHelpers.requireAdmin
import com.university.studentixflow.routes.RouteHelpers.getUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.put

// Helper extension function to convert UserData to UserResponse
private fun UserData.toUserResponse() = UserResponse(
    id = id,
    email = email,
    fullName = fullName,
    role = role,
    isActive = isActive
)

fun Route.userRoutes(userRepository: UserRepository) {
    authenticate("auth-jwt") {
        get("/users") {
            if (!call.requireAdmin()) return@get

            val users = userRepository.getAllUsers()
            call.respond(HttpStatusCode.OK, users.map { it.toUserResponse() })
        }

        delete("/users/{id}") {
            val targetUserId = call.parameters["id"]?.toIntOrNull()
            val userId = call.getUserId()

            if (targetUserId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID or token"))
                return@delete
            }

            if (!call.requireAdmin()) return@delete

            if (targetUserId == userId) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cannot deactivate own account"))
                return@delete
            }

            val isActiveStatus = userRepository.getUserIsActiveStatus(targetUserId)

            if (isActiveStatus == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                return@delete
            }

            if (!isActiveStatus) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User is already inactive"))
                return@delete
            }

            val success = userRepository.deactivateUser(targetUserId)

            if (success) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Deactivation failed due to DB error"))
            }
        }

        put("/users/{id}/approve") {
            val targetUserId = call.parameters["id"]?.toIntOrNull()
            val userId = call.getUserId()

            if (targetUserId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID or token"))
                return@put
            }

            if (!call.requireAdmin()) return@put

            if (targetUserId == userId) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cannot approve own account"))
                return@put
            }

            val success = userRepository.activateUser(targetUserId)

            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "User $targetUserId approved successfully"))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
            }
        }

        put("/users/{id}") {
            val targetUserId = call.parameters["id"]?.toIntOrNull()
            val userId = call.getUserId()

            if (targetUserId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID or token"))
                return@put
            }

            if (!call.requireAdmin()) return@put

            if (targetUserId == userId) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cannot modify own account"))
                return@put
            }

            try {
                val request = call.receive<AdminUserUpdateRequest>()

                val success = userRepository.updateUser(targetUserId, request)

                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "User $targetUserId updated successfully"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid data or email already exists"))
            }
        }
    }
}
