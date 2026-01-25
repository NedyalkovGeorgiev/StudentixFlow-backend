package com.university.studentixflow.routes

import com.auth0.jwt.JWT
import com.university.studentixflow.models.MaterialRequest
import com.university.studentixflow.models.SectionRequest
import com.university.studentixflow.models.TaskRequest
import com.university.studentixflow.models.TestRequest
import com.university.studentixflow.models.TestSubmissionRequest
import com.university.studentixflow.repository.CourseContentRepository
import com.university.studentixflow.repository.CourseRepository
import com.university.studentixflow.routes.RouteHelpers.getUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.response.respond
import java.lang.IllegalArgumentException


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

        post("/tests/{id}/submit") {
            val testId = call.parameters["id"]?.toIntOrNull()
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toIntOrNull()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (testId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid test ID or token"))
                return@post
            }

            val courseId = courseRepository.findCourseIdForTest(testId)
            if (courseId == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Test not found or is not part of any course"))
                return@post
            }

            val isEnrolled = courseRepository.isStudentEnrolled(courseId, userId)
            if (!isEnrolled) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "You are not enrolled in the course for this test"))
                return@post
            }

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

        // GET /tests/{id}/edit - Get test data for editing (with correct answers)
        get("/tests/{id}/edit") {
            val testId = call.parameters["id"]?.toIntOrNull()
            val userId = call.getUserId()
            val principal = call.principal<JWTPrincipal>()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (testId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid test ID or token"))
                return@get
            }

            val isOwner = courseContentRepository.isTestOwner(testId, userId)
            if (role != "ADMIN" && !isOwner) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                return@get
            }

            val test = courseContentRepository.getTestForEditing(testId)
            if (test == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Test not found"))
                return@get
            }

            call.respond(HttpStatusCode.OK, test)
        }

        // PUT /tests/{id} - Update an existing test
        put("/tests/{id}") {
            val testId = call.parameters["id"]?.toIntOrNull()
            val userId = call.getUserId()
            val principal = call.principal<JWTPrincipal>()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (testId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid test ID or token"))
                return@put
            }

            val isOwner = courseContentRepository.isTestOwner(testId, userId)
            if (role != "ADMIN" && !isOwner) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                return@put
            }

            try {
                val request = call.receive<TestRequest>()
                val updated = courseContentRepository.updateTest(testId, request)
                if (updated) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Test updated successfully"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Test not found"))
                }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid test data: ${e.message}"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to update test"))
            }
        }

        // DELETE /tests/{id} - Delete a test
        delete("/tests/{id}") {
            val testId = call.parameters["id"]?.toIntOrNull()
            val userId = call.getUserId()
            val principal = call.principal<JWTPrincipal>()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (testId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid test ID or token"))
                return@delete
            }

            val isOwner = courseContentRepository.isTestOwner(testId, userId)
            if (role != "ADMIN" && !isOwner) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                return@delete
            }

            val deleted = courseContentRepository.deleteTest(testId)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Test not found"))
            }
        }

        // ============================================
        // Task Endpoints
        // ============================================

        // GET /tasks/{taskId} - Get task by ID
        get("/tasks/{taskId}") {
            val taskId = call.parameters["taskId"]?.toIntOrNull()
            val userId = call.getUserId()
            val principal = call.principal<JWTPrincipal>()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (taskId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid task ID or token"))
                return@get
            }

            val isOwner = courseContentRepository.isTaskOwner(taskId, userId)
            if (role != "ADMIN" && !isOwner) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                return@get
            }

            val task = courseContentRepository.getTaskById(taskId)
            if (task == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Task not found"))
                return@get
            }

            call.respond(HttpStatusCode.OK, task)
        }

        // PUT /tasks/{taskId} - Update a task
        put("/tasks/{taskId}") {
            val taskId = call.parameters["taskId"]?.toIntOrNull()
            val userId = call.getUserId()
            val principal = call.principal<JWTPrincipal>()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (taskId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid task ID or token"))
                return@put
            }

            val isOwner = courseContentRepository.isTaskOwner(taskId, userId)
            if (role != "ADMIN" && !isOwner) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                return@put
            }

            try {
                val request = call.receive<TaskRequest>()
                val updated = courseContentRepository.updateTask(taskId, request)
                if (updated) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Task updated successfully"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Task not found"))
                }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid task data: ${e.message}"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to update task"))
            }
        }

        // DELETE /tasks/{taskId} - Delete a task
        delete("/tasks/{taskId}") {
            val taskId = call.parameters["taskId"]?.toIntOrNull()
            val userId = call.getUserId()
            val principal = call.principal<JWTPrincipal>()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (taskId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid task ID or token"))
                return@delete
            }

            val isOwner = courseContentRepository.isTaskOwner(taskId, userId)
            if (role != "ADMIN" && !isOwner) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                return@delete
            }

            val deleted = courseContentRepository.deleteTask(taskId)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Task not found"))
            }
        }

        // ============================================
        // Material Endpoints
        // ============================================

        // GET /materials/{materialId} - Get material by ID
        get("/materials/{materialId}") {
            val materialId = call.parameters["materialId"]?.toIntOrNull()
            val userId = call.getUserId()
            val principal = call.principal<JWTPrincipal>()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (materialId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid material ID or token"))
                return@get
            }

            val isOwner = courseContentRepository.isMaterialOwner(materialId, userId)
            if (role != "ADMIN" && !isOwner) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                return@get
            }

            val material = courseContentRepository.getMaterialById(materialId)
            if (material == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Material not found"))
                return@get
            }

            call.respond(HttpStatusCode.OK, material)
        }

        // PUT /materials/{materialId} - Update a material
        put("/materials/{materialId}") {
            val materialId = call.parameters["materialId"]?.toIntOrNull()
            val userId = call.getUserId()
            val principal = call.principal<JWTPrincipal>()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (materialId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid material ID or token"))
                return@put
            }

            val isOwner = courseContentRepository.isMaterialOwner(materialId, userId)
            if (role != "ADMIN" && !isOwner) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                return@put
            }

            try {
                val request = call.receive<MaterialRequest>()
                val updated = courseContentRepository.updateMaterial(materialId, request)
                if (updated) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Material updated successfully"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Material not found"))
                }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid material data: ${e.message}"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to update material"))
            }
        }

        // DELETE /materials/{materialId} - Delete a material
        delete("/materials/{materialId}") {
            val materialId = call.parameters["materialId"]?.toIntOrNull()
            val userId = call.getUserId()
            val principal = call.principal<JWTPrincipal>()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (materialId == null || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid material ID or token"))
                return@delete
            }

            val isOwner = courseContentRepository.isMaterialOwner(materialId, userId)
            if (role != "ADMIN" && !isOwner) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                return@delete
            }

            val deleted = courseContentRepository.deleteMaterial(materialId)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Material not found"))
            }
        }
    }
}