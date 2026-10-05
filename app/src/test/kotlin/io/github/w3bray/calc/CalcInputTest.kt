package io.github.w3bray.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcInputTest {

    private fun type(input: CalcInput, keys: String): CalcInput.Event {
        var last = CalcInput.Event.NONE
        for (k in keys) last = input.press(k.toString())
        return last
    }

    @Test fun emptyShowsZero() {
        val c = CalcInput()
        assertEquals("0", c.displayText("Error"))
        assertEquals(CalcInput.Event.NONE, c.press("="))
        assertEquals("0", c.displayText("Error"))
    }

    @Test fun simpleCalculation() {
        val c = CalcInput()
        assertEquals(CalcInput.Event.EVALUATED, type(c, "5+3="))
        assertEquals("8", c.expr)
        assertTrue(c.justEvaluated)
    }

    @Test fun digitAfterResultStartsOver() {
        val c = CalcInput()
        type(c, "5+3=")
        type(c, "2")
        assertEquals("2", c.expr)
        assertFalse(c.justEvaluated)
    }

    @Test fun operatorAfterResultContinues() {
        val c = CalcInput()
        type(c, "5+3=*2=")
        assertEquals("16", c.expr)
        type(c, "-1=")
        assertEquals("15", c.expr)
    }

    @Test fun equalsTwiceDoesNothing() {
        val c = CalcInput()
        type(c, "5+3==")
        assertEquals("8", c.expr)
    }

    @Test fun leadingZeroIsReplaced() {
        val c = CalcInput()
        type(c, "05")
        assertEquals("5", c.expr)
        type(c, "C00")
        assertEquals("0", c.expr)
        type(c, "C0.5")
        assertEquals("0.5", c.expr)
        type(c, "C100")
        assertEquals("100", c.expr)
    }

    @Test fun dotRules() {
        val c = CalcInput()
        type(c, "1..2")
        assertEquals("1.2", c.expr)
        type(c, "C.5")
        assertEquals("0.5", c.expr)
        type(c, "C5+.")
        assertEquals("5+0.", c.expr)
        type(c, "C(2).")
        assertEquals("(2)0.", c.expr)
        type(c, "C5+3=.")
        assertEquals("0.", c.expr)
    }

    @Test fun operatorReplacement() {
        val c = CalcInput()
        type(c, "5*+")
        assertEquals("5+", c.expr)
        type(c, "C5*-+")
        assertEquals("5+", c.expr)
        type(c, "C+")
        assertEquals("", c.expr)
        type(c, "C(+")
        assertEquals("(", c.expr)
        type(c, "C5+*/")
        assertEquals("5/", c.expr)
    }

    @Test fun minusRules() {
        val c = CalcInput()
        type(c, "-5=")
        assertEquals("-5", c.expr)
        type(c, "C5*-3=")
        assertEquals("-15", c.expr)
        type(c, "C5--")
        assertEquals("5-", c.expr)
        type(c, "C(-2)*3=")
        assertEquals("-6", c.expr)
    }

    @Test fun parenthesesRules() {
        val c = CalcInput()
        type(c, ")")
        assertEquals("", c.expr)
        type(c, "C(2+)")
        assertEquals("(2+", c.expr)
        type(c, "C(2)")
        assertEquals("(2)", c.expr)
        type(c, "C(2))")
        assertEquals("(2)", c.expr)
        type(c, "C()")
        assertEquals("(", c.expr)
        type(c, "C(2+3)4=")
        assertEquals("20", c.expr)
    }

    @Test fun backspace() {
        val c = CalcInput()
        type(c, "12")
        c.press("⌫")
        assertEquals("1", c.expr)
        c.press("⌫")
        c.press("⌫")
        assertEquals("", c.expr)
        type(c, "5+3=")
        c.press("⌫")
        assertEquals("", c.expr)
        assertFalse(c.justEvaluated)
    }

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
        c.press("=")
        assertFalse(c.error)
        assertEquals("0", c.displayText("Erro"))

        type(c, "C5/0=")
        assertEquals(CalcInput.Event.CLEARED, c.press("C"))
        assertFalse(c.error)
    }

    @Test fun clearResetsEverything() {
        val c = CalcInput()
        type(c, "5+3=")
        assertEquals(CalcInput.Event.CLEARED, c.press("C"))
        assertEquals("", c.expr)
        assertFalse(c.justEvaluated)
        assertFalse(c.error)
    }

    @Test fun maxLengthIsEnforced() {
        val c = CalcInput(maxLength = 5)
        type(c, "1234567")
        assertEquals("12345", c.expr)
        type(c, "+")
        assertEquals("12345", c.expr)
        c.press("⌫")
        type(c, "+")
        assertEquals("1234+", c.expr)
    }

    @Test fun continuingFromScientificResultUsesPlainDigits() {
        val c = CalcInput()
        type(c, "99999999*99999999=")
        assertEquals("9.9999998E+15", c.expr)
        type(c, "-")
        assertEquals("9999999800000000-", c.expr)
        type(c, "9999999800000000=")
        assertEquals("0", c.expr)
    }

    @Test fun restoreKeepsResultSemantics() {
        val c = CalcInput()
        c.restore("8", justEvaluated = true, error = false)
        type(c, "+1=")
        assertEquals("9", c.expr)

        c.restore("", justEvaluated = false, error = true)
        assertEquals("Error", c.displayText("Error"))
        type(c, "7")
        assertEquals("7", c.expr)
    }

    @Test fun unknownLabelsAreIgnored() {
        val c = CalcInput()
        assertEquals(CalcInput.Event.NONE, c.press("abc"))
        assertEquals(CalcInput.Event.NONE, c.press("x"))
        assertEquals("", c.expr)
    }
}
