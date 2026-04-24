package say.wear.calc

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.*
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Shapes
import androidx.wear.compose.material.Typography
import kotlinx.serialization.Serializable
import say.wear.calc.Colors.FirstAccent
import say.wear.calc.Colors.GrayColor
import say.wear.calc.Colors.SecondAccent
import say.wear.calc.Colors.SecondBackground
import say.wear.calc.Colors.WhiteColor
import say.wear.calc.Symbols.COS
import say.wear.calc.Symbols.DELETE
import say.wear.calc.Symbols.DIV
import say.wear.calc.Symbols.DOT
import say.wear.calc.Symbols.EXT
import say.wear.calc.Symbols.L_PAREN
import say.wear.calc.Symbols.MINUS
import say.wear.calc.Symbols.MULTI
import say.wear.calc.Symbols.PER
import say.wear.calc.Symbols.PLUS
import say.wear.calc.Symbols.POW
import say.wear.calc.Symbols.RES
import say.wear.calc.Symbols.R_PAREN
import say.wear.calc.Symbols.SIN
import say.wear.calc.Symbols.SQRT
import say.wear.calc.Symbols.TAN
import say.wear.calc.Symbols.U_MINUS

object UIConstants {
    const val PREFS_NAME = "calc_prefs"
    const val KEY_STATE = "saved_state"
    const val KEY_TIMESTAMP = "saved_timestamp"
    val BUTTON_SIZE = 32.dp
    val DISPLAY_HEIGHT = 48.dp
    val DISPLAY_WIDTH = 64.dp
    val AMBIENT_SIZE = 128.dp
    val CURSOR_PADDING = 4.dp
    val CURSOR_WIDTH = 1.dp
    const val ROTATION_THRESHOLD = 30f
    const val NUMB_RATIO = 0.350f
    const val MATH_RATIO = 0.225f
    const val ACCELERATION_THRESHOLD = 12f
    const val ACCELERATION_DELAY = 1000
    const val SWIPE_RATIO = 0.4f
    const val RESTORE_TIMEOUT = 5 * 60 * 1000   // 5 minutes
}

object Colors {
    val SecondBackground = Color(0xFF000000)
    val FirstAccent = Color(0xFF81C784)
    val SecondAccent = Color(0xFFFF8A65)
    val WhiteColor = Color(0xFFCECECE)
    val GrayColor = Color(0xFF616161)
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
    primary = FirstAccent,
    secondary = SecondAccent,
    background = SecondBackground,
    surface = GrayColor,
    onBackground = WhiteColor,
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
    Input.Operator(PLUS),
    Input.Operator(MINUS),
    Input.Operator(MULTI),
    Input.Operator(DIV),
    Input.LeftParen,
    Input.RightParen,
    Input.Extended
)
val ExtendedMathItems: List<Input> = listOf(
    Input.Result,
    Input.Operator(POW),
    Input.Function(SQRT),
    Input.Percent,
    Input.Function(SIN),
    Input.Function(COS),
    Input.Function(TAN),
    Input.Extended
)

fun Token.toSymbol(): String = when(this) {
    is Token.Number -> value
    is Token.Operator -> symbol
    is Token.Function -> name
    is Token.UnaryMinus -> U_MINUS
    is Token.LeftParen -> L_PAREN
    is Token.RightParen -> R_PAREN
    is Token.Percent -> PER
}

fun List<Token>.toDisplayString(): String {
    return joinToString("") { token ->
        when (token) {
            is Token.Number -> token.value
            is Token.Operator -> token.symbol
            is Token.Function -> token.name
            is Token.UnaryMinus -> MINUS
            is Token.LeftParen -> L_PAREN
            is Token.RightParen -> R_PAREN
            is Token.Percent -> PER
        }
    }
}

fun Input.toDisplayString(): String = when (this) {
    is Input.Digit -> value
    is Input.Operator -> symbol
    is Input.Function -> name
    is Input.Result -> RES
    is Input.Extended -> EXT
    is Input.Dot -> DOT
    is Input.Delete -> DELETE
    is Input.LeftParen -> L_PAREN
    is Input.RightParen -> R_PAREN
    is Input.Percent -> PER
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