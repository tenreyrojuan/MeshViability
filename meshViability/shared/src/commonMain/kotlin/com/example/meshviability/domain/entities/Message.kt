package com.example.meshviability.domain.entities

enum class MessageStatus {
    PENDING,
    CONNECTED,
    DISCONNECTED,
    DELIVERED,
    FAILED
}

data class LinkQuality(
    val rssiDbm : Float, // señal
    val snrDb : Float,  // relacion señal ruido
    val hops : Int? // saltos. Por cuantos nodos pasaron por la señal. Puede no haber
)

data class Message (
    val id: Int,
    val fromNodeId: Int,
    val toNodeId: Int,
    val timeStampMs: Long,
    val content : String,
    val status: MessageStatus,
    val signalQuality : LinkQuality?
)

// confirmación de entrega de un mensaje enviado
data class DeliveryUpdate(
    val messageId: Int,
    val status: MessageStatus,
)