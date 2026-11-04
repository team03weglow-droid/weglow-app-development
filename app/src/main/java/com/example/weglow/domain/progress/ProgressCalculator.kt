package com.example.weglow.domain.progress

import com.example.weglow.domain.model.RoutinePlan
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class ProgressScan(
    val id: String,
    val scannedAt: Instant?,
    val concerns: Int?,
    val settings: String?,
) {
    val valid: Boolean get() = scannedAt != null && concerns != null && concerns >= 0 && !settings.isNullOrBlank()
}

data class RoutineSchedule(val id: String, val startsAt: Instant, val zone: ZoneId, val plan: RoutinePlan)

data class ScheduledTask(
    val scheduleId: String,
    val date: LocalDate,
    val morning: Boolean,
    val stepIndex: Int,
    val dueAt: Instant,
) {
    val id: String get() = "$scheduleId/$date/${if (morning) "AM" else "PM"}/$stepIndex"
}

/** Fixed daily due times, stored in each schedule's original time zone. */
object RoutineScheduleTasks {
    val morningTime: LocalTime = LocalTime.of(8, 0)
    val eveningTime: LocalTime = LocalTime.of(20, 0)

    fun due(schedules: List<RoutineSchedule>, through: Instant): List<ScheduledTask> = buildList {
        val ordered = schedules.sortedBy { it.startsAt }
        ordered.forEachIndexed { version, schedule ->
            val until = ordered.getOrNull(version + 1)?.startsAt
            var date = schedule.startsAt.atZone(schedule.zone).toLocalDate()
            val end = minOf(through, until ?: through).atZone(schedule.zone).toLocalDate()
            while (!date.isAfter(end)) {
                listOf(true to schedule.plan.morning, false to schedule.plan.evening).forEach { (morning, steps) ->
                    val dueAt = date.atTime(if (morning) morningTime else eveningTime).atZone(schedule.zone).toInstant()
                    if (dueAt >= schedule.startsAt && dueAt <= through && (until == null || dueAt < until)) {
                        steps.forEachIndexed { index, step ->
                            if (step.product != null) add(ScheduledTask(schedule.id, date, morning, index, dueAt))
                        }
                    }
                }
                date = date.plusDays(1)
            }
        }
    }.distinctBy { it.id }

    fun scheduleOn(schedules: List<RoutineSchedule>, date: LocalDate): RoutineSchedule? =
        schedules.filter { it.startsAt.atZone(it.zone).toLocalDate() <= date }.maxByOrNull { it.startsAt }

    fun planOn(schedules: List<RoutineSchedule>, date: LocalDate): RoutinePlan? {
        val schedule = scheduleOn(schedules, date) ?: return null
        // An initial plan activated after the morning due time must not display
        // a retroactive morning checklist that can never be completed.
        val morning = date.atTime(morningTime).atZone(schedule.zone).toInstant() >= schedule.startsAt
        val evening = date.atTime(eveningTime).atZone(schedule.zone).toInstant() >= schedule.startsAt
        return schedule.plan.copy(morning = if (morning) schedule.plan.morning else emptyList(),
            evening = if (evening) schedule.plan.evening else emptyList())
    }
}

data class ProgressInterval(
    val previous: ProgressScan,
    val current: ProgressScan,
    val signedImprovement: Double? = null,
    val completedTasks: Int? = null,
    val dueTasks: Int? = null,
    val routinePercentage: Double? = null,
    val scanPoints: Double? = null,
    val routinePoints: Double? = null,
    val score: Double? = null,
    val unavailableReason: String? = null,
)

data class ProgressHistory(
    val scans: List<ProgressScan> = emptyList(),
    val intervals: List<ProgressInterval> = emptyList(),
    val invalidScanCount: Int = 0,
) {
    val points: List<ProgressInterval> get() = intervals.filter { it.score != null }
}

/** Product engagement indicator, never a diagnosis or confidence-based severity score. */
object ProgressCalculator {
    fun calculate(
        scans: List<ProgressScan>,
        tasks: List<ScheduledTask>,
        completedTaskIds: Set<String>,
        trackingStartedAt: Instant?,
        now: Instant,
    ): ProgressHistory {
        val valid = scans.filter { it.valid && it.scannedAt!! <= now }
            .distinctBy { it.id }.sortedWith(compareBy({ it.scannedAt }, { it.id }))
        val intervals = valid.zipWithNext { previous, current ->
            interval(previous, current, tasks, completedTaskIds, trackingStartedAt, now)
        }
        return ProgressHistory(scans, intervals, scans.count { !it.valid || it.scannedAt!! > now })
    }

    fun interval(
        previous: ProgressScan,
        current: ProgressScan,
        tasks: List<ScheduledTask>,
        completedTaskIds: Set<String>,
        trackingStartedAt: Instant?,
        now: Instant,
    ): ProgressInterval {
        val base = ProgressInterval(previous, current)
        if (!previous.valid || !current.valid) return base.copy(unavailableReason = "Scan counts, date or detection settings are missing or invalid.")
        val start = previous.scannedAt!!
        val end = current.scannedAt!!
        if (end <= start || end > now) return base.copy(unavailableReason = "Scan dates do not form a valid completed interval.")
        val due = tasks.distinctBy { it.id }.filter { it.dueAt > start && it.dueAt <= end && it.dueAt <= now }
        val covered = trackingStartedAt != null && trackingStartedAt <= start
        val completed = due.count { it.id in completedTaskIds }
        val routine = if (covered && due.isNotEmpty()) completed.toDouble() / due.size * 100 else null
        val compatible = previous.settings == current.settings
        val improvement = if (compatible && previous.concerns!! > 0) (previous.concerns - current.concerns!!).toDouble() / previous.concerns * 100 else null
        val scanPoints = improvement?.coerceIn(0.0, 100.0)?.times(0.5)
        val routinePoints = routine?.times(0.5)
        val reason = when {
            !compatible -> "These scans used incompatible detection settings. This scan establishes a new baseline."
            previous.concerns == 0 && current.concerns == 0 -> "No concerns detected in either scan"
            previous.concerns == 0 -> "New concerns detected"
            !covered -> "Routine tracking did not cover this full interval. Complete another scan after tracking starts."
            due.isEmpty() -> "No scheduled routine tasks were due in this interval."
            else -> null
        }
        return base.copy(
            signedImprovement = improvement,
            completedTasks = if (covered) completed else null,
            dueTasks = if (covered) due.size else null,
            routinePercentage = routine,
            scanPoints = scanPoints,
            routinePoints = routinePoints,
            score = if (scanPoints != null && routinePoints != null) scanPoints + routinePoints else null,
            unavailableReason = reason,
        )
    }
}
