package io.github.w3bray.calc

import java.math.BigDecimal

/**
 * The calculator's input state plus the key-handling rules of a normal phone calculator:
 * leading zeros, one dot per number, operator replacement, unary minus, balanced ")",
 * "digit after = starts over / operator after = continues from the result", error state.
 *
 * Pure Kotlin (unit-tested). CalcView only draws this state and forwards button presses.
 */
class CalcInput(private val maxLength: Int = Config.MAX_EXPR_LENGTH) {

    enum class Event { NONE, EVALUATED, ERROR, CLEARED }

    var expr: String = ""
        private set

    /** True right after "=": a digit then starts a new expression, an operator continues from the result. */
    var justEvaluated: Boolean = false
        private set

    var error: Boolean = false
        private set

    /** Full digits of the last result (the display may use scientific notation). */
    private var resultPlain: String? = null

    fun displayText(errorText: String): String = when {
        error -> errorText
        expr.isEmpty() -> "0"
        else -> expr
    }

    /** Handles one button label ("7", "+", "(", "⌫", "C", "=", ...). */
    fun press(label: String): Event {
        when (label) {
            "C" -> { clear(); return Event.CLEARED }
            "=" -> return evaluate()
        }
        if (error) clear() // any other key after an error starts a fresh expression
        when (label) {
            "⌫", "DEL" -> backspace()
            else -> {
                val ch = label.singleOrNull() ?: return Event.NONE
                when {
                    ch.isDigit() -> typeDigit(ch)
                    ch == '.' -> typeDot()
                    ch == '(' -> typeOpen()
                    ch == ')' -> typeClose()
                    ch == '-' -> typeMinus()
                    ch == '+' || ch == '*' || ch == '/' -> typeOperator(ch)
                }
            }
        }
        return Event.NONE
    }

    fun restore(expr: String, justEvaluated: Boolean, error: Boolean) {
        this.expr = expr
        this.justEvaluated = justEvaluated
        this.error = error
        resultPlain = if (justEvaluated) plainDigits(expr) else null
    }

    private fun clear() {
        expr = ""
        justEvaluated = false
        error = false
        resultPlain = null
    }

    private val last: Char?
        get() = expr.lastOrNull()

    private fun isOperator(c: Char?): Boolean = c != null && c in "+-*/"

    private fun hasRoom(): Boolean = expr.length < maxLength

    /** The digits/dot run at the end of the expression, i.e. the number being typed. */
    private fun currentNumber(): String = expr.takeLastWhile { it.isDigit() || it == '.' }

    private fun startFreshIfJustEvaluated() {
        if (justEvaluated) {
            expr = ""
            justEvaluated = false
            resultPlain = null
        }
    }

    private fun continueFromResultIfJustEvaluated() {
        if (justEvaluated) {
            expr = resultPlain ?: expr
            justEvaluated = false
            resultPlain = null
        }
    }

    private fun typeDigit(ch: Char) {
        startFreshIfJustEvaluated()
        if (currentNumber() == "0") expr = expr.dropLast(1) // "0" then "5" shows "5", not "05"
        if (hasRoom()) expr += ch
    }

    private fun typeDot() {
        startFreshIfJustEvaluated()
        val number = currentNumber()
        if ('.' in number) return // one dot per number
        if (!hasRoom()) return
        expr += if (number.isEmpty()) "0." else "."
    }

    private fun typeOpen() {
        startFreshIfJustEvaluated()
        if (hasRoom()) expr += '('
    }

    private fun typeClose() {
        if (justEvaluated) return
        val unclosed = expr.count { it == '(' } - expr.count { it == ')' }
        if (unclosed <= 0) return
        if (last == null || last == '(' || isOperator(last)) return
        if (hasRoom()) expr += ')'
    }

    private fun typeMinus() {
        continueFromResultIfJustEvaluated()
        if (last == '-') return // never "--"
        if (hasRoom()) expr += '-' // subtraction after a number / ")", unary minus elsewhere
    }

    private fun typeOperator(ch: Char) {
        continueFromResultIfJustEvaluated()
        while (isOperator(last)) expr = expr.dropLast(1) // "5*-" then "+" becomes "5+"
        if (expr.isEmpty() || last == '(') return
        if (hasRoom()) expr += ch
    }

    private fun backspace() {
        if (justEvaluated) {
            clear()
            return
        }
        expr = expr.dropLast(1)
    }

    private fun evaluate(): Event {
        if (error) {
            clear()
            return Event.NONE
        }
        if (justEvaluated || expr.isEmpty()) return Event.NONE
        val result = Evaluator.evaluate(expr)
        return if (result == null) {
            expr = ""
            error = true
            justEvaluated = false
            resultPlain = null
            Event.ERROR
        } else {
            expr = result.display
            resultPlain = result.plain
            justEvaluated = true
            Event.EVALUATED
        }
    }

    private fun plainDigits(text: String): String = try {
        BigDecimal(text).toPlainString()
    } catch (e: NumberFormatException) {
        text
    }
}
