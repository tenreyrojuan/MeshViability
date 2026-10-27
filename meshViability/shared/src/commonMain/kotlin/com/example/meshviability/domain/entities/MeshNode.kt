package com.example.meshviability.domain.entities

// nodo conocido por el nuestro; hopsAway = null si el firmware no lo informa
data class MeshNode(
    val num: Int,
    val longName: String,
    val shortName: String,
    val hopsAway: Int?,
)
