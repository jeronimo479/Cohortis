package com.example.cohortis

import kotlin.random.Random

/**
 * Utility object for parsing and rolling dice expressions.
 * Supports standard dice notation (XdY) and complex combos ([N*|Nx]XdY[+Z|-Z]).
 */
object DiceRoller {

    /**
     * Represents a single dice expression.
     * @property diceCount Number of dice to roll (X).
     * @property sides Number of sides on each die (Y).
     * @property modifier Fixed value added to the total (Z).
     */
    data class DiceExpr(
        val diceCount: Int,
        val sides: Int,
        val modifier: Int = 0
    )

    // Grammar: [N*|Nx]XdY[+Z|-Z]
    private val comboRegex = Regex(
        pattern = """^(?:(\d+)[*x])?(\d*)d(\d+)([+-]\d+)?$""",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Rolls the specified [expr] and returns the sum.
     *
     * @param expr The dice expression to roll.
     * @return The resulting total including the modifier.
     */
    fun rollDice(expr: DiceExpr): Int {
        var total = 0
        repeat(expr.diceCount) {
            total += Random.nextInt(1, expr.sides + 1)
        }
        return total + expr.modifier
    }

    /**
     * Formats a [DiceExpr] back into a standard dice notation string (e.g., "2d8+4").
     *
     * @param expr The dice expression to format.
     * @return A string representation of the expression.
     */
    fun formatExpr(expr: DiceExpr): String {
        val base = if (expr.diceCount == 1)
            "d${expr.sides}"
        else
            "${expr.diceCount}d${expr.sides}"

        return when {
            expr.modifier > 0 -> "$base+${expr.modifier}"
            expr.modifier < 0 -> "$base${expr.modifier}"
            else -> base
        }
    }

    /**
     * Parses a string "combo" into a repeat count and a [DiceExpr].
     * Example: "2x 1d8+4" results in (2, DiceExpr(1, 8, 4)).
     *
     * @param combo The string to parse.
     * @return A Pair containing the repeat count and the dice expression, or null if invalid.
     */
    fun parseCombo(combo: String): Pair<Int, DiceExpr>? {
        // Clean the string of all spaces to be backwards compatible with strings like "1 x 2 d 8 + 4"
        val cleaned = combo.replace(" ", "")
        val match = comboRegex.matchEntire(cleaned) ?: return null
        val repeatCount = match.groupValues[1].toIntOrNull() ?: 1
        val diceCount = match.groupValues[2].toIntOrNull() ?: 1
        val sides = match.groupValues[3].toInt()
        val modifier = match.groupValues[4].toIntOrNull() ?: 0
        return repeatCount to DiceExpr(diceCount, sides, modifier)
    }

    /**
     * Data class to hold attack roll results.
     * @property d20 The result of the d20 'to-hit' roll.
     * @property damageExpr The formatted damage expression rolled.
     * @property damageTotal The total damage result.
     */
    data class AttackResult(
        val d20: Int,
        val damageExpr: String,
        val damageTotal: Int
    )

    /**
     * Parses and rolls a segment of damage combos (e.g., "1d8 | 1d6").
     * Performs a d20 roll for each attack in the segment.
     *
     * @param segment A pipe or comma separated string of dice combos.
     * @return A list of [AttackResult] objects containing roll details.
     */
    fun rollDamageSegmentDetailed(segment: String): List<AttackResult> {
        val results = mutableListOf<AttackResult>()
        // Split by pipe or comma
        segment.split(Regex("[,|]"))
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { combo ->
                val parsed = parseCombo(combo)
                if (parsed != null) {
                    val (repeatCount, expr) = parsed
                    repeat(repeatCount) {
                        results.add(
                            AttackResult(
                                d20 = Random.nextInt(1, 21),
                                damageExpr = formatExpr(expr),
                                damageTotal = rollDice(expr)
                            )
                        )
                    }
                }
            }
        return results
    }
    
    /**
     * Rolls a single segment and returns the total sum of all dice.
     * Typically used for Hit Point rolls.
     *
     * @param segment A pipe or comma separated string of dice combos.
     * @return The total sum, with a minimum value of 1.
     */
    fun rollSegmentTotal(segment: String): Int {
        var total = 0
        segment.split(Regex("[,|]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { combo ->
                val parsed = parseCombo(combo)
                if (parsed != null) {
                    val (repeatCount, expr) = parsed
                    repeat(repeatCount) {
                        total += rollDice(expr)
                    }
                }
            }
        return if (total < 1) 1 else total
    }
}
