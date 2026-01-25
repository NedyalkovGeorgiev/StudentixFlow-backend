package com.university.studentixflow.routes

import com.university.studentixflow.repository.CourseRepository
import com.university.studentixflow.repository.TestRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.response.respond

fun Route.testRoutes(testRepository: TestRepository, courseRepository: CourseRepository) {
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

        // Get test for taking (student view - no correct answers)
        get("/tests/{id}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toIntOrNull()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                return@get
            }

            val testId = call.parameters["id"]?.toIntOrNull()
            if (testId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid test ID"))
                return@get
            }

            // Check if student is enrolled in the course containing this test
            val courseId = testRepository.getCourseIdForTest(testId)
            if (courseId == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Test not found"))
                return@get
            }

            val isEnrolled = courseRepository.isStudentEnrolled(courseId, userId)
            val isOwner = courseRepository.isCourseOwner(courseId, userId)
            val role = principal.payload.getClaim("role")?.asString()
            val isAdmin = role == "ADMIN"

            if (!isEnrolled && !isOwner && !isAdmin) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "You must be enrolled in this course to take this test"))
                return@get
            }

            val test = testRepository.getTestForTaking(testId)
            if (test == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Test not found"))
                return@get
            }

            call.respond(HttpStatusCode.OK, test)
        }
    }
}