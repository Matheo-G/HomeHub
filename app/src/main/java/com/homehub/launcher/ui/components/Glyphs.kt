package com.homehub.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.min

/*
 * Pictogrammes HomeHub, dessinés directement sur Canvas.
 *
 * Aucune bibliothèque d'icônes : ces quelques formes sont vectorielles,
 * nettes à toutes les tailles, recolorables, et n'ajoutent rien à l'APK.
 * Chaque glyphe se dessine dans un carré dont il prend toute la taille.
 */

/**
 * Indicateur Wi-Fi : trois arcs et un point.
 * @param level nombre d'arcs allumés (0 à 3).
 */
@Composable
fun WifiGlyph(level: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = min(size.width, size.height)
        val stroke = side * 0.11f
        val origin = Offset(size.width / 2f, size.height * 0.84f)
        for (arc in 1..3) {
            val radius = side * (0.26f * arc)
            drawArc(
                color = if (arc <= level) color else color.copy(alpha = 0.28f),
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(origin.x - radius, origin.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        drawCircle(color = color, radius = stroke * 0.85f, center = origin)
    }
}

/** Connexion filaire : trois nœuds reliés. */
@Composable
fun EthernetGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = min(size.width, size.height)
        val stroke = Stroke(width = side * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val node = side * 0.22f
        val topLeft = Offset((size.width - node) / 2f, size.height * 0.08f)
        drawRoundRect(color, topLeft, Size(node, node), CornerRadius(node * 0.25f), style = stroke)

        val path = Path().apply {
            moveTo(size.width / 2f, topLeft.y + node)
            lineTo(size.width / 2f, size.height * 0.55f)
            moveTo(size.width * 0.22f, size.height * 0.68f)
            lineTo(size.width * 0.22f, size.height * 0.55f)
            lineTo(size.width * 0.78f, size.height * 0.55f)
            lineTo(size.width * 0.78f, size.height * 0.68f)
        }
        drawPath(path, color, style = stroke)
        val bottomY = size.height * 0.68f
        drawRoundRect(color, Offset(size.width * 0.22f - node / 2f, bottomY), Size(node, node), CornerRadius(node * 0.25f), style = stroke)
        drawRoundRect(color, Offset(size.width * 0.78f - node / 2f, bottomY), Size(node, node), CornerRadius(node * 0.25f), style = stroke)
    }
}

/** Hors ligne : arcs Wi-Fi éteints barrés. */
@Composable
fun OfflineGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = min(size.width, size.height)
        val stroke = side * 0.11f
        val origin = Offset(size.width / 2f, size.height * 0.84f)
        for (arc in 1..3) {
            val radius = side * (0.26f * arc)
            drawArc(
                color = color.copy(alpha = 0.35f),
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(origin.x - radius, origin.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        drawLine(
            color = color,
            start = Offset(size.width * 0.15f, size.height * 0.12f),
            end = Offset(size.width * 0.85f, size.height * 0.92f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** Engrenage (Paramètres). */
@Composable
fun GearGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        val toothWidth = side * 0.16f
        val toothHeight = side * 0.2f
        for (tooth in 0 until 8) {
            rotate(degrees = tooth * 45f, pivot = center) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(center.x - toothWidth / 2f, center.y - side * 0.48f),
                    size = Size(toothWidth, toothHeight),
                    cornerRadius = CornerRadius(toothWidth * 0.3f),
                )
            }
        }
        drawCircle(color = color, radius = side * 0.29f, center = center, style = Stroke(width = side * 0.13f))
        drawCircle(color = color, radius = side * 0.08f, center = center)
    }
}

/** Grille 3 x 3 (Toutes les applications). */
@Composable
fun GridGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = min(size.width, size.height)
        val cell = side * 0.24f
        val gap = (side - cell * 3) / 2f
        val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)
        for (row in 0 until 3) {
            for (column in 0 until 3) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(origin.x + column * (cell + gap), origin.y + row * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(cell * 0.32f),
                )
            }
        }
    }
}

/** Plus (Installer). */
@Composable
fun PlusGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = min(size.width, size.height)
        val stroke = side * 0.12f
        val center = Offset(size.width / 2f, size.height / 2f)
        val half = side * 0.38f
        drawLine(color, Offset(center.x - half, center.y), Offset(center.x + half, center.y), stroke, StrokeCap.Round)
        drawLine(color, Offset(center.x, center.y - half), Offset(center.x, center.y + half), stroke, StrokeCap.Round)
    }
}

/** Coche (option sélectionnée). */
@Composable
fun CheckGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = min(size.width, size.height)
        val path = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.53f)
            lineTo(size.width * 0.42f, size.height * 0.74f)
            lineTo(size.width * 0.8f, size.height * 0.3f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = side * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Flèche sortante (ouvre une application ou un écran système). */
@Composable
fun ArrowOutGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = min(size.width, size.height)
        val stroke = Stroke(width = side * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.72f)
            lineTo(size.width * 0.72f, size.height * 0.28f)
            moveTo(size.width * 0.38f, size.height * 0.28f)
            lineTo(size.width * 0.72f, size.height * 0.28f)
            lineTo(size.width * 0.72f, size.height * 0.62f)
        }
        drawPath(path, color, style = stroke)
    }
}
