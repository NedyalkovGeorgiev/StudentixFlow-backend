package com.university.studentixflow.repository

import com.university.studentixflow.db.Courses
import com.university.studentixflow.models.CourseRequest
import com.university.studentixflow.db.DatabaseFactory.dbQuery
import com.university.studentixflow.db.Enrollments
import com.university.studentixflow.db.Users
import com.university.studentixflow.models.CourseResponse
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

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

    suspend fun getAllCourses(): List<CourseResponse> = dbQuery {
        (Courses innerJoin Users).selectAll().map {
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

    suspend fun getCoursesByTeacher(teacherId: Int): List<CourseResponse> = dbQuery {
        (Courses innerJoin Users)
            .selectAll().where { Courses.teacherId eq teacherId }
            .map {
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

    suspend fun isCourseOwner(courseId: Int, userId: Int): Boolean = dbQuery {
        Courses.selectAll().where { (Courses.id eq courseId) and (Courses.teacherId eq userId) }
            .count() > 0
    }

    suspend fun deleteCourse(courseId: Int, teacherId: Int): Boolean = dbQuery {
        val rowsDeleted = Courses.deleteWhere {
            (Courses.id eq courseId) and (Courses.teacherId eq teacherId)
        }
        rowsDeleted > 0
    }

    suspend fun deleteCourseByAdmin(courseId: Int): Boolean = dbQuery {
        Courses.deleteWhere { Courses.id eq courseId } > 0
    }

    suspend fun getCoursesByStudent(studentId: Int): List<CourseResponse> = dbQuery {
        (Courses innerJoin Enrollments innerJoin Users)
            .selectAll().where { Enrollments.studentId eq studentId }
            .map {
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