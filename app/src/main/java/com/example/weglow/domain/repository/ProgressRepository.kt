package com.example.weglow.domain.repository

import com.example.weglow.domain.model.RoutinePlan
import com.example.weglow.domain.progress.ProgressScan
import com.example.weglow.domain.progress.RoutineSchedule
import com.example.weglow.domain.progress.ScheduledTask
import java.time.ZoneId

data class ProgressRecords(
    val scans: List<ProgressScan>,
    val schedules: List<RoutineSchedule>,
    val completedTaskIds: Set<String>,
)

interface ProgressRepository {
    suspend fun load(userId: String): Result<ProgressRecords>
    suspend fun registerSchedule(userId: String, plan: RoutinePlan, zone: ZoneId): Result<Unit>
    suspend fun setCompletion(userId: String, task: ScheduledTask, completed: Boolean): Result<Unit>
}
