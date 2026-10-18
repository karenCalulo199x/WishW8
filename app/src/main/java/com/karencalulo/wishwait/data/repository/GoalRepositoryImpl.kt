package com.karencalulo.wishwait.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.karencalulo.wishwait.data.local.GoalJsonMapper
import com.karencalulo.wishwait.domain.model.SavingsGoal
import com.karencalulo.wishwait.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

class GoalRepositoryImpl(private val dataStore: DataStore<Preferences>) : GoalRepository {

    private val goalKey = stringPreferencesKey("goal_v1")
    private val completedKey = intPreferencesKey("goals_completed")

    private val prefs: Flow<Preferences> =
        dataStore.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    override val goal: Flow<SavingsGoal?> = prefs.map { GoalJsonMapper.decode(it[goalKey]) }.distinctUntilChanged()

    override val completedCount: Flow<Int> = prefs.map { it[completedKey] ?: 0 }.distinctUntilChanged()

    override suspend fun current(): SavingsGoal? = goal.first()

    override suspend fun save(goal: SavingsGoal?) {
        dataStore.edit { p ->
            val raw = GoalJsonMapper.encode(goal)
            if (raw == null) p.remove(goalKey) else p[goalKey] = raw
        }
    }

    override suspend fun markCompleted() {
        dataStore.edit { it[completedKey] = (it[completedKey] ?: 0) + 1 }
    }

    override suspend fun clearAll() {
        dataStore.edit {
            it.remove(goalKey)
            it.remove(completedKey)
        }
    }
}
