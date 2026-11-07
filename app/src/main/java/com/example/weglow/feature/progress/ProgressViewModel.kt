package com.example.weglow.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.core.auth.AccountWork
import com.example.weglow.domain.model.RoutinePlan
import com.example.weglow.domain.progress.*
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.ProgressRecords
import com.example.weglow.domain.repository.ProgressRepository
import com.example.weglow.feature.routine.routineStepKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ProgressUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val records: ProgressRecords = ProgressRecords(emptyList(), emptyList(), emptySet()),
    val history: ProgressHistory = ProgressHistory(),
    val tasksByKey: Map<String, ScheduledTask> = emptyMap(),
    val completedKeys: Set<String> = emptySet(),
    val pendingKeys: Set<String> = emptySet(),
) {
    fun planOn(date: LocalDate): RoutinePlan? = RoutineScheduleTasks.planOn(records.schedules, date)
}

class ProgressViewModel(private val repository: ProgressRepository, private val auth: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState = _uiState.asStateFlow()
    private val accountWork = AccountWork(auth, viewModelScope) { _uiState.value = ProgressUiState() }
    private val mutex = Mutex()
    private var refreshJob: Job? = null

    fun refresh(plan: RoutinePlan? = null) {
        if (auth.currentUserId() == null) return
        refreshJob?.cancel()
        refreshJob = accountWork.launch {
            mutex.withLock {
                val userId = auth.currentUserId() ?: return@withLock
                _uiState.value = _uiState.value.copy(loading = true, error = null)
                val registration = if (plan != null) await { repository.registerSchedule(userId, plan, ZoneId.systemDefault()) } else Result.success(Unit)
                if (registration.isFailure) {
                    _uiState.value = _uiState.value.copy(loading = false, error = "Couldn't save your routine schedule. Check your connection and try again.")
                    return@withLock
                }
                await { repository.load(userId) }.fold(
                    onSuccess = { _uiState.value = stateFor(it) },
                    onFailure = { _uiState.value = _uiState.value.copy(loading = false, error = "Couldn't load progress. Check your connection and try again.") },
                )
            }
        }
    }

    /** Explicit desired state makes duplicate checkbox callbacks idempotent. */
    fun setCompletion(key: String, completed: Boolean) {
        if (key in _uiState.value.pendingKeys) return
        val task = _uiState.value.tasksByKey[key] ?: return
        _uiState.value = _uiState.value.copy(pendingKeys = _uiState.value.pendingKeys + key)
        accountWork.launch {
            mutex.withLock {
                val userId = auth.currentUserId() ?: return@withLock
                await { repository.setCompletion(userId, task, completed) }.fold(
                    onSuccess = {
                        val records = _uiState.value.records
                        val ids = records.completedTaskIds.toMutableSet().apply { if (completed) add(task.id) else remove(task.id) }
                        val pending = _uiState.value.pendingKeys - key
                        _uiState.value = stateFor(records.copy(completedTaskIds = ids)).copy(pendingKeys = pending)
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(pendingKeys = _uiState.value.pendingKeys - key,
                            error = "Couldn't save this completion. Please refresh and try again.")
                    },
                )
            }
        }
    }

    fun clear() = accountWork.clear()

    /** Re-evaluate due times without adding a scan or making a network request. */
    fun updateDueTimes() {
        if (_uiState.value.loaded) {
            val current = _uiState.value
            _uiState.value = stateFor(current.records).copy(error = current.error, loading = current.loading, pendingKeys = current.pendingKeys)
        }
    }

    private fun stateFor(records: ProgressRecords): ProgressUiState {
        val now = Instant.now()
        val tasks = RoutineScheduleTasks.due(records.schedules, now)
        val bySchedule = records.schedules.associateBy { it.id }
        val byKey = tasks.associateBy { task ->
            val plan = bySchedule.getValue(task.scheduleId).plan
            val steps = if (task.morning) plan.morning else plan.evening
            routineStepKey(task.date, task.morning, task.stepIndex, steps[task.stepIndex].product?.id)
        }
        return ProgressUiState(
            loaded = true, records = records, tasksByKey = byKey,
            completedKeys = byKey.filterValues { it.id in records.completedTaskIds }.keys,
            history = ProgressCalculator.calculate(records.scans, tasks, records.completedTaskIds, records.schedules.minOfOrNull { it.startsAt }, now),
        )
    }
}
