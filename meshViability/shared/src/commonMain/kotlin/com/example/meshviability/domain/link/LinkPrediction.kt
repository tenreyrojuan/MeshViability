package com.example.meshviability.domain.link

import com.example.meshviability.domain.entities.LinkQuality

// Parámetros del enlace que se editan en la UI.
// Por defecto: Heltec V3 con Long Fast, sin cables ni antenas con ganancia.
data class LinkParameters(
    val txPowerDbm: Double = DEFAULT_TX_POWER_DBM,
    val txCableLossDb: Double = 0.0,
    val rxCableLossDb: Double = 0.0,
    val txAntennaGainDbi: Double = 0.0,
    val rxAntennaGainDbi: Double = 0.0,
    val frequencyMhz: Double = LinkBudget.DEFAULT_FREQUENCY_MHZ,
    val distanceKm: Double = DEFAULT_DISTANCE_KM,
    val spreadingFactor: SpreadingFactor = SpreadingFactor.SF11,
    val bandwidthHz: Double = LoraBandwidth.KHZ_250_IN_HZ,
    val noiseFigureDb: Double = ModemPreset.DEFAULT_NOISE_FIGURE_DB,
) {
    val budget: LinkBudget
        get() = LinkBudget(
            txPowerDbm = txPowerDbm,
            txCableLossDb = txCableLossDb,
            txAntennaGainDbi = txAntennaGainDbi,
            rxAntennaGainDbi = rxAntennaGainDbi,
            rxCableLossDb = rxCableLossDb,
            frequencyMhz = frequencyMhz,
        )

    val sensitivityDbm: Double
        get() = Sensitivity.dbm(bandwidthHz, spreadingFactor, noiseFigureDb)

    companion object {
        const val DEFAULT_TX_POWER_DBM = 20.0
        const val DEFAULT_DISTANCE_KM = 1.0
    }
}

data class LinkPrediction(
    val noiseDbm: Double,
    val sensitivityDbm: Double,
    val pathLossDb: Double,
    val rxPowerDbm: Double,
    val marginDb: Double,
    val maxRangeKm: Double,
)

fun LinkParameters.predict(): LinkPrediction {
    val sensitivity = sensitivityDbm
    return LinkPrediction(
        noiseDbm = ThermalNoise.powerDbm(bandwidthHz),
        sensitivityDbm = sensitivity,
        pathLossDb = budget.pathLossDb(distanceKm),
        rxPowerDbm = budget.predictedRssiDbm(distanceKm),
        marginDb = budget.marginDb(distanceKm, sensitivity),
        // alcance teórico: distancia donde el margen es 0
        maxRangeKm = budget.maxRangeKm(sensitivity, fadeMarginDb = 0.0),
    )
}

fun LinkQuality.margin(parameters: LinkParameters) =
    margin(parameters.sensitivityDbm, parameters.spreadingFactor)
