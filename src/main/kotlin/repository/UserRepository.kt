package com.university.studentixflow.repository

import com.university.studentixflow.db.DatabaseFactory.dbQuery
import com.university.studentixflow.db.Users
import com.university.studentixflow.models.RegisterRequest
import com.university.studentixflow.utils.PasswordHasher
import org.jetbrains.exposed.sql.insert

class UserRepository {
    suspend fun registerUser(request: RegisterRequest) = dbQuery {

        val hashedPassword = PasswordHasher.hashPassword(request.password)

        Users.insert {
            it[email] = request.email
            it[password] = hashedPassword
            it[fullName] = request.fullName
            it[role] = request.role
            it[isActive] = true
        }
    }
}