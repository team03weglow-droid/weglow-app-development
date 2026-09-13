package com.example.weglow.feature.routine

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class RoutineNote(val feeling: String?, val observations: String)

data class RoutineJournalState(
    val completedKeys: Set<String> = emptySet(),
    val notes: Map<String, RoutineNote> = emptyMap(),
    val lastSavedNoteKey: String? = null,
    val errorMessage: String? = null,
)

/** Account-scoped, device-local journal. No cloud synchronization is implied. */
class RoutineJournalViewModel(context: Context, private val authRepository: AuthRepository) : ViewModel() {
    private val preferences = context.getSharedPreferences("routine_journal", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var owner: String? = null
    private val _uiState = MutableStateFlow(RoutineJournalState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authenticationState.collect {
                mutex.withLock { loadAccount() }
            }
        }
    }

    private suspend fun loadAccount() {
        val account = authRepository.currentUserId()
        if (owner == account) return
        owner = account
        _uiState.value = RoutineJournalState()
        if (account == null) return
        val state = withContext(Dispatchers.IO) {
            runCatching {
                val notes = JSONObject(preferences.getString("notes_$account", "{}") ?: "{}")
                RoutineJournalState(
                    completedKeys = preferences.getStringSet("completed_$account", emptySet()).orEmpty().toSet(),
                    notes = notes.keys().asSequence().associateWith { key ->
                        val note = notes.getJSONObject(key)
                        RoutineNote(note.optString("feeling").takeIf { it.isNotBlank() }, note.optString("observations"))
                    },
                )
            }.getOrElse { RoutineJournalState(errorMessage = "Couldn't read your saved journal. Please reopen the app.") }
        }
        if (authRepository.currentUserId() == account) _uiState.value = state
    }

    fun toggleCompletion(key: String) = update { current ->
        val completed = current.completedKeys.toMutableSet().apply { if (!add(key)) remove(key) }
        current.copy(completedKeys = completed, lastSavedNoteKey = null)
    }

    fun saveNote(key: String, feeling: String?, observations: String) = update { current ->
        current.copy(
            notes = current.notes + (key to RoutineNote(feeling, observations.trim())),
            lastSavedNoteKey = key,
        )
    }

    private fun update(transform: (RoutineJournalState) -> RoutineJournalState) {
        val requestedAccount = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            mutex.withLock {
                loadAccount()
                if (owner != requestedAccount) return@withLock
                val updated = transform(_uiState.value).copy(errorMessage = null)
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        val notes = JSONObject()
                        updated.notes.forEach { (key, note) ->
                            notes.put(key, JSONObject().put("feeling", note.feeling.orEmpty()).put("observations", note.observations))
                        }
                        preferences.edit()
                            .putStringSet("completed_$requestedAccount", updated.completedKeys)
                            .putString("notes_$requestedAccount", notes.toString())
                            .commit()
                    }.getOrDefault(false)
                }
                if (authRepository.currentUserId() == requestedAccount) {
                    _uiState.value = if (saved) updated else _uiState.value.copy(
                        lastSavedNoteKey = null,
                        errorMessage = "Couldn't save your changes on this device. Please try again.",
                    )
                }
            }
        }
    }
}
