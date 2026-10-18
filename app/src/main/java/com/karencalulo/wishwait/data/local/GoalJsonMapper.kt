package com.karencalulo.wishwait.data.local

import com.karencalulo.wishwait.domain.model.GoalDeposit
import com.karencalulo.wishwait.domain.model.SavingsGoal
import org.json.JSONArray
import org.json.JSONObject

internal object GoalJsonMapper {

    fun toJson(g: SavingsGoal): JSONObject = JSONObject()
        .put("name", g.name)
        .put("emoji", g.emoji)
        .put("target", g.target)
        .put("createdAt", g.createdAt)
        .put(
            "deposits",
            JSONArray().apply {
                g.deposits.forEach { d ->
                    put(JSONObject().put("t", d.timestamp).put("a", d.amount).apply { d.wishId?.let { put("w", it) } })
                }
            },
        )

    fun fromJson(o: JSONObject): SavingsGoal = SavingsGoal(
        name = o.getString("name"),
        emoji = o.optString("emoji", "🎯"),
        target = o.getDouble("target"),
        createdAt = o.optLong("createdAt", 0L),
        deposits = o.optJSONArray("deposits")?.let { arr ->
            (0 until arr.length()).map { i ->
                val d = arr.getJSONObject(i)
                GoalDeposit(d.getLong("t"), d.getDouble("a"), d.optString("w").takeIf { it.isNotBlank() })
            }
        } ?: emptyList(),
    )

    fun encode(g: SavingsGoal?): String? = g?.let { toJson(it).toString() }

    fun decode(raw: String?): SavingsGoal? =
        if (raw.isNullOrBlank()) null else runCatching { fromJson(JSONObject(raw)) }.getOrNull()
}
