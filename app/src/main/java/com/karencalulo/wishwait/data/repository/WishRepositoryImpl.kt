package com.karencalulo.wishwait.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.karencalulo.wishwait.data.local.WishJsonMapper
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.repository.WishRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

class WishRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : WishRepository {

    private val key = stringPreferencesKey("wishes_v1")

    override fun observeWishes(): Flow<List<Wish>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> WishJsonMapper.decode(prefs[key]).sortedByDescending { it.createdAt } }
            .distinctUntilChanged()

    override fun observeWish(id: String): Flow<Wish?> =
        observeWishes().map { list -> list.firstOrNull { it.id == id } }.distinctUntilChanged()

    override suspend fun getWish(id: String): Wish? = getWishes().firstOrNull { it.id == id }

    override suspend fun getWishes(): List<Wish> = observeWishes().first()

    override suspend fun upsert(wish: Wish) {
        dataStore.edit { prefs ->
            val list = WishJsonMapper.decode(prefs[key]).filterNot { it.id == wish.id } + wish
            prefs[key] = WishJsonMapper.encode(list)
        }
    }

    override suspend fun delete(id: String) {
        dataStore.edit { prefs ->
            prefs[key] = WishJsonMapper.encode(WishJsonMapper.decode(prefs[key]).filterNot { it.id == id })
        }
    }

    override suspend fun replaceAll(wishes: List<Wish>) {
        dataStore.edit { it[key] = WishJsonMapper.encode(wishes) }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(key) }
    }
}
