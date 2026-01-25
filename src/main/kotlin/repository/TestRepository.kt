package com.university.studentixflow.repository

import com.university.studentixflow.db.CourseSections
import com.university.studentixflow.db.Courses
import com.university.studentixflow.db.DatabaseFactory.dbQuery
import com.university.studentixflow.db.TestResults
import com.university.studentixflow.db.Tests
import com.university.studentixflow.models.Question
import com.university.studentixflow.models.QuestionForStudent
import com.university.studentixflow.models.TestForTakingResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll

@Serializable
data class TestResultResponse(
    val testId: Int,
    val testTitle: String,
    val courseId: Int,
    val courseTitle: String,
    val score: Int,
    val maxScore: Int,
    val attemptedAt: Long
)

class TestRepository {
    /**
     * Get test results for a student with explicit join chain:
     * TestResults -> Tests (via testId)
     * Tests -> CourseSections (via sectionId)
     * CourseSections -> Courses (via courseId)
     */
    suspend fun getResultsForStudent(studentId: Int): List<TestResultResponse> = dbQuery {
        TestResults
            .join(Tests, JoinType.INNER, TestResults.testId, Tests.id)
            .join(CourseSections, JoinType.INNER, Tests.sectionId, CourseSections.id)
            .join(Courses, JoinType.INNER, CourseSections.courseId, Courses.id)
            .select(
                TestResults.testId,
                TestResults.score,
                TestResults.attemptedAt,
                Tests.title,
                Tests.maxScore,
                Courses.id,
                Courses.title
            )
            .where { TestResults.studentId eq studentId }
            .map { it.toTestResultResponse() }
    }

    private fun ResultRow.toTestResultResponse(): TestResultResponse = TestResultResponse(
        testId = this[TestResults.testId].value,
        testTitle = this[Tests.title],
        courseId = this[Courses.id].value,
        courseTitle = this[Courses.title],
        score = this[TestResults.score],
        maxScore = this[Tests.maxScore],
        attemptedAt = this[TestResults.attemptedAt]
    )

    /**
     * Get test for student taking - questions WITHOUT correct answers
     */
    suspend fun getTestForTaking(testId: Int): TestForTakingResponse? = dbQuery {
        val testRow = Tests.selectAll()
            .where { Tests.id eq testId }
            .singleOrNull() ?: return@dbQuery null

        val questions = Json.decodeFromString<List<Question>>(testRow[Tests.contentJson])

        // Strip correct answers from questions
        val questionsForStudent = questions.map { q ->
            QuestionForStudent(
                text = q.text,
                options = q.options
            )
        }

        TestForTakingResponse(
            id = testRow[Tests.id].value,
            title = testRow[Tests.title],
            maxScore = testRow[Tests.maxScore],
            questions = questionsForStudent
        )
    }

    /**
     * Get the course ID for a specific test
     */
    suspend fun getCourseIdForTest(testId: Int): Int? = dbQuery {
        Tests
            .join(CourseSections, JoinType.INNER, Tests.sectionId, CourseSections.id)
            .select(CourseSections.courseId)
            .where { Tests.id eq testId }
            .singleOrNull()?.get(CourseSections.courseId)?.value
    }
}