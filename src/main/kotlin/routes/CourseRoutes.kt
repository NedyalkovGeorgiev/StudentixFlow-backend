package com.university.studentixflow.routes

import com.university.studentixflow.models.CourseRequest
import com.university.studentixflow.models.EnrollStudentRequest
import com.university.studentixflow.models.MoveStudentRequest
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
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException

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

        get("/courses/available") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toIntOrNull()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                return@get
            }

            // Return all active courses (for enrollment browsing)
            val courses = courseRepository.getAllCourses().filter { it.isActive }
            call.respond(HttpStatusCode.OK, courses)
        }

        post("/courses/{id}/enroll") {
            val courseId = call.parameters["id"]?.toIntOrNull()
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toIntOrNull()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (courseId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid course ID or token"))
                return@post
            }

            // Students cannot enroll themselves - must be enrolled by teacher/admin
            if (role == "STUDENT") {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Students cannot enroll themselves. Please contact your teacher or admin."))
                return@post
            }

            // Only TEACHER and ADMIN can enroll students
            if (role != "TEACHER" && role != "ADMIN") {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Only teachers and admins can enroll students"))
                return@post
            }

            try {
                val request = call.receive<EnrollStudentRequest>()

                // TEACHER must own the course
                if (role == "TEACHER") {
                    val isOwner = courseRepository.isCourseOwner(courseId, userId)
                    if (!isOwner) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "You can only enroll students in your own courses"))
                        return@post
                    }
                }

                // ADMIN can enroll in any course
                courseRepository.enrollStudent(courseId, request.studentId)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Student enrolled successfully"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "An unexpected error occurred"))
            }
        }

        // ============================================
        // Student Management Endpoints
        // ============================================

        // DELETE /courses/{courseId}/students/{studentId} - Remove a student from a course
        delete("/courses/{courseId}/students/{studentId}") {
            val courseId = call.parameters["courseId"]?.toIntOrNull()
            val studentId = call.parameters["studentId"]?.toIntOrNull()
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toIntOrNull()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (courseId == null || studentId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid course ID, student ID, or token"))
                return@delete
            }

            // Authorization: ADMIN can remove from any course, TEACHER only from own courses
            val isOwner = courseRepository.isCourseOwner(courseId, userId)
            if (role != "ADMIN" && !isOwner) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                return@delete
            }

            val success = courseRepository.unenrollStudent(courseId, studentId)
            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Student removed from course successfully"))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Student enrollment not found"))
            }
        }

        // POST /courses/{courseId}/students/{studentId}/move - Move a student to another course
        post("/courses/{courseId}/students/{studentId}/move") {
            val sourceCourseId = call.parameters["courseId"]?.toIntOrNull()
            val studentId = call.parameters["studentId"]?.toIntOrNull()
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toIntOrNull()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (sourceCourseId == null || studentId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid course ID, student ID, or token"))
                return@post
            }

            try {
                val request = call.receive<MoveStudentRequest>()
                val targetCourseId = request.targetCourseId

                // Authorization logic:
                // - ADMIN: can move between any courses
                // - TEACHER: must own the TARGET course (can pull students into their courses)
                val ownsTargetCourse = courseRepository.isCourseOwner(targetCourseId, userId)
                if (role != "ADMIN" && !ownsTargetCourse) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "You can only move students into your own courses"))
                    return@post
                }

                // Verify target course exists
                val targetCourse = courseRepository.getCourseById(targetCourseId)
                if (targetCourse == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Target course not found"))
                    return@post
                }

                // Verify student is enrolled in source course
                val isEnrolled = courseRepository.isStudentEnrolled(sourceCourseId, studentId)
                if (!isEnrolled) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Student is not enrolled in the source course"))
                    return@post
                }

                courseRepository.moveStudent(sourceCourseId, targetCourseId, studentId)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Student moved successfully"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request: ${e.message}"))
            }
        }

        // GET /courses/{courseId}/participants - Get course participants (teacher and students)
        get("/courses/{courseId}/participants") {
            val courseId = call.parameters["courseId"]?.toIntOrNull()
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toIntOrNull()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (courseId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid course ID or token"))
                return@get
            }

            // Authorization: ADMIN can view any, TEACHER only own courses
            val isOwner = courseRepository.isCourseOwner(courseId, userId)
            if (role != "ADMIN" && !isOwner) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                return@get
            }

            val participants = courseRepository.getCourseParticipants(courseId)
            if (participants == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Course not found"))
                return@get
            }

            call.respond(HttpStatusCode.OK, participants)
        }
    }
}