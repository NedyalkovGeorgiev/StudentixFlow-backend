package com.university.studentixflow.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond

/**
 * Helper object containing common route utilities and validation functions
 */
object RouteHelpers {
    /**
     * @return true if admin, false otherwise
     */
    suspend fun ApplicationCall.requireAdmin(): Boolean {
        val principal = principal<JWTPrincipal>()
        val role = principal?.payload?.getClaim("role")?.asString()

        if (role != "ADMIN") {
            respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied: Admin role required"))
            return false
        }
        return true
    }

    /**
     * @return true if admin or teacher, false otherwise
     */
    suspend fun ApplicationCall.requireAdminOrTeacher(): Boolean {
        val principal = principal<JWTPrincipal>()
        val role = principal?.payload?.getClaim("role")?.asString()

        if (role != "ADMIN" && role != "TEACHER") {
            respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied: Admin or Teacher role required"))
            return false
        }
        return true
    }

    /**
     * @return user ID or null if invalid
     */
    fun ApplicationCall.getUserId(): Int? {
        val principal = principal<JWTPrincipal>()
        return principal?.payload?.subject?.toIntOrNull()
    }

    /**
     * @return role string or null if invalid
     */
//    fun ApplicationCall.getUserRole(): String? {
//        val principal = principal<JWTPrincipal>()
//        return principal?.payload?.getClaim("role")?.asString()
//    }

    /**
     * Validates and responds with error if ID is null
     * @param id the ID to validate
     * @param errorMessage custom error message
     * @return true if valid, false if invalid (response already sent)
     */
    suspend fun ApplicationCall.validateId(id: Int?, errorMessage: String = "Invalid ID"): Boolean {
        if (id == null) {
            respond(HttpStatusCode.BadRequest, mapOf("error" to errorMessage))
            return false
        }
        return true
    }

    /**
     * Combined validation for admin endpoints with ID parameter
     * @param id the ID to validate
     * @param idErrorMessage custom error message for invalid ID
     * @return true if all validations pass
     */
    suspend fun ApplicationCall.requireAdminWithId(id: Int?, idErrorMessage: String = "Invalid ID"): Boolean {
        return requireAdmin() && validateId(id, idErrorMessage)
    }
}
