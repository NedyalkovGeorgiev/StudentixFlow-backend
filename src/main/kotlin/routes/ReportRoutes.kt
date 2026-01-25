package com.university.studentixflow.routes

import com.university.studentixflow.models.UserResponse
import com.university.studentixflow.repository.CourseRepository
import com.university.studentixflow.repository.UserRepository
import com.university.studentixflow.repository.UserData
import com.university.studentixflow.repository.ParticipantInfo
import com.university.studentixflow.routes.RouteHelpers.requireAdmin
import com.university.studentixflow.routes.RouteHelpers.requireAdminWithId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class ParticipantResponse(
    val id: Int,
    val email: String,
    val fullName: String,
    val role: String
)

@Serializable
data class CourseParticipantsResponse(
    val courseId: Int,
    val teacher: ParticipantResponse,
    val students: List<ParticipantResponse>
)

// Helper extension function to convert UserData to UserResponse
private fun UserData.toUserResponse() = UserResponse(
    id = id,
    email = email,
    fullName = fullName,
    role = role,
    isActive = isActive
)

// Helper extension function to convert ParticipantInfo to ParticipantResponse
private fun ParticipantInfo.toParticipantResponse() = ParticipantResponse(
    id = id,
    email = email,
    fullName = fullName,
    role = role.name
)

fun Route.reportRoutes(userRepository: UserRepository, courseRepository: CourseRepository) {
    authenticate("auth-jwt") {
        // 7.1: Get all teachers
        get("/reports/teachers") {
            if (!call.requireAdmin()) return@get

            val teachers = userRepository.getAllTeachers()
            call.respond(HttpStatusCode.OK, teachers.map { it.toUserResponse() })
        }

        // 7.2: Get all courses
        get("/reports/courses") {
            if (!call.requireAdmin()) return@get

            val courses = courseRepository.getAllCourses()
            call.respond(HttpStatusCode.OK, courses)
        }

        // 7.3: Get all students
        get("/reports/students") {
            if (!call.requireAdmin()) return@get

            val students = userRepository.getAllStudents()
            call.respond(HttpStatusCode.OK, students.map { it.toUserResponse() })
        }

        get("/reports/teachers/{teacherId}/courses") {
            val teacherId = call.parameters["teacherId"]?.toIntOrNull()
            if (!call.requireAdminWithId(teacherId, "Invalid teacher ID")) return@get

            val courses = courseRepository.getCoursesByTeacher(teacherId!!)
            call.respond(HttpStatusCode.OK, courses)
        }

        // 7.5: Get courses by specific student
        get("/reports/students/{studentId}/courses") {
            val studentId = call.parameters["studentId"]?.toIntOrNull()
            if (!call.requireAdminWithId(studentId, "Invalid student ID")) return@get

            val courses = courseRepository.getCoursesByStudent(studentId!!)
            call.respond(HttpStatusCode.OK, courses)
        }

        // 7.6: Get all participants in a course
        get("/reports/courses/{courseId}/participants") {
            val courseId = call.parameters["courseId"]?.toIntOrNull()
            if (!call.requireAdminWithId(courseId, "Invalid course ID")) return@get

            val participants = courseRepository.getCourseParticipants(courseId!!)

            if (participants == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Course not found"))
                return@get
            }

            val response = CourseParticipantsResponse(
                courseId = courseId,
                teacher = participants.teacher.toParticipantResponse(),
                students = participants.students.map { it.toParticipantResponse() }
            )

            call.respond(HttpStatusCode.OK, response)
        }
    }
}
