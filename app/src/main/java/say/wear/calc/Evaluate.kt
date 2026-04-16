package say.wear.calc

import say.wear.calc.Symbols.COS
import say.wear.calc.Symbols.DIV
import say.wear.calc.Symbols.HUN
import say.wear.calc.Symbols.INF
import say.wear.calc.Symbols.MINUS
import say.wear.calc.Symbols.MULTI
import say.wear.calc.Symbols.PER
import say.wear.calc.Symbols.PLUS
import say.wear.calc.Symbols.POW
import say.wear.calc.Symbols.SIN
import say.wear.calc.Symbols.SQRT
import say.wear.calc.Symbols.TAN
import say.wear.calc.Symbols.U_MINUS
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

fun evaluate(tokens: List<Token>): String {
    if (tokens.isEmpty()) return ""
    if (!isValid(tokens)) return ""
    if (!isReady(tokens)) return ""

    return try {
        val rpn = toRpn(tokens)
        val result = evalRpn(rpn)

        if (result.isNaN()) return ""
        if (result.isInfinite()) return INF

        val bd = BigDecimal(result.toString())
            .setScale(10, RoundingMode.HALF_UP)
            .stripTrailingZeros()

        if (bd.scale() <= 0) bd.toPlainString() else bd.toPlainString()
    } catch (_: Exception) { "" }
}

private fun isValid(tokens: List<Token>): Boolean {
    var balance = 0
    for (i in tokens.indices) {
        val current = tokens[i]
        val next = tokens.getOrNull(i + 1)

        if (current is Token.LeftParen) balance++
        if (current is Token.RightParen) balance--
        if (balance < 0) return false

        if (current is Token.LeftParen && next is Token.RightParen) return false
        if (current is Token.Operator && next is Token.Operator) return false
        if (current is Token.Function && next is Token.Operator) return false
        if (i == 0 && (current is Token.Operator || current is Token.RightParen)) return false
        if (i == tokens.lastIndex && (current is Token.Operator || current is Token.Function || current is Token.LeftParen)) return false
    }
    return balance == 0
}

private fun isReady(tokens: List<Token>): Boolean {
    if (tokens.size == 1 && tokens[0] is Token.Number) return false
    if (tokens.size == 2 && tokens[0] is Token.UnaryMinus && tokens[1] is Token.Number) return false

    val hasBinaryOperator = tokens.any { it is Token.Operator }
    val hasFunction = tokens.any { it is Token.Function }

    return hasBinaryOperator || hasFunction
}

private fun precedence(token: Token): Int = when (token) {
    is Token.Operator -> when (token.symbol) {
        POW -> 3
        MULTI, DIV -> 2
        PLUS, MINUS -> 1
        else -> 0
    }
    is Token.Function -> 10
    is Token.UnaryMinus -> 9
    is Token.RightParen -> 0
    is Token.Percent -> 8
    else -> 0
}

private fun toRpn(tokens: List<Token>): List<String> {
    val output = mutableListOf<String>()
    val ops = ArrayDeque<Token>()

    for (t in tokens) {
        when (t) {
            is Token.Number -> {
                output.add(t.value)
                while (ops.isNotEmpty() && ops.last() is Token.Function) {
                    output.add(ops.removeLast().toSymbol())
                }
            }

            is Token.UnaryMinus -> ops.addLast(t)

            is Token.Function -> ops.addLast(t)

            is Token.LeftParen -> ops.addLast(t)

            is Token.RightParen -> {
                while (ops.isNotEmpty() && ops.last() !is Token.LeftParen) {
                    output.add(ops.removeLast().toSymbol())
                }
                ops.removeLast()
                if (ops.isNotEmpty() && ops.last() is Token.Function) {
                    output.add(ops.removeLast().toSymbol())
                }
            }

            is Token.Operator -> {
                while (ops.isNotEmpty() &&
                    ops.last() !is Token.LeftParen &&
                    ops.last() !is Token.Function &&
                    precedence(ops.last()) >= precedence(t)) {
                    output.add(ops.removeLast().toSymbol())
                }
                ops.addLast(t)
            }

            is Token.Percent -> {
                val b = output.removeAt(output.lastIndex)
                val lastOp = ops.lastOrNull()
                output.add(b)

                if (lastOp is Token.Operator && (lastOp.symbol == PLUS || lastOp.symbol == MINUS)) {
                    output.add(PER)
                    output.add(MULTI)
                    output.add(HUN)
                    output.add(DIV)
                } else {
                    output.add(HUN)
                    output.add(DIV)
                }
            }
        }
    }
    while (ops.isNotEmpty()) {
        val op = ops.removeLast()
        if (op !is Token.LeftParen) output.add(op.toSymbol())
    }
    return output
}

private fun evalRpn(rpn: List<String>): Double {
    val stack = ArrayDeque<Double>()

    for (t in rpn) {
        val num = t.toDoubleOrNull()
        if (num != null) {
            stack.addLast(num)
            continue
        }

        if (stack.isEmpty()) return Double.NaN

        when (t) {
            U_MINUS -> {
                val a = stack.removeLast()
                stack.addLast(-a)
            }
            SQRT -> {
                val a = stack.removeLast()
                if (a < 0) return Double.NaN
                stack.addLast(kotlin.math.sqrt(a))
            }
            PLUS -> {
                if (stack.size < 2) return Double.NaN
                val b = stack.removeLast()
                val a = stack.removeLast()
                stack.addLast(a + b)
            }
            MINUS -> {
                if (stack.size < 2) return Double.NaN
                val b = stack.removeLast()
                val a = stack.removeLast()
                stack.addLast(a - b)
            }
            MULTI -> {
                if (stack.size < 2) return Double.NaN
                val b = stack.removeLast()
                val a = stack.removeLast()
                stack.addLast(a * b)
            }
            DIV -> {
                if (stack.size < 2) return Double.NaN
                val b = stack.removeLast()
                val a = stack.removeLast()
                stack.addLast(a / b)
            }
            POW -> {
                if (stack.size < 2) return Double.NaN
                val b = stack.removeLast()
                val a = stack.removeLast()
                stack.addLast(a.pow(b))
            }
            PER -> {
                if (stack.size < 2) return Double.NaN
                val b = stack.removeLast()
                val a = stack.last()
                stack.addLast(a)
                stack.addLast(b)
            }
            SIN -> {
                val a = stack.removeLast()
                val radians = Math.toRadians(a)
                stack.addLast(kotlin.math.sin(radians))
            }
            COS -> {
                val a = stack.removeLast()
                val radians = Math.toRadians(a)
                stack.addLast(kotlin.math.cos(radians))
            }
            TAN -> {
                val a = stack.removeLast()
                val radians = Math.toRadians(a)
                stack.addLast(kotlin.math.tan(radians))
            }
        }
    }

    return if (stack.size == 1) stack.last() else Double.NaN
}