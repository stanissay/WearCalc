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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Shapes
import androidx.wear.compose.material.Typography
import kotlinx.serialization.Serializable

object Constants {
    const val PREFS_NAME = "calc_prefs"
    const val KEY_STATE = "saved_state"
    const val KEY_TIMESTAMP = "saved_timestamp"
    const val RESTORE_TIMEOUT = 5 * 60 * 1000   // 5 minutes

    val BUTTON_SIZE = 32.dp

    val DISPLAY_HEIGHT = 48.dp
    val DISPLAY_WIDTH = 64.dp
    val AMBIENT_SIZE = 128.dp

    val CURSOR_PADDING = 4.dp
    val THICKNESS = 1.dp

    const val NUMB_RATIO = 0.350f
    const val MATH_RATIO = 0.225f
    const val SWIPE_RATIO = 0.4f
    const val ROTATION_THRESHOLD = 30f
    const val ACCELERATION_THRESHOLD = 12f
    const val ACCELERATION_DELAY = 1000

    const val SCREEN_ANIMATION_DURATION = 300
    const val PAD_ANIMATION_DURATION = 500
    const val TEXT_ANIMATION_DURATION = 200
}

object MainColors {
    val BACKGROUND = Color(0xFF000000)
    val FIRST_ACCENT = Color(0xFF81C784)
    val SECOND_ACCENT = Color(0xFFFF8A65)
    val WHITE = Color(0xFFCECECE)
    val GRAY = Color(0xFF616161)
}

object Symbols {
    const val DOT = "."
    const val DELETE = "⌫"
    const val EXT = "…"
    const val RES = "="
    const val PLUS = "+"
    const val MINUS = "-"
    const val U_MINUS = "u-"
    const val MULTI = "×"
    const val DIV = "÷"
    const val L_PAREN = "("
    const val R_PAREN = ")"
    const val POW = "^"
    const val SQRT = "√"
    const val PER = "%"
    const val SIN = "sin"
    const val COS = "cos"
    const val TAN = "tan"
    const val INF = "∞"
    const val HUN = "100"
}

val TypographyStyle = Typography(
    body1 = TextStyle(fontSize = 16.sp)
)

val ColorStyle = Colors(
    primary = MainColors.FIRST_ACCENT,
    secondary = MainColors.SECOND_ACCENT,
    background = MainColors.BACKGROUND,
    surface = MainColors.GRAY,
    onBackground = MainColors.WHITE,
)

val ShapesStyle = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(16.dp),
)

@Serializable
data class CalcState(
    val tokens: List<Token> = emptyList(),
    val cursor: Cursor = Cursor(0, 0),
    val isExtended: Boolean = false
)

@Serializable
data class Cursor(
    val tokenIndex: Int,
    val offset: Int = 0
)

@Serializable
sealed class Token {
    @Serializable
    data class Number(val value: String) : Token()
    @Serializable
    data class Operator(val symbol: String) : Token()
    @Serializable
    data class Function(val name: String) : Token()
    object UnaryMinus : Token()
    object LeftParen : Token()
    object RightParen : Token()
    object Percent : Token()
}

@Serializable
sealed class Input {
    @Serializable
    data class Digit(val value: String) : Input()
    @Serializable
    data class Operator(val symbol: String) : Input()
    @Serializable
    data class Function(val name: String) : Input()
    object Result : Input()
    object Extended: Input()
    object Dot : Input()
    object Delete : Input()
    object LeftParen : Input()
    object RightParen : Input()
    object Percent : Input()
}

val NumberItems: List<Input> = listOf(
    Input.Digit("0"),
    Input.Digit("1"),
    Input.Digit("2"),
    Input.Digit("3"),
    Input.Digit("4"),
    Input.Digit("5"),
    Input.Digit("6"),
    Input.Digit("7"),
    Input.Digit("8"),
    Input.Digit("9"),
    Input.Dot,
    Input.Delete
)
val BaseMathItems: List<Input> = listOf(
    Input.Result,
    Input.Operator(Symbols.PLUS),
    Input.Operator(Symbols.MINUS),
    Input.Operator(Symbols.MULTI),
    Input.Operator(Symbols.DIV),
    Input.LeftParen,
    Input.RightParen,
    Input.Extended
)
val ExtendedMathItems: List<Input> = listOf(
    Input.Result,
    Input.Operator(Symbols.POW),
    Input.Function(Symbols.SQRT),
    Input.Percent,
    Input.Function(Symbols.SIN),
    Input.Function(Symbols.COS),
    Input.Function(Symbols.TAN),
    Input.Extended
)

fun Token.toSymbol(): String = when(this) {
    is Token.Number -> value
    is Token.Operator -> symbol
    is Token.Function -> name
    is Token.UnaryMinus -> Symbols.U_MINUS
    is Token.LeftParen -> Symbols.L_PAREN
    is Token.RightParen -> Symbols.R_PAREN
    is Token.Percent -> Symbols.PER
}

fun List<Token>.toDisplayString(): String {
    return joinToString("") { token ->
        when (token) {
            is Token.Number -> token.value
            is Token.Operator -> token.symbol
            is Token.Function -> token.name
            is Token.UnaryMinus -> Symbols.MINUS
            is Token.LeftParen -> Symbols.L_PAREN
            is Token.RightParen -> Symbols.R_PAREN
            is Token.Percent -> Symbols.PER
        }
    }
}

fun Input.toDisplayString(): String = when (this) {
    is Input.Digit -> value
    is Input.Operator -> symbol
    is Input.Function -> name
    is Input.Result -> Symbols.RES
    is Input.Extended -> Symbols.EXT
    is Input.Dot -> Symbols.DOT
    is Input.Delete -> Symbols.DELETE
    is Input.LeftParen -> Symbols.L_PAREN
    is Input.RightParen -> Symbols.R_PAREN
    is Input.Percent -> Symbols.PER
}

fun isMinus(token: Token?): Boolean {
    return (token is Token.Operator && token.symbol == Symbols.MINUS) || (token is Token.UnaryMinus)
}

@Composable
fun MainTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = TypographyStyle,
        colors = ColorStyle,
        shapes = ShapesStyle,
        content = content
    )
}

@Composable
fun ClickableBox(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentAlignment: Alignment = Alignment.Center,
    rippleRadius: Dp = Dp.Unspecified,
    rippleColor: Color = MaterialTheme.colors.primary,
    bounded: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "scale"
    )
    val wrappedOnClick = {
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        onClick()
    }
    val wrappedOnLongClick = onLongClick?.let { original ->
        {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            original()
        }
    }

    val clickModifier = when {
        onLongClick == null -> {
            Modifier.clickable(
                onClick = wrappedOnClick,
                indication = ripple(bounded, rippleRadius, rippleColor),
                interactionSource = interactionSource
            )
        }
        else -> {
            Modifier.combinedClickable(
                onClick = wrappedOnClick,
                onLongClick = wrappedOnLongClick,
                indication = ripple(bounded, rippleRadius, rippleColor),
                interactionSource = interactionSource
            )
        }
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .then(clickModifier)
    ) {
        Box(
            modifier = Modifier
                .align(contentAlignment)
                .matchParentSize(),
            contentAlignment = contentAlignment
        ) {
            content()
        }
    }
}

@Composable
fun MainText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.body1,
    fontSize: TextUnit = style.fontSize,
    textAlign: TextAlign = TextAlign.Center,
    color: Color = MaterialTheme.colors.onBackground,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    textDecoration: TextDecoration = TextDecoration.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = textAlign,
        style = style,
        color = color,
        maxLines = maxLines,
        textDecoration = textDecoration,
        overflow = overflow,
        fontSize = fontSize,
        onTextLayout = onTextLayout
    )
}