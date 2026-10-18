package com.karencalulo.wishwait.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** Everything lives on-device. No account, no server. */
val Context.wishWaitDataStore: DataStore<Preferences> by preferencesDataStore(name = "wishwait")
