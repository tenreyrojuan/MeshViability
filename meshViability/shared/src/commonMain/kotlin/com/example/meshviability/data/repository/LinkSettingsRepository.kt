package com.example.meshviability.data.repository

import com.example.meshviability.domain.entities.LoraSettings
import com.example.meshviability.domain.link.LinkParameters
import com.example.meshviability.domain.link.LinkThresholds
import com.example.meshviability.domain.link.withNodeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/** Parámetros del enlace y criterios del semáforo, compartidos por las pantallas. En memoria. */
class LinkSettingsRepository(nodeSettings: Flow<LoraSettings?>, scope: CoroutineScope) {

    private val _parameters = MutableStateFlow(LinkParameters())
    val parameters: StateFlow<LinkParameters> = _parameters.asStateFlow()

    private val _thresholds = MutableStateFlow(LinkThresholds())
    val thresholds: StateFlow<LinkThresholds> = _thresholds.asStateFlow()

    // con firmware anterior a 2.3 los saltos no se conocen; por defecto esos paquetes no cuentan
    private val _acceptUnknownHops = MutableStateFlow(false)
    val acceptUnknownHops: StateFlow<Boolean> = _acceptUnknownHops.asStateFlow()

    init {
        nodeSettings
            .filterNotNull()
            .onEach { s -> _parameters.update { it.withNodeSettings(s) } }
            .launchIn(scope)
    }

    fun updateParameters(transform: (LinkParameters) -> LinkParameters) = _parameters.update(transform)

    fun setThresholds(thresholds: LinkThresholds) {
        _thresholds.value = thresholds
    }

    fun setAcceptUnknownHops(accept: Boolean) {
        _acceptUnknownHops.value = accept
    }
}
