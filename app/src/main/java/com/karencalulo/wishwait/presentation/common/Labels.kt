package com.karencalulo.wishwait.presentation.common

import androidx.annotation.StringRes
import com.karencalulo.wishwait.R
import com.karencalulo.wishwait.domain.model.Achievement
import com.karencalulo.wishwait.domain.model.Affordability
import com.karencalulo.wishwait.domain.model.Category
import com.karencalulo.wishwait.domain.model.Mood
import com.karencalulo.wishwait.domain.model.Reason
import com.karencalulo.wishwait.domain.model.SaleEvent
import com.karencalulo.wishwait.domain.model.SaleKind
import com.karencalulo.wishwait.domain.model.Strictness

/** Maps domain enums to translatable text. The domain layer stays language-free. */

@get:StringRes
val Category.labelRes: Int
    get() = when (this) {
        Category.GADGETS -> R.string.category_gadgets
        Category.FASHION -> R.string.category_fashion
        Category.HOME -> R.string.category_home
        Category.FOOD -> R.string.category_food
        Category.FUN -> R.string.category_fun
        Category.TRAVEL -> R.string.category_travel
        Category.BEAUTY -> R.string.category_beauty
        Category.VEHICLE -> R.string.category_vehicle
        Category.OTHER -> R.string.category_other
    }

@get:StringRes
val Reason.labelRes: Int
    get() = when (this) {
        Reason.REPLACE_BROKEN -> R.string.reason_replace_broken
        Reason.NEEDED_FOR_WORK -> R.string.reason_needed_for_work
        Reason.UPGRADE -> R.string.reason_upgrade
        Reason.SAW_AN_AD -> R.string.reason_saw_an_ad
        Reason.ON_SALE -> R.string.reason_on_sale
        Reason.REWARD -> R.string.reason_reward
        Reason.EVERYONE_HAS_IT -> R.string.reason_everyone_has_it
        Reason.JUST_WANT_IT -> R.string.reason_just_want_it
    }

@get:StringRes
val Mood.labelRes: Int
    get() = when (this) {
        Mood.CALM -> R.string.mood_calm
        Mood.HAPPY -> R.string.mood_happy
        Mood.EXCITED -> R.string.mood_excited
        Mood.BORED -> R.string.mood_bored
        Mood.STRESSED -> R.string.mood_stressed
        Mood.SAD -> R.string.mood_sad
        Mood.TIRED -> R.string.mood_tired
        Mood.HUNGRY -> R.string.mood_hungry
    }

@get:StringRes
val Strictness.labelRes: Int
    get() = when (this) {
        Strictness.CHILL -> R.string.strictness_chill
        Strictness.BALANCED -> R.string.strictness_balanced
        Strictness.STRICT -> R.string.strictness_strict
    }

@get:StringRes
val Strictness.descriptionRes: Int
    get() = when (this) {
        Strictness.CHILL -> R.string.strictness_chill_desc
        Strictness.BALANCED -> R.string.strictness_balanced_desc
        Strictness.STRICT -> R.string.strictness_strict_desc
    }

val Strictness.emoji: String
    get() = when (this) {
        Strictness.CHILL -> "😎"
        Strictness.BALANCED -> "⚖️"
        Strictness.STRICT -> "🛡️"
    }

@get:StringRes
val Achievement.titleRes: Int
    get() = when (this) {
        Achievement.FIRST_PAUSE -> R.string.ach_first_pause
        Achievement.LET_IT_GO -> R.string.ach_let_it_go
        Achievement.MINDFUL_BUYER -> R.string.ach_mindful_buyer
        Achievement.PATIENCE_PRO -> R.string.ach_patience_pro
        Achievement.FEELINGS_FADE -> R.string.ach_feelings_fade
        Achievement.NO_REGRETS -> R.string.ach_no_regrets
        Achievement.FREE_SPIRIT -> R.string.ach_free_spirit
        Achievement.MONTH_OF_FREEDOM -> R.string.ach_month_of_freedom
        Achievement.PAID_FOR_ITSELF -> R.string.ach_paid_for_itself
        Achievement.DEBT_FREE -> R.string.ach_debt_free
        Achievement.GOAL_GETTER -> R.string.ach_goal_getter
        Achievement.OLLIE_FRIEND -> R.string.ach_ollie_friend
    }

@get:StringRes
val Achievement.descriptionRes: Int
    get() = when (this) {
        Achievement.FIRST_PAUSE -> R.string.ach_first_pause_desc
        Achievement.LET_IT_GO -> R.string.ach_let_it_go_desc
        Achievement.MINDFUL_BUYER -> R.string.ach_mindful_buyer_desc
        Achievement.PATIENCE_PRO -> R.string.ach_patience_pro_desc
        Achievement.FEELINGS_FADE -> R.string.ach_feelings_fade_desc
        Achievement.NO_REGRETS -> R.string.ach_no_regrets_desc
        Achievement.FREE_SPIRIT -> R.string.ach_free_spirit_desc
        Achievement.MONTH_OF_FREEDOM -> R.string.ach_month_of_freedom_desc
        Achievement.PAID_FOR_ITSELF -> R.string.ach_paid_for_itself_desc
        Achievement.DEBT_FREE -> R.string.ach_debt_free_desc
        Achievement.GOAL_GETTER -> R.string.ach_goal_getter_desc
        Achievement.OLLIE_FRIEND -> R.string.ach_ollie_friend_desc
    }

@get:StringRes
val Affordability.labelRes: Int
    get() = when (this) {
        Affordability.EASY -> R.string.afford_easy
        Affordability.OK -> R.string.afford_ok
        Affordability.STRETCH -> R.string.afford_stretch
        Affordability.NOT_NOW -> R.string.afford_not_now
    }

@StringRes
fun desireLabelRes(level: Int): Int = when (level) {
    1 -> R.string.desire_1
    2 -> R.string.desire_2
    3 -> R.string.desire_3
    4 -> R.string.desire_4
    else -> R.string.desire_5
}

/** "10.10", "15th Payday Sale" or "Month-end Payday Sale". */
fun SaleEvent.displayName(res: android.content.res.Resources): String = when (kind) {
    SaleKind.DOUBLE_DATE -> label
    SaleKind.PAYDAY_MID -> res.getString(R.string.sale_payday_mid)
    SaleKind.PAYDAY_END -> res.getString(R.string.sale_payday_end)
}
