package com.example.cohortis

import kotlin.random.Random

object DiceRoller {

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

    fun rollDice(expr: DiceExpr): Int {
        var total = 0
        repeat(expr.diceCount) {
            total += Random.nextInt(1, expr.sides + 1)
        }
        return total + expr.modifier
    }

    fun formatExpr(expr: DiceExpr): String {
        val base = if (expr.diceCount == 1) "d${expr.sides}" else "${expr.diceCount}d${expr.sides}"
        return when {
            expr.modifier > 0 -> "$base+${expr.modifier}"
            expr.modifier < 0 -> "$base${expr.modifier}"
            else -> base
        }
    }

    fun parseCombo(combo: String): Pair<Int, DiceExpr>? {
        val match = comboRegex.matchEntire(combo) ?: return null
        val repeatCount = match.groupValues[1].toIntOrNull() ?: 1
        val diceCount = match.groupValues[2].toIntOrNull() ?: 1
        val sides = match.groupValues[3].toInt()
        val modifier = match.groupValues[4].toIntOrNull() ?: 0
        return repeatCount to DiceExpr(diceCount, sides, modifier)
    }

    /**
     * Data class to hold attack roll results.
     */
    data class AttackResult(
        val d20: Int,
        val damageExpr: String,
        val damageTotal: Int
    )

    /**
     * Parses and rolls a segment of damage combos.
     * Returns a list of individual attack results.
     */
    fun rollDamageSegmentDetailed(segment: String): List<AttackResult> {
        val results = mutableListOf<AttackResult>()
        segment.split(Regex("[,\\s]+"))
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { combo ->
                val parsed = parseCombo(combo)
                if (parsed != null) {
                    val (repeatCount, expr) = parsed
                    repeat(repeatCount) {
                        results.add(AttackResult(
                            d20 = Random.nextInt(1, 21),
                            damageExpr = formatExpr(expr),
                            damageTotal = rollDice(expr)
                        ))
                    }
                }
            }
        return results
    }
}
