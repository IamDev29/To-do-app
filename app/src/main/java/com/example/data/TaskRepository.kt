package com.example.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {

    val allCategories: Flow<List<Category>> = taskDao.getAllCategories()
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()

    fun getTasksByCategory(categoryId: Int): Flow<List<Task>> {
        return taskDao.getTasksByCategory(categoryId)
    }

    suspend fun getTaskById(id: Int): Task? {
        return taskDao.getTaskById(id)
    }

    suspend fun insertTask(task: Task): Long {
        return taskDao.insertTask(task)
    }

    suspend fun updateTask(task: Task) {
        taskDao.updateTask(task)
    }

    suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task)
    }

    suspend fun deleteTaskById(id: Int) {
        taskDao.deleteTaskById(id)
    }

    suspend fun insertCategory(category: Category): Long {
        return taskDao.insertCategory(category)
    }

    suspend fun deleteCategory(category: Category) {
        taskDao.deleteCategory(category)
    }

    suspend fun updateCategory(category: Category) {
        taskDao.updateCategory(category)
    }
}
