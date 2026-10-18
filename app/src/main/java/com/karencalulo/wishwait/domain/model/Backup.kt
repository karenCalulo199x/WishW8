package com.karencalulo.wishwait.domain.model

data class Backup(
    val version: Int,
    val exportedAt: Long,
    val profile: UserProfile,
    val wishes: List<Wish>,
    val goal: SavingsGoal? = null,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
