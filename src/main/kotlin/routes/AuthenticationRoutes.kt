package com.university.studentixflow.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.university.studentixflow.models.AuthResponse
import com.university.studentixflow.models.LoginRequest
import com.university.studentixflow.models.RegisterRequest
import com.university.studentixflow.models.UserResponse
import com.university.studentixflow.models.UserRole
import com.university.studentixflow.repository.UserData
import com.university.studentixflow.repository.UserRepository
import io.ktor.http.*
import io.ktor.server.application.Application
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.sql.Date
import kotlin.time.Duration.Companion.hours

fun generateToken(user: UserData, application: Application): String {
    val jwtAudience = application.environment.config.property("jwt.audience").getString()
    val jwtDomain = application.environment.config.property("jwt.domain").getString()
    val jwtSecret = application.environment.config.property("jwt.secret").getString()

    val expirationDate = Date(System.currentTimeMillis() + 24.hours.inWholeMilliseconds)

    return JWT.create()
        .withAudience(jwtAudience)
        .withIssuer(jwtDomain)
        .withSubject(user.id.toString())
        .withClaim("role", user.role.name)
        .withExpiresAt(expirationDate)
        .sign(Algorithm.HMAC256(jwtSecret))
}

fun Route.authenticationRoutes(userRepository: UserRepository) {

    post("/register") {
        val request = call.receive<RegisterRequest>()

        if (request.role == UserRole.ADMIN) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cannot register as Administrator"))
            return@post
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

    post("/login") {
        val request = call.receive<LoginRequest>()

        val user = userRepository.findUserForLogin(request.email)

        if (user == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
            return@post
        }

        if (!user.isActive) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Account is inactive. Waiting for admin approval."))
            return@post
        }

        val isPasswordValid = userRepository.verifyPassword(request.password, user.hashedPassword)

        if(!isPasswordValid) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
            return@post
        }

        val token = generateToken(user, application)

        val userResponse = UserResponse(
            id = user.id,
            email = user.email,
            fullName = user.fullName,
            role = user.role,
            isActive = user.isActive
        )

        call.respond(
            HttpStatusCode.OK,
            AuthResponse(user = userResponse, token = token)
        )
    }
}