package com.example.meshviability.data.mapper

import com.example.meshviability.domain.entities.DeliveryUpdate
import com.example.meshviability.domain.entities.LinkQuality
import com.example.meshviability.domain.entities.Message
import com.example.meshviability.domain.entities.MessageStatus
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing

/**
 * Convierte un paquete de Meshtastic en un Message de la app.
 * Devuelve null si el paquete no es un mensaje de texto.
 */
fun MeshPacket.toMessageOrNull(receivedAt: Long): Message? {
    val data = decoded ?: return null
    if (data.portnum != PortNum.TEXT_MESSAGE_APP) return null

    return Message(
        id = id,
        fromNodeId = from,
        toNodeId = to,
        content = data.payload.utf8(),
        timeStampMs = receivedAt,
        status = MessageStatus.DELIVERED,
        signalQuality = toLinkQualityOrNull(),
    )
}

// RSSI = 0 significa que el paquete no vino por radio (o no se midió)
private fun MeshPacket.toLinkQualityOrNull(): LinkQuality? {
    val rssi = rx_rssi?.takeIf { it != 0 } ?: return null
    val snr = rx_snr ?: return null

    // hop_start = saltos con los que salió; hop_limit = saltos que le quedan
    val start = hop_start ?: 0
    val limit = hop_limit ?: 0
    val hops = if (start > 0) start - limit else null

    return LinkQuality(rssiDbm = rssi.toFloat(), snrDb = snr, hops = hops)
}

/**
 * Convierte un paquete de confirmación (ROUTING_APP) en una actualización de entrega.
 * Devuelve null si el paquete no es una confirmación.
 * Esto es un ACK (Acuse de Recibo, una confirmacion) que entrega el nodo
 */
fun MeshPacket.toDeliveryUpdateOrNull(): DeliveryUpdate? {
    val data = decoded ?: return null
    if (data.portnum != PortNum.ROUTING_APP) return null

    // ID del mensaje original que se está confirmando
    val requestId = data.request_id?.takeIf { it != 0 } ?: return null

    // el contenido es un mensaje Routing con el resultado
    val routing = Routing.ADAPTER.decode(data.payload)
    val error = routing.error_reason ?: return null

    val status = if (error == Routing.Error.NONE) MessageStatus.DELIVERED else MessageStatus.FAILED
    return DeliveryUpdate(messageId = requestId, status = status)
}