package com.university.studentixflow.repository

import com.university.studentixflow.db.Courses
import com.university.studentixflow.models.CourseRequest
import com.university.studentixflow.db.DatabaseFactory.dbQuery
import com.university.studentixflow.db.Enrollments
import com.university.studentixflow.db.Users
import com.university.studentixflow.models.CourseResponse
import com.university.studentixflow.models.UserRole
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class CourseRepository {
    // Helper function to map ResultRow to CourseResponse
    private fun ResultRow.toCourseResponse() = CourseResponse(
        id = this[Courses.id].value,
        title = this[Courses.title],
        description = this[Courses.description],
        teacherId = this[Courses.teacherId].value,
        teacherName = this[Users.fullName],
        isActive = this[Courses.isActive],
        startDate = this[Courses.startDate],
        durationWeeks = this[Courses.durationWeeks]
    )

    // Helper function to map ResultRow to ParticipantInfo
    private fun ResultRow.toParticipantInfo() = ParticipantInfo(
        id = this[Users.id].value,
        email = this[Users.email],
        fullName = this[Users.fullName],
        role = this[Users.role]
    )

    suspend fun createCourse(request: CourseRequest, userId: Int, teacherId: Int?): Int = dbQuery {
        Courses.insertAndGetId {
            it[title] = request.title
            it[description] = request.description
            it[this.teacherId] =  if (teacherId == null) userId else teacherId
            it[this.createdBy] = userId
            it[startDate] = request.startDate
            it[durationWeeks] = request.durationWeeks
            it[isActive] = true
        }.value
    }

    suspend fun getCourseById(id: Int): CourseResponse? = dbQuery {
        (Courses innerJoin Users)
            .selectAll().where { Courses.id eq id }
            .singleOrNull()
            ?.toCourseResponse()
    }

    suspend fun getAllCourses(): List<CourseResponse> = dbQuery {
        (Courses innerJoin Users).selectAll().map { it.toCourseResponse() }
    }

    suspend fun getCoursesByTeacher(teacherId: Int): List<CourseResponse> = dbQuery {
        (Courses innerJoin Users)
            .selectAll().where { Courses.teacherId eq teacherId }
            .map { it.toCourseResponse() }
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
            .map { it.toCourseResponse() }
    }

    suspend fun getCourseParticipants(courseId: Int): CourseParticipants? = dbQuery {
        val course = Courses.selectAll().where { Courses.id eq courseId }.singleOrNull()
            ?: return@dbQuery null

        val teacherId = course[Courses.teacherId].value
        val teacher = Users.selectAll().where { Users.id eq teacherId }.singleOrNull()
            ?: return@dbQuery null

        val students = (Enrollments innerJoin Users)
            .selectAll().where { Enrollments.courseId eq courseId }
            .map { it.toParticipantInfo() }

        CourseParticipants(
            teacher = teacher.toParticipantInfo(),
            students = students
        )
    }

    suspend fun isStudentEnrolled(courseId: Int, studentId: Int): Boolean = dbQuery {
        Enrollments.selectAll().where { (Enrollments.courseId eq courseId) and (Enrollments.studentId eq studentId) }
            .count() > 0
    }
}

data class ParticipantInfo(
    val id: Int,
    val email: String,
    val fullName: String,
    val role: UserRole
)

data class CourseParticipants(
    val teacher: ParticipantInfo,
    val students: List<ParticipantInfo>
)