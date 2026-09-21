package com.linguaai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Shared brand gradients from the Stitch-seeded design pass. Content drawn on
 * top of the hero gradient is always white; the muted/amber tokens keep their
 * contrast on both the light and dark hero stops.
 */
object BrandGradients {
    /** Hero header gradient (light mode) — emerald sweep for celebration cards. */
    val HeroLight: Brush =
        Brush.linearGradient(colors = listOf(Color(0xFF17925B), Color(0xFF0A5136)))

    /** Hero header gradient (dark mode) — deeper emerald for the same surfaces. */
    val HeroDark: Brush =
        Brush.linearGradient(colors = listOf(Color(0xFF0E6B44), Color(0xFF073823)))

    /** Warm coral pill for playful accents such as the user chat bubble. */
    val AccentPill: Brush =
        Brush.linearGradient(colors = listOf(Color(0xFFC74324), Color(0xFFE06B3C)))

    /** Content color on top of the hero gradient (both modes). */
    val OnHero: Color = Color(0xFFFFFFFF)

    /** Secondary content color on top of the hero gradient. */
    val OnHeroMuted: Color = Color(0xFFCFE9DA)

    /** Warm amber highlight (flame, tassel) that reads on the hero gradient. */
    val OnHeroAmber: Color = Color(0xFFFFDF9E)

    /** Badge medallion gradient (gold tier). */
    val BadgeGold: Brush =
        Brush.linearGradient(colors = listOf(Color(0xFFF3CE63), Color(0xFFD99B23)))

    /** Soft radial glow behind mascots and celebration moments. */
    fun mascotGlow(color: Color): Brush =
        Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent))

    /** Theme-aware hero gradient. */
    @Composable
    fun hero(): Brush = if (isSystemInDarkTheme()) HeroDark else HeroLight
}
