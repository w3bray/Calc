package io.github.w3bray.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keys are given as a string where each character is one button; 'B' stands for the "⌫" button.
 */
class CalcInputTest {

    private fun key(k: Char): String = if (k == 'B') "⌫" else k.toString()

    private fun type(c: CalcInput, keys: String): CalcInput.Event {
        var last = CalcInput.Event.NONE
        for (k in keys) last = c.press(key(k))
        return last
    }

    /** Fresh calculator, the key sequence typed, resulting display text. */
    private fun shown(keys: String, maxLength: Int = Config.MAX_EXPR_LENGTH): String {
        val c = CalcInput(maxLength)
        type(c, keys)
        return c.displayText("Error")
    }

    private fun assertShown(cases: Map<String, String>) {
        val bad = cases.filter { (keys, want) -> shown(keys) != want }
        assertTrue(bad.keys.joinToString("\n") { "$it => ${shown(it)} (expected ${cases[it]})" }, bad.isEmpty())
    }

    // ------------------------------------------------------------------ basics

    @Test fun emptyShowsZeroAndEqualsDoesNothing() {
        val c = CalcInput()
        assertEquals("0", c.displayText("Error"))
        assertEquals(CalcInput.Event.NONE, c.press("="))
        assertEquals("0", c.displayText("Error"))
        assertFalse(c.justEvaluated)
    }

    @Test fun simpleCalculation() {
        val c = CalcInput()
        assertEquals(CalcInput.Event.EVALUATED, type(c, "5+3="))
        assertEquals("8", c.expr)
        assertTrue(c.justEvaluated)
    }

    @Test fun everydayCalculations() = assertShown(mapOf(
        "12+34*2=" to "80", "2*(3+4)=" to "14", "(2+3)*(4-1)=" to "15", "10/4=" to "2.5", "1/3=" to "0.333333333333333",
        "0.1+0.2=" to "0.3", "100-100=" to "0", "5*0.5=" to "2.5", "1.5+2.5=" to "4", "2.(3)=" to "6", "0.5(2)=" to "1",
    ))

    // ------------------------------------------------------------------ after "="

    @Test fun digitAfterResultStartsOver() = assertShown(mapOf(
        "5+3=2" to "2", "5+3=." to "0.", "5+3=(" to "(", "5-5=5" to "5", "5-5=0" to "0", "5-5=05" to "5", "1.5+2.5=.5=" to "0.5",
    ))

    @Test fun operatorAfterResultContinues() {
        val c = CalcInput()
        type(c, "5+3=*2=")
        assertEquals("16", c.expr)
        type(c, "-1=")
        assertEquals("15", c.expr)
        assertShown(mapOf("5+3=+2=" to "10", "5+3=-" to "8-", "5+3=*" to "8*", "5-5=-3=" to "-3", "5+3=)" to "8"))
    }

    @Test fun equalsTwiceDoesNothing() {
        val c = CalcInput()
        assertEquals(CalcInput.Event.EVALUATED, type(c, "5+3="))
        assertEquals(CalcInput.Event.NONE, c.press("="))
        assertEquals("8", c.expr)
        assertTrue(c.justEvaluated)
    }

    @Test fun continuingKeepsTheExactValue() = assertShown(mapOf(
        "1/3=*3=" to "1",                       // not 0.999999999999999
        "1/3=+1=" to "1.33333333333333",
        "2/3=*3=" to "2",
        "1/7=*7=" to "1",
        "10/3=*3=" to "10",
        "0.0000001*0.00000001=*1000000000000000=" to "1",
        "99999999*99999999=-9999999800000000=" to "1", // exact product is 9999999800000001
    ))

    @Test fun hugeAndTinyResultsCanBeContinued() {
        val nine29 = "9".repeat(29)
        assertShown(mapOf(
            "$nine29*$nine29=" to "1E+58",
            "$nine29*$nine29=+" to "1E+58+",
            "$nine29*$nine29=+1" to "1E+58+1",
            "$nine29*$nine29=+1=" to "1E+58",
            "1/3/1${"0".repeat(44)}=" to "3.33333333333333E-45",
            "1/3/1${"0".repeat(44)}=+" to "3.33333333333333E-45+",
            "1/3/1${"0".repeat(44)}=+1=" to "1",
        ))
        val c = CalcInput()
        type(c, "1")
        repeat(20) { type(c, "*1000=") }
        assertEquals("1E+60", c.expr)
        type(c, "*2=")
        assertEquals("2E+60", c.expr)
        val d = CalcInput()
        type(d, "1")
        repeat(56) { type(d, "*0.1=") }
        assertEquals("1E-56", d.expr)
        val e = CalcInput()
        type(e, "1")
        repeat(120) { type(e, "/3=") }
        assertEquals("5.56479837676844E-58", e.expr) // exactly 3^-120, rounded once
        assertTrue(e.expr.length <= Config.MAX_EXPR_LENGTH)
    }

    @Test fun negativeResultThenOperators() {
        val c = CalcInput()
        type(c, "2-5=")
        assertEquals("-3", c.expr)
        type(c, "*2=")
        assertEquals("-6", c.expr)
        type(c, "-")
        assertEquals("-6-", c.expr)
        type(c, "-")
        assertEquals("-6-", c.expr)
        type(c, "4=")
        assertEquals("-10", c.expr)
    }

    // ------------------------------------------------------------------ editing rules

    @Test fun leadingZero() = assertShown(mapOf(
        "0" to "0", "00" to "0", "05" to "5", "0." to "0.", "0.0" to "0.0", "0.05" to "0.05", "-0" to "-0", "-05" to "-5",
        "(05" to "(5", "5+05" to "5+5", "0-05" to "0-5", "100" to "100", "1000" to "1000", "10.05" to "10.05", "00.5" to "0.5",
        "0+0=" to "0", "0.=" to "0", "(0)=" to "0",
    ))

    @Test fun leadingZeroAtMaxLength() {
        val c = CalcInput(maxLength = 3)
        type(c, "1+0")
        assertEquals("1+0", c.expr)
        type(c, "5")
        assertEquals("1+5", c.expr)
    }

    @Test fun dotRules() = assertShown(mapOf(
        "." to "0.", ".." to "0.", ".5" to "0.5", ".5." to "0.5", "5." to "5.", "5.." to "5.", "5.5." to "5.5",
        "5.+3=" to "8", "5.=" to "5", "(5.)=" to "5", "(2)." to "(2)0.", "(2).5=" to "1", "(2+3).5=" to "2.5",
        "5+." to "5+0.", "5+.5=" to "5.5", "5*.5=" to "2.5", "-." to "-0.", "-.5=" to "-0.5", "1..2" to "1.2",
        "5+3=." to "0.",
    ))

    @Test fun dotRespectsMaxLength() {
        val c = CalcInput(maxLength = 5)
        type(c, "1+2+")
        assertEquals("1+2+", c.expr)
        type(c, ".")
        assertEquals("1+2+", c.expr) // "0." needs two characters
        val d = CalcInput(maxLength = 5)
        type(d, "1+2")
        type(d, ".")
        assertEquals("1+2.", d.expr)
    }

    @Test fun operatorRules() = assertShown(mapOf(
        "+" to "0", "*" to "0", "/" to "0", "-" to "-", "--" to "-", "-+" to "0", "+-" to "-",
        "5+" to "5+", "5++" to "5+", "5+-" to "5+-", "5+-3=" to "2", "5-+" to "5+", "5-+3=" to "8", "5*-" to "5*-",
        "5*--" to "5*-", "5*-3=" to "-15", "5*-+" to "5+", "5*-+3=" to "8", "5+*" to "5*", "5*/" to "5/", "5+*/" to "5/",
        "5/-2=" to "-2.5", "5+=" to "5", "5-=" to "5", "5*=" to "5", "5+-=" to "5", "(+" to "(", "(*" to "(", "(-" to "(-",
        "(-5)=" to "-5", "(-+" to "(", "2--3=" to "-1", "2-(-3)=" to "5", "2*-(3)=" to "-6",
    ))

    @Test fun parenthesesRules() = assertShown(mapOf(
        ")" to "0", "5)" to "5", "5+)" to "5+", "(5+)" to "(5+", "(2+)" to "(2+", "(2)" to "(2)", "(2))" to "(2)",
        "()" to "(", "(2+3)4=" to "20", "(2+3)(4=" to "20", "5*()" to "5*(", "5*(2)=" to "10", "((5)+2))" to "((5)+2)",
        "(2*(3+4))=" to "14", "((((2=" to "2", "-(2+3)=" to "-5", "5-(2+3)=" to "0", "(-(-2))=" to "2", "5+3=)" to "8",
    ))

    @Test fun backspace() = assertShown(mapOf(
        "B" to "0", "BB" to "0", "5B" to "0", "5+3B" to "5+", "5+3BBB" to "0", "5+3BBBB" to "0", "0.5BB" to "0",
        "(2)B" to "(2", "5+3=B" to "0", "5+3=BB" to "0", "5+3=B5" to "5", "5+3=B+" to "0", "5+3=+B" to "8",
        "5+3=+B5" to "85", "5+3=+B5=" to "85", "5+3=+B.5=" to "8.5", "5+3=+B+2=" to "10", "5+3=+B=" to "8",
        "1/3=+B5=" to "0.333333333333334", "5-5=+B5=" to "5",
    ))

    @Test fun backspaceIntoScientificResultDropsTheWholeExponent() = assertShown(mapOf(
        "1/7000000=" to "1.42857142857143E-7",
        "1/7000000=+B" to "1.42857142857143E-7",
        "1/7000000=+BB" to "1.42857142857143",      // "…E-" is never shown
        "1/7000000=+BB+1=" to "2.42857142857143",   // the "+" is an addition, not an exponent sign
        "1/7000000=+BB*2=" to "2.85714285714286",
        "1/7000000=+BB=" to "1.42857142857143",
        "1/7000000=+B." to "1.42857142857143E-7",   // no dot inside an exponent
        "${"9".repeat(29)}*${"9".repeat(29)}=+BB" to "1E+5",
        "${"9".repeat(29)}*${"9".repeat(29)}=+BBB" to "1",
        "${"9".repeat(29)}*${"9".repeat(29)}=+BBB+1=" to "2",
        "${"9".repeat(29)}*${"9".repeat(29)}=+BBB-1=" to "0",
        "0.0000001*0.00000001=+BBB" to "1",
        "0.0000001*0.00000001=+BBB*2=" to "2",
        "0.0000001*0.00000001=+BB5=" to "1E-15",     // editing the exponent digit itself still works
    ))

    @Test fun resultsOutsideTheExponentLimitAreAnError() {
        val nine29 = "9".repeat(29)
        val c = CalcInput()
        type(c, "$nine29*$nine29=")
        assertEquals("1E+58", c.expr)
        var last = CalcInput.Event.EVALUATED
        var steps = 0
        while (last == CalcInput.Event.EVALUATED && steps < 400) {
            last = type(c, "*$nine29=")
            steps++
            if (last == CalcInput.Event.EVALUATED) {
                // every displayed result must still be readable by the evaluator
                assertEquals(c.expr, Evaluator.evaluate(c.expr)!!.display)
            }
        }
        assertEquals("overflow expected after ~343 steps, got $steps", CalcInput.Event.ERROR, last)
        assertTrue(c.error)
        val d = CalcInput()
        d.restore("1E+10001", justEvaluated = true, error = false) // not displayable any more, but must not throw
        assertFalse(d.justEvaluated)
    }

    @Test fun backspaceIntoResultDigitsFallsBackToTheDisplayedValue() {
        val c = CalcInput()
        type(c, "1/3=*")
        assertEquals("0.333333333333333*", c.expr)
        type(c, "B")
        assertEquals("0.333333333333333", c.expr)
        type(c, "*3=")
        assertEquals("1", c.expr) // still bound to the exact value
        val d = CalcInput()
        type(d, "1/3=*BB") // edited into the digits: the text is now literal
        assertEquals("0.33333333333333", d.expr)
        type(d, "*3=")
        assertEquals("0.99999999999999", d.expr)
    }

    // ------------------------------------------------------------------ errors and blanks

    @Test fun errorState() {
        val c = CalcInput()
        assertEquals(CalcInput.Event.ERROR, type(c, "5/0="))
        assertTrue(c.error)
        assertEquals("Erro", c.displayText("Erro"))
        type(c, "1")
        assertFalse(c.error)
        assertEquals("1", c.expr)

        type(c, "C5/0=")
        assertTrue(c.error)
        assertEquals(CalcInput.Event.NONE, c.press("="))
        assertFalse(c.error)
        assertEquals("0", c.displayText("Erro"))

        type(c, "C5/0=")
        assertEquals(CalcInput.Event.CLEARED, c.press("C"))
        assertFalse(c.error)
        assertShown(mapOf(
            "5/0=5" to "5", "5/0=+" to "0", "5/0=+5=" to "5", "5/0=." to "0.", "5/0=.5=" to "0.5", "5/0=(" to "(",
            "5/0=-5=" to "-5", "1/(2-2)=" to "Error", "0/0=" to "Error", "5/0=B" to "0", "5/0=B5" to "5",
        ))
    }

    @Test fun incompleteInputIsNotAnError() {
        for (keys in listOf("(=", "-=", "((=", "(-=", "5*(=", "5+(=", "5+(-=", "5+((=")) {
            val c = CalcInput()
            val ev = type(c, keys)
            assertFalse("'$keys' should not be an error", c.error)
            if (keys.startsWith("5")) {
                assertEquals("'$keys'", CalcInput.Event.EVALUATED, ev)
                assertEquals("'$keys'", "5", c.expr)
            } else {
                assertEquals("'$keys'", CalcInput.Event.NONE, ev)
                assertEquals("'$keys'", keys.dropLast(1), c.expr) // text stays as typed
            }
        }
    }

    @Test fun clearResetsEverything() {
        val c = CalcInput()
        type(c, "5+3=")
        assertEquals(CalcInput.Event.CLEARED, c.press("C"))
        assertEquals("", c.expr)
        assertFalse(c.justEvaluated)
        assertFalse(c.error)
        type(c, "+1=")
        assertEquals("1", c.expr) // nothing of the old result leaked into the new expression
    }

    // ------------------------------------------------------------------ length limit

    @Test fun maxLengthIsEnforced() {
        val c = CalcInput(maxLength = 5)
        type(c, "1234567")
        assertEquals("12345", c.expr)
        type(c, "+")
        assertEquals("12345", c.expr)
        type(c, "B+")
        assertEquals("1234+", c.expr)
    }

    @Test fun maxLengthBoundaryWithResults() {
        val c = CalcInput(maxLength = 6)
        type(c, "12345+")
        assertEquals("12345+", c.expr)
        type(c, "3")
        assertEquals("12345+", c.expr)
        type(c, "=")
        assertEquals("12345", c.expr)
        type(c, "*")
        assertEquals("12345*", c.expr) // the operator after a result is always accepted
        type(c, "2")
        assertEquals("12345*", c.expr)
        type(c, "=")
        assertEquals("12345", c.expr)
    }

    // ------------------------------------------------------------------ restore / odd input

    @Test fun restoreKeepsResultSemantics() {
        val c = CalcInput()
        c.restore("8", justEvaluated = true, error = false)
        type(c, "+1=")
        assertEquals("9", c.expr)

        val sci = CalcInput()
        sci.restore("9.9999998E+15", justEvaluated = true, error = false)
        type(sci, "-")
        assertEquals("9.9999998E+15-", sci.expr)
        type(sci, "9999999800000000=")
        assertEquals("0", sci.expr) // the exact value is gone with the process; the displayed digits are used

        val tiny = CalcInput()
        tiny.restore("1E-15", justEvaluated = true, error = false)
        type(tiny, "*1000000000000000=")
        assertEquals("1", tiny.expr)

        val err = CalcInput()
        err.restore("", justEvaluated = false, error = true)
        assertEquals("Error", err.displayText("Error"))
        type(err, "7")
        assertEquals("7", err.expr)

        val partial = CalcInput()
        partial.restore("5+", justEvaluated = false, error = false)
        type(partial, "3=")
        assertEquals("8", partial.expr)
    }

    @Test fun restoreNeverThrowsOnInconsistentInput() {
        val a = CalcInput()
        a.restore("", justEvaluated = true, error = false)
        assertFalse(a.justEvaluated)
        type(a, "+")
        assertEquals("", a.expr)

        val b = CalcInput()
        b.restore("abc", justEvaluated = true, error = false)
        assertFalse(b.justEvaluated)
        assertEquals(CalcInput.Event.ERROR, type(b, "+1="))
        assertTrue(b.error)

        val d = CalcInput()
        d.restore("8", justEvaluated = true, error = true)
        assertTrue(d.error)
        assertFalse(d.justEvaluated)
    }

    @Test fun unknownLabelsAreIgnored() {
        val c = CalcInput()
        assertEquals(CalcInput.Event.NONE, c.press("abc"))
        assertEquals(CalcInput.Event.NONE, c.press("x"))
        assertEquals(CalcInput.Event.NONE, c.press("٣")) // non-ASCII digit
        assertEquals(CalcInput.Event.NONE, c.press("E"))
        assertEquals(CalcInput.Event.NONE, c.press("A"))
        assertEquals("", c.expr)
    }

    private fun previewOf(keys: String): String? {
        val c = CalcInput()
        type(c, keys)
        return c.preview()
    }

    @Test fun previewShowsWhatEqualsWouldGive() {
        val cases = mapOf(
            "12+3" to "15", "2*(3+4" to "14", "10/4" to "2.5", "2(3)" to "6", "(2)3" to "6", "5-8" to "-3",
            "1/3*3" to "1", "0.1+0.2" to "0.3", "12+3*" to "15", "2*(3+4)-" to "14",
        )
        val bad = cases.filter { (k, want) -> previewOf(k) != want }
        assertTrue(bad.keys.joinToString("\n") { "$it => ${previewOf(it)} (expected ${cases[it]})" }, bad.isEmpty())
    }

    @Test fun previewIsAbsentWhenThereIsNothingToShow() {
        for (keys in listOf("", "5", "-5", "(5", "(-5)", "5.", "5+", "5*(", "(", "-", "5/0", "1/(2-2)", "5+3=", "5/0=")) {
            assertEquals("'$keys'", null, previewOf(keys))
        }
    }

    @Test fun previewUsesTheExactAnswerAndDoesNotChangeState() {
        val c = CalcInput()
        type(c, "1/3=*3")
        assertEquals("1", c.preview()) // exact binding, not 0.999999999999999
        assertEquals("0.333333333333333*3", c.expr)
        assertFalse(c.justEvaluated)
        assertEquals(CalcInput.Event.EVALUATED, c.press("="))
        assertEquals("1", c.expr)
    }

    @Test fun eventsAreReportedCorrectly() {
        val c = CalcInput()
        assertEquals(CalcInput.Event.NONE, c.press("5"))
        assertEquals(CalcInput.Event.NONE, c.press("+"))
        assertEquals(CalcInput.Event.NONE, c.press("3"))
        assertEquals(CalcInput.Event.EVALUATED, c.press("="))
        assertEquals(CalcInput.Event.NONE, c.press("="))
        assertEquals(CalcInput.Event.CLEARED, c.press("C"))
        assertEquals(CalcInput.Event.ERROR, type(c, "1/0="))
        assertEquals(CalcInput.Event.NONE, c.press("⌫"))
        assertEquals(CalcInput.Event.NONE, c.press("DEL"))
    }
}
