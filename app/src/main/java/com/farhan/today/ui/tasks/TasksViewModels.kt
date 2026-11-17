package com.farhan.today.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farhan.today.data.db.ProjectProgress
import com.farhan.today.data.db.TaskEntity
import com.farhan.today.data.db.TaskRow
import com.farhan.today.data.db.TaskStatus
import com.farhan.today.data.repo.TaskInput
import com.farhan.today.data.repo.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class TasksState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val project: ProjectProgress? = null,
    val projects: List<ProjectProgress> = emptyList(),
    val active: List<TaskRow> = emptyList(),
    val done: List<TaskRow> = emptyList(),
    val projectMissing: Boolean = false,
)

/**
 * Dipakai layar Tugas (projectId = null: semua tugas) dan Detail Proyek (projectId terisi).
 * Aksi tulis sama, jadi satu ViewModel cukup.
 */
class TasksViewModel(
    private val repo: TaskRepository,
    private val clock: Clock,
    private val projectId: String? = null,
) : ViewModel() {

    private val tasks: Flow<List<TaskRow>> = projectId?.let { repo.tasksForProject(it) } ?: repo.allTasks()
    private val project: Flow<ProjectProgress?> = projectId?.let { repo.project(it) } ?: flowOf(null)

    val state: StateFlow<TasksState> = combine(repo.projects(), tasks, project) { projects, all, p ->
        TasksState(
            loading = false,
            today = LocalDate.now(clock),
            project = p,
            projects = projects,
            active = all.filter { it.task.status == TaskStatus.TODO },
            done = all.filter { it.task.status == TaskStatus.DONE }.sortedByDescending { it.task.completedAt ?: 0 },
            projectMissing = projectId != null && p == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksState())

    fun toggle(id: String, onResult: (TaskStatus?) -> Unit = {}) = viewModelScope.launch { onResult(repo.toggleDone(id)) }
    fun delete(id: String) = viewModelScope.launch { repo.deleteTask(id) }
    fun restore(id: String) = viewModelScope.launch { repo.restoreTask(id) }
    fun saveTask(input: TaskInput, id: String?, onSaved: () -> Unit, onError: () -> Unit) = viewModelScope.launch {
        try {
            repo.saveTask(input, id)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            onError()
            return@launch
        }
        onSaved()
    }
    fun saveProject(name: String, description: String, id: String? = null) = viewModelScope.launch { repo.saveProject(name, description, id = id) }
    fun deleteProject(id: String, then: () -> Unit) = viewModelScope.launch { repo.deleteProject(id); then() }
    suspend fun load(id: String): TaskEntity? = repo.task(id)
}
