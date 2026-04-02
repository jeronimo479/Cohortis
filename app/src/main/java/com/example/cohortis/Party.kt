package com.example.cohortis

import java.util.UUID

/**
 * Represents a group of members.
 * Used to organize combatants or adventuring parties.
 *
 * @property id Unique identifier for the party.
 * @property name The name of the party (e.g., "The Brave Ones").
 * @property members A mutable list of [Member] objects belonging to this party.
 * @property isActive Whether this party is currently shown in the active view.
 */
data class Party(
    val id: UUID = UUID.randomUUID(),
    var name: String = "",
    val members: MutableList<Member> = mutableListOf(),
    var isActive: Boolean = false
)
