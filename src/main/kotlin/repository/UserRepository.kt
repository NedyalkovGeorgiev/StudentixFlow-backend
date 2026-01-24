package com.university.studentixflow.repository

import com.university.studentixflow.db.DatabaseFactory.dbQuery
import com.university.studentixflow.db.Users
import com.university.studentixflow.models.AdminUserUpdateRequest
import com.university.studentixflow.models.RegisterRequest
import com.university.studentixflow.models.UserRole
import com.university.studentixflow.utils.PasswordHasher
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.and

data class UserData(
    val id: Int,
    val email: String,
    val role: UserRole,
    val fullName: String,
    val isActive: Boolean,
    val hashedPassword: String
)

class UserRepository {
    suspend fun registerUser(request: RegisterRequest) = dbQuery {

        val hashedPassword = PasswordHasher.hashPassword(request.password)

        Users.insert {
            it[email] = request.email
            it[password] = hashedPassword
            it[fullName] = request.fullName
            it[role] = request.role
        }
    }

    suspend fun findUserForLogin(email: String): UserData? = dbQuery {
        val result = Users.selectAll().where { Users.email eq email }.singleOrNull()

        result?.let {
            UserData(
                id = it[Users.id].value,
                email = it[Users.email],
                role = it[Users.role],
                fullName = it[Users.fullName],
                isActive = it[Users.isActive],
                hashedPassword = it[Users.password]
            )
        }
    }

    fun verifyPassword(plainPassword: String, hashedPassword: String): Boolean {
        return PasswordHasher.verifyPassword(plainPassword, hashedPassword)
    }

    suspend fun getAllUsers(): List<UserData> = dbQuery {
        Users.selectAll().map { mapUserRowToUserData(it) }
    }

    suspend fun deactivateUser(userId: Int): Boolean = dbQuery {
        Users.update({ Users.id eq userId }) {
            it[isActive] = false
        } > 0
    }

    private fun mapUserRowToUserData(row: ResultRow): UserData {
        return UserData(
            id = row[Users.id].value,
            email = row[Users.email],
            role = row[Users.role],
            fullName = row[Users.fullName],
            isActive = row[Users.isActive],
            hashedPassword = row[Users.password]
        )
    }

    suspend fun activateUser(userId: Int): Boolean = dbQuery {
        Users.update({ Users.id eq userId }) {
            it[isActive] = true
        } > 0
    }

    suspend fun updateUser(userId: Int, request: AdminUserUpdateRequest): Boolean = dbQuery {
        request.email?.let { newEmail ->
            val existingUser = Users.selectAll().where { (Users.email eq newEmail) and (Users.id neq userId)}
                .singleOrNull()

            if (existingUser != null) {
                throw IllegalArgumentException("Email address already taken by another user.")
            }
        }

        Users.update({ Users.id eq userId }) {
            request.fullName?.let { fullName -> it[Users.fullName] = fullName }
            request.email?.let { email -> it[Users.email] = email }
            request.role?.let { role -> it[Users.role] = role }
            request.isActive?.let { isActive -> it[Users.isActive] = isActive }
        } > 0
    }

    suspend fun getUserIsActiveStatus(userId: Int): Boolean? = dbQuery {
        Users.select(Users.isActive).where { Users.id eq userId }.singleOrNull()?.get(Users.isActive)
    }
}