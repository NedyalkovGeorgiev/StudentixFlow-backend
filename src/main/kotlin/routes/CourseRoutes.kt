package com.university.studentixflow.routes

import com.university.studentixflow.models.CourseRequest
import com.university.studentixflow.models.CourseWithContentResponse
import com.university.studentixflow.repository.CourseContentRepository
import com.university.studentixflow.repository.CourseRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.response.*
import io.ktor.server.routing.delete
import io.ktor.server.routing.get

fun Route.courseRoutes(courseRepository: CourseRepository, courseContentRepository: CourseContentRepository) {
    authenticate("auth-jwt") {
        post("/courses") {
            val principal = call.principal<JWTPrincipal>()

            val userId = principal?.payload?.subject?.toIntOrNull()

            val role = principal?.payload?.getClaim("role")?.asString()

            if (role != "TEACHER" && role != "ADMIN") {
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Only teacher and admins can create courses")
                )
                return@post
            }

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                return@post
            }

            try {
                val request = call.receive<CourseRequest>()
                var teacherId = if (role == "ADMIN") request.teacherId else null
                val courseId = courseRepository.createCourse(request, userId, teacherId)

                call.respond(HttpStatusCode.Created, mapOf("id" to courseId))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        get("/courses") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toIntOrNull()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                return@get
            }

            val courses = when (role) {
                "ADMIN" -> courseRepository.getAllCourses()
                "TEACHER" -> courseRepository.getCoursesByTeacher(userId)
                "STUDENT" -> courseRepository.getCoursesByStudent(userId)
                else -> emptyList()
            }

            call.respond(HttpStatusCode.OK, courses)
        }

        get("/courses/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toIntOrNull()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (id == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid course ID or token"))
                return@get
            }

            val course = courseRepository.getCourseById(id)

            if (course == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Course not found"))
                return@get
            }

            val isOwner = course.teacherId == userId
            val canView = when (role) {
                "ADMIN" -> true
                "TEACHER" -> isOwner
                "STUDENT" -> courseRepository.isStudentEnrolled(id, userId)
                else -> false
            }

            if (!canView) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied to this course detail"))
                return@get
            }

            val sectionsWithContent = courseContentRepository.getCourseContent(id)

            val response = CourseWithContentResponse(
                id = course.id,
                title = course.title,
                description = course.description,
                teacherId = course.teacherId,
                teacherName = course.teacherName,
                isActive = course.isActive,
                startDate = course.startDate,
                durationWeeks = course.durationWeeks,
                sections = sectionsWithContent
            )

            call.respond(HttpStatusCode.OK, response)
        }

        delete("/courses/{id}") {
            val courseId = call.parameters["id"]?.toIntOrNull()
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toIntOrNull()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (courseId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID or token"))
                return@delete
            }

            if (role == "ADMIN") {
                val success = courseRepository.deleteCourseByAdmin(courseId)

                if (success) {
                    call.respond(HttpStatusCode.NoContent)
                    return@delete
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Course not found"))
                    return@delete
                }
            }

            if (role == "TEACHER") {
                val success = courseRepository.deleteCourse(courseId, userId)

                if (success) {
                    call.respond(HttpStatusCode.NoContent)
                    return@delete
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "You do not have permission to delete this course"))
                    return@delete
                }
            }

            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "You do not have permission to delete this course"))
        }
    }
}