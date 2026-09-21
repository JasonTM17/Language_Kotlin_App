package com.linguaai.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Shared motion vocabulary for the redesigned surfaces. Screens take durations
 * and springs from here so interaction timing feels like one product.
 */
object LinguaMotion {
    const val DurationFast = 120
    const val DurationMedium = 240
    const val DurationSlow = 400

    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardEasing: Easing = FastOutSlowInEasing

    fun <T> fast() = tween<T>(DurationFast, easing = StandardEasing)

    fun <T> medium() = tween<T>(DurationMedium, easing = EmphasizedEasing)

    fun <T> slow() = tween<T>(DurationSlow, easing = EmphasizedEasing)

    fun <T> pop() =
        spring<T>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        )

    fun <T> smooth() =
        spring<T>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
}
