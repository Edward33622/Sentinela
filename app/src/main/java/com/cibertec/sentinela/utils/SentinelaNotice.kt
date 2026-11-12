package com.cibertec.sentinela.utils

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import com.cibertec.sentinela.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Avisos centrales de marca para resultados que requieren atención del usuario. */
object SentinelaNotice {

    enum class Type { SUCCESS, INFO, WARNING, ERROR }

    fun show(
        activity: Activity,
        title: String,
        message: CharSequence,
        type: Type,
        primaryText: String,
        onPrimary: () -> Unit,
        secondaryText: String? = null,
        onSecondary: (() -> Unit)? = null,
        cancelable: Boolean = false
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val content = activity.layoutInflater.inflate(R.layout.dialog_sentinela_notice, null)
        val (symbol, accent, tint) = when (type) {
            Type.SUCCESS -> Triple("✓", Color.rgb(6, 78, 59), Color.rgb(209, 250, 229))
            Type.INFO -> Triple("i", Color.rgb(3, 105, 161), Color.rgb(224, 242, 254))
            Type.WARNING -> Triple("!", Color.rgb(161, 98, 7), Color.rgb(254, 243, 199))
            Type.ERROR -> Triple("×", Color.rgb(185, 28, 28), Color.rgb(254, 226, 226))
        }

        content.findViewById<TextView>(R.id.noticeIcon).apply {
            text = symbol
            setTextColor(accent)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tint)
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        content.findViewById<TextView>(R.id.noticeTitle).text = title
        content.findViewById<TextView>(R.id.noticeMessage).text = message

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(content)
            .setCancelable(cancelable)
            .create()

        content.findViewById<MaterialButton>(R.id.noticePrimary).apply {
            text = primaryText
            setOnClickListener {
                dialog.dismiss()
                onPrimary()
            }
        }

        content.findViewById<MaterialButton>(R.id.noticeSecondary).apply {
            if (secondaryText.isNullOrBlank()) {
                visibility = View.GONE
            } else {
                text = secondaryText
                visibility = View.VISIBLE
                setOnClickListener {
                    dialog.dismiss()
                    onSecondary?.invoke()
                }
            }
        }

        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0.42f)
                val screenWidth = activity.resources.displayMetrics.widthPixels
                val horizontalMargin = (32 * activity.resources.displayMetrics.density).toInt()
                val maxWidth = (420 * activity.resources.displayMetrics.density).toInt()
                setLayout((screenWidth - horizontalMargin).coerceAtMost(maxWidth), WindowManager.LayoutParams.WRAP_CONTENT)
            }
            ViewCompat.setElevation(content, (16 * activity.resources.displayMetrics.density))
        }
        dialog.show()
    }
}
