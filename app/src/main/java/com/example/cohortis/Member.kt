package com.example.cohortis

import java.util.UUID

/**
 * Represents a member (either a Player Character or an NPC/Monster) in a party.
 * Holds stats, hit points, and dice rolling configurations.
 */
data class Member(
    /** Unique identifier for the member. */
    val id: UUID = UUID.randomUUID(),
    /** The name of the member. */
    var name: String = "",
    /** Whether the member is a Player Character (PC). Affects UI display and logic. */
    var isPC: Boolean = false,
    /** Description of class and levels (for PCs) or other identifying info. */
    var classLevels: String = "",
    /** To-Hit Armor Class 0 (THAC0) value for combat calculations. */
    var thac0: Int = 20,
    /** The current Armor Class of the member. */
    var armorClass: Int = 10,
    /** Hit dice string used for HP calculation, e.g., "1d8+2 | 1d4". */
    var hitDice: String = "",
    /** The maximum hit points the member can have. */
    var hpFull: Int = 0,
    /** The current hit points of the member. */
    var hpCurrent: Int = 0,
    /** String representing damage rolls, e.g., "1d6 | 1d4". */
    var damageRolls: String = "",
    /** Special detection abilities (e.g., "Detect Traps 30%"). */
    var specialDetections: String? = null,
    /** Special attack descriptions or modifiers. */
    var specialAttacks: String? = null,
    /** A character used to distinguish between multiple clones of the same member template. */
    var cloneTag: Char = 0.toChar(),
    /** Stores the result of the last 'to hit' roll performed for this member. */
    var lastToHitRoll: Int = 0
) {
    /**
     * Returns the name of the member formatted for display.
     * For clones, it includes the [cloneTag] as a prefix (e.g., "a) Zendra").
     * Only returns the first word of the name.
     *
     * @return A formatted display name string.
     */
    fun getDisplayName(): String {
        val nameBase = name.trim().split(" ").firstOrNull() ?: ""
        return if (cloneTag != 0.toChar()) {
            "$cloneTag)$nameBase"
        } else {
            nameBase
        }
    }

    /**
     * Checks if the member has any special detections or attacks defined.
     *
     * @return True if either [specialDetections] or [specialAttacks] is not blank.
     */
    fun hasSpecial(): Boolean {
        return !specialDetections.isNullOrBlank() || !specialAttacks.isNullOrBlank()
    }

    /**
     * Rolls for HP based on the [hitDice] string.
     * Parses up to three segments separated by '|', rolls the dice for each, and returns the total.
     * If [hitDice] is blank or parsing fails, returns the current [hpFull].
     *
     * @return The total HP rolled, or [hpFull] as a fallback.
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

    /**
     * Creates a deep copy of the member with a new UUID and freshly rolled HP.
     * Used when adding multiple instances of a monster template to a party.
     *
     * @return A new [Member] instance based on the current one.
     */
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
