package com.example.toothtimer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

fun ZoneType.description(): String = when (this) {
    ZoneType.CHEW_TOP_RIGHT -> "Верх справа · жевательная"
    ZoneType.CHEW_TOP_LEFT -> "Верх слева · жевательная"
    ZoneType.CHEW_BOTTOM_RIGHT -> "Низ справа · жевательная"
    ZoneType.CHEW_BOTTOM_LEFT -> "Низ слева · жевательная"
    ZoneType.OUTER_TOP_RIGHT -> "Верх справа · наружная"
    ZoneType.OUTER_TOP_LEFT -> "Верх слева · наружная"
    ZoneType.OUTER_BOTTOM_RIGHT -> "Низ справа · наружная"
    ZoneType.OUTER_BOTTOM_LEFT -> "Низ слева · наружная"
    ZoneType.FRONT_TOP -> "Верх · передние зубы"
    ZoneType.FRONT_BOTTOM -> "Низ · передние зубы"
    ZoneType.CUSTOM -> "Своя зона (без подсветки)"
}

/** View into an open mouth: the person's right is on the left of the screen. */
@Composable
fun TeethMap(activeZone: ZoneType, modifier: Modifier = Modifier) {
    val upper = activeZone in setOf(ZoneType.CHEW_TOP_RIGHT, ZoneType.CHEW_TOP_LEFT,
        ZoneType.OUTER_TOP_RIGHT, ZoneType.OUTER_TOP_LEFT, ZoneType.FRONT_TOP)
    val right = activeZone in setOf(ZoneType.CHEW_TOP_RIGHT, ZoneType.CHEW_BOTTOM_RIGHT,
        ZoneType.OUTER_TOP_RIGHT, ZoneType.OUTER_BOTTOM_RIGHT)
    val front = activeZone == ZoneType.FRONT_TOP || activeZone == ZoneType.FRONT_BOTTOM
    val outer = activeZone.name.startsWith("OUTER")
    Column(modifier.background(Color(0xFFF0F7FA), RoundedCornerShape(20.dp))
        .padding(10.dp).semantics { contentDescription = activeZone.description() },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Ваша правая", fontSize = 12.sp)
            Text("ВЕРХ", fontSize = 12.sp)
            Text("Ваша левая", fontSize = 12.sp)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val unit = min(size.width / 340f, size.height / 190f)
                val cx = size.width / 2
                val cy = size.height / 2
                val teal = Color(0xFF007F86)
                for (top in listOf(true, false)) {
                    val sign = if (top) -1f else 1f
                    // Pink gum band follows each arch behind the individual crowns.
                    drawArc(Color(0xFFF1C7CE), if (top) 180f else 0f, 180f, false,
                        Offset(cx - 139 * unit, cy + sign * 11 * unit - 75 * unit),
                        Size(278 * unit, 150 * unit), style = Stroke(25 * unit))
                    for (i in 0..13) {
                        val angle = PI - i * PI / 13
                        val x = cx + cos(angle).toFloat() * 128 * unit
                        val y = cy + sign * (12 + sin(angle).toFloat() * 66) * unit
                        val selected = activeZone != ZoneType.CUSTOM && top == upper &&
                            (if (front) i in 5..8 else if (right) i <= 4 else i >= 9)
                        val anterior = i in 5..8
                        val w = (if (anterior) 19 else if (i <= 2 || i >= 11) 14 else 20) * unit
                        val h = (if (anterior) 19 else 27) * unit
                        val center = Offset(x, y)
                        rotate(sign * (90 - i * 180f / 13), center) {
                            val start = Offset(x - w / 2, y - h / 2)
                            drawRoundRect(Color.White, start, Size(w, h), CornerRadius(6 * unit))
                            drawRoundRect(if (selected) teal else Color(0xFF8DADB7), start,
                                Size(w, h), CornerRadius(6 * unit), style = Stroke((if (selected) 2.5f else 1f) * unit))
                            if (selected && !outer) {
                                drawRoundRect(teal, Offset(x - w * .32f, y - h * .32f),
                                    Size(w * .64f, h * .64f), CornerRadius(4 * unit))
                            }
                            if (!anterior) {
                                val groove = if (selected && !outer) Color.White else Color(0xFFB5CAD0)
                                drawLine(groove, Offset(x, y - h * .25f), Offset(x, y + h * .25f), 1.2f * unit)
                                drawLine(groove, Offset(x - w * .23f, y), Offset(x + w * .23f, y), 1.2f * unit)
                            }
                            // Outer surface is a distinct stripe on the lip/cheek side.
                            if (selected && outer) drawLine(teal,
                                Offset(x - w * .35f, y + sign * h * .35f),
                                Offset(x + w * .35f, y + sign * h * .35f), 5 * unit)
                        }
                    }
                }
            }
            Text(if (activeZone == ZoneType.CUSTOM) "Следуйте названию зоны"
                else if (front) "Передние зубы" else if (outer) "Со стороны щеки" else "Жевательная поверхность",
                fontSize = 12.sp, color = Color(0xFF007F86), textAlign = TextAlign.Center)
        }
        Text("НИЗ · вид в открытый рот", fontSize = 12.sp)
        Text("${activeZone.description()} · выделено бирюзовым", fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}
