package io.github.w3bray.calc

/**
 * The calculator's input state plus the key-handling rules of a normal phone calculator:
 * leading zeros, one dot per number, operator replacement, unary minus, balanced ")",
 * "digit after = starts over / operator after = continues from the result", error state.
 *
 * Continuing from a result keeps the EXACT value of that result (1/3 = then *3 = gives 1), while
 * the display keeps showing the short rounded text. Pure Kotlin (unit-tested); CalcView only
 * draws this state and forwards button presses.
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

    /**
     * Exact value of the last result and the text it is displayed with. While [expr] still starts
     * with that text, evaluation substitutes the exact value for it.
     */
    private var answer: Evaluator.Value? = null
    private var answerText: String? = null

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
                    ch in '0'..'9' -> typeDigit(ch)
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

    /** Restores state saved by the activity. The exact value is gone, so the displayed digits are reused. */
    fun restore(expr: String, justEvaluated: Boolean, error: Boolean) {
        this.expr = expr
        this.justEvaluated = justEvaluated && !error
        this.error = error
        answer = null
        answerText = null
        if (this.justEvaluated) {
            val result = Evaluator.evaluate(expr)
            if (result != null) {
                answer = result.value
                answerText = expr
            } else {
                this.justEvaluated = false
            }
        }
    }

    private fun clear() {
        expr = ""
        justEvaluated = false
        error = false
        answer = null
        answerText = null
    }

    private val last: Char?
        get() = expr.lastOrNull()

    private fun isOperator(c: Char?): Boolean = c != null && Evaluator.isOperator(c)

    private fun fits(extraChars: Int): Boolean = expr.length + extraChars <= maxLength

    /**
     * The number token at the end of the expression: digits/dot, plus an exponent part when the
     * text is a scientific result such as "1.42857142857143E-7" (only results carry an 'E').
     */
    private fun currentNumber(): String = NUMBER_AT_END.find(expr)?.value ?: ""

    /**
     * Backspacing inside a scientific result can leave "…E", "…E+" or "…E-", text no key can
     * produce; drop the dangling marker so the next key is never swallowed as an exponent.
     */
    private fun trimDanglingExponent() {
        when {
            expr.endsWith("E+") || expr.endsWith("E-") -> expr = expr.dropLast(2)
            expr.endsWith("E") -> expr = expr.dropLast(1)
        }
    }

    /** After "=", digits, "." and "(" start a new expression. */
    private fun startFreshIfJustEvaluated() {
        if (justEvaluated) {
            expr = ""
            justEvaluated = false
            answer = null
            answerText = null
        }
        // "8" "+" "⌫" then "5": the user is now editing the digits of the result into "85",
        // so the text no longer stands for the exact value.
        if (expr == answerText) {
            answer = null
            answerText = null
        }
    }

    /** The exact value applies only while the result text is followed by an operator (or nothing). */
    private fun boundAnswer(): Evaluator.Value? {
        val value = answer ?: return null
        val text = answerText ?: return null
        if (!expr.startsWith(text)) return null
        if (expr.length > text.length && !isOperator(expr[text.length])) return null
        return value
    }

    /** After "=", an operator continues from the result; the exact value stays bound to its text. */
    private fun continueFromResultIfJustEvaluated(): Boolean {
        if (!justEvaluated) return false
        justEvaluated = false
        return true
    }

    private fun typeDigit(ch: Char) {
        startFreshIfJustEvaluated()
        if (currentNumber() == "0") expr = expr.dropLast(1) // "0" then "5" shows "5", not "05"
        if (fits(1)) expr += ch
    }

    private fun typeDot() {
        startFreshIfJustEvaluated()
        val number = currentNumber()
        if ('.' in number || 'E' in number) return // one dot per number, none inside an exponent
        val add = if (number.isEmpty()) "0." else "."
        if (fits(add.length)) expr += add
    }

    private fun typeOpen() {
        startFreshIfJustEvaluated()
        if (fits(1)) expr += '('
    }

    private fun typeClose() {
        if (justEvaluated) return
        val unclosed = expr.count { it == '(' } - expr.count { it == ')' }
        if (unclosed <= 0) return
        if (last == null || last == '(' || isOperator(last)) return
        if (fits(1)) expr += ')'
    }

    private fun typeMinus() {
        val continued = continueFromResultIfJustEvaluated()
        trimDanglingExponent() // defensive: never let "-" become an exponent sign
        if (last == '-') return // never "--"
        if (continued || fits(1)) expr += '-' // subtraction after a number / ")", unary minus elsewhere
    }

    private fun typeOperator(ch: Char) {
        val continued = continueFromResultIfJustEvaluated()
        trimDanglingExponent() // defensive: never let "+" become an exponent sign
        while (isOperator(last)) expr = expr.dropLast(1) // "5*-" then "+" becomes "5+"
        if (expr.isEmpty() || last == '(') return
        if (continued || fits(1)) expr += ch
    }

    private fun backspace() {
        if (justEvaluated) {
            clear()
            return
        }
        expr = expr.dropLast(1)
        trimDanglingExponent() // "1E+58" ⌫ -> "1E+5" ⌫ -> "1" (the whole exponent goes at once)
        // Editing into the result's own digits means the text no longer stands for the exact value.
        val text = answerText
        if (text != null && !expr.startsWith(text)) {
            answer = null
            answerText = null
        }
    }

    private fun evaluate(): Event {
        if (error) {
            clear()
            return Event.NONE
        }
        if (justEvaluated || expr.isEmpty()) return Event.NONE
        // Only "(" / operators typed so far: nothing to compute yet, keep the text as it is.
        if (Evaluator.isAccepted(expr) && Evaluator.isBlank(expr)) return Event.NONE

        val result = computeResult()
        return if (result == null) {
            expr = ""
            error = true
            justEvaluated = false
            answer = null
            answerText = null
            Event.ERROR
        } else {
            expr = result.display
            answer = result.value
            answerText = result.display
            justEvaluated = true
            Event.EVALUATED
        }
    }

    /** Evaluates [expr] exactly as "=" would (exact answer binding included), without changing state. */
    private fun computeResult(): Evaluator.Result? {
        val boundValue = boundAnswer()
        return if (boundValue != null) {
            Evaluator.evaluate(Evaluator.ANSWER_TOKEN + expr.substring(answerText!!.length), boundValue)
        } else {
            Evaluator.evaluate(expr)
        }
    }

    /**
     * Live preview of the result while typing (what "=" would show), or null when there is nothing
     * worth previewing: a result is on screen, an error, nothing evaluable yet, a lone number, or an
     * expression that would be an error (e.g. division by zero).
     */
    fun preview(): String? {
        if (error || justEvaluated || expr.isEmpty()) return null
        if (!Evaluator.isAccepted(expr) || Evaluator.isBlank(expr)) return null
        val normalized = Evaluator.normalize(expr) ?: return null
        if (!hasOperation(normalized)) return null // a lone number: nothing to preview
        return computeResult()?.display
    }

    /** True when [s] contains a binary operation or an implicit multiplication such as "2(3)". */
    private fun hasOperation(s: String): Boolean {
        for (i in 1 until s.length) {
            val c = s[i]
            val p = s[i - 1]
            val pEndsNumber = p in '0'..'9' || p == '.' || p == ')'
            when {
                c == '+' || c == '*' || c == '/' -> return true
                c == '-' && pEndsNumber -> return true // binary minus ("E-7" has p == 'E')
                c == '(' && pEndsNumber -> return true // 2(3), (2)(3)
                p == ')' && (c in '0'..'9' || c == '.') -> return true // (2)3
            }
        }
        return false
    }

    private companion object {
        /** Trailing number token: "123", "1.5", ".5", "5." and scientific results "1.5E-7", "1E+58". */
        val NUMBER_AT_END = Regex("""[0-9.]+(?:E[+-]?[0-9]*)?$""")
    }
}
