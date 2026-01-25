package com.university.studentixflow.routes

import com.university.studentixflow.repository.TestRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.response.respond

fun Route.testRoutes(testRepository: TestRepository) {
    authenticate("auth-jwt") {
        get("/me/results") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toIntOrNull()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                return@get
            }

            val results = testRepository.getResultsForStudent(userId)
            call.respond(HttpStatusCode.OK, results)
        }
    }
}