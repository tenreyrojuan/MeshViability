package com.example.meshviability.domain.link

import com.example.meshviability.domain.entities.LinkQuality

// Solo un paquete directo (0 saltos) mide el enlace A–B. En uno retransmitido,
// RSSI y SNR son del último salto.
enum class MeasurementValidity { VALID, RELAYED, UNKNOWN_HOPS, VIA_MQTT }

fun LinkQuality.validity(acceptUnknownHops: Boolean = false): MeasurementValidity = when {
    viaMqtt -> MeasurementValidity.VIA_MQTT
    hops == null -> if (acceptUnknownHops) MeasurementValidity.VALID else MeasurementValidity.UNKNOWN_HOPS
    hops > 0 -> MeasurementValidity.RELAYED
    else -> MeasurementValidity.VALID
}

data class LinkEvaluation(
    val margin: LinkMargin,
    val level: LinkLevel,
    val expectedRssiDbm: Double,
    // positiva: el enlace real pierde más que el espacio libre
    val extraLossDb: Double,
)

fun LinkQuality.evaluate(
    parameters: LinkParameters,
    thresholds: LinkThresholds = LinkThresholds(),
): LinkEvaluation {
    val margin = margin(parameters)
    val expected = parameters.predict().rxPowerDbm
    return LinkEvaluation(
        margin = margin,
        level = margin.level(thresholds),
        expectedRssiDbm = expected,
        extraLossDb = expected - rssiDbm,
    )
}
