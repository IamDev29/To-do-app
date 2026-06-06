package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    // Categories/Folders
    @Query("SELECT * FROM categories ORDER BY id ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Delete
    suspend fun deleteCategory(category: Category)

    @Update
    suspend fun updateCategory(category: Category)

    // Tasks
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, priority DESC, dueDate ASC, createdAt DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE categoryId = :categoryId ORDER BY isCompleted ASC, priority DESC, dueDate ASC")
    fun getTasksByCategory(categoryId: Int): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)

    // Quick Entry / Widgets can use synchronous operations safely on background threads
    @Query("SELECT * FROM categories")
    fun getCategoriesBlocking(): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTaskBlocking(task: Task): Long
}
