package say.wear.calc

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.MaterialTheme
import say.wear.calc.UIConstants.ACCELERATION_DELAY
import say.wear.calc.UIConstants.ACCELERATION_THRESHOLD
import say.wear.calc.UIConstants.CURSOR_PADDING
import say.wear.calc.UIConstants.CURSOR_WIDTH
import say.wear.calc.UIConstants.DISPLAY_HEIGHT
import say.wear.calc.UIConstants.DISPLAY_WIDTH
import say.wear.calc.UIConstants.MATH_RATIO
import say.wear.calc.UIConstants.NUMB_RATIO
import say.wear.calc.UIConstants.ROTATION_THRESHOLD
import say.wear.calc.UIConstants.SWIPE_RATIO
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        setContent {
            MainTheme {
                val context = LocalContext.current
                val haptic = LocalHapticFeedback.current
                val configuration = LocalConfiguration.current
                val density = LocalDensity.current
                val focusRequester = remember { FocusRequester() }
                var rotationAccumulator = 0f
                val thresholdPx = with(density) { (configuration.screenWidthDp * SWIPE_RATIO).dp.toPx() }
                var totalDragDistanceX by remember { mutableFloatStateOf(0f) }
                var totalDragDistanceY by remember { mutableFloatStateOf(0f) }
                var isSwipeHandled by remember { mutableStateOf(false) }
                var state by remember { mutableStateOf(CalcState()) }
                val displayResult by remember(state.tokens) {
                    derivedStateOf {
                        evaluate(state.tokens)
                    }
                }

                DisposableEffect(Unit) {
                    val sensorManager = context.getSystemService(SENSOR_SERVICE) as SensorManager
                    val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                    var lastShakeTime = 0L

                    val sensorListener = object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent?) {
                            if (event == null) return
                            val x = event.values[0]
                            val y = event.values[1]
                            val z = event.values[2]
                            val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH

                            if (acceleration > ACCELERATION_THRESHOLD && !state.tokens.isEmpty()) {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastShakeTime > ACCELERATION_DELAY) {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    state = CalcState()
                                    lastShakeTime = currentTime
                                }
                            }
                        }

                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                    }

                    if (accelerometer != null) sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)

                    onDispose {
                        if (accelerometer != null) sensorManager.unregisterListener(sensorListener)
                    }
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    totalDragDistanceX = 0f
                                    totalDragDistanceY = 0f
                                    isSwipeHandled = false
                                },
                                onDragEnd = {
                                    totalDragDistanceX = 0f
                                    totalDragDistanceY = 0f
                                    isSwipeHandled = false
                                },
                                onDragCancel = {
                                    totalDragDistanceX = 0f
                                    totalDragDistanceY = 0f
                                    isSwipeHandled = false
                                },
                                onDrag = { change, dragAmount ->
                                    if (isSwipeHandled) return@detectDragGestures
                                    totalDragDistanceX += dragAmount.x
                                    totalDragDistanceY += dragAmount.y

                                    if (abs(totalDragDistanceX) > thresholdPx || abs(totalDragDistanceY) > thresholdPx) {
                                        if (abs(totalDragDistanceX) > abs(totalDragDistanceY)) {
                                            if (totalDragDistanceX < -thresholdPx) {
                                                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                                state = reduce(state, Input.Delete)
                                                isSwipeHandled = true
                                            }
                                        } else {
                                            if (totalDragDistanceY < -thresholdPx) {
                                                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                                state = state.copy(isExtended = !state.isExtended)
                                                isSwipeHandled = true
                                            }
                                        }
                                        change.consume()
                                    }
                                }
                            )
                        }
                        .onRotaryScrollEvent { rotaryEvent ->
                            rotationAccumulator += rotaryEvent.verticalScrollPixels
                            if (abs(rotationAccumulator) >= ROTATION_THRESHOLD) {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
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
    CircularPad(
        modifier = modifier,
        items = NumberItems,
        radiusRatio = NUMB_RATIO,
        contentColor = MaterialTheme.colors.primary,
        onClick = onClick,
        onLongClick = { if (it is Input.Delete) onLongClick() }
    )
}

@Composable
fun CircularMathPad(
    modifier: Modifier = Modifier,
    isExtended: Boolean,
    onClick: (Input) -> Unit
) {
    CircularPad(
        modifier = modifier,
        items = if (isExtended) ExtendedMathItems else BaseMathItems,
        radiusRatio = MATH_RATIO,
        contentColor = MaterialTheme.colors.secondary,
        onClick = onClick
    )
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

    Column(modifier = Modifier.height(DISPLAY_HEIGHT).width(DISPLAY_WIDTH)) {
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
                                .width(CURSOR_WIDTH)
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
        ) { MainText(text = displayResult, maxLines = Int.MAX_VALUE) }
    }
}