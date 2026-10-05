package io.github.w3bray.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvaluatorTest {

    private fun eval(expr: String): String? = Evaluator.evaluate(expr)?.display

    @Test fun basicArithmetic() {
        assertEquals("5", eval("2+3"))
        assertEquals("-1", eval("2-3"))
        assertEquals("6", eval("2*3"))
        assertEquals("2.5", eval("10/4"))
        assertEquals("-3", eval("7-10"))
    }

    @Test fun precedenceAndParentheses() {
        assertEquals("14", eval("2+3*4"))
        assertEquals("20", eval("(2+3)*4"))
        assertEquals("2", eval("((2))"))
        assertEquals("1", eval("8/2/4"))
        assertEquals("2", eval("2/4*4"))
        assertEquals("7", eval("10-2-1"))
    }

    @Test fun decimalsAreExact() {
        assertEquals("0.3", eval("0.1+0.2"))
        assertEquals("0.333333333333333", eval("1/3"))
        assertEquals("1", eval("1/3*3"))
        assertEquals("1", eval(".5+.5"))
        assertEquals("5", eval("5."))
        assertEquals("0.00000000000001", eval("0.0000001*0.0000001")) // 15 digits: still plain
        assertEquals("1E-15", eval("0.0000001*0.00000001")) // 16 digits: scientific
    }

    @Test fun unaryMinusAndPlus() {
        assertEquals("-6", eval("-2*3"))
        assertEquals("-6", eval("2*-3"))
        assertEquals("5", eval("2--3"))
        assertEquals("-5", eval("-5"))
        assertEquals("5", eval("+5"))
        assertEquals("-1", eval("-(2-1)"))
    }

    @Test fun implicitMultiplication() {
        assertEquals("6", eval("2(3)"))
        assertEquals("6", eval("(2)(3)"))
        assertEquals("6", eval("(2)3"))
        assertEquals("20", eval("(2+3)4"))
    }

    @Test fun lenientEndings() {
        assertEquals("5", eval("5+"))
        assertEquals("3", eval("3-"))
        assertEquals("5", eval("5*("))
        assertEquals("5", eval("(2+3"))
        assertEquals("9", eval("((2+3)+4"))
    }

    @Test fun invalidExpressions() {
        assertNull(eval(""))
        assertNull(eval("+"))
        assertNull(eval("("))
        assertNull(eval("()"))
        assertNull(eval("."))
        assertNull(eval("1..2"))
        assertNull(eval("abc"))
        assertNull(eval("5/0"))
        assertNull(eval("1/(2-2)"))
        assertNull(eval(")"))
        assertNull(eval("2*)"))
    }

    @Test fun zeroResults() {
        assertEquals("0", eval("100-100"))
        assertEquals("0", eval("0*5"))
        assertEquals("0", eval("-0"))
        assertEquals("0", eval("0.0"))
    }

    @Test fun bigAndSmallNumbers() {
        assertEquals("1000000000000", eval("1000000*1000000"))
        assertEquals("9.9999998E+15", eval("99999999*99999999"))
        assertEquals("9999999800000000", Evaluator.evaluate("99999999*99999999")!!.plain)
        assertEquals("123456789012345", eval("123456789012345"))
        assertEquals("1.23456789012346E+15", eval("1234567890123456"))
    }

    @Test fun normalizeBalancesAndTrims() {
        assertEquals("(2+3)", Evaluator.normalize("(2+3"))
        assertEquals("5", Evaluator.normalize("5+("))
        assertNull(Evaluator.normalize("+-*/"))
        assertNull(Evaluator.normalize(""))
    }
}
