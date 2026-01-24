package com.university.studentixflow.repository

import com.university.studentixflow.db.CourseSections
import com.university.studentixflow.db.Courses
import com.university.studentixflow.models.SectionRequest
import com.university.studentixflow.db.DatabaseFactory.dbQuery
import com.university.studentixflow.db.Materials
import com.university.studentixflow.db.Tasks
import com.university.studentixflow.models.MaterialRequest
import com.university.studentixflow.models.TaskRequest
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll

class CourseContentRepository {
    suspend fun createSection(courseId: Int, request: SectionRequest): Int = dbQuery {
        CourseSections.insertAndGetId {
            it[this.courseId] = courseId
            it[weekNumber] = request.weekNumber
            it[title] = request.title
            it[url] = request.url
            it[description] = request.description
            it[sortOrder] = request.sortOrder
        }.value
    }

    suspend fun createTask(sectionId: Int, request: TaskRequest): Int = dbQuery {
        Tasks.insertAndGetId {
            it[this.sectionId] = sectionId
            it[title] = request.title
            it[description] = request.description
            it[dueDate] = request.dueDate
        }.value
    }

    suspend fun createMaterial(sectionId: Int, request: MaterialRequest): Int = dbQuery {
        Materials.insertAndGetId {
            it[this.sectionId] = sectionId
            it[title] = request.title
            it[url] = request.url
            it[type] = request.type
            it[isVisible] = request.isVisible
        }.value
    }

    suspend fun isSectionOwner(sectionId: Int, userId: Int): Boolean = dbQuery {
        (CourseSections innerJoin Courses)
            .selectAll().where { (CourseSections.id eq sectionId) and (Courses.teacherId eq userId) }
            .count() > 0
    }
}