/*
 * Round Calculator for Wear OS
 * Copyright (C) 2026 [stanissay]
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package stanissay.wear.calc

import stanissay.wear.calc.Symbols.DOT
import stanissay.wear.calc.Symbols.MINUS

fun reduce(state: CalcState, input: Input): CalcState {
    return when (input) {
        is Input.Digit -> inputDigit(state, input)
        is Input.Operator -> inputOperator(state, input)
        is Input.Function -> inputFunction(state, input)
        is Input.Result -> finalizeCalculation(state)
        is Input.Extended -> extendItems(state)
        is Input.Dot -> inputDot(state)
        is Input.Delete -> handleDelete(state)
        is Input.LeftParen -> inputLeftParen(state)
        is Input.RightParen -> inputRightParen(state)
        is Input.Percent -> inputPercent(state)
    }
}

private fun inputDigit(state: CalcState, input: Input.Digit): CalcState {
    val tokens = state.tokens.toMutableList()
    val (index, offset) = state.cursor
    val numberInfo = findNumberToken(tokens, index)

    if (numberInfo != null) {
        val (numIndex, numToken) = numberInfo
        val currentOffset = if (numIndex == index) offset else numToken.value.length
        val newValue = numToken.value.substring(0, currentOffset) + input.value + numToken.value.substring(currentOffset)
        tokens[numIndex] = numToken.copy(value = newValue)
        return state.copy(tokens = tokens, cursor = Cursor(numIndex, currentOffset + 1))
    }

    tokens.add(index, Token.Number(input.value))
    return state.copy(tokens = tokens, cursor = Cursor(index + 1, input.value.length))
}

private fun inputOperator(state: CalcState, input: Input.Operator): CalcState {
    val tokens = state.tokens.toMutableList()
    val (index, offset) = state.cursor
    val symbol = input.symbol
    val tokenUnderCursor = tokens.getOrNull(index)
    val isInsideNumber = (tokenUnderCursor is Token.Number && offset > 0)
    val isAtStartOfExpression = (index == 0 && offset == 0)
    val canInsert = canInsertOperator(tokens, index, offset)
    val prevToken = tokens.getOrNull(index - 1)
    val canBeUnary = isAtStartOfExpression || (canInsert && !isInsideNumber) || prevToken is Token.Operator || prevToken is Token.Function || prevToken is Token.LeftParen
    val isUnaryMinus = (symbol == MINUS) && canBeUnary && offset == 0
    if (!isUnaryMinus && !canInsert) return state
    val tokenToInsert = if (isUnaryMinus) Token.UnaryMinus else Token.Operator(symbol)
    val numberInfo = findNumberToken(tokens, index)

    if (numberInfo != null) {
        val (numIndex, numToken) = numberInfo
        val realOffset = if (numIndex == index - 1) numToken.value.length else offset
        val left = numToken.value.substring(0, realOffset)
        val right = numToken.value.substring(realOffset)
        val tokenToInsert = if (isUnaryMinus) Token.UnaryMinus else Token.Operator(symbol)
        val newParts = mutableListOf<Token>()
        if (left.isNotEmpty()) newParts.add(Token.Number(left))
        newParts.add(tokenToInsert)
        if (right.isNotEmpty()) newParts.add(Token.Number(right))

        tokens.removeAt(numIndex)
        tokens.addAll(numIndex, newParts)

        return state.copy(
            tokens = tokens,
            cursor = Cursor(numIndex + newParts.indexOf(tokenToInsert) + 1, 0),
            isExtended = false
        )
    }

    if (prevToken is Token.Operator && tokenToInsert !is Token.UnaryMinus) {
        tokens[index - 1] = tokenToInsert
        return state.copy(tokens = tokens, isExtended = false)
    }

    tokens.add(index, tokenToInsert)
    return state.copy(tokens = tokens, cursor = Cursor(index + 1, 0), isExtended = false)
}

private fun inputFunction(state: CalcState, input: Input.Function): CalcState {
    val tokens = state.tokens.toMutableList()
    val index = state.cursor.tokenIndex
    val offset = state.cursor.offset
    val canInsert = canInsertFunction(tokens, index, offset)
    if (!canInsert) return state

    tokens.add(index, Token.Function(input.name))

    return state.copy(
        tokens = tokens,
        cursor = Cursor(index + 1, 0),
        isExtended = false
    )
}

private fun finalizeCalculation(state: CalcState): CalcState {
    val result = evaluate(state.tokens)
    if (result.isEmpty()) return state
    val newToken = Token.Number(result)

    return CalcState(
        tokens = listOf(newToken),
        cursor = Cursor(0, result.length),
        isExtended = false
    )
}

private fun extendItems(state: CalcState): CalcState {
    return state.copy(isExtended = !state.isExtended)
}

private fun inputDot(state: CalcState): CalcState {
    val tokens = state.tokens.toMutableList()
    val (index, offset) = state.cursor
    val numberInfo = findNumberToken(tokens, index)

    if (numberInfo != null) {
        val (numIndex, numToken) = numberInfo
        if (numToken.value.contains(DOT)) return state
        val currentOffset = if (numIndex == index) offset else numToken.value.length
        val newValue = numToken.value.substring(0, currentOffset) + DOT + numToken.value.substring(currentOffset)
        tokens[numIndex] = numToken.copy(value = newValue)
        return state.copy(tokens = tokens, cursor = Cursor(numIndex, currentOffset + 1))
    }

    tokens.add(index, Token.Number("0$DOT"))
    return state.copy(tokens = tokens, cursor = Cursor(index + 1, 2))
}

private fun handleDelete(state: CalcState): CalcState {
    val (tokens, cursor) = state
    val index = cursor.tokenIndex
    val offset = cursor.offset
    if (tokens.isEmpty()) return state
    val numberInfo = findNumberToken(tokens, index)

    if (numberInfo != null) {
        val (numIndex, numToken) = numberInfo
        val isInsideOrAtEndOfNumber = (numIndex == index && offset > 0) || (numIndex == index - 1)
        if (isInsideOrAtEndOfNumber) {
            val realOffset = if (numIndex == index - 1) numToken.value.length else offset
            val stateWithOffset = state.copy(cursor = Cursor(numIndex, realOffset))
            return deleteChar(stateWithOffset, numToken, numIndex)
        }
    }

    return deleteToken(state, index - 1)
}

private fun deleteChar(state: CalcState, token: Token.Number, index: Int): CalcState {
    val offset = state.cursor.offset
    val newValue = token.value.removeRange(offset - 1, offset)
    val tokens = state.tokens.toMutableList()

    if (newValue.isNotEmpty()) {
        tokens[index] = token.copy(value = newValue)
        return state.copy(tokens = tokens, cursor = Cursor(index, offset - 1))
    }

    tokens.removeAt(index)
    val newIndex = index.coerceAtMost(tokens.size)

    return state.copy(
        tokens = tokens,
        cursor = Cursor(newIndex, 0)
    )
}

private fun deleteToken(state: CalcState, indexToRemove: Int): CalcState {
    if (indexToRemove !in state.tokens.indices) return state
    val tokens = state.tokens.toMutableList()
    tokens.removeAt(indexToRemove)
    val newIndex = indexToRemove.coerceAtMost(tokens.size)
    val token = tokens.getOrNull(newIndex - 1)
    val newOffset = if (token is Token.Number) token.value.length else 0

    return state.copy(
        tokens = tokens,
        cursor = Cursor(newIndex, newOffset)
    )
}

private fun inputLeftParen(state: CalcState): CalcState {
    val tokens = state.tokens.toMutableList()
    val index = state.cursor.tokenIndex
    val offset = state.cursor.offset
    val canInsert = canInsertLeftParen(tokens, index, offset)
    if (!canInsert) return state

    tokens.add(index, Token.LeftParen)

    return state.copy(
        tokens = tokens,
        cursor = Cursor(index + 1, 0)
    )
}

private fun inputRightParen(state: CalcState): CalcState {
    val tokens = state.tokens.toMutableList()
    val index = state.cursor.tokenIndex
    val offset = state.cursor.offset
    val canInsert = canInsertRightParen(tokens, index, offset)
    if (!canInsert) return state
    val numberInfo = findNumberToken(tokens, index)

    if (numberInfo != null) {
        val (numIndex, numToken) = numberInfo
        val realOffset = if (numIndex == index - 1) numToken.value.length else offset
        val leftPart = numToken.value.substring(0, realOffset)
        val rightPart = numToken.value.substring(realOffset)
        val newParts = mutableListOf<Token>()
        if (leftPart.isNotEmpty()) newParts.add(Token.Number(leftPart))
        newParts.add(Token.RightParen)
        if (rightPart.isNotEmpty()) newParts.add(Token.Number(rightPart))

        tokens.removeAt(numIndex)
        tokens.addAll(numIndex, newParts)

        return state.copy(
            tokens = tokens,
            cursor = Cursor(numIndex + newParts.indexOf(Token.RightParen) + 1, 0)
        )
    }

    tokens.add(index, Token.RightParen)

    return state.copy(
        tokens = tokens,
        cursor = Cursor(index + 1, 0)
    )
}

private fun inputPercent(state: CalcState): CalcState {
    val tokens = state.tokens.toMutableList()
    val index = state.cursor.tokenIndex
    val numberInfo = findNumberToken(tokens, index) ?: return state
    val (numIndex, _) = numberInfo
    val insertIndex = numIndex + 1

    tokens.add(insertIndex, Token.Percent)

    return state.copy(
        tokens = tokens,
        cursor = Cursor(insertIndex + 1, 0),
        isExtended = false
    )
}

private fun findNumberToken(tokens: List<Token>, index: Int): Pair<Int, Token.Number>? {
    return when {
        tokens.getOrNull(index) is Token.Number -> index to tokens[index] as Token.Number
        tokens.getOrNull(index - 1) is Token.Number -> (index - 1) to tokens[index - 1] as Token.Number
        else -> null
    }
}

private fun canInsertOperator(tokens: List<Token>, index: Int, offset: Int): Boolean {
    val tokenUnderCursor = tokens.getOrNull(index)
    if (tokenUnderCursor is Token.Number && offset > 0) return true
    val prevToken = tokens.getOrNull(index - 1)
    return prevToken is Token.Number ||
            prevToken is Token.RightParen ||
            prevToken is Token.Percent ||
            prevToken is Token.Function ||
            prevToken is Token.Operator
}

private fun canInsertFunction(tokens: List<Token>, index: Int, offset: Int): Boolean {
    if (index == 0 && offset == 0) return true
    val tokenUnderCursor = tokens.getOrNull(index)
    if (tokenUnderCursor is Token.Number && offset > 0) return false
    val prevToken = tokens.getOrNull(index - 1)
    return prevToken is Token.Operator ||
            prevToken is Token.UnaryMinus ||
            prevToken is Token.LeftParen ||
            prevToken is Token.Function
}

private fun canInsertLeftParen(tokens: List<Token>, index: Int, offset: Int): Boolean {
    if (offset > 0) return false
    if (index == 0 && offset == 0) return true
    val prevToken = tokens.getOrNull(index - 1)
    return prevToken is Token.Operator ||
            prevToken is Token.UnaryMinus ||
            prevToken is Token.LeftParen ||
            prevToken is Token.Function
}

private fun canInsertRightParen(tokens: List<Token>, index: Int, offset: Int): Boolean {
    val openCount = tokens.take(index).count { it is Token.LeftParen }
    val closedCount = tokens.take(index).count { it is Token.RightParen }
    if (openCount <= closedCount) return false
    val currentToken = tokens.getOrNull(index)
    val prevToken = tokens.getOrNull(index - 1)
    if (currentToken is Token.Number && offset > 0) return true

    return prevToken is Token.Number ||
            prevToken is Token.RightParen ||
            prevToken is Token.LeftParen ||
            prevToken is Token.Percent
}

fun moveCursor(state: CalcState, delta: Int): CalcState {
    val tokens = state.tokens
    val (index, offset) = state.cursor
    val currentToken = tokens.getOrNull(index)

    if (currentToken is Token.Number) {
        val nextOffset = offset + delta
        if (nextOffset in 0..currentToken.value.length) {
            return state.copy(cursor = Cursor(index, nextOffset))
        }
    }

    val targetIndex = (index + if (delta > 0) 1 else -1).coerceIn(0, tokens.size)
    if (targetIndex == index && offset == 0 && delta < 0) return state
    if (targetIndex == index && (currentToken !is Token.Number || offset == currentToken.value.length) && delta > 0) return state

    val nextOffset = when {
        delta > 0 -> 0
        delta < 0 && targetIndex < tokens.size && tokens[targetIndex] is Token.Number ->
            (tokens[targetIndex] as Token.Number).value.length
        else -> 0
    }

    return state.copy(cursor = Cursor(targetIndex, nextOffset))
}