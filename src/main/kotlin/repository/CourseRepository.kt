package com.university.studentixflow.repository

import com.university.studentixflow.db.Courses
import com.university.studentixflow.models.CourseRequest
import com.university.studentixflow.db.DatabaseFactory.dbQuery
import com.university.studentixflow.db.Users
import com.university.studentixflow.models.CourseResponse
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll

class CourseRepository {
    suspend fun createCourse(request: CourseRequest, teacherId: Int): Int = dbQuery {
        Courses.insertAndGetId {
            it[title] = request.title
            it[description] = request.description
            it[this.teacherId] = teacherId
            it[startDate] = request.startDate
            it[durationWeeks] = request.durationWeeks
            it[isActive] = true
        }.value
    }

    suspend fun getCourseById(id: Int): CourseResponse? = dbQuery {
        val result = (Courses innerJoin Users)
            .selectAll().where { Courses.id eq id }
            .singleOrNull()

        result?.let {
            CourseResponse(
                id = it[Courses.id].value,
                title = it[Courses.title],
                description = it[Courses.description],
                teacherId = it[Courses.teacherId].value,
                teacherName = it[Users.fullName],
                isActive = it[Courses.isActive],
                startDate = it[Courses.startDate],
                durationWeeks = it[Courses.durationWeeks]
            )
        }
    }
}