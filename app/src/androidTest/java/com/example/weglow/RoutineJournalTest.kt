package com.example.weglow

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.AuthenticationState
import com.example.weglow.feature.routine.RoutineJournalViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Uses a separate preference file and fake identities; never touches the user's journal. */
@RunWith(AndroidJUnit4::class)
class RoutineJournalTest {
    @Test fun journalPersistsAndSeparatesAccounts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val preferenceName = "journal-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(instrumentation.targetContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences(preferenceName, mode)
        }
        val auth = FakeAuth()
        val stores = mutableListOf<ViewModelStore>()
        fun create(): RoutineJournalViewModel {
            lateinit var model: RoutineJournalViewModel
            instrumentation.runOnMainSync {
                model = RoutineJournalViewModel(context, auth)
                stores += ViewModelStore().apply { put("journal", model) }
            }
            return model
        }
        try {
            val first = create()
            instrumentation.runOnMainSync {
                first.toggleCompletion("2026-09-12-AM-1-product")
                first.saveNote("2026-09-12", "Hydrated", " Comfortable ")
            }
            await { first.uiState.value.lastSavedNoteKey != null }
            assertTrue(first.uiState.value.completedKeys.contains("2026-09-12-AM-1-product"))
            assertEquals("Comfortable", first.uiState.value.notes["2026-09-12"]?.observations)

            val restored = create()
            await { restored.uiState.value.notes.isNotEmpty() }
            assertEquals(first.uiState.value.notes, restored.uiState.value.notes)
            assertEquals(first.uiState.value.completedKeys, restored.uiState.value.completedKeys)

            instrumentation.runOnMainSync {
                auth.id = null
                auth.authenticationState.value = AuthenticationState.NOT_AUTHENTICATED
            }
            await { restored.uiState.value.notes.isEmpty() }
            instrumentation.runOnMainSync {
                auth.id = "account-b"
                auth.authenticationState.value = AuthenticationState.AUTHENTICATED
                restored.saveNote("2026-09-12", "Dry", "Second account")
            }
            await { restored.uiState.value.lastSavedNoteKey != null }
            assertTrue(restored.uiState.value.completedKeys.isEmpty())
            assertEquals("Second account", restored.uiState.value.notes["2026-09-12"]?.observations)
        } finally {
            instrumentation.runOnMainSync { stores.forEach { it.clear() } }
            context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(25)
        assertTrue("Journal operation did not finish", condition())
    }

    private class FakeAuth : AuthRepository {
        var id: String? = "account-a"
        override val authenticationState = MutableStateFlow(AuthenticationState.AUTHENTICATED)
        override fun currentUserId() = id
        override fun currentUserDisplayName(): String? = null
        override fun hasActiveSession() = id != null
        override suspend fun signUp(email: String, password: String) = Result.success(Unit)
        override suspend fun signIn(email: String, password: String) = Result.success(Unit)
        override suspend fun signInWithGoogle() = Result.success(Unit)
        override suspend fun signOut() = Result.success(Unit)
    }
}
