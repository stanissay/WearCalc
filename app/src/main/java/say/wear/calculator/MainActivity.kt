package say.wear.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.MaterialTheme
import say.wear.calculator.Symbols.DELETE
import say.wear.calculator.Symbols.DOT
import say.wear.calculator.Symbols.EXT
import say.wear.calculator.Symbols.L_PAREN
import say.wear.calculator.Symbols.PER
import say.wear.calculator.Symbols.RES
import say.wear.calculator.Symbols.R_PAREN
import say.wear.calculator.UIConstants.BUTTON_SIZE
import say.wear.calculator.UIConstants.CURSOR_PADDING
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        setContent {
            MainTheme {
                val focusRequester = remember { FocusRequester() }
                var rotationAccumulator = 0f
                val rotationThreshold = 30f
                var state by remember { mutableStateOf(CalcState()) }
                val displayResult by remember(state.tokens) {
                    derivedStateOf {
                        evaluate(state.tokens)
                    }
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background)
                        .onRotaryScrollEvent { rotaryEvent ->
                            rotationAccumulator += rotaryEvent.verticalScrollPixels
                            if (abs(rotationAccumulator) >= rotationThreshold) {
                                val direction = if (rotationAccumulator > 0) 1 else -1
                                state = moveCursor(state, direction)
                                rotationAccumulator = 0f
                            }
                            true
                        }
                        .focusRequester(focusRequester)
                        .focusable(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularNumberPad(
                        modifier = Modifier,
                        onClick = { input -> state = reduce(state, input) },
                        onLongClick = { state = CalcState() }
                    )
                    CircularMathPad(
                        modifier = Modifier,
                        isExtended = state.isExtended,
                        onClick = { input -> state = reduce(state, input) }
                    )
                    CenterDisplay(state = state, displayResult = displayResult)
                }
            }
        }
    }
}

@Composable
fun CircularNumberPad(
    modifier: Modifier = Modifier,
    onClick: (Input) -> Unit,
    onLongClick: () -> Unit
) {
    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val sizePx = constraints.maxWidth.toFloat()
        val center = sizePx / 2f
        val radius = sizePx * 0.35f
        val angleStep = (2 * Math.PI) / NumberItems.size

        NumberItems.forEachIndexed { index, input ->
            val angle = angleStep * index - Math.PI / 2
            val x = center + radius * cos(angle).toFloat()
            val y = center + radius * sin(angle).toFloat()
            val text = when (input) {
                is Input.Digit -> input.value
                is Input.Operator -> input.symbol
                is Input.Function -> input.name
                is Input.Result -> RES
                is Input.Extended -> EXT
                is Input.Dot -> DOT
                is Input.Delete -> DELETE
                is Input.LeftParen -> L_PAREN
                is Input.RightParen -> R_PAREN
                is Input.Percent -> PER
            }

            ClickableBox(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (x - (BUTTON_SIZE/2).toPx()).toInt(),
                            (y - (BUTTON_SIZE/2).toPx()).toInt()
                        )
                    }
                    .size(BUTTON_SIZE),
                onClick = { onClick(input) },
                onLongClick = { if(input is Input.Delete) onLongClick() }
            ) { MainText(text = text, color = MaterialTheme.colors.primary) }
        }
    }
}

@Composable
fun CircularMathPad(
    modifier: Modifier = Modifier,
    isExtended: Boolean,
    onClick: (Input) -> Unit
) {
    val items = if (isExtended) ExtendedMathItems else BaseMathItems

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val sizePx = constraints.maxWidth.toFloat()
        val center = sizePx / 2f
        val radius = sizePx * 0.225f
        val angleStep = (2 * Math.PI) / items.size

        items.forEachIndexed { index, input ->
            val angle = angleStep * index - Math.PI / 2
            val x = center + radius * cos(angle).toFloat()
            val y = center + radius * sin(angle).toFloat()
            val text = when (input) {
                is Input.Digit -> input.value
                is Input.Operator -> input.symbol
                is Input.Function -> input.name
                is Input.Result -> RES
                is Input.Extended -> EXT
                is Input.Dot -> DOT
                is Input.Delete -> DELETE
                is Input.LeftParen -> L_PAREN
                is Input.RightParen -> R_PAREN
                is Input.Percent -> PER
            }

            ClickableBox(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (x - (BUTTON_SIZE/2).toPx()).toInt(),
                            (y - (BUTTON_SIZE/2).toPx()).toInt()
                        )
                    }
                    .size(BUTTON_SIZE),
                onClick = { onClick(input) }
            ) { MainText(text = text, color = MaterialTheme.colors.secondary) }
        }
    }
}

@Composable
fun CenterDisplay(state: CalcState, displayResult: String) {
    val density = LocalDensity.current
    val inputScrollState = rememberScrollState()
    val resultScrollState = rememberScrollState()
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val text = state.tokens.toDisplayString()
    val stringCursorIndex = run {
        val base = state.tokens
            .take(state.cursor.tokenIndex)
            .toDisplayString()
            .length

        val current = state.tokens.getOrNull(state.cursor.tokenIndex)

        if (current is Token.Number) {
            base + state.cursor.offset.coerceIn(0, current.value.length)
        } else {
            base
        }
    }

    LaunchedEffect(stringCursorIndex, layoutResult) {
        val layout = layoutResult ?: return@LaunchedEffect

        val rect = layout.getCursorRect(stringCursorIndex)
        val cursorX = rect.left

        val target = (cursorX + with(density) { CURSOR_PADDING.toPx() }).toInt()

        if (target != inputScrollState.value) {
            inputScrollState.animateScrollTo(target)
        }
    }

    Column(modifier = Modifier.height(48.dp).width(64.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(inputScrollState),
            contentAlignment = Alignment.CenterStart
        ) {
            MainText(
                modifier = Modifier.padding(horizontal = CURSOR_PADDING),
                text = text,
                onTextLayout = { layoutResult = it }
            )

            val layout = layoutResult
            if (layout != null && text.isNotEmpty()) {
                if (stringCursorIndex <= layout.layoutInput.text.length) {
                    val cursorRect = runCatching {
                        layout.getCursorRect(stringCursorIndex)
                    }.getOrNull()

                    cursorRect?.let { rect ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = CURSOR_PADDING)
                                .offset { IntOffset(rect.left.toInt(), 0) }
                                .width(1.dp)
                                .height(with(density) { rect.height.toDp() })
                                .background(MaterialTheme.colors.surface)
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(resultScrollState),
            contentAlignment = Alignment.Center
        ) {
            MainText(
                text = displayResult,
                maxLines = Int.MAX_VALUE
            )
        }
    }
}