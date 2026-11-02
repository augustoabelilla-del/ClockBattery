package com.example.clockbattery

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Teal = Color(0xFF00E5D4)
private val Green = Color(0xFF7CFC7C)
private val Amber = Color(0xFFFFB74D)
private val Red = Color(0xFFFF5C5C)
private val Dim = Color(0xFFB0B3B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Teal)) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) { ClockScreen() }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Refresh any widgets on the home screen with the latest battery level
        val mgr = AppWidgetManager.getInstance(this)
        mgr.getAppWidgetIds(ComponentName(this, ClockWidget::class.java))
            .forEach { ClockWidget.render(this, mgr, it) }
    }
}

private data class BatteryState(
    val percent: Int = 0,
    val charging: Boolean = false,
    val tempC: Float = 0f
)

private fun readBattery(i: Intent?): BatteryState {
    if (i == null) return BatteryState()
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return BatteryState(
        percent = if (level >= 0 && scale > 0) level * 100 / scale else 0,
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL,
        tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
    )
}

@Composable
fun ClockScreen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("clock", Context.MODE_PRIVATE) }

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var battery by remember { mutableStateOf(BatteryState()) }
    var h24 by remember { mutableStateOf(prefs.getBoolean("h24", DateFormat.is24HourFormat(ctx))) }
    var keepOn by remember { mutableStateOf(prefs.getBoolean("keepOn", false)) }
    var showSeconds by remember { mutableStateOf(prefs.getBoolean("seconds", false)) }

    // Tick once per second, aligned to the second boundary
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }

    // Live battery updates
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                battery = readBattery(i)
            }
        }
        val sticky = ContextCompat.registerReceiver(
            ctx, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        battery = readBattery(sticky)
        onDispose { ctx.unregisterReceiver(receiver) }
    }

    // Keep-screen-on switch
    DisposableEffect(keepOn) {
        val window = (ctx as? Activity)?.window
        if (keepOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val date = Date(now)
    val timeText = SimpleDateFormat(if (h24) "HH:mm" else "h:mm", Locale.getDefault()).format(date)
    val ampm = if (h24) "" else SimpleDateFormat("a", Locale.getDefault()).format(date)
    val secText = SimpleDateFormat("ss", Locale.getDefault()).format(date)
    val dateText = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(date)

    val batteryColor = when {
        battery.charging -> Green
        battery.percent <= 15 -> Red
        battery.percent <= 30 -> Amber
        else -> Teal
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Scale the clock to the screen, so it fits the cover screen and the inner screen
        val clockSize = (maxWidth.value / 3.4f).coerceIn(40f, 150f)

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                timeText,
                color = Color.White,
                fontSize = clockSize.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
            if (ampm.isNotEmpty() || showSeconds) {
                Text(
                    listOf(ampm, if (showSeconds) secText else "").filter { it.isNotEmpty() }.joinToString("  "),
                    color = Dim,
                    fontSize = (clockSize / 3).sp
                )
            }
            Text(dateText, color = Dim, fontSize = 16.sp, textAlign = TextAlign.Center)

            Spacer(Modifier.height(36.dp))

            Text(
                (if (battery.charging) "⚡ " else "") + "${battery.percent}%",
                color = batteryColor,
                fontSize = (clockSize / 1.6f).sp,
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(
                progress = { battery.percent / 100f },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(10.dp),
                color = batteryColor,
                trackColor = Color(0xFF2A2C31)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                (if (battery.charging) "Charging" else "On battery") +
                    "  ·  %.1f °C".format(battery.tempC),
                color = Dim,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(32.dp))

            SettingRow("24-hour clock", h24) { h24 = it; prefs.edit().putBoolean("h24", it).apply() }
            SettingRow("Show seconds", showSeconds) { showSeconds = it; prefs.edit().putBoolean("seconds", it).apply() }
            SettingRow("Keep screen on", keepOn) { keepOn = it; prefs.edit().putBoolean("keepOn", it).apply() }
        }
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth(0.85f)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFFE6E6E6), fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
