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
    var damageRolls: String = "",
    var specialDetections: String? = null,
    var specialAttacks: String? = null,
    var cloneTag: Char = 0.toChar(),
    var lastToHitRoll: Int = 0
) {
    fun hasSpecial(): Boolean {
        return !specialDetections.isNullOrBlank() || !specialAttacks.isNullOrBlank()
    }

    /**
     * Rolls for HP based on the hitDice string.
     * Supports up to three segments separated by '|', e.g. "1d8+2 | 1d4"
     */
    fun rollHp(): Int {
        if (hitDice.isBlank()) return hpFull
        
        val segments = hitDice.split("|")
        var total = 0
        
        segments.take(3).forEach { segment ->
            val result = DiceRoller.parseCombo(segment.trim())
            if (result != null) {
                val (repeatCount, expr) = result
                repeat(repeatCount) {
                    total += DiceRoller.rollDice(expr)
                }
            }
        }
        
        return if (total > 0) total else hpFull
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
