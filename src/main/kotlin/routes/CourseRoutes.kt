package com.university.studentixflow.routes

import com.university.studentixflow.models.CourseRequest
import com.university.studentixflow.repository.CourseRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.response.*

fun Route.courseRoutes(courseRepository: CourseRepository) {
    authenticate("auth-jwt") {
        post("/courses") {
            val principal = call.principal<JWTPrincipal>()

            val teacherId = principal?.payload?.subject?.toIntOrNull()

            val role = principal?.payload?.getClaim("role")?.asString()

            if (role != "TEACHER" && role != "ADMIN") {
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Only teacher and admins can create courses")
                )
                return@post
            }

            if (teacherId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                return@post
            }

            try {
                val request = call.receive<CourseRequest>()
                val courseId = courseRepository.createCourse(request, teacherId)

                call.respond(HttpStatusCode.Created, mapOf("id" to courseId))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }
    }
}