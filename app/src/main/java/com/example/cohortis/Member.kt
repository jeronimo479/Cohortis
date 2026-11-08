package com.example.cohortis

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Represents a member template (either a Player Character or an NPC/Monster).
 * Holds stats, max hit points, and dice rolling configurations.
 * Actual current HP and clone tags are tracked per party instance.
 */
data class Member(
    /** The alias/name of the member. Serialized first as alias. Accepts alias, _name_, name on import. */
    @SerializedName("alias", alternate = ["_name_", "name"])
    var name: String = "",
    /** Unique identifier for the member template. */
    var id: UUID = UUID.randomUUID(),
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
    /** String representing damage rolls, e.g., "1d6 | 1d4". */
    var damageRolls: String = "",
    /** Special detection abilities (e.g., "Detect Traps 30%"). */
    var specialDetections: String = "",
    /** Special attack descriptions or modifiers. */
    var specialAttacks: String = "",
    /** Movement speed or mode, e.g., "120' (40')", "9, Fl 18". */
    var movement: String = "",
    /** Size category, e.g., "M", "L (10' tall)". */
    var size: String = "",
    /** Experience point value for defeating this member (for NPCs). */
    var xp: Int = 0

) {
    /**
     * Checks if the member has any special detections or attacks defined.
     *
     * @return True if any special field is not blank.
     */
    fun hasSpecial(): Boolean {
        return specialDetections.isNotBlank() || specialAttacks.isNotBlank() || movement.isNotBlank() || size.isNotBlank()
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
}
