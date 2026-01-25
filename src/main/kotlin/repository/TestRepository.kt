package com.university.studentixflow.repository

import com.university.studentixflow.db.Courses
import kotlinx.serialization.Serializable
import com.university.studentixflow.db.DatabaseFactory.dbQuery
import com.university.studentixflow.db.TestResults
import com.university.studentixflow.db.Tests
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import com.university.studentixflow.db.CourseSections

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
    suspend fun getResultsForStudent(studentId: Int): List<TestResultResponse> = dbQuery {
        (TestResults innerJoin Tests innerJoin CourseSections innerJoin Courses)
            .selectAll()
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
}