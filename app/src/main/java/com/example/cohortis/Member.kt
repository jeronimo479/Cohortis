package com.example.cohortis

import java.util.UUID

data class Member(
    val id: UUID = UUID.randomUUID(),
    var name: String = "",
    var isPC: Boolean = false,
    var classLevels: String = "",
    var thac0: Int = 20,
    var armorClass: Int = 10,
    var hitDice: String = "",
    var hpFull: Int = 1,
    var hpCurrent: Int = 1,
    var attacks: String = "1",
    var damageRolls: String = "1d8",
    var specialDetections: String? = null,
    var specialAttacks: String? = null,
    var cloneTag: Char = 0.toChar(),
    var lastToHitRoll: Int = 0
) {
    fun hasSpecial(): Boolean {
        return !specialDetections.isNullOrBlank() || !specialAttacks.isNullOrBlank()
    }

    fun rollHp(): Int {
        if (hitDice.isBlank()) return hpFull
        val result = DiceRoller.parseCombo(hitDice)
        return if (result != null) {
            val (repeat, expr) = result
            var total = 0
            repeat(repeat) {
                total += DiceRoller.rollDice(expr)
            }
            total.coerceAtLeast(1)
        } else {
            hpFull
        }
    }

    fun clone(): Member {
        val newHp = rollHp()
        return this.copy(
            id = UUID.randomUUID(), 
            lastToHitRoll = 0,
            hpFull = newHp,
            hpCurrent = newHp
        )
    }
}
