package com.university.studentixflow.repository

import com.university.studentixflow.db.CourseSections
import com.university.studentixflow.db.Courses
import com.university.studentixflow.db.DatabaseFactory.dbQuery
import com.university.studentixflow.db.Enrollments
import com.university.studentixflow.db.Tests
import com.university.studentixflow.db.Users
import com.university.studentixflow.models.CourseRequest
import com.university.studentixflow.models.CourseResponse
import com.university.studentixflow.models.UserRole
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class CourseRepository {

    // Create aliases for Users table when joining multiple times
    private val TeacherAlias = Users.alias("teacher")
    private val CreatorAlias = Users.alias("creator")

    // Helper function to map ResultRow to CourseResponse using TeacherAlias
    private fun ResultRow.toCourseResponse() = CourseResponse(
        id = this[Courses.id].value,
        title = this[Courses.title],
        description = this[Courses.description],
        teacherId = this[Courses.teacherId].value,
        teacherName = this[TeacherAlias[Users.fullName]],
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

    // Overload for aliased table
    private fun ResultRow.toParticipantInfo(alias: Alias<Users>) = ParticipantInfo(
        id = this[alias[Users.id]].value,
        email = this[alias[Users.email]],
        fullName = this[alias[Users.fullName]],
        role = this[alias[Users.role]]
    )

    suspend fun createCourse(request: CourseRequest, userId: Int, teacherId: Int?): Int = dbQuery {
        Courses.insertAndGetId {
            it[title] = request.title
            it[description] = request.description
            it[this.teacherId] = teacherId ?: userId
            it[createdBy] = userId
            it[startDate] = request.startDate
            it[durationWeeks] = request.durationWeeks
            it[isActive] = true
        }.value
    }

    /**
     * Get course by ID with explicit join on teacherId -> Users
     */
    suspend fun getCourseById(id: Int): CourseResponse? = dbQuery {
        Courses
            .join(TeacherAlias, JoinType.INNER, Courses.teacherId, TeacherAlias[Users.id])
            .select(
                Courses.id,
                Courses.title,
                Courses.description,
                Courses.teacherId,
                Courses.isActive,
                Courses.startDate,
                Courses.durationWeeks,
                TeacherAlias[Users.fullName]
            )
            .where { Courses.id eq id }
            .singleOrNull()
            ?.toCourseResponse()
    }

    /**
     * Get all courses with teacher info
     */
    suspend fun getAllCourses(): List<CourseResponse> = dbQuery {
        Courses
            .join(TeacherAlias, JoinType.INNER, Courses.teacherId, TeacherAlias[Users.id])
            .select(
                Courses.id,
                Courses.title,
                Courses.description,
                Courses.teacherId,
                Courses.isActive,
                Courses.startDate,
                Courses.durationWeeks,
                TeacherAlias[Users.fullName]
            )
            .map { it.toCourseResponse() }
    }

    /**
     * Get courses by teacher with explicit join condition
     */
    suspend fun getCoursesByTeacher(teacherId: Int): List<CourseResponse> = dbQuery {
        Courses
            .join(TeacherAlias, JoinType.INNER, Courses.teacherId, TeacherAlias[Users.id])
            .select(
                Courses.id,
                Courses.title,
                Courses.description,
                Courses.teacherId,
                Courses.isActive,
                Courses.startDate,
                Courses.durationWeeks,
                TeacherAlias[Users.fullName]
            )
            .where { Courses.teacherId eq teacherId }
            .map { it.toCourseResponse() }
    }

    /**
     * Check course ownership - no join needed, just filter on indexed FK
     */
    suspend fun isCourseOwner(courseId: Int, userId: Int): Boolean = dbQuery {
        Courses
            .select(Courses.id)
            .where { (Courses.id eq courseId) and (Courses.teacherId eq userId) }
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

    /**
     * Get courses by student - properly chained joins:
     * Enrollments -> Courses (via courseId)
     * Courses -> Users/TeacherAlias (via teacherId)
     */
    suspend fun getCoursesByStudent(studentId: Int): List<CourseResponse> = dbQuery {
        Enrollments
            .join(Courses, JoinType.INNER, Enrollments.courseId, Courses.id)
            .join(TeacherAlias, JoinType.INNER, Courses.teacherId, TeacherAlias[Users.id])
            .select(
                Courses.id,
                Courses.title,
                Courses.description,
                Courses.teacherId,
                Courses.isActive,
                Courses.startDate,
                Courses.durationWeeks,
                TeacherAlias[Users.fullName]
            )
            .where { Enrollments.studentId eq studentId }
            .map { it.toCourseResponse() }
    }

    /**
     * Get course participants with separate queries (cleaner than complex multi-join)
     * Uses indexed lookups on primary keys
     */
    suspend fun getCourseParticipants(courseId: Int): CourseParticipants? = dbQuery {
        // Get course and teacher in one query
        val courseWithTeacher = Courses
            .join(TeacherAlias, JoinType.INNER, Courses.teacherId, TeacherAlias[Users.id])
            .select(
                Courses.id,
                TeacherAlias[Users.id],
                TeacherAlias[Users.email],
                TeacherAlias[Users.fullName],
                TeacherAlias[Users.role]
            )
            .where { Courses.id eq courseId }
            .singleOrNull() ?: return@dbQuery null

        val teacher = ParticipantInfo(
            id = courseWithTeacher[TeacherAlias[Users.id]].value,
            email = courseWithTeacher[TeacherAlias[Users.email]],
            fullName = courseWithTeacher[TeacherAlias[Users.fullName]],
            role = courseWithTeacher[TeacherAlias[Users.role]]
        )

        // Get enrolled students with explicit join on studentId
        val students = Enrollments
            .join(Users, JoinType.INNER, Enrollments.studentId, Users.id)
            .select(
                Users.id,
                Users.email,
                Users.fullName,
                Users.role
            )
            .where { Enrollments.courseId eq courseId }
            .map { it.toParticipantInfo() }

        CourseParticipants(
            teacher = teacher,
            students = students
        )
    }

    /**
     * Enroll student - uses unique index (studentId, courseId) for duplicate check
     */
    suspend fun enrollStudent(courseId: Int, studentId: Int) = dbQuery {
        // Verify course exists and is active (uses primary key index)
        val courseExists = Courses
            .select(Courses.id)
            .where { (Courses.id eq courseId) and (Courses.isActive eq true) }
            .count() > 0

        if (!courseExists) {
            throw IllegalArgumentException("Course not found or is not active")
        }

        // Check existing enrollment using unique index (studentId, courseId)
        val alreadyEnrolled = Enrollments
            .select(Enrollments.id)
            .where { (Enrollments.studentId eq studentId) and (Enrollments.courseId eq courseId) }
            .count() > 0

        if (alreadyEnrolled) {
            throw IllegalStateException("You are already enrolled in this course")
        }

        Enrollments.insert {
            it[this.studentId] = studentId
            it[this.courseId] = courseId
            it[enrolledAt] = System.currentTimeMillis()
        }
    }

    /**
     * Check enrollment - leverages unique index (studentId, courseId)
     */
    suspend fun isStudentEnrolled(courseId: Int, studentId: Int): Boolean = dbQuery {
        Enrollments
            .select(Enrollments.id)
            .where { (Enrollments.courseId eq courseId) and (Enrollments.studentId eq studentId) }
            .count() > 0
    }

    /**
     * Find course ID for a test - explicit join chain through FKs
     */
    suspend fun findCourseIdForTest(testId: Int): Int? = dbQuery {
        Tests
            .join(CourseSections, JoinType.INNER, Tests.sectionId, CourseSections.id)
            .join(Courses, JoinType.INNER, CourseSections.courseId, Courses.id)
            .select(Courses.id)
            .where { Tests.id eq testId }
            .singleOrNull()
            ?.get(Courses.id)
            ?.value
    }

    /**
     * Get course with both teacher AND creator information
     * Uses two aliases to join Users table twice
     */
    suspend fun getCourseWithCreatorInfo(id: Int): CourseWithCreatorResponse? = dbQuery {
        Courses
            .join(TeacherAlias, JoinType.INNER, Courses.teacherId, TeacherAlias[Users.id])
            .join(CreatorAlias, JoinType.INNER, Courses.createdBy, CreatorAlias[Users.id])
            .select(
                Courses.id,
                Courses.title,
                Courses.description,
                Courses.teacherId,
                Courses.createdBy,
                Courses.isActive,
                Courses.startDate,
                Courses.durationWeeks,
                TeacherAlias[Users.fullName],
                CreatorAlias[Users.fullName]
            )
            .where { Courses.id eq id }
            .singleOrNull()
            ?.let { row ->
                CourseWithCreatorResponse(
                    id = row[Courses.id].value,
                    title = row[Courses.title],
                    description = row[Courses.description],
                    teacherId = row[Courses.teacherId].value,
                    teacherName = row[TeacherAlias[Users.fullName]],
                    createdById = row[Courses.createdBy].value,
                    createdByName = row[CreatorAlias[Users.fullName]],
                    isActive = row[Courses.isActive],
                    startDate = row[Courses.startDate],
                    durationWeeks = row[Courses.durationWeeks]
                )
            }
    }

    /**
     * Remove a student from a course (unenroll)
     */
    suspend fun unenrollStudent(courseId: Int, studentId: Int): Boolean = dbQuery {
        val deletedRows = Enrollments.deleteWhere {
            (Enrollments.courseId eq courseId) and (Enrollments.studentId eq studentId)
        }
        deletedRows > 0
    }

    /**
     * Move a student from one course to another
     * This removes them from the source course and enrolls them in the target course
     */
    suspend fun moveStudent(sourceCourseId: Int, targetCourseId: Int, studentId: Int): Unit = dbQuery {
        // Remove from source course
        Enrollments.deleteWhere {
            (Enrollments.courseId eq sourceCourseId) and (Enrollments.studentId eq studentId)
        }

        // Check if already enrolled in target course
        val alreadyEnrolled = Enrollments
            .select(Enrollments.id)
            .where { (Enrollments.studentId eq studentId) and (Enrollments.courseId eq targetCourseId) }
            .count() > 0

        if (!alreadyEnrolled) {
            // Enroll in target course
            Enrollments.insert {
                it[this.studentId] = studentId
                it[this.courseId] = targetCourseId
                it[enrolledAt] = System.currentTimeMillis()
            }
        }
    }
}

// Extended response class when you need creator info too
data class CourseWithCreatorResponse(
    val id: Int,
    val title: String,
    val description: String,
    val teacherId: Int,
    val teacherName: String,
    val createdById: Int,
    val createdByName: String,
    val isActive: Boolean,
    val startDate: Long,
    val durationWeeks: Int
)

@Serializable
data class ParticipantInfo(
    val id: Int,
    val email: String,
    val fullName: String,
    val role: UserRole
)

@Serializable
data class CourseParticipants(
    val teacher: ParticipantInfo,
    val students: List<ParticipantInfo>
)
