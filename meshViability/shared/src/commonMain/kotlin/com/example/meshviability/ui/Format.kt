package com.example.meshviability.ui

import com.example.meshviability.data.protocol.MeshProtocolClient
import com.example.meshviability.domain.entities.MeshNode
import kotlin.math.abs
import kotlin.math.roundToLong

// acepta coma decimal; null si no es un número
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

fun formatDecimal(value: Double, decimals: Int = 1): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = (value * factor).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val whole = abs(scaled) / factor
    if (decimals == 0) return "$sign$whole"
    val fraction = (abs(scaled) % factor).toString().padStart(decimals, '0')
    return "$sign$whole,$fraction"
}

// valor editable: sin decimales sobrantes y con punto, como lo escribe el usuario
fun formatInput(value: Double): String =
    if (value == value.roundToLong().toDouble()) value.roundToLong().toString() else value.toString()

fun formatElapsed(fromMs: Long, toMs: Long): String {
    val seconds = ((toMs - fromMs) / 1000).coerceAtLeast(0)
    return "+${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
}

// "!1a2b3c4d" es como Meshtastic muestra un número de nodo
fun nodeId(num: Int): String = "!" + num.toUInt().toString(16).padStart(8, '0')

fun nodeLabel(num: Int, nodes: List<MeshNode>, myNodeNum: Int?): String = when (num) {
    MeshProtocolClient.BROADCAST -> "Todos"
    myNodeNum -> "Yo"
    else -> nodes.firstOrNull { it.num == num }?.longName?.takeIf { it.isNotBlank() } ?: nodeId(num)
}
