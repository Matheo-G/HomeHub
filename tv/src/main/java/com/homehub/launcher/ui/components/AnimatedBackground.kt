package com.homehub.launcher.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.homehub.launcher.data.models.WallpaperStyle
import com.homehub.launcher.ui.theme.HubMotion
import com.homehub.launcher.ui.theme.LocalAccent
import com.homehub.launcher.ui.theme.WallpaperPalette
import com.homehub.launcher.ui.theme.wallpaperPalette
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fond de l'accueil : dégradé sombre, deux lumières qui dérivent lentement,
 * particules qui montent en scintillant, vignettage.
 *
 * Conception :
 *  - une seule horloge (`time`, de 0 à 1 en deux minutes) pilote tout ;
 *    chaque mouvement est périodique sur un nombre entier de cycles, la
 *    boucle se raccorde donc sans saut visible ;
 *  - `time` n'est lu que dans la phase de dessin : l'animation redessine un
 *    calque, elle ne recompose jamais ;
 *  - dégradés et pinceaux sont construits une fois dans `drawWithCache` puis
 *    simplement translatés à chaque image ;
 *  - l'horloge d'animation Compose est suspendue dès que l'activité n'est
 *    plus visible : aucune consommation en arrière-plan.
 */
@Composable
fun AnimatedBackground(style: WallpaperStyle, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Crossfade(
        targetState = style,
        animationSpec = tween(HubMotion.WallpaperCrossfadeMillis),
        modifier = modifier,
        label = "wallpaper",
    ) { current ->
        if (current.isAnimated) {
            LivingWallpaper(current, accent)
        } else {
            StillWallpaper(current, accent)
        }
    }
}

/** Fond animé (Aurore, Nébuleuse). */
@Composable
private fun LivingWallpaper(style: WallpaperStyle, accent: State<Color>) {
    val transition = rememberInfiniteTransition(label = "wallpaperClock")
    val time = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(HubMotion.WallpaperLoopMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wallpaperTime",
    )
    val particles = remember { Particle.generate(count = PARTICLE_COUNT, seed = PARTICLE_SEED) }

    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val palette = wallpaperPalette(style, accent.value)
                val base = Brush.verticalGradient(listOf(palette.top, palette.bottom))
                val primaryRadius = size.maxDimension * 0.55f
                val secondaryRadius = size.maxDimension * 0.42f
                val primaryGlow = Brush.radialGradient(
                    colors = listOf(palette.glowPrimary, Color.Transparent),
                    center = Offset.Zero,
                    radius = primaryRadius,
                )
                val secondaryGlow = Brush.radialGradient(
                    colors = listOf(palette.glowSecondary, Color.Transparent),
                    center = Offset.Zero,
                    radius = secondaryRadius,
                )
                val vignette = vignetteBrush(this.size.width, this.size.height)
                val particleRadiusUnit = 1.dp.toPx()

                onDrawBehind {
                    val angle = time.value * TWO_PI
                    drawRect(base)

                    translate(
                        left = size.width * (0.24f + 0.10f * sin(angle)),
                        top = size.height * (0.16f + 0.08f * cos(angle)),
                    ) {
                        drawCircle(primaryGlow, radius = primaryRadius, center = Offset.Zero)
                    }
                    translate(
                        left = size.width * (0.80f + 0.07f * cos(angle * 2f)),
                        top = size.height * (0.88f + 0.06f * sin(angle)),
                    ) {
                        drawCircle(secondaryGlow, radius = secondaryRadius, center = Offset.Zero)
                    }

                    drawParticles(particles, time.value, palette.particle, particleRadiusUnit)
                    drawRect(vignette)
                }
            },
    )
}

/** Fond statique (Minuit) : mêmes couches, sans horloge ni particules. */
@Composable
private fun StillWallpaper(style: WallpaperStyle, accent: State<Color>) {
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val palette = wallpaperPalette(style, accent.value)
                onDrawBehind { drawStaticWallpaper(palette) }
            },
    )
}

/**
 * Dessin fixe d'un fond : réutilisé par le fond « Minuit » et par les
 * aperçus de la page Paramètres.
 */
fun DrawScope.drawStaticWallpaper(palette: WallpaperPalette) {
    drawRect(Brush.verticalGradient(listOf(palette.top, palette.bottom)))
    val radius = size.maxDimension * 0.55f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.glowPrimary, Color.Transparent),
            center = Offset(size.width * 0.28f, size.height * 0.18f),
            radius = radius,
        ),
        radius = radius,
        center = Offset(size.width * 0.28f, size.height * 0.18f),
    )
    if (palette.glowSecondary != Color.Transparent) {
        val secondary = size.maxDimension * 0.42f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.glowSecondary, Color.Transparent),
                center = Offset(size.width * 0.82f, size.height * 0.9f),
                radius = secondary,
            ),
            radius = secondary,
            center = Offset(size.width * 0.82f, size.height * 0.9f),
        )
    }
    drawRect(vignetteBrush(size.width, size.height))
}

/** Assombrit doucement les bords pour concentrer le regard au centre. */
private fun vignetteBrush(width: Float, height: Float): Brush = Brush.radialGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
    center = Offset(width / 2f, height / 2f),
    radius = maxOf(width, height) * 0.78f,
)

/**
 * Particules qui montent lentement et scintillent.
 * Positions normalisées (0..1) : indépendantes de la résolution.
 */
private fun DrawScope.drawParticles(
    particles: List<Particle>,
    time: Float,
    color: Color,
    radiusUnit: Float,
) {
    if (color.alpha == 0f) return
    for (particle in particles) {
        // Montée : `rise` cycles entiers par boucle, donc raccord parfait.
        val y = fract(particle.startY - time * particle.rise)
        val sway = 0.012f * sin((time * particle.rise + particle.phase) * TWO_PI)
        val twinkle = 0.55f + 0.45f * sin((time * particle.twinkle + particle.phase) * TWO_PI)
        // Disparition en douceur près des bords haut et bas.
        val edgeFade = (minOf(y, 1f - y) * 6f).coerceIn(0f, 1f)
        drawCircle(
            color = color,
            radius = particle.radius * radiusUnit,
            center = Offset((particle.x + sway) * size.width, y * size.height),
            alpha = particle.maxAlpha * twinkle * edgeFade,
        )
    }
}

/** Partie fractionnaire positive, pour boucler une coordonnée dans 0..1. */
private fun fract(value: Float): Float = value - floor(value)

/** 2π en simple précision. */
private const val TWO_PI: Float = 6.2831855f
private const val PARTICLE_COUNT = 36
private const val PARTICLE_SEED = 42

/**
 * Particule immuable, générée une fois avec une graine fixe
 * (même ciel à chaque démarrage).
 *
 * @property rise nombre entier de traversées verticales par boucle.
 * @property twinkle nombre entier de scintillements par boucle.
 */
@Immutable
private class Particle(
    val x: Float,
    val startY: Float,
    val radius: Float,
    val rise: Int,
    val twinkle: Int,
    val phase: Float,
    val maxAlpha: Float,
) {
    companion object {
        fun generate(count: Int, seed: Int): List<Particle> {
            val random = Random(seed)
            return List(count) {
                Particle(
                    x = random.nextFloat(),
                    startY = random.nextFloat(),
                    radius = 0.8f + random.nextFloat() * 1.6f,
                    rise = 1 + random.nextInt(3),
                    twinkle = 3 + random.nextInt(6),
                    phase = random.nextFloat(),
                    maxAlpha = 0.18f + random.nextFloat() * 0.32f,
                )
            }
        }
    }
}
