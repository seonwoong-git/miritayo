package com.miritayo.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val Blue = Color(0xFF2F7FF6)
private val Navy = Color(0xFF173B67)
private val Mint = Color(0xFF23AD8B)
private val Red = Color(0xFFE75367)
private val SoftBlue = Color(0xFFEAF3FF)
private val SoftMint = Color(0xFFE9F8F3)
private val SoftRed = Color(0xFFFFF0F2)
private val SoftGray = Color(0xFFF4F6F9)

private enum class Screen {
    HOME, REHEARSAL, LIVE, INCIDENT, GUARDIAN, COMPLETE, RECORDS, SETTINGS, ABOUT
}

private enum class HelpMode { NONE, ACTUAL, TEST }

private enum class Incident(
    val label: String,
    val short: String,
    val icon: String,
    val message: String,
    val hint: String,
    val steps: List<String>
) {
    PASSED(
        "정류장을 지나쳤을 때",
        "정류장을 지나쳤어요",
        "🚏",
        "정류장을 지나쳤어요.",
        "다음 정류장에서 내려요.",
        listOf(
            "버스 안에서 기다려요.",
            "다음 정류장에서 내려요.",
            "내린 뒤 다시 확인해요."
        )
    ),
    WRONG_BUS(
        "다른 버스를 탔을 때",
        "다른 버스를 탔어요",
        "🚌",
        "다른 버스를 탔어요.",
        "다음 정류장에서 내려요.",
        listOf(
            "버스 번호를 다시 확인해요.",
            "다음 정류장에서 내려요.",
            "맞는 버스를 찾아요."
        )
    ),
    DELAY(
        "버스가 늦을 때",
        "버스가 늦어요",
        "⏱️",
        "버스가 늦고 있어요.",
        "조금 더 기다려요.",
        listOf(
            "정류장에서 기다려요.",
            "조금 더 기다려요.",
            "오래 기다리면 도움을 요청해요."
        )
    )
}

private data class Log(
    val type: Incident,
    val help: Boolean,
    val test: Boolean,
    val seconds: Int,
    val at: Long
)

private data class AppSettings(
    val tts: Boolean = true,
    val vibrate: Boolean = true,
    val largeText: Boolean = false,
    val icons: Boolean = true
)

private val LocalIcons = staticCompositionLocalOf { true }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current

    var screen by remember { mutableStateOf(Screen.HOME) }
    var incident by remember { mutableStateOf(Incident.PASSED) }
    var startTime by remember { mutableLongStateOf(0L) }
    var logs by remember { mutableStateOf(loadLogs(context)) }
    var pendingIncident by remember { mutableStateOf<Incident?>(null) }
    var helpMode by remember { mutableStateOf(HelpMode.NONE) }
    var requestedAt by remember { mutableLongStateOf(0L) }
    var gpsText by remember { mutableStateOf("GPS 확인 전") }
    var settings by remember { mutableStateOf(loadSettings(context)) }

    val tts = rememberTts(context)

    fun speak(text: String) {
        if (settings.tts) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "miritayo")
        }
    }

    fun vibrate() {
        if (!settings.vibrate) return
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(220, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(220)
        }
    }

    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity.density, settings.largeText) {
        Density(
            density = baseDensity.density,
            fontScale = if (settings.largeText) 1.18f else 1f
        )
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            secondary = Mint,
            background = Color(0xFFF7FAFF),
            surface = Color.White,
            onSurface = Navy
        )
    ) {
        CompositionLocalProvider(
            LocalDensity provides scaledDensity,
            LocalIcons provides settings.icons
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFFF7FAFF)
            ) {
                when (screen) {
                    Screen.HOME -> Home(recommend(logs)) { screen = it }

                    Screen.REHEARSAL -> Rehearsal(
                        recommend(logs),
                        speak = { speak(it) },
                        home = { screen = Screen.HOME }
                    )

                    Screen.LIVE -> Live(
                        gps = gpsText,
                        onGps = { gpsText = it },
                        onIncident = { selected ->
                            incident = selected
                            startTime = System.currentTimeMillis()
                            vibrate()
                            speak(selected.message + " " + selected.hint)
                            screen = Screen.INCIDENT
                        },
                        home = { screen = Screen.HOME }
                    )

                    Screen.INCIDENT -> IncidentView(
                        incident = incident,
                        speak = {
                            speak(
                                incident.message + " " + incident.hint + " " +
                                    incident.steps.joinToString(" ")
                            )
                        },
                        success = {
                            logs = logs + Log(
                                type = incident,
                                help = false,
                                test = false,
                                seconds = elapsed(startTime),
                                at = System.currentTimeMillis()
                            )
                            saveLogs(context, logs)
                            screen = Screen.COMPLETE
                        },
                        help = {
                            pendingIncident = incident
                            helpMode = HelpMode.ACTUAL
                            requestedAt = System.currentTimeMillis()
                            screen = Screen.GUARDIAN
                        },
                        test = {
                            pendingIncident = incident
                            helpMode = HelpMode.TEST
                            requestedAt = System.currentTimeMillis()
                            screen = Screen.GUARDIAN
                        }
                    )

                    Screen.GUARDIAN -> Guardian(
                        pending = pendingIncident,
                        mode = helpMode,
                        at = requestedAt,
                        gps = gpsText,
                        onGps = { gpsText = it },
                        acknowledge = {
                            if (pendingIncident != null && helpMode != HelpMode.NONE) {
                                logs = logs + Log(
                                    type = incident,
                                    help = helpMode == HelpMode.ACTUAL,
                                    test = helpMode == HelpMode.TEST,
                                    seconds = elapsed(startTime),
                                    at = System.currentTimeMillis()
                                )
                                saveLogs(context, logs)
                            }
                            pendingIncident = null
                            helpMode = HelpMode.NONE
                            screen = if (startTime > 0L) Screen.COMPLETE else Screen.HOME
                        },
                        home = {
                            pendingIncident = null
                            helpMode = HelpMode.NONE
                            screen = Screen.HOME
                        }
                    )

                    Screen.COMPLETE -> Complete(
                        incident = incident,
                        log = logs.lastOrNull(),
                        records = { screen = Screen.RECORDS },
                        home = { screen = Screen.HOME }
                    )

                    Screen.RECORDS -> Records(
                        logs = logs,
                        practice = { screen = Screen.REHEARSAL },
                        clear = {
                            logs = emptyList()
                            saveLogs(context, logs)
                        },
                        home = { screen = Screen.HOME }
                    )

                    Screen.SETTINGS -> Settings(
                        settings = settings,
                        change = {
                            settings = it
                            saveSettings(context, it)
                        },
                        speak = { speak("미리타요 접근성 설정 화면입니다.") },
                        home = { screen = Screen.HOME }
                    )

                    Screen.ABOUT -> About { screen = Screen.HOME }
                }
            }
        }
    }
}

@Composable
private fun rememberTts(context: Context): TextToSpeech? {
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
            }
        }
        tts = engine
        onDispose { engine.shutdown() }
    }

    return tts
}

private fun elapsed(start: Long): Int {
    if (start <= 0L) return 1
    return ((System.currentTimeMillis() - start) / 1000L).toInt().coerceAtLeast(1)
}

@Composable
private fun Page(
    title: String,
    sub: String? = null,
    body: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (LocalIcons.current) {
                Text(text = "🚌", fontSize = 28.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "미리타요",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Blue
                )
                Text(
                    text = "발달장애인의 대중교통 사전 연습·실전 이동 훈련",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }

        Text(
            text = title,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Navy
        )

        sub?.let {
            Text(
                text = it,
                fontSize = 17.sp,
                lineHeight = 25.sp,
                color = Color(0xFF647991)
            )
        }

        body()
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun BoxInfo(
    title: String,
    color: Color = Color.White,
    modifier: Modifier = Modifier,
    body: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = color
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Navy
            )
            body()
        }
    }
}

@Composable
private fun Home(rec: Incident?, go: (Screen) -> Unit) {
    Page(
        title = "미리 연습하고,\n실제 이동 상황에 대비해요.",
        sub = "사전 연습과 실전 이동 훈련을 이어서 사용해요."
    ) {
        BoxInfo("연습 경로 예시", SoftBlue) {
            Text(
                text = "우리집 → 도서관",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(text = "102번 버스 · 버스 지상 구간")
            Text(
                text = "※ 실제 지도 길안내가 아닌 훈련용 예시 경로입니다.",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        Button(
            onClick = { go(Screen.REHEARSAL) },
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text(text = if (rec == null) "미리 연습하기" else "추천 연습: ${rec.label}")
        }

        Button(
            onClick = { go(Screen.LIVE) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Mint)
        ) {
            Text(text = "실전 이동 훈련")
        }

        OutlinedButton(
            onClick = { go(Screen.RECORDS) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(text = "기록 보기") }

        OutlinedButton(
            onClick = { go(Screen.SETTINGS) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(text = "접근성 설정") }

        TextButton(
            onClick = { go(Screen.GUARDIAN) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(text = "보호자 확인 화면") }

        TextButton(
            onClick = { go(Screen.ABOUT) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(text = "앱 정보 · 향후 기능") }
    }
}

@Composable
private fun Rehearsal(
    rec: Incident?,
    speak: (String) -> Unit,
    home: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    val target = rec ?: Incident.PASSED
    val titles = listOf("정류장을 확인해요.", "102번 버스를 확인해요.", target.message)
    val hints = listOf("중앙도서관 정류장", "교통카드를 준비하고 버스에 타요.", target.hint)

    Page(
        title = "사전 리허설",
        sub = "한 화면에서 한 행동씩, 실전과 같은 표현으로 연습해요."
    ) {
        BoxInfo(
            title = "${step + 1} / 3",
            color = if (step == 2) SoftRed else SoftBlue
        ) {
            Text(
                text = titles[step],
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = hints[step],
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = if (step == 2) Red else Blue,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedButton(
            onClick = { speak(titles[step] + " " + hints[step]) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(text = "음성으로 듣기") }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { if (step > 0) step-- else home() },
                modifier = Modifier.weight(1f)
            ) { Text(text = "이전") }

            Button(
                onClick = { if (step < 2) step++ else home() },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = if (step == 2) "연습 완료" else "다음")
            }
        }
    }
}

@Composable
private fun Live(
    gps: String,
    onGps: (String) -> Unit,
    onIncident: (Incident) -> Unit,
    home: () -> Unit
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            requestLocation(context, onGps)
        } else {
            onGps("위치 권한이 없어 GPS를 읽지 못했습니다.")
        }
    }

    Page(
        title = "실전 이동 훈련",
        sub = "현재 위치를 확인하고, 상황이 생기면 해당 항목을 선택해 단계별로 연습해요."
    ) {
        BoxInfo("현재 위치 확인", SoftBlue) {
            Text(text = gps)
            OutlinedButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (granted) {
                        requestLocation(context, onGps)
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "GPS로 현재 위치 확인")
            }
        }

        Text(
            text = "어떤 상황인가요?",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )

        Incident.values().forEach { item ->
            OutlinedButton(
                onClick = { onIncident(item) },
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                val prefix = if (LocalIcons.current) item.icon + " " else ""
                Text(text = prefix + item.short)
            }
        }

        BoxInfo("현재 구현 / 후속 개발", SoftGray) {
            Text(text = "현재 구현: GPS 위치 확인 · 3개 상황 선택 · 단계별 대처 안내")
            Text(
                text = "후속 개발: 실제 지도 · 실시간 버스 정보 · 자동 경로이탈 감지",
                color = Color.Gray
            )
        }

        TextButton(
            onClick = home,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text(text = "홈으로") }
    }
}

private fun requestLocation(context: Context, output: (String) -> Unit) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    try {
        val lastLocation = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }

        if (lastLocation != null) {
            output(formatLocation(lastLocation))
            return
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                output(formatLocation(location))
                locationManager.removeUpdates(this)
            }
        }

        val provider = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            LocationManager.NETWORK_PROVIDER
        } else {
            LocationManager.GPS_PROVIDER
        }

        locationManager.requestLocationUpdates(
            provider,
            0L,
            0f,
            listener,
            Looper.getMainLooper()
        )
        output("GPS 위치를 확인 중입니다.")
    } catch (_: Exception) {
        output("현재 위치를 확인하지 못했습니다.")
    }
}

private fun formatLocation(location: Location): String {
    return "현재 위치: %.5f, %.5f · 오차 약 %.0fm".format(
        location.latitude,
        location.longitude,
        location.accuracy
    )
}

@Composable
private fun IncidentView(
    incident: Incident,
    speak: () -> Unit,
    success: () -> Unit,
    help: () -> Unit,
    test: () -> Unit
) {
    Page(
        title = "괜찮아요.",
        sub = "한 화면에서 한 행동씩 확인해요."
    ) {
        BoxInfo(incident.short, SoftRed) {
            Text(
                text = incident.hint,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Red
            )
            incident.steps.forEachIndexed { index, text ->
                Text(text = "${index + 1}. $text", fontSize = 18.sp)
            }
        }

        OutlinedButton(
            onClick = speak,
            modifier = Modifier.fillMaxWidth()
        ) { Text(text = "음성으로 다시 듣기") }

        Button(
            onClick = success,
            modifier = Modifier.fillMaxWidth().height(58.dp)
        ) { Text(text = "혼자 다음 행동을 했어요") }

        OutlinedButton(
            onClick = help,
            modifier = Modifier.fillMaxWidth().height(58.dp)
        ) { Text(text = "도움이 필요해요") }

        TextButton(
            onClick = test,
            modifier = Modifier.fillMaxWidth()
        ) { Text(text = "도움요청 기능 확인(테스트)") }
    }
}

@Composable
private fun Guardian(
    pending: Incident?,
    mode: HelpMode,
    at: Long,
    gps: String,
    onGps: (String) -> Unit,
    acknowledge: () -> Unit,
    home: () -> Unit
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            requestLocation(context, onGps)
        } else {
            onGps("위치 권한이 없습니다.")
        }
    }

    Page(
        title = "보호자 확인 화면",
        sub = "도움 요청 상황과 위치정보를 같은 앱의 시연 화면에서 확인해요."
    ) {
        if (pending == null) {
            BoxInfo("현재 도움 요청 없음") {
                Text(
                    text = "※ 실제 보호자 푸시 알림·원격 전송은 후속 개발 항목입니다.",
                    color = Color.Gray
                )
            }
        } else {
            BoxInfo(
                title = if (mode == HelpMode.TEST) "기능 확인(테스트)" else "도움 요청",
                color = if (mode == HelpMode.TEST) SoftBlue else SoftRed
            ) {
                Text(
                    text = "상황: ${pending.short}",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "요청 시각: ${fmt(at)}")
                Text(text = "위치정보: $gps")

                OutlinedButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (granted) {
                            requestLocation(context, onGps)
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "현재 위치 다시 확인")
                }

                Button(
                    onClick = acknowledge,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (mode == HelpMode.TEST) {
                            "기능 확인 완료"
                        } else {
                            "보호자 확인 화면 확인 완료"
                        }
                    )
                }
            }
        }

        BoxInfo("후속 개발 예정", SoftGray) {
            Text(text = "• 네이버지도·카카오맵 실제 지도 연동")
            Text(text = "• FCM 보호자 푸시 알림")
            Text(text = "• 전화·메시지 빠른 연결")
        }

        TextButton(
            onClick = home,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text(text = "홈으로") }
    }
}

@Composable
private fun Complete(
    incident: Incident,
    log: Log?,
    records: () -> Unit,
    home: () -> Unit
) {
    Page(
        title = "수행 기록을 저장했어요.",
        sub = "실제 수행 기록은 다음 리허설 추천에 반영됩니다."
    ) {
        BoxInfo("이번 수행", SoftMint) {
            Text(
                text = incident.label,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            log?.let {
                val resultText = when {
                    it.test -> "기능 확인 테스트"
                    it.help -> "실제 도움 요청"
                    else -> "독립 대처"
                }
                Text(text = "결과: $resultText")
                Text(text = "대처시간: ${it.seconds}초")
                if (it.test) {
                    Text(
                        text = "※ 기능 테스트는 실제 도움요청 통계에서 제외됩니다.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        Button(
            onClick = records,
            modifier = Modifier.fillMaxWidth()
        ) { Text(text = "기록과 추천 보기") }

        OutlinedButton(
            onClick = home,
            modifier = Modifier.fillMaxWidth()
        ) { Text(text = "홈으로") }
    }
}

@Composable
private fun Records(
    logs: List<Log>,
    practice: () -> Unit,
    clear: () -> Unit,
    home: () -> Unit
) {
    val actualLogs = logs.filter { !it.test }
    val recommended = recommend(logs)
    val averageSeconds = if (actualLogs.isEmpty()) {
        0
    } else {
        actualLogs.map { it.seconds }.average().roundToInt()
    }

    Page(
        title = "기록 → 다음 리허설",
        sub = "실제 수행 기록을 바탕으로 다시 연습할 상황을 규칙 기반으로 추천해요."
    ) {
        BoxInfo("다음 추천", SoftRed) {
            Text(
                text = recommended?.label ?: "실제 수행 기록이 아직 없어요.",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxInfo(
                title = "독립 대처",
                color = SoftMint,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${actualLogs.count { !it.help }}회",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            BoxInfo(
                title = "실제 도움",
                color = SoftRed,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${actualLogs.count { it.help }}회",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxInfo(
                title = "기능 테스트",
                color = SoftBlue,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${logs.count { it.test }}회",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            BoxInfo(
                title = "평균 대처시간",
                color = SoftGray,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (actualLogs.isEmpty()) "-" else "${averageSeconds}초",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        BoxInfo("최근 기록") {
            if (logs.isEmpty()) {
                Text(text = "아직 기록이 없습니다.")
            } else {
                logs.takeLast(8).reversed().forEach { item ->
                    val resultText = when {
                        item.test -> "기능 테스트"
                        item.help -> "실제 도움 요청"
                        else -> "독립 대처"
                    }
                    Text(text = "${item.type.short} · $resultText · ${item.seconds}초")
                }
            }
        }

        Button(
            onClick = practice,
            modifier = Modifier.fillMaxWidth()
        ) { Text(text = "추천 상황 다시 연습하기") }

        OutlinedButton(
            onClick = clear,
            modifier = Modifier.fillMaxWidth()
        ) { Text(text = "기록 초기화") }

        TextButton(
            onClick = home,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text(text = "홈으로") }
    }
}

@Composable
private fun Settings(
    settings: AppSettings,
    change: (AppSettings) -> Unit,
    speak: () -> Unit,
    home: () -> Unit
) {
    Page(
        title = "접근성 설정",
        sub = "사용자가 필요한 안내 방식을 직접 선택해요."
    ) {
        BoxInfo("설정") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "음성 안내", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.tts,
                    onCheckedChange = { change(settings.copy(tts = it)) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "진동 알림", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.vibrate,
                    onCheckedChange = { change(settings.copy(vibrate = it)) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "큰 글자", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.largeText,
                    onCheckedChange = { change(settings.copy(largeText = it)) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "그림·아이콘 함께 보기", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.icons,
                    onCheckedChange = { change(settings.copy(icons = it)) }
                )
            }
        }

        OutlinedButton(
            onClick = speak,
            modifier = Modifier.fillMaxWidth()
        ) { Text(text = "현재 화면 읽어주기") }

        BoxInfo("화면 원칙", SoftBlue) {
            Text(text = "• 한 화면에서 한 행동씩 안내")
            Text(text = "• 짧고 구체적인 문장")
            Text(text = "• 큰 버튼과 명확한 색상 구분")
            Text(text = "• 10대·성인도 사용할 수 있는 표현")
        }

        TextButton(
            onClick = home,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text(text = "홈으로") }
    }
}

@Composable
private fun About(home: () -> Unit) {
    Page(
        title = "앱 정보 · 향후 기능",
        sub = "Miritayo v0.3 · 실증 피드백 반영 MVP"
    ) {
        BoxInfo("v0.3 반영 내용", SoftMint) {
            Text(text = "• ‘실전 이동 훈련’ 명칭 통일")
            Text(text = "• 돌발상황 안내 문구 단순화")
            Text(text = "• 보호자 확인 화면의 GPS 위치정보 확인")
            Text(text = "• 실제 도움요청과 기능 테스트 기록 분리")
            Text(text = "• 큰 글자 설정 실제 반영")
            Text(text = "• 현재 구현/후속개발 기능 구분")
        }

        BoxInfo("초기 실증에서 확인", SoftBlue) {
            Text(text = "• 3개 돌발상황 다음 행동 선택 성공")
            Text(text = "• 돌발상황 안내 이해도 2/5 → 문구 단순화 필요")
            Text(text = "• 실제 지도·경로 연계 필요 의견")
        }

        BoxInfo("후속 개발 예정", SoftGray) {
            Text(text = "• 네이버지도·카카오맵 실제 지도 연동")
            Text(text = "• 버스 공공데이터 연계")
            Text(text = "• 자동 경로이탈 감지")
            Text(text = "• FCM 보호자 푸시")
            Text(text = "• 서버·DB 및 추가 실증")
        }

        TextButton(
            onClick = home,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text(text = "홈으로") }
    }
}

private fun recommend(logs: List<Log>): Incident? {
    val actualLogs = logs.filter { !it.test }
    if (actualLogs.isEmpty()) return null

    return Incident.values().maxByOrNull { incident ->
        actualLogs
            .filter { it.type == incident }
            .sumOf { item ->
                (if (item.help) 300 else 100) + item.seconds.coerceAtMost(60)
            }
    }
}

private fun saveLogs(context: Context, logs: List<Log>) {
    val array = JSONArray()
    logs.takeLast(50).forEach { item ->
        array.put(
            JSONObject()
                .put("type", item.type.name)
                .put("help", item.help)
                .put("test", item.test)
                .put("seconds", item.seconds)
                .put("at", item.at)
        )
    }

    context.getSharedPreferences("miritayo", Context.MODE_PRIVATE)
        .edit()
        .putString("logs", array.toString())
        .apply()
}

private fun loadLogs(context: Context): List<Log> {
    return runCatching {
        val prefs = context.getSharedPreferences("miritayo", Context.MODE_PRIVATE)
        val raw = prefs.getString("logs", "[]") ?: "[]"
        val array = JSONArray(raw)

        (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val oldHelpValue = obj.optBoolean("help", false)
            val hasTestField = obj.has("test")

            Log(
                type = Incident.valueOf(obj.getString("type")),
                help = if (hasTestField) oldHelpValue else false,
                test = if (hasTestField) obj.optBoolean("test", false) else oldHelpValue,
                seconds = obj.optInt("seconds", 1).coerceAtLeast(1),
                at = obj.optLong("at", System.currentTimeMillis())
            )
        }
    }.getOrDefault(emptyList())
}

private fun saveSettings(context: Context, settings: AppSettings) {
    context.getSharedPreferences("miritayo", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("tts", settings.tts)
        .putBoolean("vib", settings.vibrate)
        .putBoolean("large", settings.largeText)
        .putBoolean("icons", settings.icons)
        .apply()
}

private fun loadSettings(context: Context): AppSettings {
    val prefs = context.getSharedPreferences("miritayo", Context.MODE_PRIVATE)
    return AppSettings(
        tts = prefs.getBoolean("tts", true),
        vibrate = prefs.getBoolean("vib", true),
        largeText = prefs.getBoolean("large", false),
        icons = prefs.getBoolean("icons", true)
    )
}

private fun fmt(time: Long): String {
    if (time <= 0L) return "-"
    return SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.KOREA).format(Date(time))
}
