package com.karencalulo.wishwait.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.karencalulo.wishwait.domain.model.Strictness
import com.karencalulo.wishwait.domain.model.DEFAULT_PAYDAY_SALES
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Currency
import java.util.Locale

class ProfileRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : ProfileRepository {

    private object Keys {
        val income = doublePreferencesKey("income")
        val hours = intPreferencesKey("hours_per_month")
        val savings = doublePreferencesKey("monthly_savings")
        val currency = stringPreferencesKey("currency")
        val strictness = stringPreferencesKey("strictness")
        val onboarded = booleanPreferencesKey("onboarded")
        val reminders = booleanPreferencesKey("reminders")
        val fetchLinks = booleanPreferencesKey("fetch_link_details")
        val paydays = stringPreferencesKey("paydays")
        val paydaySales = stringPreferencesKey("payday_sales")
        val tipsGiven = intPreferencesKey("tips_given")
    }

    override val profile: Flow<UserProfile> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it.toProfile() }
            .distinctUntilChanged()

    override suspend fun current(): UserProfile = profile.first()

    override suspend fun update(transform: (UserProfile) -> UserProfile) {
        dataStore.edit { prefs ->
            val p = transform(prefs.toProfile())
            prefs[Keys.income] = p.monthlyIncome
            prefs[Keys.hours] = p.hoursPerMonth
            prefs[Keys.savings] = p.monthlySavings
            prefs[Keys.currency] = p.currencyCode
            prefs[Keys.strictness] = p.strictness.name
            prefs[Keys.onboarded] = p.onboarded
            prefs[Keys.reminders] = p.remindersEnabled
            prefs[Keys.fetchLinks] = p.fetchLinkDetails
            prefs[Keys.paydays] = p.paydays.joinToString(",")
            prefs[Keys.paydaySales] = p.paydaySales.joinToString(",")
            prefs[Keys.tipsGiven] = p.tipsGiven
        }
    }

    override suspend fun reset() {
        dataStore.edit { prefs ->
            listOf(Keys.income, Keys.hours, Keys.savings, Keys.currency, Keys.strictness, Keys.onboarded, Keys.reminders, Keys.fetchLinks, Keys.paydays, Keys.paydaySales, Keys.tipsGiven)
                .forEach { prefs.remove(it) }
        }
    }

    private fun Preferences.toProfile() = UserProfile(
        monthlyIncome = this[Keys.income] ?: 0.0,
        hoursPerMonth = this[Keys.hours] ?: UserProfile.DEFAULT_HOURS_PER_MONTH,
        monthlySavings = this[Keys.savings] ?: 0.0,
        currencyCode = this[Keys.currency] ?: deviceCurrency(),
        strictness = Strictness.entries.firstOrNull { it.name == this[Keys.strictness] } ?: Strictness.BALANCED,
        onboarded = this[Keys.onboarded] ?: false,
        remindersEnabled = this[Keys.reminders] ?: true,
        fetchLinkDetails = this[Keys.fetchLinks] ?: true,
        paydays = this[Keys.paydays].orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..31 }.distinct().sorted(),
        // Never set = both Payday Sales on. An empty value = both turned off.
        paydaySales = this[Keys.paydaySales]?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: DEFAULT_PAYDAY_SALES,
        tipsGiven = this[Keys.tipsGiven] ?: 0,
    )

    private fun deviceCurrency(): String =
        runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull() ?: "USD"
}
