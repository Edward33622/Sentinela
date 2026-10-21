package com.cibertec.sentinela.views

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.cibertec.sentinela.databinding.ActivityFakeCallBinding

class FakeCallActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CALLER_NAME = "extra_caller_name"
    }

    private lateinit var binding: ActivityFakeCallBinding
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configurarPantallaBloqueada()

        binding = ActivityFakeCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Mamá"
        binding.tvCallerName.text = callerName

        iniciarTimbradoYVibracion()

        binding.btnAcceptCall.setOnClickListener { contestarLlamada() }
        binding.btnDeclineCall.setOnClickListener { rechazarLlamada() }
        binding.btnHangUpCall.setOnClickListener { rechazarLlamada() }
    }

    private fun configurarPantallaBloqueada() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun iniciarTimbradoYVibracion() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null) {
            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun detenerTimbradoYVibracion() {
        try {
            ringtone?.stop()
            ringtone = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vibrator?.cancel()
    }

    private fun contestarLlamada() {
        detenerTimbradoYVibracion()

        binding.layoutIncomingButtons.visibility = View.GONE
        binding.layoutInCallOptions.visibility = View.VISIBLE
        binding.layoutHangupButton.visibility = View.VISIBLE
        binding.tvCallStatus.text = "Llamada móvil"

        binding.chronometerCall.apply {
            visibility = View.VISIBLE
            base = SystemClock.elapsedRealtime()
            start()
        }
    }

    private fun rechazarLlamada() {
        detenerTimbradoYVibracion()
        binding.chronometerCall.stop()
        finish()
    }

    override fun onDestroy() {
        detenerTimbradoYVibracion()
        super.onDestroy()
    }
}
