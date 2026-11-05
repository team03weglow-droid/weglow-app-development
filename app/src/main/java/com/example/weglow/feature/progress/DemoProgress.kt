package com.example.weglow.feature.progress

import com.example.weglow.domain.progress.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Synthetic preview only; never passed to a repository or saved as user history. */
object DemoProgress {
    fun history(): ProgressHistory {
        val baseline = Instant.parse("2026-10-01T12:00:00Z")
        val counts = listOf(100, 80, 100, 50, 25)
        val scans = counts.mapIndexed { index, count ->
            ProgressScan("demo-scan-$index", baseline.plus(index * 7L, ChronoUnit.DAYS), count, "demo-compatible-settings")
        }
        val completedPerInterval = listOf(6, 9, 7, 8)
        val tasks = scans.drop(1).flatMapIndexed { interval, scan ->
            (0 until 10).map { step ->
                val due = scan.scannedAt!!.minusSeconds(3600 - step.toLong())
                ScheduledTask("demo-schedule-$interval", due.atZone(ZoneOffset.UTC).toLocalDate(), true, step, due)
            }
        }
        val completed = tasks.filter { task ->
            val interval = task.scheduleId.substringAfterLast('-').toInt()
            task.stepIndex < completedPerInterval[interval]
        }.map { it.id }.toSet()
        return ProgressCalculator.calculate(scans, tasks, completed, baseline, scans.last().scannedAt!!)
    }
}
