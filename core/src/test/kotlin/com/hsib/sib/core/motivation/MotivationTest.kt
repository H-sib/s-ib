package com.hsib.sib.core.motivation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotivationTest {
    @Test fun daysPickTheRightPool() {
        assertEquals(Motivation.Bucket.OVERDUE, Motivation.bucket(-3))
        assertEquals(Motivation.Bucket.TODAY, Motivation.bucket(0))
        assertEquals(Motivation.Bucket.TOMORROW, Motivation.bucket(1))
        assertEquals(Motivation.Bucket.FEW_DAYS, Motivation.bucket(3))
        assertEquals(Motivation.Bucket.THIS_WEEK, Motivation.bucket(7))
        assertEquals(Motivation.Bucket.TWO_WEEKS, Motivation.bucket(14))
        assertEquals(Motivation.Bucket.FAR, Motivation.bucket(15))
    }

    @Test fun thousandsOfDifferentLinesExist() {
        assertTrue(Motivation.possibleLines() >= 2000)
    }

    @Test fun theSameDayGivesTheSameLineAndAnotherDayAnotherOne() {
        val a = Motivation.line(2, "deadline-1", 20000)
        assertEquals(a, Motivation.line(2, "deadline-1", 20000))
        val week = (0 until 30).map { Motivation.line(10, "deadline-1", 20000L + it) }.toSet()
        assertTrue("only ${week.size} different lines in 30 days", week.size >= 20)
    }

    @Test fun differentDeadlinesDoNotAllGetTheSameLine() {
        val lines = (0 until 40).map { Motivation.line(5, "id-$it", 20000) }.toSet()
        assertTrue(lines.size >= 25)
    }

    @Test fun everyLineFollowsTheWritingRules() {
        val justWord = Regex("\bjust\b", RegexOption.IGNORE_CASE)
        for (d in listOf(-5L, 0L, 1L, 3L, 6L, 12L, 40L)) for (k in 0 until 400) {
            for (flags in listOf(false to false, true to false, false to true)) for (g in Motivation.Group.values()) {
                val line = Motivation.line(d, "k$k", 20000L + k, flags.first, flags.second, g)
                assertFalse(line, justWord.containsMatchIn(line) || line.any { ch -> ch == '!' || ch.code == 0x2013 || ch.code == 0x2014 })
                assertTrue(line.endsWith("."))
            }
        }
    }

    @Test fun eachKindOfDeadlineGetsItsOwnTalk() {
        val exam = (0 until 300).map { Motivation.line(5, "k$it", 20000L, group = Motivation.Group.EXAM) }.toSet()
        val bill = (0 until 300).map { Motivation.line(5, "k$it", 20000L, group = Motivation.Group.BILL) }.toSet()
        assertTrue((exam - bill).isNotEmpty() && (bill - exam).isNotEmpty())
        assertTrue(exam.any { it.contains("past paper", ignoreCase = true) || it.contains("revis", ignoreCase = true) })
        assertTrue(bill.any { it.contains("pay", ignoreCase = true) })
    }

    @Test fun closeOnesAreWarmAndNeverSarcastic() {
        val lines = (0 until 200).map { Motivation.line(-3, "k$it", 20000L, group = Motivation.Group.CLOSE) }
        assertTrue(lines.none { it.contains("late", ignoreCase = true) && it.contains("verdict") })
        assertTrue(lines.toSet().size >= 60)
    }

    @Test fun categoriesMapToGroups() {
        assertEquals(Motivation.Group.ASSIGNMENT, Motivation.groupFor(com.hsib.sib.core.model.Category.ASSIGNMENT))
        assertEquals(Motivation.Group.EXAM, Motivation.groupFor(com.hsib.sib.core.model.Category.PROF))
        assertEquals(Motivation.Group.CLOSE, Motivation.groupFor(com.hsib.sib.core.model.Category.ANNIVERSARY))
        assertEquals(Motivation.Group.BILL, Motivation.groupFor(com.hsib.sib.core.model.Category.BILL))
    }

    @Test fun aNameIsUsedInSomeLinesAndNeverBreaksTheRules() {
        val lines = (0 until 300).map { Motivation.line(5, "k$it", 20000L, name = "Rafi") }
        val named = lines.count { it.startsWith("Rafi, ") }
        assertTrue("named $named of 300", named in 60..140)
        assertTrue(lines.none { it.contains("Rafi, Rafi") })
        assertTrue(lines.filter { it.startsWith("Rafi, ") }.all { it.length > 12 && it.endsWith(".") })
        assertEquals(Motivation.line(5, "k1", 20000L), Motivation.line(5, "k1", 20000L, name = null))
        assertEquals(Motivation.line(5, "k1", 20000L), Motivation.line(5, "k1", 20000L, name = "  "))
    }

    @Test fun finishingGetsAKindLineThatFollowsTheRules() {
        val lines = (0 until 200).map { Motivation.doneLine("k$it", 20000L, "Rafi") }.toSet()
        assertTrue("distinct ${lines.size}", lines.size >= 10)
        assertTrue(lines.filter { it.contains("!") || it.contains("just", true) }.toString(), lines.none { it.contains("!") || it.contains("just", true) || it.any { ch -> ch.code == 0x2013 || ch.code == 0x2014 } })
        assertTrue("none named: $lines", lines.any { it.startsWith("Rafi, ") })
        assertEquals(Motivation.doneLine("a", 1L), Motivation.doneLine("a", 1L, null))
    }

    @Test fun startedAndMedicalPoolsAreUsed() {
        val plain = (0 until 300).map { Motivation.line(5, "k$it", 20000L) }.toSet()
        val started = (0 until 300).map { Motivation.line(5, "k$it", 20000L, started = true) }.toSet()
        val medical = (0 until 300).map { Motivation.line(5, "k$it", 20000L, medical = true) }.toSet()
        assertTrue((started - plain).isNotEmpty())
        assertTrue((medical - plain).isNotEmpty())
    }
}
