package com.example.clockbattery

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.SystemClock
import android.widget.RemoteViews

/**
 * Home-screen widget. The time and date are TextClock views, so Android keeps them
 * ticking by itself. The battery level is refreshed every ~5 minutes, and straight away
 * when charging starts/stops or the battery goes low/ok.
 */
class ClockWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(ctx, mgr, it) }
        scheduleNext(ctx)
    }

    override fun onEnabled(ctx: Context) {
        scheduleNext(ctx)
    }

    override fun onDisabled(ctx: Context) {
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(tickIntent(ctx))
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ACTION_TICK,
            Intent.ACTION_BATTERY_LOW,
            Intent.ACTION_BATTERY_OKAY,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED -> {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, ClockWidget::class.java))
                if (ids.isNotEmpty()) {
                    ids.forEach { render(ctx, mgr, it) }
                    scheduleNext(ctx)
                }
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.example.clockbattery.TICK"

        private fun tickIntent(ctx: Context): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, 0,
                Intent(ctx, ClockWidget::class.java).setAction(ACTION_TICK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun scheduleNext(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // Inexact, non-waking alarm: costs almost no battery
            am.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 5 * 60_000L,
                tickIntent(ctx)
            )
        }

        fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
            val sticky = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            val views = RemoteViews(ctx.packageName, R.layout.widget_clock)
            views.setTextViewText(R.id.battery, if (charging) "$pct%  ⚡" else "$pct%")
            views.setTextColor(
                R.id.battery,
                when {
                    charging -> Color.parseColor("#7CFC7C")
                    pct <= 15 -> Color.parseColor("#FF5C5C")
                    pct <= 30 -> Color.parseColor("#FFB74D")
                    else -> Color.parseColor("#00E5D4")
                }
            )
            views.setProgressBar(R.id.bar, 100, pct, false)

            val open = PendingIntent.getActivity(
                ctx, 1, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.root, open)

            mgr.updateAppWidget(id, views)
        }
    }
}
