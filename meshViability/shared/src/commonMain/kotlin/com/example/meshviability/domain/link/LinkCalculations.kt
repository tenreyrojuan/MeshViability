package com.example.meshviability.domain.link

import com.example.meshviability.domain.entities.LinkQuality
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

// Decibeles y dBm
object Decibel {
    const val POWER_FACTOR = 10.0      // dB = 10 log10(P1 / P2)
    const val LOG_BASE = 10.0          // base del logaritmo decimal
    const val MILLIWATT_IN_W = 1e-3    // referencia del dBm: 1 mW

    // P[dBm] = 10 log10(P / 1 mW). De potencia a Decibelios.
    fun wattsToDbm(watts: Double): Double = POWER_FACTOR * log10(watts / MILLIWATT_IN_W)
}

// Ruido térmico: N = KTB
object ThermalNoise {
    const val BOLTZMANN_J_PER_K = 1.380649e-23  // Constante K
    const val CELSIUS_TO_KELVIN = 273.0         // de Celsius a Kevin
    const val STANDARD_TEMPERATURE_C = 17.0     // Temperatura estandar

    // N[dBm] = 10 log10(K · T · B / 1 mW)
    fun powerDbm(bandwidthHz: Double, temperatureC: Double = STANDARD_TEMPERATURE_C): Double {
        val temperatureK = temperatureC + CELSIUS_TO_KELVIN
        val noiseW = BOLTZMANN_J_PER_K * temperatureK * bandwidthHz

        return Decibel.wattsToDbm(noiseW)
    }
}

// Pérdida en el espacio libre
object FreeSpaceLoss {
    const val CONSTANT_DB = 32.45   // constante para f en MHz y d en km
    const val LOG_FACTOR = 20.0     // la potencia decae con el cuadrado de f y de d

    // L0 = 32,45 + 20 log f[MHz] + 20 log d[km]. Perdida L0
    fun lossDb(frequencyMhz: Double, distanceKm: Double): Double =
        CONSTANT_DB + LOG_FACTOR * log10(frequencyMhz) + LOG_FACTOR * log10(distanceKm)
}

// =====================================================================
// Parámetros del módem LoRa (segun meshtastic; esto no esta en las unidades)
// =====================================================================

// SNR mínimo para demodular según el factor de dispersion. Ignoren esto
enum class SpreadingFactor(val value: Int, val requiredSnrDb: Double) {
    SF7(7, -7.5),
    SF8(8, -10.0),
    SF9(9, -12.5),
    SF10(10, -15.0),
    SF11(11, -17.5),
    SF12(12, -20.0),
}

// Anchos de banda usados por los presets de Meshtastic.
object LoraBandwidth {

    const val KHZ_125_IN_HZ = 125_000.0

    const val KHZ_250_IN_HZ = 250000.0
}

// Presets de Meshtastic. Verificar contra la documentación oficial.
// De nuevo ignoren esto.
enum class ModemPreset(val spreadingFactor: SpreadingFactor, val bandwidthHz: Double) {
    SHORT_FAST(SpreadingFactor.SF7, LoraBandwidth.KHZ_250_IN_HZ),
    SHORT_SLOW(SpreadingFactor.SF8, LoraBandwidth.KHZ_250_IN_HZ),
    MEDIUM_FAST(SpreadingFactor.SF9, LoraBandwidth.KHZ_250_IN_HZ),
    MEDIUM_SLOW(SpreadingFactor.SF10, LoraBandwidth.KHZ_250_IN_HZ),
    LONG_FAST(SpreadingFactor.SF11, LoraBandwidth.KHZ_250_IN_HZ),
    LONG_MODERATE(SpreadingFactor.SF11, LoraBandwidth.KHZ_125_IN_HZ),
    LONG_SLOW(SpreadingFactor.SF12, LoraBandwidth.KHZ_125_IN_HZ);

    // SRX = N (ruido térmico) + NF (índice de ruido) + SNR mínimo
    fun sensitivityDbm(
        noiseFigureDb: Double = DEFAULT_NOISE_FIGURE_DB,
        temperatureC: Double = ThermalNoise.STANDARD_TEMPERATURE_C,
    ): Double =
        ThermalNoise.powerDbm(bandwidthHz, temperatureC) +
                noiseFigureDb +
                spreadingFactor.requiredSnrDb

    companion object {
        const val DEFAULT_NOISE_FIGURE_DB = 6.0   // valor típico de los chips SX126x
    }
}

// =====================================================================
// Margen de una medición real
// =====================================================================

// niveles de enlace: Solido, Marginal y No confiable
// con esto evaluamos que tna confiable es la señal
enum class LinkLevel { SOLID, MARGINAL, UNRELIABLE }

data class LinkMargin(val rssiMarginDb: Double, val snrMarginDb: Double) {

    // se clasifica según el peor de los dos márgenes
    val level: LinkLevel
        get() {
            val worst = minOf(rssiMarginDb, snrMarginDb)

            // when es un switch de kotlin
            return when {
                worst >= SOLID_THRESHOLD_DB -> LinkLevel.SOLID
                worst >= USABLE_THRESHOLD_DB -> LinkLevel.MARGINAL
                else -> LinkLevel.UNRELIABLE
            }
        }

    // companion en kotlin es static. Una funcion que no requiere instancia.
    companion object {
        const val SOLID_THRESHOLD_DB = 10.0   // margen que tolera desvanecimientos
        const val USABLE_THRESHOLD_DB = 0.0   // por debajo, el receptor no entiende la señal
    }
}

// MARGEN = RSSI − SRX, más el margen de SNR propio de LoRa
fun LinkQuality.margin(
    preset: ModemPreset,
    noiseFigureDb: Double = ModemPreset.DEFAULT_NOISE_FIGURE_DB) =
    LinkMargin(
    rssiMarginDb = rssiDbm - preset.sensitivityDbm(noiseFigureDb),
    snrMarginDb = snrDb - preset.spreadingFactor.requiredSnrDb,
)

// =====================================================================
// Balance de enlace (predicción)
// =====================================================================

// PRX = PTX − Lcable_tx + G_tx − Lespacio + G_rx − Lcable_rx.
// Calculamos si deberia ser viable o no el enlace
data class LinkBudget(
    val txPowerDbm: Double,
    val txCableLossDb: Double = 0.0,
    val txAntennaGainDbi: Double = 0.0,
    val rxAntennaGainDbi: Double = 0.0,
    val rxCableLossDb: Double = 0.0,
    val frequencyMhz: Double = DEFAULT_FREQUENCY_MHZ,
    val pathLossExponent: Double = FREE_SPACE_EXPONENT)
{

    // Con exponente 2 es exactamente la pérdida en el espacio libre
    fun pathLossDb(distanceKm: Double): Double {
        val d = max(distanceKm, MIN_DISTANCE_KM)
        val lossAtReference = FreeSpaceLoss.lossDb(frequencyMhz, REFERENCE_DISTANCE_KM)
        return (lossAtReference + Decibel.POWER_FACTOR * pathLossExponent * log10(d / REFERENCE_DISTANCE_KM))
    }

    fun predictedRssiDbm(distanceKm: Double) : Double
    = txPowerDbm - txCableLossDb + txAntennaGainDbi - pathLossDb(distanceKm) + rxAntennaGainDbi - rxCableLossDb

    // MARGEN = RSSI − SRX. La SRX puede venir de una hoja de datos o de un preset.
    fun marginDb(distanceKm: Double, sensitivityDbm: Double): Double =
        predictedRssiDbm(distanceKm) - sensitivityDbm

    // SNR[dB] = PRX − N
    fun predictedSnrDb(
        distanceKm: Double,
        bandwidthHz: Double,
        temperatureC: Double = ThermalNoise.STANDARD_TEMPERATURE_C
    ): Double
    = predictedRssiDbm(distanceKm) - ThermalNoise.powerDbm(bandwidthHz, temperatureC)

    // Distancia máxima que conserva un margen de seguridad sobre la sensibilidad
    fun maxRangeKm(
        sensitivityDbm: Double,
        fadeMarginDb: Double = DEFAULT_FADE_MARGIN_DB
    ): Double
    {
        val netGainDb = txPowerDbm - txCableLossDb + txAntennaGainDbi +
                rxAntennaGainDbi - rxCableLossDb
        val allowedPathLossDb = netGainDb - sensitivityDbm - fadeMarginDb
        val lossAtReference = FreeSpaceLoss.lossDb(frequencyMhz, REFERENCE_DISTANCE_KM)
        val exponent = (allowedPathLossDb - lossAtReference) /
                (Decibel.POWER_FACTOR * pathLossExponent)
        return REFERENCE_DISTANCE_KM * Decibel.LOG_BASE.pow(exponent)
    }

    companion object {
        const val DEFAULT_FREQUENCY_MHZ = 915.0   // banda ISM usada en Argentina
        const val FREE_SPACE_EXPONENT = 2.0       // 2 = espacio libre; 3 a 4 = obstáculos
        const val DEFAULT_FADE_MARGIN_DB = 10.0   // reserva para desvanecimientos
        const val REFERENCE_DISTANCE_KM = 1.0     // la fórmula del apunte usa km
        const val MIN_DISTANCE_KM = 0.001         // no se calcula por debajo de 1 m
    }
}