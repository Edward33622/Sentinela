package com.cibertec.sentinela.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

object BatteryMonitor {

    data class BatteryInfo(
        val level: Int,
        val isCharging: Boolean,
        val formatted: String
    )

    fun obtenerInformacionBateria(context: Context): BatteryInfo {
        val batteryIntent: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val rawLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        val level = if (rawLevel >= 0 && scale > 0) {
            ((rawLevel.toFloat() / scale.toFloat()) * 100f).toInt()
        } else {
            100
        }

        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val icon = when {
            isCharging -> "⚡🔋"
            level <= 15 -> "🪫"
            level <= 40 -> "🔋"
            else -> "🔋"
        }

        val chargingText = if (isCharging) " (Cargando)" else ""
        val formatted = "$icon $level%$chargingText"

        return BatteryInfo(
            level = level,
            isCharging = isCharging,
            formatted = formatted
        )
    }
}
