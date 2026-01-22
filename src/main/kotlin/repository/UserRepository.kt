package com.university.studentixflow.repository

import com.university.studentixflow.db.DatabaseFactory.dbQuery
import com.university.studentixflow.db.Users
import com.university.studentixflow.models.RegisterRequest
import org.jetbrains.exposed.sql.insert

class UserRepository {
    suspend fun registerUser(request: RegisterRequest) = dbQuery {
        Users.insert {
            it[email] = request.email
            it[password] = request.password
            it[fullName] = request.fullName
            it[role] = request.role
        }
    }
}