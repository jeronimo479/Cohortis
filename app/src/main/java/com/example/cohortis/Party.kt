package com.example.cohortis

import java.util.UUID

/**
 * Represents a member's instance within a specific party.
 * Tracks state that is unique to this party instance, like current HP and clone tags.
 */
data class PartyMember(
    /** Reference to the [Member] template ID. */
    var memberId: UUID,
    /** The current hit points of this member instance in this party. */
    var hpCurrent: Int = 0,
    /** The maximum hit points for this specific instance (useful for clones). */
    var hpFull: Int = 0,
    /** A character used to distinguish between multiple clones of the same member template. */
    var cloneTag: Char = 0.toChar()
) {
    /** Temporary storage for name during JSON import/migration to resolve ID mismatches. */
    @Transient
    var tempName: String? = null

    /**
     * Returns the name of the member formatted for display.
     * Uses the template name and adds the [cloneTag] as a prefix if present.
     */
    fun getDisplayName(template: Member, full: Boolean = false): String {
        val nameToUse = if (full) template.name.trim() else (template.name.trim().split(" ").firstOrNull() ?: "")
        return if (cloneTag != 0.toChar()) {
            "$cloneTag)$nameToUse"
        } else {
            nameToUse
        }
    }

    /**
     * Gets the effective maximum HP for this instance.
     * Prefers its own [hpFull] if set (> 0), otherwise falls back to the template's [Member.hpFull].
     */
    fun getEffectiveHpFull(template: Member): Int {
        return if (hpFull > 0) hpFull else template.hpFull
    }
}

/**
 * Represents a group of members.
 * Used to organize combatants or adventuring parties.
 *
 * @property id Unique identifier for the party.
 * @property name The name of the party (e.g., "The Brave Ones").
 * @property members A mutable list of [PartyMember] references belonging to this party.
 * @property isActive Whether this party is currently shown in the active view.
 */
data class Party(
    var id: UUID = UUID.randomUUID(),
    var name: String = "",
    val members: MutableList<PartyMember> = mutableListOf(),
    var isActive: Boolean = false
)
