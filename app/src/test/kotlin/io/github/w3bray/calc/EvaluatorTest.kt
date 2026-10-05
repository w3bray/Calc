package io.github.w3bray.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class EvaluatorTest {

    private fun eval(expr: String): String? = Evaluator.evaluate(expr)?.display

    /** Asserts every (expression -> expected display) pair, reporting all mismatches at once. */
    private fun assertAll(cases: Map<String, String?>) {
        val bad = cases.filter { (e, want) -> eval(e) != want }
        assertTrue(bad.keys.joinToString("\n") { "$it => ${eval(it)} (expected ${cases[it]})" }, bad.isEmpty())
    }

    private fun assertAllInvalid(exprs: List<String>) {
        val accepted = exprs.filter { eval(it) != null }
        assertTrue("should be invalid: ${accepted.map { "$it => ${eval(it)}" }}", accepted.isEmpty())
    }

    // ------------------------------------------------------------------ basics

    @Test fun basicArithmetic() = assertAll(mapOf(
        "2+3" to "5", "2-3" to "-1", "2*3" to "6", "10/4" to "2.5", "7-10" to "-3",
        "100*100" to "10000", "0+0" to "0", "5" to "5",
    ))

    @Test fun precedenceAndAssociativity() = assertAll(mapOf(
        "2+3*4" to "14", "(2+3)*4" to "20", "((2))" to "2", "8/2/4" to "1", "2/4*4" to "2",
        "10-2-1" to "7", "2-3-4" to "-5", "2+3*4-6/2" to "11", "2*3+4*5" to "26", "2*(3+4)*5" to "70",
        "((((((((((((((((((((5))))))))))))))))))))" to "5", "(((2+3)*4)-5)/3" to "5",
    ))

    @Test fun unaryMinusAndPlus() = assertAll(mapOf(
        "-2*3" to "-6", "2*-3" to "-6", "2--3" to "5", "-5" to "-5", "+5" to "5", "-(2-1)" to "-1",
        "--5" to "5", "-+-5" to "5", "2---3" to "-1", "2*--3" to "6", "-(-(-5))" to "-5", "-2(3)" to "-6",
        "2(-3)" to "-6", "(2)-3" to "-1", "(2)(-3)" to "-6", "2+-+-3" to "5", "-2*-3" to "6",
        "-(2+3)*-2" to "10", "2*-3*4" to "-24", "2/-(4)" to "-0.5", "-5--5" to "0", "-(5)" to "-5", "+(5)" to "5",
    ))

    @Test fun implicitMultiplication() = assertAll(mapOf(
        "2(3)" to "6", "(2)(3)" to "6", "(2)3" to "6", "(2+3)4" to "20", "6/2(3)" to "9", "2(3)4" to "24",
        "2*3(4)" to "24", "2(3)+4" to "10", "1/2(3)" to "1.5", "2(3)/4" to "1.5", "(1)(2)(3)(4)" to "24",
        "(2+3)(4+5)" to "45", "2(3+4)" to "14", "(2)3+(4)5" to "26", "2.(3)" to "6", "(2).5" to "1", ".5(2)" to "1",
    ))

    // ------------------------------------------------------------------ exactness (single rounding)

    @Test fun arithmeticIsExact() = assertAll(mapOf(
        "0.1+0.2" to "0.3", "0.1+0.2-0.3" to "0", "1-0.9" to "0.1", "3*1.1" to "3.3",
        "1/3*3-1" to "0", "(1/3)*3-1" to "0", "1/3+1/3+1/3-1" to "0", "1/7*7-1" to "0", "1/9*9-1" to "0",
        "10/3*3-10" to "0", "100/3*3-100" to "0", "2/3-1/3-1/3" to "0", "5/3*3-5" to "0",
        "1/3*3" to "1", "2/3*3" to "2", "1/7*7" to "1", "1/6*6-1" to "0", "10/3*3" to "10",
    ))

    @Test fun longLiteralsAreNotTruncated() = assertAll(mapOf(
        "100000000000000000000+1-100000000000000000000" to "1",
        "12345678901234567890123+1-12345678901234567890123" to "1",
        "123456789012345678901-123456789012345678900" to "1",
        "123456789012345678901+0-123456789012345678900" to "1",
    ))

    @Test fun roundsOnlyOnce() = assertAll(mapOf(
        // exact 885241665251453499998 -> 15 digits 8.85241665251453E+20 (double rounding gave ...454)
        "10314510014*85824887857" to "8.85241665251453E+20",
        "0.123456789012344499995+0" to "0.123456789012344",
        "0.123456789012344499995*1" to "0.123456789012344",
        "0.1234567890123445*1" to "0.123456789012345", // HALF_UP on an exact tie
    ))

    @Test fun quotientsMatchSingleRoundingOfExactValue() {
        val bad = ArrayList<String>()
        for (a in 1..120) for (b in 1..120) {
            val exact = BigDecimal(a).divide(BigDecimal(b), MathContext(60, RoundingMode.HALF_UP))
            val want = exact.round(MathContext(15, RoundingMode.HALF_UP)).stripTrailingZeros()
            val got = Evaluator.evaluate("$a/$b")!!.value.toBigDecimal(15)
            if (want.compareTo(got) != 0) bad += "$a/$b want=$want got=$got"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    // ------------------------------------------------------------------ display formatting

    @Test fun displayFormatting() = assertAll(mapOf(
        "1000000000000000" to "1E+15", "999999999999999+1" to "1E+15", "123456789012345*10" to "1.23456789012345E+15",
        "-1234567890123456" to "-1.23456789012346E+15", "1234567890123456" to "1.23456789012346E+15",
        "123456789012345" to "123456789012345", "99999999999999.9+0.1" to "100000000000000", "999999999999999.9" to "1E+15",
        "0.1234567890123456" to "0.123456789012346", "1/3" to "0.333333333333333", "1/7" to "0.142857142857143",
        "1/70000" to "0.0000142857142857143", "1/700000" to "0.00000142857142857143", "1/7000000" to "1.42857142857143E-7",
        "1/3000000" to "3.33333333333333E-7", "0.00123456789012345" to "0.00123456789012345",
        "0.0000001*0.0000001" to "1E-14", "0.0000001*0.00000001" to "1E-15", "-0.0000001*0.00000001" to "-1E-15",
        "0.000001" to "0.000001", "0.0000001" to "1E-7", "2.50*2" to "5", "1.10*3" to "3.3", "0.1*3" to "0.3",
        "1000000*1000000" to "1000000000000", "99999999*99999999" to "9.9999998E+15",
    ))

    @Test fun zeroResults() = assertAll(mapOf(
        "100-100" to "0", "0*5" to "0", "-0" to "0", "0.0" to "0", "-0.0" to "0", "0." to "0", "00005" to "5", "0005.500" to "5.5",
    ))

    @Test fun formatHelperDirectly() {
        assertEquals("0", Evaluator.format(BigDecimal("0.000"), 15, -6))
        assertEquals("1.5E+20", Evaluator.format(BigDecimal("150000000000000000000"), 15, -6))
        assertEquals("-2.5E-9", Evaluator.format(BigDecimal("-0.0000000025"), 15, -6))
        assertEquals("123.45", Evaluator.format(BigDecimal("123.4500"), 15, -6))
    }

    // ------------------------------------------------------------------ leniency / invalid input

    @Test fun lenientEndings() = assertAll(mapOf(
        "5+" to "5", "3-" to "3", "5*(" to "5", "(2+3" to "5", "((2+3)+4" to "9", "5." to "5", ".5" to "0.5",
        "5.+3" to "8", "0." to "0", "(5+" to "5", "2*(3+" to "6", "((2+3" to "5", "(2+3)-" to "5", "(2+3)(" to "5",
        "5.(" to "5", "5+-*(" to "5",
    ))

    @Test fun invalidExpressions() = assertAllInvalid(listOf(
        "", "+", "(", "()", ".", "1..2", "abc", "5/0", "1/(2-2)", ")", "2*)", "2)+3", ")(", "(2))", "2)", "(2+3))",
        "2+3)", "(())", "2()", "-.", "5+.", "(.)", "2.3.4", "*5", "/5", "(-)", "(*", "   ", "e", "-(", "-", "1a2", "1e5",
        "2E", "2E+", "1E+99999", "1 + 2",
    ))

    @Test fun nothingBlankIsEvaluable() = assertAllInvalid(listOf("(", "((", "-", "(-", "+-*/", "-("))

    @Test fun divisionByZero() {
        assertAllInvalid(listOf(
            "5/0", "1/(2-2)", "1/(0.1-0.1)", "(1/0)+1", "1+2/(3*0)", "0/0", "1/-0", "1/0.0", "1/(1/0)", "2(1/0)",
            "1/(2(0))", "1/0.", "1/(0)", "1/-(0)", "1/((1-1)*5)", "0/0*0", "(5/0)(0)", "1/0+", "1/0*(",
        ))
        assertEquals("1E+56", eval("1/0.00000000000000000000000000000000000000000000000000000001"))
    }

    // ------------------------------------------------------------------ extremes within 60 characters

    @Test fun extremesStayFastAndSane() {
        val nine29 = "9".repeat(29)
        val nine58 = "9".repeat(58)
        val t0 = System.nanoTime()
        assertAll(mapOf(
            "9".repeat(60) to "1E+60",
            "$nine29*$nine29" to "1E+58",
            "$nine58/3" to "3.33333333333333E+57",
            "1/$nine58" to "1E-58",
            "(${"9".repeat(28)})(${"9".repeat(28)})" to "1E+56",
            "0.0000001*0.0000001*0.0000001*0.0000001*0.0000001*0.0000001" to "1E-42",
            "9999999999(9999999999)(9999999999)(9999999999)(9999999999)" to "9.999999995E+49",
            "$nine58*0" to "0",
            "$nine29/$nine29" to "1",
            "$nine29-$nine29" to "0",
            "1/3/3/3/3/3/3/3/3/3/3/3/3/3/3/3/3/3/3/3/3/3/3/3/3/3/3/3/3/3/3" to "4.85693574961886E-15", // 1/3^30
        ))
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("too slow: $ms ms", ms < 2000)
    }

    // ------------------------------------------------------------------ scientific notation input & answers

    @Test fun displayStringsCanBeEvaluatedAgain() {
        val exprs = listOf(
            "1/3", "1/7", "99999999*99999999", "0.0000001*0.00000001", "-0.0000001*0.00000001", "1234567890123456",
            "-1234567890123456", "1/3000000", "1/70000", "2/3", "10/4", "1000000*1000000", "1/3*3-1", "0.1+0.2", "-5", "100-100",
            "${"9".repeat(29)}*${"9".repeat(29)}", "1/${"9".repeat(58)}",
        )
        for (e in exprs) {
            val first = Evaluator.evaluate(e)!!
            val again = Evaluator.evaluate(first.display)
            assertNotNull("display of $e not evaluable: ${first.display}", again)
            assertEquals("display of $e changed on re-evaluation", first.display, again!!.display)
        }
    }

    @Test fun scientificNotationInput() = assertAll(mapOf(
        "1E+15" to "1E+15", "1.5E+15*2" to "3E+15", "1E-7*10" to "0.000001", "1E+5+1" to "100001", "2E3" to "2000",
        "1E-15*1000000000000000" to "1", "1.23456789012346E+15-1234567890123460" to "0", "1E+10000" to "1E+10000",
    ))

    @Test fun answerTokenUsesTheExactValue() {
        val third = Evaluator.evaluate("1/3")!!
        assertEquals("0.333333333333333", third.display)
        assertEquals("1", Evaluator.evaluate("A*3", third.value)!!.display)
        assertEquals("1.33333333333333", Evaluator.evaluate("A+1", third.value)!!.display)
        assertEquals("0.666666666666667", Evaluator.evaluate("2A", third.value)!!.display)
        assertEquals("0.333333333333333", Evaluator.evaluate("A", third.value)!!.display)
        assertNull(Evaluator.evaluate("A+1", null))
        assertNull(Evaluator.evaluate("AA+1", null))
    }

    // ------------------------------------------------------------------ helpers

    @Test fun normalizeBalancesAndTrims() {
        assertEquals("(2+3)", Evaluator.normalize("(2+3"))
        assertEquals("5", Evaluator.normalize("5+("))
        assertNull(Evaluator.normalize("+-*/"))
        assertNull(Evaluator.normalize(""))
        assertNull(Evaluator.normalize("(("))
    }

    @Test fun isBlankAndIsAccepted() {
        assertTrue(Evaluator.isBlank(""))
        assertTrue(Evaluator.isBlank("("))
        assertTrue(Evaluator.isBlank("-"))
        assertTrue(Evaluator.isBlank("(-"))
        assertFalse(Evaluator.isBlank("5+"))
        assertFalse(Evaluator.isBlank("()"))
        assertTrue(Evaluator.isAccepted("1+2*(3)/4-5.6"))
        assertTrue(Evaluator.isAccepted("1E+5"))
        assertTrue(Evaluator.isAccepted("A"))
        assertFalse(Evaluator.isAccepted("1a2"))
        assertFalse(Evaluator.isAccepted("1 +2"))
    }

    @Test fun valueArithmetic() {
        val half = Evaluator.evaluate("1/2")!!.value
        val third = Evaluator.evaluate("1/3")!!.value
        assertEquals(Evaluator.evaluate("5/6")!!.value, half + third)
        assertEquals(Evaluator.evaluate("1/6")!!.value, half - third)
        assertEquals(Evaluator.evaluate("1/6")!!.value, half * third)
        assertEquals(Evaluator.evaluate("3/2")!!.value, half / third)
        assertEquals(Evaluator.evaluate("-1/2")!!.value, -half)
        assertTrue((half - half).isZero)
        assertEquals("0.5", half.toBigDecimal(15).toPlainString())
        assertEquals("1/3", third.toString())
    }
}
