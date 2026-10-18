package com.karencalulo.wishwait.data.local

import com.karencalulo.wishwait.domain.model.Category
import com.karencalulo.wishwait.domain.model.DesireCheck
import com.karencalulo.wishwait.domain.model.Mood
import com.karencalulo.wishwait.domain.model.PaymentPlan
import com.karencalulo.wishwait.domain.model.Reason
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import org.json.JSONArray
import org.json.JSONObject

/** Wishes are stored as one small JSON array — no database needed for a personal list. */
internal object WishJsonMapper {

    fun encode(wishes: List<Wish>): String =
        JSONArray().apply { wishes.forEach { put(encode(it)) } }.toString()

    fun decode(raw: String?): List<Wish> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                runCatching { decode(array.getJSONObject(i)) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(w: Wish): JSONObject = JSONObject().apply {
        put("id", w.id)
        put("name", w.name)
        put("price", w.price)
        put("category", w.category.name)
        put("reasons", JSONArray(w.reasons.map { it.name }))
        put("moods", JSONArray(w.moods.map { it.name }))
        put("note", w.note)
        put("createdAt", w.createdAt)
        put("cooldownDays", w.cooldownDays)
        put("cooldownEndsAt", w.cooldownEndsAt)
        put("status", w.status.name)
        w.decidedAt?.let { put("decidedAt", it) }
        put(
            "desireLog",
            JSONArray().apply {
                w.desireLog.forEach { put(JSONObject().put("t", it.timestamp).put("l", it.level)) }
            },
        )
        w.gladRating?.let { put("glad", it) }
        if (w.usageLog.isNotEmpty()) put("uses", JSONArray(w.usageLog))
        w.worthTargetPerUse?.let { put("worthTarget", it) }
        w.incomeAtCreation?.let { put("incomeAt", it) }
        w.hoursAtCreation?.let { put("hoursAt", it) }
        w.link?.let { put("link", it) }
        w.store?.let { put("store", it) }
        w.payment?.let { p ->
            put(
                "payment",
                JSONObject()
                    .put("months", p.months)
                    .put("monthly", p.monthlyPayment)
                    .put("firstDue", p.firstDueAt)
                    .put("paid", p.paidCount),
            )
        }
    }

    private fun decode(o: JSONObject): Wish = Wish(
        id = o.getString("id"),
        name = o.getString("name"),
        price = o.getDouble("price"),
        category = enumOr(o.optString("category"), Category.OTHER),
        reasons = o.optJSONArray("reasons").strings().mapNotNull { enumOrNull<Reason>(it) }.toSet(),
        moods = o.optJSONArray("moods").strings().mapNotNull { enumOrNull<Mood>(it) }.toSet(),
        note = o.optString("note", ""),
        createdAt = o.getLong("createdAt"),
        cooldownDays = o.optInt("cooldownDays", 0),
        cooldownEndsAt = o.getLong("cooldownEndsAt"),
        status = enumOr(o.optString("status"), WishStatus.WAITING),
        decidedAt = if (o.has("decidedAt")) o.getLong("decidedAt") else null,
        desireLog = o.optJSONArray("desireLog")?.let { arr ->
            (0 until arr.length()).map { i ->
                val e = arr.getJSONObject(i)
                DesireCheck(e.getLong("t"), e.getInt("l"))
            }
        } ?: emptyList(),
        gladRating = if (o.has("glad")) o.getBoolean("glad") else null,
        usageLog = o.optJSONArray("uses")?.let { arr -> (0 until arr.length()).map { arr.getLong(it) } } ?: emptyList(),
        worthTargetPerUse = if (o.has("worthTarget")) o.getDouble("worthTarget") else null,
        incomeAtCreation = if (o.has("incomeAt")) o.getDouble("incomeAt") else null,
        hoursAtCreation = if (o.has("hoursAt")) o.getInt("hoursAt") else null,
        link = o.optString("link").takeIf { it.isNotBlank() },
        store = o.optString("store").takeIf { it.isNotBlank() },
        payment = o.optJSONObject("payment")?.let { p ->
            PaymentPlan(
                months = p.getInt("months"),
                monthlyPayment = p.getDouble("monthly"),
                firstDueAt = p.getLong("firstDue"),
                paidCount = p.optInt("paid", 0),
            )
        },
    )

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
        enumValues<E>().firstOrNull { it.name == name }

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        enumOrNull<E>(name) ?: fallback
}
