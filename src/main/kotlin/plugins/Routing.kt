package com.university.studentixflow.plugins

import com.university.studentixflow.repository.CourseRepository
import com.university.studentixflow.repository.UserRepository
import com.university.studentixflow.routes.authenticationRoutes
import com.university.studentixflow.routes.courseRoutes
import com.university.studentixflow.routes.userRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.resources.*

fun Application.configureRouting() {
    install(StatusPages) {
        exception<IllegalStateException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to cause.message))
        }
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "An unexpected error occurred"))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid data format: ${cause.message}"))
        }
    }

    install(Resources)

    val userRepository = UserRepository()
    val courseRepository = CourseRepository()

    routing {
        get("/") {
            call.respondText("StudentixFlow API is running!")
        }

        authenticationRoutes(userRepository)
        courseRoutes(courseRepository)
        userRoutes(userRepository)
    }
}