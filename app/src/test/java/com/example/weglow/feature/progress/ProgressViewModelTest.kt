package com.example.weglow.feature.progress

import com.example.weglow.domain.model.*
import com.example.weglow.domain.progress.*
import com.example.weglow.domain.repository.*
import com.example.weglow.security.SecurityTestAuth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class Repository : ProgressRepository {
        val step = RoutineStep("Cleanse", Product("p", "Cleanser", "", null), emptyList())
        val plan = RoutinePlan(listOf(step), listOf(step))
        val start = Instant.now().minusSeconds(4 * 86400)
        var records = ProgressRecords(emptyList(), listOf(RoutineSchedule("schedule", start, ZoneId.of("UTC"), plan)), emptySet())
        var writes = 0
        var gate: CompletableDeferred<Unit>? = null
        var failWrites = false
        var failLoads = false
        override suspend fun load(userId: String): Result<ProgressRecords> {
            gate?.await()
            return if (failLoads) Result.failure(Exception("offline")) else Result.success(records)
        }
        override suspend fun registerSchedule(userId: String, plan: RoutinePlan, zone: ZoneId) = Result.success(Unit)
        override suspend fun setCompletion(userId: String, task: ScheduledTask, completed: Boolean): Result<Unit> {
            writes++
            gate?.await()
            if (failWrites) return Result.failure(Exception("offline"))
            records = records.copy(completedTaskIds = records.completedTaskIds.toMutableSet().apply { if (completed) add(task.id) else remove(task.id) })
            return Result.success(Unit)
        }
    }

    @Test fun duplicateCallbacksAndUncheckingPersistOneState_andDoNotAddChartPoints() = runTest(dispatcher) {
        val repo = Repository()
        val vm = ProgressViewModel(repo, SecurityTestAuth())
        vm.refresh(); runCurrent()
        val key = vm.uiState.value.tasksByKey.keys.first()
        repo.gate = CompletableDeferred()
        vm.setCompletion(key, true); vm.setCompletion(key, true); runCurrent()
        assertEquals(1, repo.writes)
        repo.gate!!.complete(Unit); runCurrent()
        assertTrue(key in vm.uiState.value.completedKeys)
        vm.setCompletion(key, true); runCurrent()
        assertEquals(1, vm.uiState.value.records.completedTaskIds.size)
        vm.setCompletion(key, false); runCurrent()
        assertFalse(key in vm.uiState.value.completedKeys)
        assertTrue(vm.uiState.value.history.points.isEmpty())
    }

    @Test fun failedWriteDoesNotGrantCredit_andCanBeRetried() = runTest(dispatcher) {
        val repo = Repository()
        val vm = ProgressViewModel(repo, SecurityTestAuth())
        vm.refresh(); runCurrent()
        val key = vm.uiState.value.tasksByKey.keys.first()
        repo.failWrites = true
        vm.setCompletion(key, true); runCurrent()
        assertFalse(key in vm.uiState.value.completedKeys)
        assertTrue(vm.uiState.value.pendingKeys.isEmpty())
        assertNotNull(vm.uiState.value.error)
        repo.failWrites = false
        vm.setCompletion(key, true); runCurrent()
        assertTrue(key in vm.uiState.value.completedKeys)
    }

    @Test fun cloudCompletionsSurviveNewViewModel_andOldAccountStateClears() = runTest(dispatcher) {
        val repo = Repository()
        val auth = SecurityTestAuth()
        val first = ProgressViewModel(repo, auth)
        first.refresh(); runCurrent()
        val key = first.uiState.value.tasksByKey.keys.first()
        first.setCompletion(key, true); runCurrent()
        val reopened = ProgressViewModel(repo, auth)
        reopened.refresh(); runCurrent()
        assertTrue(key in reopened.uiState.value.completedKeys)
        auth.switchTo("bob"); runCurrent()
        assertTrue(first.uiState.value.completedKeys.isEmpty())
        assertTrue(reopened.uiState.value.records.schedules.isEmpty())
        assertFalse(reopened.uiState.value.loaded)
    }

    @Test fun accountSwitchWhileLoadingDoesNotPublishPreviousAccountsRecords() = runTest(dispatcher) {
        val repo = Repository().apply { gate = CompletableDeferred() }
        val auth = SecurityTestAuth()
        val vm = ProgressViewModel(repo, auth)
        vm.refresh(); runCurrent()
        auth.switchTo("bob"); runCurrent()
        repo.gate!!.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.records.schedules.isEmpty())
        assertFalse(vm.uiState.value.loaded)
    }

    @Test fun missingDatabaseIsAnError_notAFakeScore() = runTest(dispatcher) {
        val repo = Repository().apply { failLoads = true }
        val vm = ProgressViewModel(repo, SecurityTestAuth())
        vm.refresh(); runCurrent()
        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.loaded)
        assertTrue(vm.uiState.value.history.points.isEmpty())
    }
}
