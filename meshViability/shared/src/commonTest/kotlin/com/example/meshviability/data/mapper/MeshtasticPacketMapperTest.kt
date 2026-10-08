package com.example.meshviability.data.mapper

import com.example.meshviability.domain.entities.Message
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MeshPacketMapperTest {

    private fun paquete(
        portnum: PortNum = PortNum.TEXT_MESSAGE_APP,
        rssi: Int = -95,
        hopStart: Int = 3,
        hopLimit: Int = 3,
    ) = MeshPacket(
        from = 0x5678,
        to = -1,
        id = 77,
        rx_rssi = rssi,
        rx_snr = 4.25f,
        hop_start = hopStart,
        hop_limit = hopLimit,
        decoded = Data(portnum = portnum, payload = "hola".encodeUtf8()),
    )

    @Test
    fun textoSeConvierteEnMensaje() {
        val msg = paquete().toMessageOrNull(receivedAt = 1000L)!!
        assertEquals("hola", msg.content)
        assertEquals(0x5678, msg.fromNodeId)
        assertEquals(-1, msg.toNodeId)
        assertEquals(1000L, msg.timeStampMs)
    }

    @Test
    fun paqueteQueNoEsTextoDevuelveNull() {
        assertNull(paquete(portnum = PortNum.POSITION_APP).toMessageOrNull(1000L))
    }

    @Test
    fun saltosSeCalculanBien() {
        val directo = paquete(hopStart = 3, hopLimit = 3).toMessageOrNull(0L)!!
        val retransmitido = paquete(hopStart = 3, hopLimit = 1).toMessageOrNull(0L)!!
        assertEquals(0, directo.signalQuality!!.hops)
        assertEquals(2, retransmitido.signalQuality!!.hops)
    }

    @Test
    fun sinRssiNoHayMedicion() {
        assertNull(paquete(rssi = 0).toMessageOrNull(0L)!!.signalQuality)
    }
}