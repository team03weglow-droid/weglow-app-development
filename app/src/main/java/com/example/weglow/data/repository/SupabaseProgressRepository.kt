package com.example.weglow.data.repository

import com.example.weglow.domain.model.RoutinePlan
import com.example.weglow.domain.progress.ProgressScan
import com.example.weglow.domain.progress.RoutineSchedule
import com.example.weglow.domain.progress.ScheduledTask
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.ProgressRecords
import com.example.weglow.domain.repository.ProgressRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId

@Serializable
internal data class ProgressScanRow(
    val progress_scan_id: String,
    val Date_time: String? = null,
    val detection_settings: String? = null,
    val dark_spots: Int? = null, val open_pores: Int? = null, val black_heads: Int? = null,
    val acne_scars: Int? = null, val papules: Int? = null, val whiteheads: Int? = null,
    val nodules: Int? = null, val cysts: Int? = null, val pustules: Int? = null, val freckles: Int? = null,
) {
    fun toDomain(): ProgressScan {
        val counts = listOf(dark_spots, open_pores, black_heads, acne_scars, papules, whiteheads, nodules, cysts, pustules, freckles)
        val total = if (counts.all { it != null && it >= 0 }) counts.sumOf { it!!.toLong() }.takeIf { it <= Int.MAX_VALUE }?.toInt() else null
        return ProgressScan(progress_scan_id, Date_time?.let { runCatching { Instant.parse(it) }.getOrNull() }, total, detection_settings)
    }
}

@Serializable
internal data class RoutineScheduleRow(val id: String, val effective_from: String, val zone_id: String, val plan: RoutinePlan) {
    fun toDomain() = RoutineSchedule(id, Instant.parse(effective_from), ZoneId.of(zone_id), plan)
}

@Serializable
internal data class RoutineCompletionRow(
    val schedule_id: String, val task_date: String, val morning: Boolean, val step_index: Int, val completed: Boolean,
) {
    val taskId: String get() = "$schedule_id/$task_date/${if (morning) "AM" else "PM"}/$step_index"
}

class SupabaseProgressRepository(private val client: SupabaseClient, private val auth: AuthRepository) : ProgressRepository {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun requireOwner(userId: String) {
        check(auth.currentUserId() == userId && auth.hasActiveSession()) { "Sign in to access progress." }
    }

    private suspend fun <T> request(block: suspend () -> T): Result<T> = try { Result.success(block()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }

    override suspend fun registerSchedule(userId: String, plan: RoutinePlan, zone: ZoneId): Result<Unit> = request {
        requireOwner(userId)
        client.postgrest.rpc("register_routine_schedule", buildJsonObject {
            put("p_plan", json.encodeToJsonElement(plan)); put("p_zone", zone.id)
        })
        requireOwner(userId)
    }

    override suspend fun load(userId: String): Result<ProgressRecords> = request {
        requireOwner(userId)
        val scans = pages { offset ->
            client.postgrest["Scan-profiles"].select {
                filter { eq("user_prof_id", userId) }
                order("Date_time", Order.ASCENDING); order("progress_scan_id", Order.ASCENDING)
                range(offset, offset + PAGE_SIZE - 1)
            }.decodeList<ProgressScanRow>()
        }
        requireOwner(userId)
        val schedules = pages { offset ->
            client.postgrest["routine_schedules"].select {
                filter { eq("profile_id", userId) }; order("effective_from", Order.ASCENDING)
                range(offset, offset + PAGE_SIZE - 1)
            }.decodeList<RoutineScheduleRow>()
        }
        requireOwner(userId)
        val completions = pages { offset ->
            client.postgrest["routine_task_completions"].select {
                filter { eq("profile_id", userId) }
                order("schedule_id", Order.ASCENDING); order("task_date", Order.ASCENDING)
                order("morning", Order.ASCENDING); order("step_index", Order.ASCENDING)
                range(offset, offset + PAGE_SIZE - 1)
            }.decodeList<RoutineCompletionRow>()
        }
        requireOwner(userId)
        ProgressRecords(scans.map { it.toDomain() }, schedules.map { it.toDomain() }, completions.filter { it.completed }.map { it.taskId }.toSet())
    }

    override suspend fun setCompletion(userId: String, task: ScheduledTask, completed: Boolean): Result<Unit> = request {
        requireOwner(userId)
        client.postgrest.rpc("set_routine_task_completion", buildJsonObject {
            put("p_schedule_id", task.scheduleId); put("p_task_date", task.date.toString())
            put("p_morning", task.morning); put("p_step_index", task.stepIndex); put("p_completed", completed)
        })
        requireOwner(userId)
    }

    private suspend fun <T> pages(fetch: suspend (Long) -> List<T>): List<T> = buildList {
        var offset = 0L
        do {
            val page = fetch(offset)
            addAll(page)
            offset += page.size
        } while (page.size == PAGE_SIZE.toInt())
    }

    private companion object { const val PAGE_SIZE = 500L }
}
