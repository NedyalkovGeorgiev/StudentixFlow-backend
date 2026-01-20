package com.university.studentixflow.db

import org.jetbrains.exposed.dao.id.IntIdTable

object Users : IntIdTable("users") {
    val email = varchar("email", 100).uniqueIndex()
    val password = varchar("password", 255)
    val fullName = varchar("full_name", 150)
    val role = varchar("role", 20)
}

object Courses : IntIdTable("courses") {
    val title = varchar("title", 200)
    val description = text("description")
    val teacherId = reference("teacher_id", Users)
}

object Enrollments : IntIdTable("enrollments") {
    val studentId = reference("student_id", Users)
    val courseId = reference("course_id", Courses)
}

object Tests : IntIdTable("tests") {
    val courseId = reference("course_id", Courses)
    val title = varchar("title", 255)
    val contentJson = text("content_json")
}