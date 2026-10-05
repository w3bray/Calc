package io.github.w3bray.calc

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Evaluates calculator expressions such as "2+3*(4-1)/2".
 *
 * The arithmetic is EXACT: every value is a fraction of two BigIntegers, so 1/3*3-1 is exactly 0
 * and 0.1+0.2 is exactly 0.3. Rounding happens once, when the final value is formatted for the
 * display (Config.RESULT_PRECISION_DIGITS significant digits). Pure Kotlin, covered by JVM tests.
 *
 * Grammar (recursive descent, standard precedence):
 *   expression := term (('+' | '-') term)*
 *   term       := unary (('*' | '/') unary | <implicit multiplication> unary)*
 *   unary      := ('+' | '-') unary | primary
 *   primary    := number | ANSWER | '(' expression ')'
 *   number     := digits ['.' digits] ['E' ['+'|'-'] digits]      (".5" and "5." are fine too)
 *
 * Lenient like phone calculators: dangling operators / "(" at the end are dropped and missing ")"
 * are added. Any character outside the accepted set makes the whole expression invalid.
 */
object Evaluator {

    /** An exact rational number num/den (den > 0, always reduced). */
    class Value private constructor(val num: BigInteger, val den: BigInteger) {

        val isZero: Boolean
            get() = num.signum() == 0

        operator fun plus(o: Value): Value = of(num * o.den + o.num * den, den * o.den)
        operator fun minus(o: Value): Value = of(num * o.den - o.num * den, den * o.den)
        operator fun times(o: Value): Value = of(num * o.num, den * o.den)
        operator fun div(o: Value): Value {
            if (o.isZero) throw ArithmeticException("division by zero")
            return of(num * o.den, den * o.num)
        }
        operator fun unaryMinus(): Value = of(num.negate(), den)

        /** The value rounded ONCE to [precision] significant digits, trailing zeros removed. */
        fun toBigDecimal(precision: Int): BigDecimal =
            BigDecimal(num).divide(BigDecimal(den), MathContext(precision, RoundingMode.HALF_UP)).stripTrailingZeros()

        override fun equals(other: Any?): Boolean = other is Value && num == other.num && den == other.den
        override fun hashCode(): Int = 31 * num.hashCode() + den.hashCode()
        override fun toString(): String = if (den == BigInteger.ONE) num.toString() else "$num/$den"

        companion object {
            val ZERO: Value = Value(BigInteger.ZERO, BigInteger.ONE)

            fun of(num: BigInteger, den: BigInteger): Value {
                if (den.signum() == 0) throw ArithmeticException("division by zero")
                var n = num
                var d = den
                if (d.signum() < 0) {
                    n = n.negate()
                    d = d.negate()
                }
                val g = n.gcd(d) // gcd(0, d) == d, so 0/d reduces to 0/1
                if (g > BigInteger.ONE) {
                    n /= g
                    d /= g
                }
                return Value(n, d)
            }

            fun of(decimal: BigDecimal): Value {
                val unscaled = decimal.unscaledValue()
                val scale = decimal.scale()
                return if (scale >= 0) of(unscaled, BigInteger.TEN.pow(scale))
                else of(unscaled * BigInteger.TEN.pow(-scale), BigInteger.ONE)
            }
        }
    }

    /** [display] is what the screen shows; [value] is the exact result, used to keep calculating. */
    data class Result(val display: String, val value: Value)

    /** Stands for "the previous answer" inside an expression. Not typeable from the keypad. */
    const val ANSWER_TOKEN = 'A'

    private const val EXPONENT_CHAR = 'E'
    private const val MAX_EXPONENT_MAGNITUDE = 10_000 // |decimal exponent| of any literal or result
    private const val MAX_EXPONENT_DIGITS = 6 // lexeme guard, so "1E+99999999999" never reaches BigDecimal

    /** Decimal exponent of the first significant digit (1234.5 -> 3, 0.00012 -> -4). */
    private fun decimalExponent(d: BigDecimal): Long = d.precision().toLong() - d.scale() - 1
    private val OPERATORS = setOf('+', '-', '*', '/')
    private val ACCEPTED_CHARS: Set<Char> = (Config.ALLOWED_INPUT + EXPONENT_CHAR + ANSWER_TOKEN).toSet()

    private class EvalException(message: String) : Exception(message)

    /**
     * Returns the result, or null when the expression is invalid (unknown character, bad syntax,
     * division by zero, missing answer for [ANSWER_TOKEN]).
     */
    fun evaluate(
        expression: String,
        answer: Value? = null,
        precision: Int = Config.RESULT_PRECISION_DIGITS,
        maxPlainDigits: Int = Config.RESULT_MAX_PLAIN_DIGITS,
        minPlainExponent: Int = Config.RESULT_MIN_PLAIN_EXPONENT,
    ): Result? {
        if (!isAccepted(expression)) return null
        val normalized = normalize(expression) ?: return null
        return try {
            val parser = Parser(normalized, answer)
            val value = parser.parseExpression()
            if (!parser.atEnd()) throw EvalException("unexpected '${parser.peek()}'")
            val rounded = value.toBigDecimal(precision)
            // Overflow, like a physical calculator: a result the number lexer could not read back
            // (|exponent| > MAX_EXPONENT_MAGNITUDE) is an error rather than an unusable display.
            if (rounded.signum() != 0 && abs(decimalExponent(rounded)) > MAX_EXPONENT_MAGNITUDE) {
                throw EvalException("result too large or too small")
            }
            Result(format(rounded, maxPlainDigits, minPlainExponent), value)
        } catch (e: EvalException) {
            null
        } catch (e: ArithmeticException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** True when every character is one the evaluator understands. */
    fun isAccepted(expression: String): Boolean = expression.all { it in ACCEPTED_CHARS }

    /** True when there is nothing to compute yet: empty, or only dangling operators and "(". */
    fun isBlank(expression: String): Boolean = normalize(expression) == null

    /** Drops dangling operators / "(" at the end and closes unbalanced "(". Null when nothing is left. */
    fun normalize(expression: String): String? {
        val s = expression.trimEnd('+', '-', '*', '/', '(')
        if (s.isEmpty()) return null
        val unclosed = s.count { it == '(' } - s.count { it == ')' }
        return if (unclosed > 0) s + ")".repeat(unclosed) else s
    }

    /**
     * Plain decimal for everyday magnitudes, "d.dddE+x" / "d.dddE-x" otherwise:
     * scientific when the integer part would need more than [maxPlainDigits] digits or when the
     * number is smaller than 10^[minPlainExponent].
     */
    fun format(value: BigDecimal, maxPlainDigits: Int, minPlainExponent: Int): String {
        if (value.signum() == 0) return "0"
        val v = value.stripTrailingZeros()
        val exponent = decimalExponent(v)
        if (exponent in minPlainExponent.toLong() until maxPlainDigits.toLong()) return v.toPlainString()
        val digits = v.unscaledValue().abs().toString()
        val mantissa = if (digits.length == 1) digits else digits[0] + "." + digits.substring(1)
        val sign = if (v.signum() < 0) "-" else ""
        val expSign = if (exponent < 0) "-" else "+"
        return "$sign${mantissa}E$expSign${abs(exponent)}"
    }

    private class Parser(private val s: String, private val answer: Value?) {
        private var i = 0

        fun atEnd(): Boolean = i >= s.length
        fun peek(): Char? = if (atEnd()) null else s[i]
        private fun next(): Char = s[i++]

        fun parseExpression(): Value {
            var value = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { next(); value += parseTerm() }
                    '-' -> { next(); value -= parseTerm() }
                    else -> return value
                }
            }
        }

        fun parseTerm(): Value {
            var value = parseUnary()
            while (true) {
                val c = peek()
                when {
                    c == '*' -> { next(); value *= parseUnary() }
                    c == '/' -> { next(); value /= parseUnary() }
                    // implicit multiplication: 2(3), (2)(3), (2)3, 2A
                    c == '(' || c == ANSWER_TOKEN || (c != null && (c in '0'..'9' || c == '.')) -> {
                        value *= parseUnary()
                    }
                    else -> return value
                }
            }
        }

        fun parseUnary(): Value = when (peek()) {
            '-' -> { next(); -parseUnary() }
            '+' -> { next(); parseUnary() }
            else -> parsePrimary()
        }

        fun parsePrimary(): Value {
            val c = peek() ?: throw EvalException("unexpected end of expression")
            return when {
                c == '(' -> {
                    next()
                    val value = parseExpression()
                    if (peek() != ')') throw EvalException("expected ')'")
                    next()
                    value
                }
                c == ANSWER_TOKEN -> {
                    next()
                    answer ?: throw EvalException("no previous answer")
                }
                c in '0'..'9' || c == '.' -> parseNumber()
                else -> throw EvalException("unexpected '$c'")
            }
        }

        private fun parseNumber(): Value {
            val start = i
            var dots = 0
            while (!atEnd() && (s[i] in '0'..'9' || s[i] == '.')) {
                if (s[i] == '.') dots++
                i++
            }
            if (dots > 1 || (i - start == 1 && s[start] == '.')) throw EvalException("bad number")
            // Optional exponent, as produced by the display ("1.5E+15", "1E-7"): E, sign, digits.
            if (!atEnd() && s[i] == EXPONENT_CHAR) {
                var j = i + 1
                if (j < s.length && (s[j] == '+' || s[j] == '-')) j++
                val expStart = j
                while (j < s.length && s[j] in '0'..'9') j++
                if (j == expStart) throw EvalException("bad exponent")
                if (j - expStart > MAX_EXPONENT_DIGITS) throw EvalException("exponent too large")
                i = j
            }
            // A dot glued to the number ("1E+5.5") is a malformed literal, not "times 0.5".
            if (!atEnd() && s[i] == '.') throw EvalException("bad number")
            val decimal = BigDecimal(s.substring(start, i)) // accepts "5.", ".5", "1.5E+15"
            if (decimal.signum() != 0 && abs(decimalExponent(decimal)) > MAX_EXPONENT_MAGNITUDE) {
                throw EvalException("exponent too large")
            }
            return Value.of(decimal)
        }
    }

    /** Exposed for tests: the operator characters. */
    fun isOperator(c: Char): Boolean = c in OPERATORS
}
