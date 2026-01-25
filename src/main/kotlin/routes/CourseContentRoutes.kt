package com.university.studentixflow.routes

import com.university.studentixflow.models.MaterialRequest
import com.university.studentixflow.models.SectionRequest
import com.university.studentixflow.models.TaskRequest
import com.university.studentixflow.models.TestRequest
import com.university.studentixflow.repository.CourseContentRepository
import com.university.studentixflow.repository.CourseRepository
import com.university.studentixflow.routes.RouteHelpers.getUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.response.respond


fun Route.courseContentRoutes(
    courseRepository: CourseRepository,
    courseContentRepository: CourseContentRepository
) {
    authenticate("auth-jwt") {
        post("/courses/{courseId}/sections") {
            val courseId = call.parameters["courseId"]?.toIntOrNull()
            val userId = call.getUserId()
            val principal = call.principal<JWTPrincipal>()
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
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid section data: ${e.message}"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to create section"))
            }
        }

        post("/sections/{sectionId}/tasks") {
            val sectionId = call.parameters["sectionId"]?.toIntOrNull()
            val userId = call.getUserId()
            val principal = call.principal<JWTPrincipal>()
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
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid task data: ${e.message}"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to create task"))
            }
        }

        post("/sections/{sectionId}/materials") {
            val sectionId = call.parameters["sectionId"]?.toIntOrNull()
            val userId = call.getUserId()
            val principal = call.principal<JWTPrincipal>()
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
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid material data: ${e.message}"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to create material"))
            }
        }

        post("/sections/{sectionId}/tests") {
            val sectionId = call.parameters["sectionId"]?.toIntOrNull()
            val userId = call.getUserId()
            val principal = call.principal<JWTPrincipal>()
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
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid test data: ${e.message}"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to create test"))
            }
        }
    }
}