package com.university.studentixflow.repository

import com.university.studentixflow.db.CourseSections
import com.university.studentixflow.db.Courses
import com.university.studentixflow.models.SectionRequest
import com.university.studentixflow.db.DatabaseFactory.dbQuery
import com.university.studentixflow.db.Materials
import com.university.studentixflow.db.Tasks
import com.university.studentixflow.db.TestResults
import com.university.studentixflow.models.MaterialRequest
import com.university.studentixflow.models.SectionResponse
import com.university.studentixflow.models.SectionWithContentResponse
import com.university.studentixflow.models.TaskRequest
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import com.university.studentixflow.models.MaterialResponse
import com.university.studentixflow.models.TaskResponse
import com.university.studentixflow.models.TestRequest
import kotlinx.serialization.json.Json
import com.university.studentixflow.db.Tests
import com.university.studentixflow.models.Question
import com.university.studentixflow.models.TestSubmissionRequest
import org.jetbrains.exposed.sql.insert

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

    suspend fun getCourseContent(courseId: Int): List<SectionWithContentResponse> = dbQuery {
        val sections = CourseSections.selectAll()
            .where { CourseSections.courseId eq courseId }
            .map { it.toSectionResponse() }

        if (sections.isEmpty()) {
            return@dbQuery emptyList()
        }

        val sectionIds = sections.map { it.id }

        val taskBySectionId = Tasks.selectAll()
            .where { Tasks.sectionId inList sectionIds }
            .map { it.toTaskResponse() }
            .groupBy { it.sectionId }

        val materialBySectionId = Materials.selectAll()
            .where { Materials.sectionId inList sectionIds }
            .map { it.toMaterialResponse() }
            .groupBy { it.sectionId }

        sections.map { sectionData ->
            SectionWithContentResponse(
                id = sectionData.id,
                courseId = sectionData.courseId,
                weekNumber = sectionData.weekNumber,
                title = sectionData.title,
                description = sectionData.description,
                url = sectionData.url,
                sortOrder = sectionData.sortOrder,
                tasks = taskBySectionId[sectionData.id] ?: emptyList(),
                materials = materialBySectionId[sectionData.id] ?: emptyList()
            )
        }
    }

    private fun ResultRow.toSectionResponse(): SectionResponse = SectionResponse(
        id = this[CourseSections.id].value,
        courseId = this[CourseSections.courseId].value,
        weekNumber = this[CourseSections.weekNumber],
        title = this[CourseSections.title],
        description = this[CourseSections.description],
        url = this[CourseSections.url],
        sortOrder = this[CourseSections.sortOrder]
    )

    private fun ResultRow.toTaskResponse(): TaskResponse = TaskResponse(
        id = this[Tasks.id].value,
        sectionId = this[Tasks.sectionId].value,
        title = this[Tasks.title],
        description = this[Tasks.description],
        dueDate = this[Tasks.dueDate]
    )

    private fun ResultRow.toMaterialResponse(): MaterialResponse = MaterialResponse(
        id = this[Materials.id].value,
        sectionId = this[Materials.sectionId].value,
        title = this[Materials.title],
        url = this[Materials.url],
        type = this[Materials.type],
        isVisible = this[Materials.isVisible]
    )

    suspend fun createTest(sectionId: Int, request: TestRequest): Int = dbQuery {
        Tests.insertAndGetId {
            it[this.sectionId] = sectionId
            it[title] = request.title
            it[maxScore] = request.maxScore
            it[contentJson] = Json.encodeToString(request.questions)
        }.value
    }

    suspend fun submitTest(
        testId: Int, studentId: Int, submissionRequest: TestSubmissionRequest
    ): Int = dbQuery {
        val testRow = Tests.selectAll().where { Tests.id eq testId }.singleOrNull()
            ?: throw IllegalArgumentException("Test not found")

        val questions = Json.decodeFromString<List<Question>>(testRow[Tests.contentJson])
        val maxScore = testRow[Tests.maxScore]

        var correctAnswerCount = 0
        val studentAnswers = submissionRequest.answers.associateBy { it.questionIndex }

        for ((index, question) in questions.withIndex()) {
            val studentAnswer = studentAnswers[index]
            if (studentAnswer != null && studentAnswer.chosenOptionIndex == question.correctOptionIndex) {
                correctAnswerCount++
            }
        }

        val score = if(questions.isNotEmpty()) {
            (correctAnswerCount.toDouble() / questions.size * maxScore).toInt()
        } else {
            0
        }

        TestResults.insert {
            it[this.testId] = testId
            it[this.studentId] = studentId
            it[this.score] = score
            it[attemptedAt] = System.currentTimeMillis()
        }

        score
    }
}