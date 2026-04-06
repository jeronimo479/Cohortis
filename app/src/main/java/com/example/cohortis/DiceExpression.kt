package com.example.cohortis.dice

import kotlin.random.Random

/**
 * ============================================================
 * DICE DOMAIN MODEL – AUTHORITATIVE DOCUMENTATION
 * ============================================================
 *
 * This file defines the complete dice grammar and behavior for the app.
 * Dice expressions were historically stored as Strings; this file formalizes
 * that structure into explicit domain objects while preserving the ability
 * to serialize back to strings.
 *
 * ------------------------------------------------------------
 * ORIGINAL REQUIREMENTS (DO NOT DELETE)
 * ------------------------------------------------------------
 *
 * A DiceRoll is:
 *   X d Y +/- Z
 *     - X = number of dice
 *     - Y = number of faces
 *     - Z = modifier
 *
 * A DiceSegment is:
 *   N (X d Y +/- Z)
 *     - N = number of times the DiceRoll is performed
 *
 * A DiceGroup is:
 *   Several DiceSegments rolled at the same time,
 *   represented by commas ',' between segments.
 *
 * A DiceExpression is:
 *   One or more DiceGroups separated by pipes '|',
 *   where each group is resolved independently.
 *
 * Example:
 *   "3d8,d6 | d4,d5+2 | 4x 2d4+1"
 *
 * Editing rules:
 *   - When editing a DiceSegment, N, X, Y, and Z must be explicit.
 *
 * Display minimization rules:
 *   - Do NOT display "Nx" when N == 1
 *   - Do NOT display "X" when X == 1
 *   - Do NOT display "+/-Z" when Z == 0
 *
 * ------------------------------------------------------------
 * IMPORTANT DESIGN NOTES
 * ------------------------------------------------------------
 *
 * - Punctuation (commas, pipes) is a *serialization detail*.
 * - Behavior and semantics live in the domain objects below.
 * - Strings are accepted only at the parse/format boundaries.
 * - All dice parsing logic lives in this file.
 *
 * If you change rules here, you are changing the rules of the app.
 */

/** Relationships:

DiceExpression
 +-- List<DiceGroup>
        +-- List<DiceSegment>
               +-- repeat (N)
               +-- DiceRoll
                     +-- diceCount (X)
                     +-- sides (Y)
                     +-- modifier (Z)
*/

/* ============================================================
 * DiceRoll,  XdY +/- Z
 * ============================================================
 *
 * Represents a single mechanical dice roll:
 *   Roll X dice with Y sides, then apply modifier Z.
 *
 * This class intentionally knows NOTHING about repetition,
 * grouping, or serialization context.
 */
data class DiceRoll(
    val diceCount: Int,    // X
    val sides: Int,        // Y
    val modifier: Int = 0  // Z
) {
    /**
     * Check for valid X,Y&Z
     */
    
    fun isValid(): Boolean =
        diceCount > 0 && sides > 0 && sides <= 1000

    /**
     * Rolls this DiceRoll exactly once.
     */
    fun rollOnce(): Int {
        var total = 0
        repeat(diceCount) {
            total += Random.nextInt(1, sides + 1)
        }
        return total + modifier
    }

    /**
     * Formatted with minimization rules applied.
     *
     * Examples:
     *   1d8      -> "d8"
     *   2d6+1    -> "2d6+1"
     *   1d4+0    -> "d4"
     */
    fun toShortString(): String {
        val xPart = if (diceCount == 1) "" else diceCount.toString()
        val zPart = when {
            modifier > 0 -> "+$modifier"
            modifier < 0 -> modifier.toString()
            else -> ""
        }
        return "${xPart}d$sides$zPart"
    }

    /**
     * Long, fully explicit form.
     *
     * Used internally and for edit-mode expansion.
     * Minimization rules DO NOT apply here.
     */
    fun toLongString(): String {
        val zPart = when {
            modifier >= 0 -> "+$modifier"
            modifier < 0 -> modifier.toString()
            else -> ""
        }
        return "${diceCount}d$sides$zPart"
    }
}


/* ============================================================
 * DiceSegment — N × DiceRoll
 * ============================================================
 *
 * Represents repeated execution of a DiceRoll.
 *
 * Example:
 *   4x (2d4+1)
 *
 * NOTE:
 * - DiceSegment owns N.
 * - DiceRoll owns X, Y, Z.
 * - Keeping these separate avoids semantic confusion.
 */
data class DiceSegment(
    val repeat: Int = 1,   // N
    val roll: DiceRoll
) {

    /**
     * Check for valid segments
     */    
    fun isValid(): Boolean =
        repeat > 0 && roll.isValid()

    /**
     * Rolls the underlying DiceRoll N times and sums the results.
     */
    fun rollTotal(): Int =
        (1..repeat).sumOf { roll.rollOnce() }

    /**
     * Formatted with minimization rules applied.
     * - Suppress "Nx" when N == 1
     */
    fun toShortString(): String {
        val nPart = if (repeat == 1) "" else "${repeat}x "
        return nPart + roll.toShortString()
    }

    /**
     * Long, fully explicit form.
     * - Always fully expanded (N, X, Y, Z visible)
     */
    fun toLongString(): String =
        "${repeat}x ${roll.toLongString()}"
}


/* ============================================================
 * DiceGroup — rolled simultaneously (comma-separated)
 * ============================================================
 *
 * A DiceGroup represents DiceSegments that are rolled independently
 * but conceptually resolved together as a single result.
 *
 * IMPORTANT:
 * - Order is not significant.
 * - There is no chaining or dependency between segments.
 * - The comma is merely the serialization delimiter.
 */
data class DiceGroup(
    val segments: List<DiceSegment>
) {

    /**
     * Check for valid dice group.
     */
    fun isValid(): Boolean =
        segments.isNotEmpty() && segments.all { it.isValid() }

    /**
     * Rolls all segments and sums the results.
     */
    fun rollTotal(): Int =
        segments.sumOf { it.rollTotal() }

    /**
     * Formatted with minimization rules applied.
     */
    fun toShortString(): String =
        segments.joinToString(",") { it.toShortString() }

    /**
     * Long, fully explicit form.
     */
    fun toLongString(): String =
        segments.joinToString(", ") { it.toLongString() }
}


/* ============================================================
 * DiceExpression — full pipe-separated expression
 * ============================================================
 *
 * A DiceExpression is the top-level evaluatable dice structure.
 * Each DiceGroup is rolled independently; their totals may be
 * logged or displayed separately depending on usage.
 */
data class DiceExpression(
    val groups: List<DiceGroup>
) {

    /**
     * Check for valie Expressions
     */    
    fun isValid(): Boolean =
        groups.isNotEmpty() && groups.all { it.isValid() }

    /**
     * Rolls all groups and returns the summed result.
     */
    fun rollTotal(): Int =
        groups.sumOf { it.rollTotal() }

    /**
     * Formatted with minimization rules applied.
     */
    fun toShortString(): String =
        groups.joinToString("|") { it.toShortString() }

    /**
     * Long, fully explicit form.
     */
    fun toLongString(): String =
        groups.joinToString(" | ") { it.toLongString() }

    override fun toString(): String = toShortString()

    companion object {

        /**
         * Regex for parsing a single DiceSegment.
         *
         * Grammar:
         *   [N x] [X] d Y [ +/- Z]
         *
         * Examples matched:
         *   d8
         *   2d6+1
         *   4x 2d4-1
         */
        private val SEGMENT_REGEX =
            Regex(
                """^\s*(?:(\d+)x\s*)?(\d*)d(\d+)([+-]\d+)?\s*$""",
                RegexOption.IGNORE_CASE
            )

        /**
         * Parses a single DiceSegment string.
         */
        fun parseSegment(raw: String): DiceSegment? {
            val match = SEGMENT_REGEX.matchEntire(raw) ?: return null

            val n = match.groupValues[1].toIntOrNull() ?: 1
            val x = match.groupValues[2]
                .takeIf { it.isNotEmpty() }
                ?.toInt() ?: 1
            val y = match.groupValues[3].toInt()
            val z = match.groupValues[4].toIntOrNull() ?: 0

            return DiceSegment(
                repeat = n,
                roll = DiceRoll(
                    diceCount = x,
                    sides = y,
                    modifier = z
                )
            )
        }

        /**
         * Parses a full DiceExpression string.
         *
         * Rules:
         * - '|' separates DiceGroups
         * - ',' separates DiceSegments within a group
         */
        fun parse(raw: String): DiceExpression {
            val groups = raw.split("|")
                .map { groupStr ->
                    val segments = groupStr.split(",")
                        .mapNotNull { parseSegment(it.trim()) }
                    DiceGroup(segments)
                }
            return DiceExpression(groups)
        }
    }
}
