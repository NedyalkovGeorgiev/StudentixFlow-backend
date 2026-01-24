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