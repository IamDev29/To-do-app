package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val dueDate: Long? = null,
    val priority: Int = 1, // 0 = Low, 1 = Medium, 2 = High
    val isCompleted: Boolean = false,
    val categoryId: Int? = null,
    val isRecurring: Boolean = false,
    val recurrencePattern: String? = null, // "DAILY", "WEEKLY", "MONTHLY", null
    val reminderTime: Long? = null, // epoch millis
    val createdAt: Long = System.currentTimeMillis(),
    val userEmail: String? = null
)
