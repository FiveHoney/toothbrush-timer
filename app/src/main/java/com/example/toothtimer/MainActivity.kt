package com.example.toothtimer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.StrokeCap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlin.math.ceil
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class AudioCue(val uri: String, val name: String)

data class CleaningStepConfig(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val durationSec: Int,
    val zoneType: ZoneType = ZoneType.CUSTOM,
    val audioCues: List<AudioCue> = emptyList()
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
    var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME) }

    var startedAt by rememberSaveable { mutableStateOf(0L) }

    BackHandler(enabled = currentScreen != Screen.HOME && currentScreen != Screen.CLEANING) { currentScreen = Screen.HOME }
    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .background(Color.White)
        ) {
            when (currentScreen) {
                Screen.HOME -> HomeScreen(
                    onStart = { startedAt = System.currentTimeMillis(); currentScreen = Screen.CLEANING },
                    onHistory = { currentScreen = Screen.HISTORY },
                    onSettings = { currentScreen = Screen.SETTINGS }
                )

                Screen.CLEANING -> CleaningScreen(
                    steps = steps,
                    pauseBetweenZonesSec = storage.loadPauseSec(),
                    onCompleted = { durationSec ->
                        val item = CleaningHistoryItem(
                            startedAtMillis = startedAt,
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
                            startedAtMillis = startedAt,
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

    val context = LocalContext.current
    val clockSaver = remember {
        listSaver<SessionClock, Long>(
            save = { listOf(it.index.toLong(), it.phase.ordinal.toLong(), it.phaseElapsedMs, it.elapsedMs) },
            restore = { SessionClock(steps.map { step -> step.durationSec * 1000L }, pauseBetweenZonesSec * 1000L,
                it[0].toInt(), CleaningPhase.values()[it[1].toInt()], it[2], it[3]) }
        )
    }
    var session by rememberSaveable(stateSaver = clockSaver) { mutableStateOf(SessionClock(steps.map { it.durationSec * 1000L }, pauseBetweenZonesSec * 1000L)) }
    var isPaused by rememberSaveable { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var audioFinished by remember { mutableStateOf(false) }
    var audioError by remember { mutableStateOf(false) }
    val audio = remember { CuePlayer(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val phase = session.phase
    val stepIndex = session.index
    val currentStep = steps[stepIndex]
    val elapsedSec = (session.elapsedMs / 1000).toInt()
    val timeLeft = ceil(session.remainingMs / 1000.0).toInt()
    val pauseLeft = ceil((session.transitionMs - session.phaseElapsedMs).coerceAtLeast(0) / 1000.0).toInt()
    val progress = session.cleaningProgress

    BackHandler { showExitDialog = true }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { isPaused = true; audio.setPaused(true) }
        }
        lifecycle.addObserver(observer)
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            lifecycle.removeObserver(observer)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            audio.close()
        }
    }
    val suspended = isPaused || showExitDialog
    LaunchedEffect(suspended) { audio.setPaused(suspended) }
    LaunchedEffect(phase, stepIndex) {
        if (phase == CleaningPhase.BETWEEN_STEPS) {
            audioFinished = false
            audioError = false
            for (cue in currentStep.audioCues) {
                if (!audio.play(cue.uri)) audioError = true
            }
            audioFinished = true
        }
    }
    LaunchedEffect(suspended) {
        if (suspended) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val delta = (now - previous) / 1_000_000
            previous += delta * 1_000_000
            val next = session.advance(delta, audioFinished)
            // Reset before the next transition, so a previous playlist cannot unlock it.
            if (next.index != session.index) audioFinished = false
            session = next
            if (next.phase == CleaningPhase.COMPLETED) {
                onCompleted((next.elapsedMs / 1000).toInt())
                break
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
                    onInterrupted(elapsedSec, session.completedSteps)
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
            .verticalScroll(rememberScrollState())
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
            TeethMap(activeZone = currentStep.zoneType, modifier = Modifier.fillMaxWidth().height(230.dp))
            CircularTimer(value = pauseLeft, label = if (pauseLeft == 0 && !audioFinished) "озвучка…" else "переход", color = Color(0xFF2F76D2), progress = if (session.transitionMs == 0L) 1f else (session.phaseElapsedMs.toFloat() / session.transitionMs).coerceIn(0f, 1f))
            if (audioError) Text("Запись недоступна — пропускаем её", color = Color(0xFFB3261E))
            Spacer(Modifier.height(24.dp))
            Text("Следующая зона:", fontSize = 16.sp, color = Color.DarkGray)
            Text(
                text = currentStep.title,
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
            CircularTimer(value = timeLeft, label = if (isPaused) "пауза" else "секунд", color = Color(0xFF2F76D2), progress = session.zoneProgress)
        }

        Spacer(Modifier.height(24.dp))

        Text("Прогресс чистки • ${(progress * 100).toInt()}%", color = Color.DarkGray)
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
    val context = LocalContext.current
    var currentPause by remember { mutableIntStateOf(pauseSec) }
    var showResetDialog by remember { mutableStateOf(false) }
    var audioPickerStepId by remember { mutableStateOf<String?>(null) }
    val audioPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val index = steps.indexOfFirst { it.id == audioPickerStepId }
        audioPickerStepId = null
        if (index >= 0) {
            val additions = uris.map { uri ->
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val name = runCatching {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                }.getOrNull() ?: "Аудиозапись"
                AudioCue(uri.toString(), name)
            }
            steps[index] = steps[index].copy(audioCues = steps[index].audioCues + additions)
            onStepsChanged()
        }
    }

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
                    increment = 1,
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
                    onZoneChange = { zone ->
                        steps[index] = step.copy(zoneType = zone)
                        onStepsChanged()
                    },
                    onDurationChange = { newDuration ->
                        steps[index] = step.copy(durationSec = newDuration)
                        onStepsChanged()
                    },
                    onSelectAudioCue = {
                        audioPickerStepId = step.id
                        audioPickerLauncher.launch(arrayOf("audio/*"))
                    },
                    onRemoveAudioCue = { cueIndex ->
                        steps[index] = step.copy(audioCues = step.audioCues.filterIndexed { i, _ -> i != cueIndex })
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
                    "Версия 1.1.0",
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
    onZoneChange: (ZoneType) -> Unit,
    onSelectAudioCue: () -> Unit,
    onRemoveAudioCue: (Int) -> Unit,
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

            Spacer(Modifier.height(10.dp))

            ZoneSelector(step.zoneType, onZoneChange)
            AudioCueControls(
                cues = step.audioCues,
                onSelectAudioCue = onSelectAudioCue,
                onRemoveAudioCue = onRemoveAudioCue
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
fun AudioCueControls(cues: List<AudioCue>, onSelectAudioCue: () -> Unit, onRemoveAudioCue: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text("Озвучка перехода к этой зоне", fontWeight = FontWeight.Bold)
        Text("Записи звучат по очереди. Чистка начнётся после всех записей и паузы.", fontSize = 14.sp)
        cues.forEachIndexed { index, cue ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}. ${cue.name}", modifier = Modifier.weight(1f))
                TextButton(onClick = { onRemoveAudioCue(index) }) { Text("Удалить") }
            }
        }
        Button(onClick = onSelectAudioCue, modifier = Modifier.fillMaxWidth()) { Text("Добавить аудиофайлы") }
    }
}

@Composable
fun ZoneSelector(zone: ZoneType, onChange: (ZoneType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }) { Text("На схеме: ${zone.description()}") }
    if (expanded) AlertDialog(
        onDismissRequest = { expanded = false },
        title = { Text("Область на схеме") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ZoneType.values().forEach { value ->
                    TextButton(onClick = { onChange(value); expanded = false }) { Text(value.description()) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text("Закрыть") } }
    )
}

@Composable
fun CounterRow(
    title: String,
    value: Int,
    suffix: String,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    increment: Int = 5
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF7F7F7), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - increment).coerceAtLeast(min)) }) {
            Text("−", fontSize = 24.sp)
        }
        Text("$value $suffix", fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + increment).coerceAtMost(max)) }) {
            Text("+", fontSize = 24.sp)
        }
    }
}

@Composable
fun CircularTimer(value: Int, label: String, color: Color, progress: Float) {
    Box(
        modifier = Modifier.size(150.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            drawCircle(
                color = Color(0xFFE0E0E0),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
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

fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() }
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
                    durationSec = obj.getInt("durationSec").coerceIn(5, 120),
                    zoneType = runCatching { ZoneType.valueOf(obj.optString("zoneType", "CUSTOM")) }.getOrDefault(ZoneType.CUSTOM),
                    audioCues = obj.optJSONArray("audioCues")?.let { cues ->
                        List(cues.length()) { i ->
                            val cue = cues.getJSONObject(i)
                            AudioCue(cue.getString("uri"), cue.optString("name", "Аудиозапись"))
                        }
                    } ?: listOfNotNull(obj.optNullableString("audioCueUri")?.let { AudioCue(it, "Озвучка зоны") })
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
                    .put("audioCues", JSONArray().apply { step.audioCues.forEach { put(JSONObject().put("uri", it.uri).put("name", it.name)) } })
            )
        }
        prefs.edit().putString("steps", array.toString()).apply()
    }

    fun loadPauseSec(): Int = prefs.getInt("pauseSec", 2).coerceIn(0, 30)

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
                    durationSec = obj.getInt("durationSec").coerceIn(5, 120),
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

