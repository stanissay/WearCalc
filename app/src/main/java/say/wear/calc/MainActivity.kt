package say.wear.calc

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material.MaterialTheme
import kotlinx.serialization.json.Json
import say.wear.calc.UIConstants.ACCELERATION_DELAY
import say.wear.calc.UIConstants.ACCELERATION_THRESHOLD
import say.wear.calc.UIConstants.AMBIENT_SIZE
import say.wear.calc.UIConstants.BUTTON_SIZE
import say.wear.calc.UIConstants.CURSOR_PADDING
import say.wear.calc.UIConstants.CURSOR_WIDTH
import say.wear.calc.UIConstants.DISPLAY_HEIGHT
import say.wear.calc.UIConstants.DISPLAY_WIDTH
import say.wear.calc.UIConstants.KEY_STATE
import say.wear.calc.UIConstants.KEY_TIMESTAMP
import say.wear.calc.UIConstants.MATH_RATIO
import say.wear.calc.UIConstants.NUMB_RATIO
import say.wear.calc.UIConstants.PAD_ANIMATION_DURATION
import say.wear.calc.UIConstants.PREFS_NAME
import say.wear.calc.UIConstants.RESTORE_TIMEOUT
import say.wear.calc.UIConstants.ROTATION_THRESHOLD
import say.wear.calc.UIConstants.SCREEN_ANIMATION_DURATION
import say.wear.calc.UIConstants.SWIPE_RATIO
import say.wear.calc.UIConstants.TEXT_ANIMATION_DURATION
import kotlin.math.*

class MainActivity : ComponentActivity() {
    private var state by mutableStateOf(CalcState())

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        restoreState()
        setTheme(android.R.style.Theme_DeviceDefault)
        setContent {
            MainTheme {
                CalcScreen(
                    state = state,
                    onStateChange = { newState -> state = newState },
                    onFinish = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        restoreState()
    }

    override fun onPause() {
        super.onPause()
        saveState()
    }

    override fun onStop() {
        super.onStop()
        saveState()
    }

    private fun saveState() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val json = Json.encodeToString(state)

        prefs.edit {
            putString(KEY_STATE, json)
            putLong(KEY_TIMESTAMP, SystemClock.elapsedRealtime())
        }
    }

    private fun restoreState() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lastTime = prefs.getLong(KEY_TIMESTAMP, 0L)

        if (SystemClock.elapsedRealtime() - lastTime < RESTORE_TIMEOUT) {
            val json = prefs.getString(KEY_STATE, null)
            if (json != null) {
                state = try {
                    Json.decodeFromString<CalcState>(json)
                } catch (_: Exception) {
                    CalcState()
                }
            }
        } else {
            state = CalcState()
        }
    }
}

@Composable
fun CalcScreen(
    state: CalcState,
    onStateChange: (CalcState) -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentState by rememberUpdatedState(state)
    val ambientState = remember { mutableStateOf(false) }
    val displayResult by remember(currentState.tokens) { derivedStateOf { evaluate(currentState.tokens) } }

    DisposableEffect(lifecycleOwner) {
        val observer = AmbientLifecycleObserver(
            context as Activity,
            callbacks = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
                override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) { ambientState.value = true }
                override fun onExitAmbient() { ambientState.value = false }
                override fun onUpdateAmbient() {}
            }
        )
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastShakeTime = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val accel = sqrt(event.values[0].pow(2) + event.values[1].pow(2) + event.values[2].pow(2)) - SensorManager.GRAVITY_EARTH
                if (accel > ACCELERATION_THRESHOLD && currentState.tokens.isNotEmpty()) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastShakeTime > ACCELERATION_DELAY) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onStateChange(CalcState())
                        lastShakeTime = currentTime
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    Crossfade(
        targetState = ambientState.value,
        animationSpec = tween(durationMillis = SCREEN_ANIMATION_DURATION, easing = LinearOutSlowInEasing),
        label = "ambientTransition"
    ) { isAmbient ->
        if (isAmbient) {
            AmbientDisplay(
                displayResult = displayResult.ifBlank { currentState.tokens.toDisplayString() }
            )
        } else {
            ActiveDisplay(
                state = state,
                displayResult = displayResult,
                onStateChange = onStateChange,
                onFinish = onFinish
            )
        }
    }
}

@Composable
fun ActiveDisplay(
    state: CalcState,
    displayResult: String,
    onStateChange: (CalcState) -> Unit,
    onFinish: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val currentState by rememberUpdatedState(state)
    val focusRequester = remember { FocusRequester() }
    val thresholdPx = with(density) { (configuration.screenWidthDp * SWIPE_RATIO).dp.toPx() }
    var rotationAccumulator = 0f
    var totalDragDistanceX by remember { mutableFloatStateOf(0f) }
    var totalDragDistanceY by remember { mutableFloatStateOf(0f) }
    var isSwipeHandled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { totalDragDistanceX = 0f; totalDragDistanceY = 0f; isSwipeHandled = false },
                    onDrag = { change, dragAmount ->
                        if (isSwipeHandled) return@detectDragGestures
                        totalDragDistanceX += dragAmount.x
                        totalDragDistanceY += dragAmount.y

                        if (abs(totalDragDistanceX) > thresholdPx || abs(totalDragDistanceY) > thresholdPx) {
                            isSwipeHandled = true
                            change.consume()

                            if (abs(totalDragDistanceX) > abs(totalDragDistanceY)) {
                                if (totalDragDistanceX < -thresholdPx) {
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    onStateChange(reduce(currentState, Input.Delete))
                                } else if (totalDragDistanceX > thresholdPx) {
                                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOff)
                                    onFinish()
                                }
                            } else if (totalDragDistanceY < -thresholdPx) {
                                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                onStateChange(currentState.copy(isExtended = !currentState.isExtended))
                            }
                        }
                    }
                )
            }
            .onRotaryScrollEvent { rotaryEvent ->
                rotationAccumulator += rotaryEvent.verticalScrollPixels
                if (abs(rotationAccumulator) >= ROTATION_THRESHOLD) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onStateChange(moveCursor(currentState, if (rotationAccumulator > 0) 1 else -1))
                    rotationAccumulator = 0f
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        CircularNumberPad(
            onClick = { input -> onStateChange(reduce(currentState, input)) },
            onLongClick = { onStateChange(CalcState()) }
        )
        CircularMathPad(
            isExtended = currentState.isExtended,
            onClick = { input -> onStateChange(reduce(currentState, input)) }
        )
        CenterDisplay(
            state = currentState,
            displayResult = displayResult,
            onClick = { input -> onStateChange(reduce(currentState, input)) },
        )
    }
}

@Composable
fun AmbientDisplay(displayResult: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(AMBIENT_SIZE),
            contentAlignment = Alignment.Center
        ) { MainText(text = displayResult, maxLines = Int.MAX_VALUE) }
    }
}

@Composable
fun CircularNumberPad(
    modifier: Modifier = Modifier,
    onClick: (Input) -> Unit,
    onLongClick: (Input) -> Unit
) {
    CircularPad(
        modifier = modifier,
        items = NumberItems,
        radiusRatio = NUMB_RATIO,
        contentColor = MaterialTheme.colors.primary,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@Composable
fun CircularMathPad(
    modifier: Modifier = Modifier,
    isExtended: Boolean,
    onClick: (Input) -> Unit
) {
    AnimatedContent(
        targetState = isExtended,
        transitionSpec = {
            slideInVertically(animationSpec = tween(PAD_ANIMATION_DURATION, easing = FastOutSlowInEasing)) { it / 2 } +
                    fadeIn(animationSpec = tween(PAD_ANIMATION_DURATION)) togetherWith
                    slideOutVertically(animationSpec = tween(PAD_ANIMATION_DURATION, easing = FastOutSlowInEasing)) { -it / 2 } +
                    fadeOut(animationSpec = tween(PAD_ANIMATION_DURATION))
        },
        modifier = modifier,
        label = "mathPadTransition"
    ) { extended ->
        CircularPad(
            items = if (extended) ExtendedMathItems else BaseMathItems,
            radiusRatio = MATH_RATIO,
            contentColor = MaterialTheme.colors.secondary,
            onClick = onClick
        )
    }
}

@Composable
fun CenterDisplay(
    state: CalcState,
    displayResult: String,
    onClick: (Input) -> Unit
) {
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

    ClickableBox(
        shape = MaterialTheme.shapes.medium,
        rippleColor = Color.Transparent,
        onClick = { onClick(Input.Result) }
    ) {
        Column(modifier = Modifier.height(DISPLAY_HEIGHT).width(DISPLAY_WIDTH)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .horizontalScroll(inputScrollState),
                contentAlignment = Alignment.CenterStart
            ) {
                AnimatedContent(
                    targetState = text,
                    transitionSpec = {
                        scaleIn(animationSpec = tween(TEXT_ANIMATION_DURATION), initialScale = 0.9f) + fadeIn(tween(TEXT_ANIMATION_DURATION)) togetherWith
                                scaleOut(animationSpec = tween(TEXT_ANIMATION_DURATION), targetScale = 1f) + fadeOut(tween(TEXT_ANIMATION_DURATION))
                    },
                    label = "deleteAnimation"
                ) { text ->
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
            }
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(resultScrollState),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = displayResult,
                    transitionSpec = {
                        if (targetState.isBlank()) {
                            (fadeIn(animationSpec = tween(TEXT_ANIMATION_DURATION, easing = FastOutSlowInEasing)) +
                                    scaleIn(initialScale = 1f, animationSpec = tween(TEXT_ANIMATION_DURATION, easing = FastOutSlowInEasing))) togetherWith
                                    (fadeOut(animationSpec = tween(TEXT_ANIMATION_DURATION, easing = FastOutSlowInEasing)) +
                                            scaleOut(targetScale = 0.9f, animationSpec = tween(TEXT_ANIMATION_DURATION, easing = FastOutSlowInEasing)))
                        } else {
                            scaleIn(animationSpec = tween(TEXT_ANIMATION_DURATION), initialScale = 0.9f) + fadeIn(tween(TEXT_ANIMATION_DURATION)) togetherWith
                                    scaleOut(animationSpec = tween(TEXT_ANIMATION_DURATION), targetScale = 1f) + fadeOut(tween(TEXT_ANIMATION_DURATION))
                        }
                    },
                    label = "clearAnimation"
                ) { text -> MainText(text = text, maxLines = Int.MAX_VALUE) }
            }
        }
    }
}

@Composable
fun CircularPad(
    modifier: Modifier = Modifier,
    items: List<Input>,
    radiusRatio: Float,
    contentColor: Color,
    onClick: (Input) -> Unit,
    onLongClick: ((Input) -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val sizePx = constraints.maxWidth.toFloat()
        val center = sizePx / 2f
        val radius = sizePx * radiusRatio
        val angleStep = (2 * PI) / items.size

        items.forEachIndexed { index, input ->
            val angle = angleStep * index - PI / 2
            val x = center + radius * cos(angle).toFloat()
            val y = center + radius * sin(angle).toFloat()

            ClickableBox(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (x - (BUTTON_SIZE / 2).toPx()).toInt(),
                            (y - (BUTTON_SIZE / 2).toPx()).toInt()
                        )
                    }
                    .size(BUTTON_SIZE),
                onClick = { onClick(input) },
                onLongClick = if (input == Input.Delete) { { onLongClick?.invoke(input) } } else null
            ) { MainText(text = input.toDisplayString(), color = contentColor) }
        }
    }
}