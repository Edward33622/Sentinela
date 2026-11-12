package com.cibertec.sentinela.utils

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.google.android.material.snackbar.Snackbar

/** Avisos breves con la identidad visual de Sentinela. */
object SentinelaToast {

    enum class Type { SUCCESS, INFO, WARNING, ERROR }
    enum class Length { SHORT, LONG }

    fun show(
        activity: Activity,
        message: CharSequence,
        type: Type = Type.INFO,
        length: Length = Length.SHORT
    ) {
        if (activity.isFinishing || activity.isDestroyed || message.isBlank()) return
        val root = activity.findViewById<View>(android.R.id.content) ?: return
        val snackbar = Snackbar.make(root, message, Snackbar.LENGTH_SHORT)
            .setAnimationMode(Snackbar.ANIMATION_MODE_FADE)

        val container = snackbar.view as Snackbar.SnackbarLayout
        val style = styleFor(type)
        container.setPadding(0, dp(activity, 6), 0, dp(activity, 6))
        container.background = GradientDrawable().apply {
            setColor(style.background)
            cornerRadius = dp(activity, 18).toFloat()
            setStroke(dp(activity, 1), style.border)
        }
        ViewCompat.setElevation(container, dp(activity, 12).toFloat())

        (container.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.setMargins(dp(activity, 16), params.topMargin, dp(activity, 16), dp(activity, 22))
            container.layoutParams = params
        }

        val content = container.getChildAt(0)
        (content.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.gravity = Gravity.CENTER_VERTICAL
            params.marginStart = dp(activity, 54)
            params.marginEnd = dp(activity, 12)
            content.layoutParams = params
        }

        container.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 3
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        val icon = TextView(activity).apply {
            text = style.icon
            textSize = 18f
            setTextColor(style.accent)
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(style.iconBackground)
            }
        }
        container.addView(
            icon,
            0,
            FrameLayout.LayoutParams(dp(activity, 36), dp(activity, 36), Gravity.CENTER_VERTICAL).apply {
                marginStart = dp(activity, 12)
            }
        )

        snackbar.duration = when (length) {
            Length.SHORT -> 2200
            Length.LONG -> 3600
        }
        snackbar.show()
    }

    private data class Style(
        val background: Int,
        val border: Int,
        val accent: Int,
        val iconBackground: Int,
        val icon: String
    )

    private fun styleFor(type: Type) = when (type) {
        Type.SUCCESS -> Style(Color.rgb(7, 59, 46), Color.rgb(44, 118, 96), Color.rgb(52, 211, 153), Color.rgb(29, 89, 72), "✓")
        Type.INFO -> Style(Color.rgb(16, 42, 67), Color.rgb(50, 85, 111), Color.rgb(103, 232, 249), Color.rgb(30, 61, 89), "i")
        Type.WARNING -> Style(Color.rgb(74, 44, 10), Color.rgb(154, 107, 39), Color.rgb(251, 191, 36), Color.rgb(105, 65, 18), "!")
        Type.ERROR -> Style(Color.rgb(76, 16, 20), Color.rgb(159, 58, 66), Color.rgb(252, 165, 165), Color.rgb(104, 27, 35), "×")
    }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
