package com.cibertec.sentinela.views

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cibertec.sentinela.utils.Conexion
import com.cibertec.sentinela.utils.SessionManager

/**
 * Punto de entrada: limpia sesiones anteriores obsoletas de servidores locales y
 * dirige al Onboarding o Login si no hay sesión activa en la nube.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent?.data
        val host = url?.host?.lowercase()
        val esAtajoEmergencia = url?.scheme?.lowercase() == "sentinela" &&
                (host == "activate" || host == "deactivate")

        val session = SessionManager(this)

        // Si cambió el servidor hacia Railway (o es la primera apertura en la nube),
        // limpiar cualquier sesión residual antigua para que el usuario inicie desde el principio.
        if (session.ultimoServidorConectado != Conexion.baseURL) {
            session.cerrarSesion()
            session.onboardingVisto = false
            session.ultimoServidorConectado = Conexion.baseURL
        }

        val token = session.userToken
        if (!token.isNullOrEmpty()) {
            val esAtajoUrgente = esAtajoEmergencia
            val destino = when {
                !session.pinsConfigured -> PinSetupActivity::class.java
                !session.tutorialSentinelaVisto && !esAtajoUrgente -> TutorialActivity::class.java
                else -> HomeActivity::class.java
            }
            startActivity(
                Intent(this, destino)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(HomeActivity.EXTRA_TRIGGER_EMERGENCY, esAtajoEmergencia)
            )
        } else if (session.onboardingVisto) {
            // No logueado, pero ya vio las tarjetas -> ir directo al Login
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            // Primera vez que se abre la app -> mostrar las tarjetas de bienvenida
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        finish()
    }
}
