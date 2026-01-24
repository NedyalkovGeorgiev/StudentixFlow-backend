package com.university.studentixflow.routes

import com.university.studentixflow.models.UserResponse
import com.university.studentixflow.repository.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.response.respond
import io.ktor.server.routing.delete

fun Route.userRoutes(userRepository: UserRepository) {
    authenticate("auth-jwt") {
        get("/users") {
            val principal = call.principal<JWTPrincipal>()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (role != "ADMIN") {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied: Admin role required"))
                return@get
            }

            val users = userRepository.getAllUsers()

            val userResponses = users.map { userData ->
                UserResponse(
                    id = userData.id,
                    email = userData.email,
                    fullName = userData.fullName,
                    role = userData.role,
                    isActive = userData.isActive
                )
            }

            call.respond(HttpStatusCode.OK, userResponses)
        }

        delete("/users/{id}") {
            val targetUserId = call.parameters["id"]?.toIntOrNull()
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toIntOrNull()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (targetUserId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID or token"))
                return@delete
            }

            if (role != "ADMIN") {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied: Admin role required"))
                return@delete
            }

            if (targetUserId == userId) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cannot deactivate own account"))
                return@delete
            }

            val success = userRepository.deactivateUser(targetUserId)

            if (success) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found or already inactive"))
            }
        }
    }
}