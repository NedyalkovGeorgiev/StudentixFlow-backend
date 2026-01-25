package com.university.studentixflow.routes

import com.auth0.jwt.JWT
import com.university.studentixflow.models.MaterialRequest
import com.university.studentixflow.models.SectionRequest
import com.university.studentixflow.models.TaskRequest
import com.university.studentixflow.models.TestRequest
import com.university.studentixflow.models.TestSubmissionRequest
import com.university.studentixflow.repository.CourseContentRepository
import com.university.studentixflow.repository.CourseRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.response.respond
import java.lang.IllegalArgumentException


fun Route.courseContentRoutes(
    courseRepository: CourseRepository,
    courseContentRepository: CourseContentRepository
) {
    authenticate("auth-jwt") {
        post("/courses/{courseId}/sections") {
            val courseId = call.parameters["courseId"]?.toIntOrNull()
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toIntOrNull()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (courseId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID or token"))
                return@post
            }

            val isOwner = courseRepository.isCourseOwner(courseId, userId)
            if (role != "ADMIN" && !isOwner) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Only the course owner can add sections"))
                return@post
            }

            try {
                val request = call.receive<SectionRequest>()
                val id = courseContentRepository.createSection(courseId, request)
                call.respond(HttpStatusCode.Created, mapOf("id" to id))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        post("/sections/{sectionId}/tasks") {
            val sectionId = call.parameters["sectionId"]?.toIntOrNull()
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toIntOrNull()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (sectionId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID or token"))
                return@post
            }

            val isOwner = courseContentRepository.isSectionOwner(sectionId, userId)
            if (role != "ADMIN" && !isOwner) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                return@post
            }

            try {
                val request = call.receive<TaskRequest>()
                val id = courseContentRepository.createTask(sectionId, request)
                call.respond(HttpStatusCode.Created, mapOf("id" to id))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        post("/sections/{sectionId}/materials") {
            val sectionId = call.parameters["sectionId"]?.toIntOrNull()
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toIntOrNull()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (sectionId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID or token"))
                return@post
            }

            val isOwner = courseContentRepository.isSectionOwner(sectionId, userId)

            if (role != "ADMIN" && !isOwner) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                return@post
            }

            try {
                val request = call.receive<MaterialRequest>()
                val id = courseContentRepository.createMaterial(sectionId, request)
                call.respond(HttpStatusCode.Created, mapOf("id" to id))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        post("/sections/{sectionId}/tests") {
            val sectionId = call.parameters["sectionId"]?.toIntOrNull()
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toIntOrNull()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (sectionId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID or token"))
                return@post
            }

            val isOwner = courseContentRepository.isSectionOwner(sectionId, userId)
            if (role != "ADMIN" && !isOwner) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                return@post
            }

            try {
                val request = call.receive<TestRequest>()
                val id = courseContentRepository.createTest(sectionId, request)
                call.respond(HttpStatusCode.Created, mapOf("id" to id))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        post("/tests/{id}/submit") {
            val testId = call.parameters["id"]?.toIntOrNull()
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toIntOrNull()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (testId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid test ID or token"))
                return@post
            }
            // TODO: Check if student is enrolled
            try {
                val request = call.receive<TestSubmissionRequest>()
                val score = courseContentRepository.submitTest(testId, userId, request)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Test submitted successfully", "score" to score.toString()))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid submission format", "details" to e.message))
            }
        }
    }
}