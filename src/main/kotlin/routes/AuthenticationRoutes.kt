package com.university.studentixflow.routes

import com.university.studentixflow.models.RegisterRequest
import com.university.studentixflow.repository.UserRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authenticationRoutes(userRepository: UserRepository) {
    val validRoles = listOf("STUDENT", "TEACHER", "ADMIN")

    post("/register") {
        val request = call.receive<RegisterRequest>()

        if (request.role.uppercase() !in validRoles) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Invalid role. Must be STUDENT, TEACHER, or ADMIN")
            )
        }

        try {
            userRepository.registerUser(request)

            call.respond(
                HttpStatusCode.Created,
                mapOf("message" to "User registered successfully")
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.Conflict,
                mapOf("error" to "Email already exists")
            )
        }
    }
}