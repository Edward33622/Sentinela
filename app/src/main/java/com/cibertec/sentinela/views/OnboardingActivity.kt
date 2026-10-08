package com.cibertec.sentinela.views

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.cibertec.sentinela.R
import com.cibertec.sentinela.databinding.ActivityOnboardingBinding
import com.cibertec.sentinela.databinding.ItemOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private data class Pagina(val imagen: Int, val titulo: String, val descripcion: String)

    private val paginas = listOf(
        Pagina(
            R.drawable.card1,
            "Protección Inteligente",
            "Tu seguridad en tus manos. Activa el modo emergencia con un solo gesto o comando de voz."
        ),
        Pagina(
            R.drawable.card3,
            "Alerta en Tiempo Real",
            "Comparte tu ubicación exacta con tus contactos de confianza y autoridades al instante."
        ),
        Pagina(
            R.drawable.card2,
            "Evidencia Segura",
            "Grabación automática de audio y video. Tus pruebas quedan resguardadas en la nube."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.adapter = OnboardingAdapter()
        // Sin efecto de rebote, igual que scrollView.bounces = false
        (binding.viewPager.getChildAt(0) as? RecyclerView)?.overScrollMode = View.OVER_SCROLL_NEVER

        binding.btnOmitir.setOnClickListener { irALogin() }
    }

    private fun irALogin() = startActivity(Intent(this, LoginActivity::class.java))

    private fun irARegistro() = startActivity(Intent(this, RegisterActivity::class.java))

    private inner class OnboardingAdapter : RecyclerView.Adapter<OnboardingAdapter.PaginaViewHolder>() {

        inner class PaginaViewHolder(val item: ItemOnboardingBinding) : RecyclerView.ViewHolder(item.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaginaViewHolder {
            return PaginaViewHolder(ItemOnboardingBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun getItemCount() = paginas.size

        override fun onBindViewHolder(holder: PaginaViewHolder, position: Int) {
            val pagina = paginas[position]
            with(holder.item) {
                imgCard.setImageResource(pagina.imagen)
                lblTitulo.text = pagina.titulo
                lblDescripcion.text = pagina.descripcion

                listOf(dot1, dot2, dot3).forEachIndexed { i, dot ->
                    dot.setBackgroundResource(if (i == position) R.drawable.bg_dot_on else R.drawable.bg_dot_off)
                }

                // Los botones solo aparecen en la última página
                panelBotones.visibility = if (position == paginas.lastIndex) View.VISIBLE else View.INVISIBLE
                btnComienza.setOnClickListener { irARegistro() }
                btnIniciarSesion.setOnClickListener { irALogin() }
            }
        }
    }
}
