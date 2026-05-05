package com.example.toothtimer

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class CleaningStepConfig(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val durationSec: Int,
    val zoneType: ZoneType = ZoneType.CUSTOM
)

enum class ZoneType {
    CHEW_TOP_RIGHT,
    CHEW_TOP_LEFT,
    CHEW_BOTTOM_RIGHT,
    CHEW_BOTTOM_LEFT,
    OUTER_TOP_RIGHT,
    OUTER_TOP_LEFT,
    OUTER_BOTTOM_RIGHT,
    OUTER_BOTTOM_LEFT,
    FRONT_TOP,
    FRONT_BOTTOM,
    CUSTOM
}

data class CleaningHistoryItem(
    val startedAtMillis: Long,
    val durationSec: Int,
    val status: SessionStatus,
    val completedSteps: Int,
    val totalSteps: Int
)

enum class SessionStatus {
    COMPLETED,
    INTERRUPTED
}

enum class Screen {
    HOME,
    CLEANING,
    HISTORY,
    SETTINGS,
    DONE
}

enum class CleaningPhase {
    STEP,
    BETWEEN_STEPS,
    COMPLETED
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ToothTimerApp()
        }
    }
}

@Composable
fun ToothTimerApp() {
    val context = LocalContext.current
    val storage = remember { AppStorage(context) }
    val steps = remember { mutableStateListOf<CleaningStepConfig>().apply { addAll(storage.loadSteps()) } }
    val history = remember { mutableStateListOf<CleaningHistoryItem>().apply { addAll(storage.loadHistory()) } }
    var currentScreen by remember { mutableStateOf(Screen.HOME) }

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            when (currentScreen) {
                Screen.HOME -> HomeScreen(
                    onStart = { currentScreen = Screen.CLEANING },
                    onHistory = { currentScreen = Screen.HISTORY },
                    onSettings = { currentScreen = Screen.SETTINGS }
                )

                Screen.CLEANING -> CleaningScreen(
                    steps = steps,
                    pauseBetweenZonesSec = storage.loadPauseSec(),
                    onCompleted = { durationSec ->
                        val item = CleaningHistoryItem(
                            startedAtMillis = System.currentTimeMillis() - durationSec * 1000L,
                            durationSec = durationSec,
                            status = SessionStatus.COMPLETED,
                            completedSteps = steps.size,
                            totalSteps = steps.size
                        )
                        history.add(0, item)
                        storage.saveHistory(history)
                        currentScreen = Screen.DONE
                    },
                    onInterrupted = { durationSec, completedSteps ->
                        val item = CleaningHistoryItem(
                            startedAtMillis = System.currentTimeMillis() - durationSec * 1000L,
                            durationSec = durationSec,
                            status = SessionStatus.INTERRUPTED,
                            completedSteps = completedSteps,
                            totalSteps = steps.size
                        )
                        history.add(0, item)
                        storage.saveHistory(history)
                        currentScreen = Screen.HOME
                    }
                )

                Screen.DONE -> DoneScreen(
                    onHome = { currentScreen = Screen.HOME }
                )

                Screen.HISTORY -> HistoryScreen(
                    history = history,
                    onBack = { currentScreen = Screen.HOME },
                    onClear = {
                        history.clear()
                        storage.saveHistory(history)
                    }
                )

                Screen.SETTINGS -> SettingsScreen(
                    steps = steps,
                    pauseSec = storage.loadPauseSec(),
                    onBack = { currentScreen = Screen.HOME },
                    onStepsChanged = { storage.saveSteps(steps) },
                    onPauseChanged = { storage.savePauseSec(it) },
                    onResetDefaults = {
                        steps.clear()
                        steps.addAll(defaultSteps())
                        storage.saveSteps(steps)
                        storage.savePauseSec(2)
                    }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    onStart: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Зубной таймер",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(36.dp))

        ToothIcon(modifier = Modifier.size(140.dp))

        Spacer(Modifier.height(36.dp))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("НАЧАТЬ ЧИСТКУ", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(18.dp))

        OutlinedButton(
            onClick = onHistory,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("История", fontSize = 18.sp)
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Настройки", fontSize = 18.sp)
        }
    }
}

@Composable
fun CleaningScreen(
    steps: List<CleaningStepConfig>,
    pauseBetweenZonesSec: Int,
    onCompleted: (durationSec: Int) -> Unit,
    onInterrupted: (durationSec: Int, completedSteps: Int) -> Unit
) {
    if (steps.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Нет зон чистки. Добавь зоны в настройках.")
        }
        return
    }

    var phase by remember { mutableStateOf(CleaningPhase.STEP) }
    var stepIndex by remember { mutableIntStateOf(0) }
    var timeLeft by remember { mutableIntStateOf(steps[0].durationSec) }
    var pauseLeft by remember { mutableIntStateOf(pauseBetweenZonesSec) }
    var isPaused by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableIntStateOf(0) }
    var showExitDialog by remember { mutableStateOf(false) }

    val currentStep = steps[stepIndex]
    val totalDuration = steps.sumOf { it.durationSec } + pauseBetweenZonesSec * (steps.size - 1)
    val progress = if (totalDuration > 0) elapsedSec.toFloat() / totalDuration.toFloat() else 0f

    LaunchedEffect(phase, stepIndex, isPaused) {
        if (isPaused) return@LaunchedEffect

        if (phase == CleaningPhase.STEP) {
            while (timeLeft > 0 && !isPaused) {
                delay(1000)
                timeLeft -= 1
                elapsedSec += 1
            }

            if (timeLeft <= 0 && !isPaused) {
                if (stepIndex >= steps.lastIndex) {
                    phase = CleaningPhase.COMPLETED
                    onCompleted(elapsedSec)
                } else {
                    phase = CleaningPhase.BETWEEN_STEPS
                    pauseLeft = pauseBetweenZonesSec
                }
            }
        }

        if (phase == CleaningPhase.BETWEEN_STEPS) {
            while (pauseLeft > 0 && !isPaused) {
                delay(1000)
                pauseLeft -= 1
                elapsedSec += 1
            }

            if (pauseLeft <= 0 && !isPaused) {
                stepIndex += 1
                timeLeft = steps[stepIndex].durationSec
                phase = CleaningPhase.STEP
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Завершить чистку?") },
            text = { Text("Текущая чистка сохранится как прерванная.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onInterrupted(elapsedSec, stepIndex)
                }) {
                    Text("Завершить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Продолжить")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { showExitDialog = true }) {
                Text("✕", fontSize = 28.sp, color = Color.Black)
            }
            Text(
                text = "${stepIndex + 1} из ${steps.size}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = { isPaused = !isPaused }) {
                Text(if (isPaused) "▶" else "Ⅱ", fontSize = 28.sp, color = Color.Black)
            }
        }

        Spacer(Modifier.height(20.dp))

        if (phase == CleaningPhase.BETWEEN_STEPS) {
            Text(
                text = "Готовимся к следующей зоне",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            CircularTimer(value = pauseLeft, label = "секунды", color = Color(0xFF2F76D2))
            Spacer(Modifier.height(24.dp))
            Text("Следующая зона:", fontSize = 16.sp, color = Color.DarkGray)
            Text(
                text = steps[stepIndex + 1].title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = currentStep.title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
            TeethMap(
                activeZone = currentStep.zoneType,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            )
            Spacer(Modifier.height(18.dp))
            CircularTimer(value = timeLeft, label = "секунд", color = Color(0xFF2F76D2))
        }

        Spacer(Modifier.height(24.dp))

        ProgressBar(progress = progress)

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = { isPaused = !isPaused },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isPaused) "Продолжить" else "Пауза")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = { showExitDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
            ) {
                Text("Завершить")
            }
        }
    }
}

@Composable
fun DoneScreen(onHome: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("✓", fontSize = 96.sp, color = Color(0xFF2E9D3F))
        Spacer(Modifier.height(24.dp))
        Text(
            "Чистка завершена!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Отличная работа. Ты сделал свои зубы чище.",
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("НА ГЛАВНЫЙ ЭКРАН", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HistoryScreen(
    history: List<CleaningHistoryItem>,
    onBack: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("←", fontSize = 28.sp, color = Color.Black) }
            Text(
                "История",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onClear) { Text("Очистить") }
        }

        Spacer(Modifier.height(16.dp))

        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Пока нет сохранённых чисток")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(history) { _, item ->
                    HistoryCard(item)
                }
            }
        }
    }
}

@Composable
fun HistoryCard(item: CleaningHistoryItem) {
    val formatterDate = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    val formatterTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val date = Date(item.startedAtMillis)
    val statusText = if (item.status == SessionStatus.COMPLETED) "✓" else "!"
    val statusColor = if (item.status == SessionStatus.COMPLETED) Color(0xFF2E9D3F) else Color(0xFFF4A62A)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F7)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(formatterDate.format(date), fontWeight = FontWeight.Bold)
                Text(formatterTime.format(date), color = Color.DarkGray)
            }
            Text(formatDuration(item.durationSec), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            Text(statusText, fontSize = 26.sp, color = statusColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SettingsScreen(
    steps: MutableList<CleaningStepConfig>,
    pauseSec: Int,
    onBack: () -> Unit,
    onStepsChanged: () -> Unit,
    onPauseChanged: (Int) -> Unit,
    onResetDefaults: () -> Unit
) {
    var currentPause by remember { mutableIntStateOf(pauseSec) }
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Сбросить настройки?") },
            text = { Text("Список зон и пауза вернутся к значениям по умолчанию.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    currentPause = 2
                    onResetDefaults()
                }) {
                    Text("Сбросить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("←", fontSize = 28.sp, color = Color.Black) }
            Text(
                "Настройки",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Пауза между зонами", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                CounterRow(
                    title = "Длительность паузы",
                    value = currentPause,
                    suffix = "сек",
                    min = 0,
                    max = 30,
                    onChange = {
                        currentPause = it
                        onPauseChanged(it)
                    }
                )
            }

            item {
                Divider()
                Spacer(Modifier.height(8.dp))
                Text("Зоны чистки", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Можно менять названия, длительность и порядок зон.",
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )
            }

            itemsIndexed(steps, key = { _, step -> step.id }) { index, step ->
                StepSettingsCard(
                    index = index,
                    total = steps.size,
                    step = step,
                    onTitleChange = { newTitle ->
                        steps[index] = step.copy(title = newTitle)
                        onStepsChanged()
                    },
                    onDurationChange = { newDuration ->
                        steps[index] = step.copy(durationSec = newDuration)
                        onStepsChanged()
                    },
                    onMoveUp = {
                        if (index > 0) {
                            val item = steps.removeAt(index)
                            steps.add(index - 1, item)
                            onStepsChanged()
                        }
                    },
                    onMoveDown = {
                        if (index < steps.lastIndex) {
                            val item = steps.removeAt(index)
                            steps.add(index + 1, item)
                            onStepsChanged()
                        }
                    },
                    onDelete = {
                        if (steps.size > 1) {
                            steps.removeAt(index)
                            onStepsChanged()
                        }
                    }
                )
            }

            item {
                Button(
                    onClick = {
                        steps.add(
                            CleaningStepConfig(
                                title = "Новая зона",
                                durationSec = 10,
                                zoneType = ZoneType.CUSTOM
                            )
                        )
                        onStepsChanged()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Добавить зону")
                }
            }

            item {
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
                ) {
                    Text("Сбросить настройки")
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    "Версия 1.0.0",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun StepSettingsCard(
    index: Int,
    total: Int,
    step: CleaningStepConfig,
    onTitleChange: (String) -> Unit,
    onDurationChange: (Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F7)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2F76D2),
                    modifier = Modifier.width(26.dp)
                )
                OutlinedTextField(
                    value = step.title,
                    onValueChange = onTitleChange,
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    label = { Text("Название зоны") }
                )
            }

            Spacer(Modifier.height(10.dp))

            CounterRow(
                title = "Время",
                value = step.durationSec,
                suffix = "сек",
                min = 5,
                max = 120,
                onChange = onDurationChange
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onMoveUp, enabled = index > 0) { Text("↑ Выше") }
                TextButton(onClick = onMoveDown, enabled = index < total - 1) { Text("↓ Ниже") }
                TextButton(onClick = onDelete, enabled = total > 1) { Text("Удалить", color = Color(0xFFD32F2F)) }
            }
        }
    }
}

@Composable
fun CounterRow(
    title: String,
    value: Int,
    suffix: String,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF7F7F7), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 5).coerceAtLeast(min)) }) {
            Text("−", fontSize = 24.sp)
        }
        Text("$value $suffix", fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + 5).coerceAtMost(max)) }) {
            Text("+", fontSize = 24.sp)
        }
    }
}

@Composable
fun CircularTimer(value: Int, label: String, color: Color) {
    Box(
        modifier = Modifier.size(150.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFFE0E0E0),
                style = Stroke(width = 12f)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 12f)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), fontSize = 42.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 14.sp)
        }
    }
}

@Composable
fun ProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .background(Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(12.dp)
                .background(Color(0xFF2F76D2), RoundedCornerShape(8.dp))
        )
    }
}

@Composable
fun ToothIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 8f)
        val blue = Color(0xFF2F76D2)
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            cubicTo(w * 0.18f, h * 0.02f, w * 0.08f, h * 0.25f, w * 0.18f, h * 0.45f)
            cubicTo(w * 0.28f, h * 0.62f, w * 0.22f, h * 0.92f, w * 0.38f, h * 0.88f)
            cubicTo(w * 0.46f, h * 0.86f, w * 0.45f, h * 0.68f, w * 0.5f, h * 0.68f)
            cubicTo(w * 0.55f, h * 0.68f, w * 0.54f, h * 0.86f, w * 0.62f, h * 0.88f)
            cubicTo(w * 0.78f, h * 0.92f, w * 0.72f, h * 0.62f, w * 0.82f, h * 0.45f)
            cubicTo(w * 0.92f, h * 0.25f, w * 0.82f, h * 0.02f, w * 0.5f, h * 0.08f)
            close()
        }
        drawPath(path, color = blue, style = stroke)
        drawCircle(Color.Black, radius = 5f, center = Offset(w * 0.40f, h * 0.42f))
        drawCircle(Color.Black, radius = 5f, center = Offset(w * 0.60f, h * 0.42f))
        drawArc(Color.Black, startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = Offset(w * 0.38f, h * 0.47f), size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.18f), style = Stroke(width = 5f))
    }
}

@Composable
fun TeethMap(activeZone: ZoneType, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val blue = Color(0xFF5A9BE8)
        val gray = Color(0xFF222222)
        val inactive = Color.White
        val center = Offset(size.width / 2f, size.height / 2f)
        val radiusX = size.width * 0.32f
        val radiusY = size.height * 0.34f
        val toothRadius = size.minDimension * 0.045f

        val activeTop = when (activeZone) {
            ZoneType.CHEW_TOP_RIGHT, ZoneType.OUTER_TOP_RIGHT -> setOf(0, 1, 2)
            ZoneType.CHEW_TOP_LEFT, ZoneType.OUTER_TOP_LEFT -> setOf(7, 8, 9)
            ZoneType.FRONT_TOP -> setOf(3, 4, 5, 6)
            else -> emptySet()
        }
        val activeBottom = when (activeZone) {
            ZoneType.CHEW_BOTTOM_RIGHT, ZoneType.OUTER_BOTTOM_RIGHT -> setOf(0, 1, 2)
            ZoneType.CHEW_BOTTOM_LEFT, ZoneType.OUTER_BOTTOM_LEFT -> setOf(7, 8, 9)
            ZoneType.FRONT_BOTTOM -> setOf(3, 4, 5, 6)
            else -> emptySet()
        }

        fun drawTooth(pos: Offset, active: Boolean) {
            drawCircle(
                color = if (active) blue else inactive,
                radius = toothRadius,
                center = pos
            )
            drawCircle(
                color = gray,
                radius = toothRadius,
                center = pos,
                style = Stroke(width = 2.5f)
            )
        }

        for (i in 0 until 10) {
            val angle = PI + (i / 9.0) * PI
            val x = center.x + cos(angle).toFloat() * radiusX
            val y = center.y + sin(angle).toFloat() * radiusY - size.height * 0.07f
            drawTooth(Offset(x, y), i in activeTop)
        }

        for (i in 0 until 10) {
            val angle = 0.0 + (i / 9.0) * PI
            val x = center.x + cos(angle).toFloat() * radiusX
            val y = center.y + sin(angle).toFloat() * radiusY + size.height * 0.07f
            drawTooth(Offset(x, y), i in activeBottom)
        }

        drawContext.canvas.nativeCanvas.apply {
            drawText(
                "схема зон",
                center.x,
                size.height - 12f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }
    }
}

fun defaultSteps(): List<CleaningStepConfig> = listOf(
    CleaningStepConfig(
        title = "Жевательная поверхность — верх справа",
        durationSec = 20,
        zoneType = ZoneType.CHEW_TOP_RIGHT
    ),
    CleaningStepConfig(
        title = "Жевательная поверхность — верх слева",
        durationSec = 20,
        zoneType = ZoneType.CHEW_TOP_LEFT
    ),
    CleaningStepConfig(
        title = "Жевательная поверхность — низ справа",
        durationSec = 20,
        zoneType = ZoneType.CHEW_BOTTOM_RIGHT
    ),
    CleaningStepConfig(
        title = "Жевательная поверхность — низ слева",
        durationSec = 20,
        zoneType = ZoneType.CHEW_BOTTOM_LEFT
    ),
    CleaningStepConfig(
        title = "Внешняя боковая поверхность — верх справа",
        durationSec = 10,
        zoneType = ZoneType.OUTER_TOP_RIGHT
    ),
    CleaningStepConfig(
        title = "Внешняя боковая поверхность — верх слева",
        durationSec = 10,
        zoneType = ZoneType.OUTER_TOP_LEFT
    ),
    CleaningStepConfig(
        title = "Внешняя боковая поверхность — низ справа",
        durationSec = 10,
        zoneType = ZoneType.OUTER_BOTTOM_RIGHT
    ),
    CleaningStepConfig(
        title = "Внешняя боковая поверхность — низ слева",
        durationSec = 10,
        zoneType = ZoneType.OUTER_BOTTOM_LEFT
    ),
    CleaningStepConfig(
        title = "Передние зубы — сверху",
        durationSec = 15,
        zoneType = ZoneType.FRONT_TOP
    ),
    CleaningStepConfig(
        title = "Передние зубы — снизу",
        durationSec = 15,
        zoneType = ZoneType.FRONT_BOTTOM
    )
)

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val sec = seconds % 60
    return if (minutes > 0) "${minutes} мин ${sec} сек" else "${sec} сек"
}

class AppStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tooth_timer", Context.MODE_PRIVATE)

    fun loadSteps(): List<CleaningStepConfig> {
        val raw = prefs.getString("steps", null) ?: return defaultSteps()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val obj = array.getJSONObject(index)
                CleaningStepConfig(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.getString("title"),
                    durationSec = obj.getInt("durationSec"),
                    zoneType = runCatching { ZoneType.valueOf(obj.optString("zoneType", "CUSTOM")) }.getOrDefault(ZoneType.CUSTOM)
                )
            }.ifEmpty { defaultSteps() }
        }.getOrDefault(defaultSteps())
    }

    fun saveSteps(steps: List<CleaningStepConfig>) {
        val array = JSONArray()
        steps.forEach { step ->
            array.put(
                JSONObject()
                    .put("id", step.id)
                    .put("title", step.title)
                    .put("durationSec", step.durationSec)
                    .put("zoneType", step.zoneType.name)
            )
        }
        prefs.edit().putString("steps", array.toString()).apply()
    }

    fun loadPauseSec(): Int = prefs.getInt("pauseSec", 2)

    fun savePauseSec(value: Int) {
        prefs.edit().putInt("pauseSec", value).apply()
    }

    fun loadHistory(): List<CleaningHistoryItem> {
        val raw = prefs.getString("history", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val obj = array.getJSONObject(index)
                CleaningHistoryItem(
                    startedAtMillis = obj.getLong("startedAtMillis"),
                    durationSec = obj.getInt("durationSec"),
                    status = SessionStatus.valueOf(obj.getString("status")),
                    completedSteps = obj.optInt("completedSteps", 0),
                    totalSteps = obj.optInt("totalSteps", 0)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveHistory(history: List<CleaningHistoryItem>) {
        val array = JSONArray()
        history.take(100).forEach { item ->
            array.put(
                JSONObject()
                    .put("startedAtMillis", item.startedAtMillis)
                    .put("durationSec", item.durationSec)
                    .put("status", item.status.name)
                    .put("completedSteps", item.completedSteps)
                    .put("totalSteps", item.totalSteps)
            )
        }
        prefs.edit().putString("history", array.toString()).apply()
    }
}
