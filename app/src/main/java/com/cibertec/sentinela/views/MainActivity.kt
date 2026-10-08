package com.cibertec.sentinela.views

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cibertec.sentinela.utils.SessionManager

/**
 * Punto de entrada (equivalente a SceneDelegate): si hay token guardado va directo al Home,
 * si no al Onboarding. También atiende el esquema sentinela://activate | sentinela://deactivate.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent?.data
        val host = url?.host?.lowercase()
        val esAtajoEmergencia = url?.scheme?.lowercase() == "sentinela" &&
                (host == "activate" || host == "deactivate")

        val token = SessionManager(this).userToken
        if (!token.isNullOrEmpty()) {
            // Usuario ya logueado -> ir directo al Home
            startActivity(
                Intent(this, HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(HomeActivity.EXTRA_TRIGGER_EMERGENCY, esAtajoEmergencia)
            )
        } else {
            // No logueado -> ir al Onboarding/Login
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        finish()
    }
}
