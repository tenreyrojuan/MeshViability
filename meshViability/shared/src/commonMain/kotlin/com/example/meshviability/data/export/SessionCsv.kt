package com.example.meshviability.data.export

import com.example.meshviability.domain.entities.Message
import com.example.meshviability.domain.link.LinkParameters
import com.example.meshviability.domain.link.LinkThresholds
import com.example.meshviability.domain.link.MeasurementValidity
import com.example.meshviability.domain.link.evaluate
import com.example.meshviability.domain.link.validity

/** Sesión como CSV (RFC 4180: coma y punto decimal). Una fila por mensaje, con los parámetros usados. */
object SessionCsv {

    val HEADER = listOf(
        "tiempo_ms", "id", "origen", "destino", "texto", "estado",
        "rssi_dbm", "snr_db", "saltos", "validez",
        "margen_rssi_db", "margen_snr_db", "margen_peor_db", "nivel",
        "rssi_esperado_dbm", "perdida_extra_db",
        "ptx_dbm", "frecuencia_mhz", "distancia_km", "sf", "bw_khz", "nf_db",
    )

    private const val HZ_PER_KHZ = 1000.0

    fun build(
        messages: List<Message>,
        parameters: LinkParameters,
        thresholds: LinkThresholds,
        acceptUnknownHops: Boolean,
        label: (Int) -> String,
    ): String {
        val p = parameters
        val params = listOf(
            p.txPowerDbm.toString(), p.frequencyMhz.toString(), p.distanceKm.toString(),
            p.spreadingFactor.value.toString(), (p.bandwidthHz / HZ_PER_KHZ).toString(), p.noiseFigureDb.toString(),
        )
        val rows = messages.map { m ->
            val q = m.signalQuality
            val validity = q?.validity(acceptUnknownHops)
            val e = q?.takeIf { validity == MeasurementValidity.VALID }?.evaluate(p, thresholds)
            listOf(
                m.timeStampMs.toString(), m.id.toUInt().toString(), label(m.fromNodeId), label(m.toNodeId),
                m.content, if (q == null) m.status.name else "",
                q?.rssiDbm?.toString().orEmpty(), q?.snrDb?.toString().orEmpty(),
                q?.hops?.toString().orEmpty(), validity?.name.orEmpty(),
                e?.margin?.rssiMarginDb?.toString().orEmpty(), e?.margin?.snrMarginDb?.toString().orEmpty(),
                e?.margin?.worstDb?.toString().orEmpty(), e?.level?.name.orEmpty(),
                e?.expectedRssiDbm?.toString().orEmpty(), e?.extraLossDb?.toString().orEmpty(),
            ) + params
        }
        return (listOf(HEADER) + rows).joinToString("\r\n", postfix = "\r\n") { row -> row.joinToString(",", transform = ::escape) }
    }

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
}
