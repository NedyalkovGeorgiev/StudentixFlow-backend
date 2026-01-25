package com.university.studentixflow.models

import kotlinx.serialization.Serializable

@Serializable
data class AnswerOption(
    val text: String
)

@Serializable
data class Question(
    val text: String,
    val options: List<AnswerOption>,
    val correctOptionIndex: Int
)

@Serializable
data class TestRequest(
    val title: String,
    val maxScore: Int,
    val questions: List<Question>
)

@Serializable
data class StudentAnswer(
    val questionIndex: Int,
    val chosenOptionIndex: Int
)

@Serializable
data class TestSubmissionRequest(
    val answers: List<StudentAnswer>
)

// For listing tests in course content (no questions)
@Serializable
data class TestSummaryResponse(
    val id: Int,
    val sectionId: Int,
    val title: String,
    val maxScore: Int
)

// For students taking tests (questions WITHOUT correct answers)
@Serializable
data class QuestionForStudent(
    val text: String,
    val options: List<AnswerOption>
    // Note: no correctOptionIndex - hidden from students
)

@Serializable
data class TestForTakingResponse(
    val id: Int,
    val title: String,
    val maxScore: Int,
    val questions: List<QuestionForStudent>
)

// For teachers/admins editing tests (WITH correct answers)
@Serializable
data class TestEditResponse(
    val id: Int,
    val sectionId: Int,
    val title: String,
    val maxScore: Int,
    val questions: List<Question>  // Includes correctOptionIndex
)
