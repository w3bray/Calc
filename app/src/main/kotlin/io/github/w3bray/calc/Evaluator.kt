package io.github.w3bray.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Evaluates calculator expressions such as "2+3*(4-1)/2" with BigDecimal arithmetic
 * (so 0.1+0.2 is 0.3, not 0.30000000000000004). Pure Kotlin, covered by JVM unit tests.
 *
 * Grammar (recursive descent, standard precedence):
 *   expression := term (('+' | '-') term)*
 *   term       := unary (('*' | '/') unary | <implicit multiplication> unary)*
 *   unary      := ('+' | '-') unary | primary
 *   primary    := number | '(' expression ')'
 *
 * Lenient like phone calculators: trailing operators / "(" are dropped and missing ")" are added.
 */
object Evaluator {

    /** [display] is what the screen shows; [plain] is the full digits used to keep calculating. */
    data class Result(val display: String, val plain: String)

    private class EvalException(message: String) : Exception(message)

    private val mathContext = MathContext(20, RoundingMode.HALF_UP)

    /** Returns the result, or null when the expression is invalid (including division by zero). */
    fun evaluate(
        expression: String,
        precision: Int = Config.RESULT_PRECISION_DIGITS,
        maxPlainDigits: Int = Config.RESULT_MAX_PLAIN_DIGITS,
    ): Result? {
        val normalized = normalize(expression) ?: return null
        return try {
            val parser = Parser(normalized)
            val value = parser.parseExpression()
            if (!parser.atEnd()) throw EvalException("unexpected '${parser.peek()}'")
            val rounded = value.round(MathContext(precision, RoundingMode.HALF_UP)).stripTrailingZeros()
            if (rounded.signum() == 0) {
                Result("0", "0")
            } else {
                Result(format(rounded, maxPlainDigits), rounded.toPlainString())
            }
        } catch (e: EvalException) {
            null
        } catch (e: ArithmeticException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Keeps only allowed characters, drops dangling operators / "(" at the end and closes
     * unbalanced parentheses. Returns null when nothing evaluable is left.
     */
    fun normalize(expression: String): String? {
        var s = expression.filter { it in Config.ALLOWED_INPUT }
        s = s.trimEnd('+', '-', '*', '/', '(')
        if (s.isEmpty()) return null
        val unclosed = s.count { it == '(' } - s.count { it == ')' }
        if (unclosed > 0) s += ")".repeat(unclosed)
        return s
    }

    private fun format(value: BigDecimal, maxPlainDigits: Int): String {
        val plain = value.toPlainString()
        return if (plain.count { it.isDigit() } <= maxPlainDigits) plain else value.toString()
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd(): Boolean = i >= s.length
        fun peek(): Char? = if (atEnd()) null else s[i]
        private fun next(): Char = s[i++]

        fun parseExpression(): BigDecimal {
            var value = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { next(); value = value.add(parseTerm(), mathContext) }
                    '-' -> { next(); value = value.subtract(parseTerm(), mathContext) }
                    else -> return value
                }
            }
        }

        fun parseTerm(): BigDecimal {
            var value = parseUnary()
            while (true) {
                val c = peek()
                when {
                    c == '*' -> { next(); value = value.multiply(parseUnary(), mathContext) }
                    c == '/' -> {
                        next()
                        val divisor = parseUnary()
                        if (divisor.signum() == 0) throw EvalException("division by zero")
                        value = value.divide(divisor, mathContext)
                    }
                    // implicit multiplication: 2(3), (2)(3), (2)3
                    c == '(' || (c != null && (c.isDigit() || c == '.')) -> {
                        value = value.multiply(parseUnary(), mathContext)
                    }
                    else -> return value
                }
            }
        }

        fun parseUnary(): BigDecimal = when (peek()) {
            '-' -> { next(); parseUnary().negate() }
            '+' -> { next(); parseUnary() }
            else -> parsePrimary()
        }

        fun parsePrimary(): BigDecimal {
            val c = peek() ?: throw EvalException("unexpected end of expression")
            if (c == '(') {
                next()
                val value = parseExpression()
                if (peek() != ')') throw EvalException("expected ')'")
                next()
                return value
            }
            if (c.isDigit() || c == '.') {
                val start = i
                var dots = 0
                while (!atEnd() && (s[i].isDigit() || s[i] == '.')) {
                    if (s[i] == '.') dots++
                    i++
                }
                val text = s.substring(start, i)
                if (dots > 1 || text == ".") throw EvalException("bad number '$text'")
                return BigDecimal(text) // accepts "5." and ".5"
            }
            throw EvalException("unexpected '$c'")
        }
    }
}
