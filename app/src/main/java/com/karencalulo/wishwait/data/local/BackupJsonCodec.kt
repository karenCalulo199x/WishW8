package com.karencalulo.wishwait.data.local

import com.karencalulo.wishwait.domain.model.Backup
import com.karencalulo.wishwait.domain.model.Strictness
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.repository.BackupCodec
import org.json.JSONObject

class BackupJsonCodec : BackupCodec {

    override fun encode(backup: Backup): String {
        val p = backup.profile
        return JSONObject()
            .put("app", APP_TAG)
            .put("version", backup.version)
            .put("exportedAt", backup.exportedAt)
            .put(
                "profile",
                JSONObject()
                    .put("income", p.monthlyIncome)
                    .put("hours", p.hoursPerMonth)
                    .put("savings", p.monthlySavings)
                    .put("currency", p.currencyCode)
                    .put("strictness", p.strictness.name)
                    .put("reminders", p.remindersEnabled)
                    .put("fetchLinks", p.fetchLinkDetails)
                    .put("paydays", org.json.JSONArray(p.paydays))
                    .put("paydaySales", org.json.JSONArray(p.paydaySales))
                    .put("tipsGiven", p.tipsGiven),
            )
            .put("wishes", org.json.JSONArray(WishJsonMapper.encode(backup.wishes)))
            .apply { backup.goal?.let { put("goal", GoalJsonMapper.toJson(it)) } }
            .toString(2)
    }

    override fun decode(text: String): Backup? = runCatching {
        val o = JSONObject(text)
        if (o.optString("app") != APP_TAG) return null
        val p = o.getJSONObject("profile")
        Backup(
            version = o.optInt("version", 1),
            exportedAt = o.optLong("exportedAt", 0L),
            profile = UserProfile(
                monthlyIncome = p.optDouble("income", 0.0),
                hoursPerMonth = p.optInt("hours", UserProfile.DEFAULT_HOURS_PER_MONTH),
                monthlySavings = p.optDouble("savings", 0.0),
                currencyCode = p.optString("currency", "USD"),
                strictness = Strictness.entries.firstOrNull { it.name == p.optString("strictness") } ?: Strictness.BALANCED,
                onboarded = true,
                remindersEnabled = p.optBoolean("reminders", true),
                fetchLinkDetails = p.optBoolean("fetchLinks", true),
                paydays = p.optJSONArray("paydays")?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: emptyList(),
                paydaySales = p.optJSONArray("paydaySales")?.let { a -> (0 until a.length()).map { a.getInt(it) } }
                    ?: com.karencalulo.wishwait.domain.model.DEFAULT_PAYDAY_SALES,
                tipsGiven = p.optInt("tipsGiven", 0),
            ),
            wishes = WishJsonMapper.decode(o.optJSONArray("wishes")?.toString()),
            goal = o.optJSONObject("goal")?.let { runCatching { GoalJsonMapper.fromJson(it) }.getOrNull() },
        )
    }.getOrNull()

    companion object {
        const val APP_TAG = "WishWait"
    }
}
