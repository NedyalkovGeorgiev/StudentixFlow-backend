package com.university.studentixflow.db

import com.university.studentixflow.models.MaterialType
import com.university.studentixflow.models.UserRole
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object Users : IntIdTable("users") {
    val email = varchar("email", 100).uniqueIndex()
    val password = varchar("password", 255)
    val fullName = varchar("full_name", 150)
    val role = enumerationByName("role", 20, UserRole::class)
    val isActive = bool("is_active").default(false)
}

object Courses : IntIdTable("courses") {
    val title = varchar("title", 200)
    val description = text("description")
    val teacherId = reference("teacher_id", Users, onDelete = ReferenceOption.RESTRICT)
    val createdBy = reference("created_by", Users, onDelete = ReferenceOption.RESTRICT)
    val isActive = bool("is_active").default(true)
    val startDate = long("start_date")
    val durationWeeks = integer("duration_weeks")
}

object Enrollments : IntIdTable("enrollments") {
    val studentId = reference("student_id", Users, onDelete = ReferenceOption.RESTRICT)
    val courseId = reference("course_id", Courses, onDelete = ReferenceOption.RESTRICT)
    val enrolledAt = long("enrolled_at")

    init {
        uniqueIndex(studentId, courseId)
    }
}

object CourseSections : IntIdTable("course_sections") {
    val courseId = reference("course_id", Courses, onDelete = ReferenceOption.CASCADE)
    val weekNumber = integer("week_number")
    val title = varchar("title", 255)
    val description = varchar("description", 1000)
    val url = varchar("url", 500).nullable()
    val sortOrder = integer("sort_order").default(0)
}

object Tests : IntIdTable("tests") {
    val sectionId = reference("section_id", CourseSections, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 255)
    val contentJson = text("content_json")
    val maxScore = integer("max_score")
}

object Tasks: IntIdTable("tasks") {
    val sectionId = reference("section_id", CourseSections, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 255)
    val description = text("description")
    val dueDate = long("due_date")
}

object TestResults: IntIdTable("test_results") {
    val testId = reference("test_id", Tests, onDelete = ReferenceOption.CASCADE)
    val studentId = reference("student_id", Users, onDelete = ReferenceOption.RESTRICT)
    val score = integer("score")
    val attemptedAt = long("attempted_at")
}

object Materials: IntIdTable("materials") {
    val sectionId = reference("section_id", CourseSections, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 255)
    val url = varchar("url", 500)
    val type = enumerationByName("type", 50, MaterialType::class)
    val isVisible = bool("is_visible").default(false)
}

object TaskSubmissions : IntIdTable("task_submissions") {
    val taskId = reference("task_id", Tasks, onDelete = ReferenceOption.CASCADE)
    val studentId = reference("student_id", Users)
    val submissionText = text("submission_text").nullable()
    val fileUrl = varchar("file_url", 500).nullable()
    val grade = integer("grade").nullable()
    val submittedAt = long("submitted_at")
}