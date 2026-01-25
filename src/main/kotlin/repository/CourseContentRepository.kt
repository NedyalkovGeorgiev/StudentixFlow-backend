package com.university.studentixflow.repository

import com.university.studentixflow.db.CourseSections
import com.university.studentixflow.db.Courses
import com.university.studentixflow.db.DatabaseFactory.dbQuery
import com.university.studentixflow.db.Materials
import com.university.studentixflow.db.Tasks
import com.university.studentixflow.db.TestResults
import com.university.studentixflow.db.Tests
import com.university.studentixflow.models.MaterialRequest
import com.university.studentixflow.models.MaterialResponse
import com.university.studentixflow.models.Question
import com.university.studentixflow.models.SectionRequest
import com.university.studentixflow.models.SectionResponse
import com.university.studentixflow.models.SectionWithContentResponse
import com.university.studentixflow.models.TaskRequest
import com.university.studentixflow.models.TaskResponse
import com.university.studentixflow.models.TestRequest
import com.university.studentixflow.models.TestSubmissionRequest
import com.university.studentixflow.models.TestSummaryResponse
import kotlinx.serialization.json.Json
import com.university.studentixflow.models.TestEditResponse
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

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

    /**
     * Check if user owns the section via course teacherId
     * Explicit join: CourseSections -> Courses (via courseId)
     */
    suspend fun isSectionOwner(sectionId: Int, userId: Int): Boolean = dbQuery {
        CourseSections
            .join(Courses, JoinType.INNER, CourseSections.courseId, Courses.id)
            .select(CourseSections.id)
            .where { (CourseSections.id eq sectionId) and (Courses.teacherId eq userId) }
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

        val testsBySectionId = Tests.selectAll()
            .where { Tests.sectionId inList sectionIds }
            .map { it.toTestSummaryResponse() }
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
                materials = materialBySectionId[sectionData.id] ?: emptyList(),
                tests = testsBySectionId[sectionData.id] ?: emptyList()
            )
        }
    }

    private fun ResultRow.toTestSummaryResponse(): TestSummaryResponse = TestSummaryResponse(
        id = this[Tests.id].value,
        sectionId = this[Tests.sectionId].value,
        title = this[Tests.title],
        maxScore = this[Tests.maxScore]
    )

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
        val existingResult = TestResults.selectAll().where {
            (TestResults.studentId eq studentId) and (TestResults.testId eq testId)
        }.singleOrNull()

        if (existingResult != null) {
            throw IllegalStateException("You have already submitted this test.")
        }

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

    /**
     * Get test with full question data for editing (includes correctOptionIndex)
     */
    suspend fun getTestForEditing(testId: Int): TestEditResponse? = dbQuery {
        val testRow = Tests.selectAll()
            .where { Tests.id eq testId }
            .singleOrNull() ?: return@dbQuery null

        val questions = Json.decodeFromString<List<Question>>(testRow[Tests.contentJson])

        TestEditResponse(
            id = testRow[Tests.id].value,
            sectionId = testRow[Tests.sectionId].value,
            title = testRow[Tests.title],
            maxScore = testRow[Tests.maxScore],
            questions = questions
        )
    }

    /**
     * Update an existing test
     */
    suspend fun updateTest(testId: Int, request: TestRequest): Boolean = dbQuery {
        val updatedRows = Tests.update({ Tests.id eq testId }) {
            it[title] = request.title
            it[maxScore] = request.maxScore
            it[contentJson] = Json.encodeToString(request.questions)
        }
        updatedRows > 0
    }

    /**
     * Check if user owns the test via section -> course teacherId
     */
    suspend fun isTestOwner(testId: Int, userId: Int): Boolean = dbQuery {
        Tests
            .join(CourseSections, JoinType.INNER, Tests.sectionId, CourseSections.id)
            .join(Courses, JoinType.INNER, CourseSections.courseId, Courses.id)
            .select(Tests.id)
            .where { (Tests.id eq testId) and (Courses.teacherId eq userId) }
            .count() > 0
    }

    /**
     * Delete a test by ID
     */
    suspend fun deleteTest(testId: Int): Boolean = dbQuery {
        Tests.deleteWhere { Tests.id eq testId } > 0
    }

    // ============================================
    // Task Methods
    // ============================================

    /**
     * Get task by ID
     */
    suspend fun getTaskById(taskId: Int): TaskResponse? = dbQuery {
        Tasks.selectAll()
            .where { Tasks.id eq taskId }
            .singleOrNull()
            ?.toTaskResponse()
    }

    /**
     * Update an existing task
     */
    suspend fun updateTask(taskId: Int, request: TaskRequest): Boolean = dbQuery {
        val updatedRows = Tasks.update({ Tasks.id eq taskId }) {
            it[title] = request.title
            it[description] = request.description
            it[dueDate] = request.dueDate
        }
        updatedRows > 0
    }

    /**
     * Delete a task by ID
     */
    suspend fun deleteTask(taskId: Int): Boolean = dbQuery {
        Tasks.deleteWhere { Tasks.id eq taskId } > 0
    }

    /**
     * Check if user owns the task via section -> course teacherId
     */
    suspend fun isTaskOwner(taskId: Int, userId: Int): Boolean = dbQuery {
        Tasks
            .join(CourseSections, JoinType.INNER, Tasks.sectionId, CourseSections.id)
            .join(Courses, JoinType.INNER, CourseSections.courseId, Courses.id)
            .select(Tasks.id)
            .where { (Tasks.id eq taskId) and (Courses.teacherId eq userId) }
            .count() > 0
    }

    // ============================================
    // Material Methods
    // ============================================

    /**
     * Get material by ID
     */
    suspend fun getMaterialById(materialId: Int): MaterialResponse? = dbQuery {
        Materials.selectAll()
            .where { Materials.id eq materialId }
            .singleOrNull()
            ?.toMaterialResponse()
    }

    /**
     * Update an existing material
     */
    suspend fun updateMaterial(materialId: Int, request: MaterialRequest): Boolean = dbQuery {
        val updatedRows = Materials.update({ Materials.id eq materialId }) {
            it[title] = request.title
            it[url] = request.url
            it[type] = request.type
            it[isVisible] = request.isVisible
        }
        updatedRows > 0
    }

    /**
     * Delete a material by ID
     */
    suspend fun deleteMaterial(materialId: Int): Boolean = dbQuery {
        Materials.deleteWhere { Materials.id eq materialId } > 0
    }

    /**
     * Check if user owns the material via section -> course teacherId
     */
    suspend fun isMaterialOwner(materialId: Int, userId: Int): Boolean = dbQuery {
        Materials
            .join(CourseSections, JoinType.INNER, Materials.sectionId, CourseSections.id)
            .join(Courses, JoinType.INNER, CourseSections.courseId, Courses.id)
            .select(Materials.id)
            .where { (Materials.id eq materialId) and (Courses.teacherId eq userId) }
            .count() > 0
    }
}