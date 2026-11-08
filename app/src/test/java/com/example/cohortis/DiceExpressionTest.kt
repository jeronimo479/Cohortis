package com.example.cohortis

import com.example.cohortis.dice.DiceExpression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceExpressionTest {

    @Test
    fun testParseSimple() {
        val expr = DiceExpression.parse("1d8")
        assertEquals(1, expr.groups.size)
        assertEquals(1, expr.groups[0].segments.size)
        assertEquals(1, expr.groups[0].segments[0].roll.diceCount)
        assertEquals(8, expr.groups[0].segments[0].roll.sides)
        assertEquals(0, expr.groups[0].segments[0].roll.modifier)
    }

    @Test
    fun testParseComplex() {
        // "3d8,d6 | d4,d5+2 | 4x 2d4+1"
        val expr = DiceExpression.parse("3d8,d6 | d4,d5+2 | 4x 2d4+1")
        assertEquals(3, expr.groups.size)
        
        // Group 0: 3d8, d6
        assertEquals(2, expr.groups[0].segments.size)
        assertEquals(3, expr.groups[0].segments[0].roll.diceCount)
        assertEquals(8, expr.groups[0].segments[0].roll.sides)
        assertEquals(1, expr.groups[0].segments[1].roll.diceCount)
        assertEquals(6, expr.groups[0].segments[1].roll.sides)

        // Group 1: d4, d5+2
        assertEquals(2, expr.groups[1].segments.size)
        assertEquals(1, expr.groups[1].segments[0].roll.diceCount)
        assertEquals(4, expr.groups[1].segments[0].roll.sides)
        assertEquals(1, expr.groups[1].segments[1].roll.diceCount)
        assertEquals(5, expr.groups[1].segments[1].roll.sides)
        assertEquals(2, expr.groups[1].segments[1].roll.modifier)

        // Group 2: 4x 2d4+1
        assertEquals(1, expr.groups[2].segments.size)
        assertEquals(4, expr.groups[2].segments[0].repeat)
        assertEquals(2, expr.groups[2].segments[0].roll.diceCount)
        assertEquals(4, expr.groups[2].segments[0].roll.sides)
        assertEquals(1, expr.groups[2].segments[0].roll.modifier)
    }

    @Test
    fun testRollRange() {
        val expr = DiceExpression.parse("2d4+2")
        repeat(100) {
            val roll = expr.rollTotal()
            assertTrue("Roll $roll out of range [4, 10]", roll in 4..10)
        }
    }

    @Test
    fun testSerialization() {
        val raw = "3d8,d6|d4,d5+2|4x 2d4+1"
        val expr = DiceExpression.parse(raw)
        // Note: toShortString might remove spaces or use specific delimiters
        // The current implementation uses '|' and ',' without extra spaces in toShortString
        val expected = "3d8,d6|d4,d5+2|4x 2d4+1"
        assertEquals(expected, expr.toShortString())
    }
}
