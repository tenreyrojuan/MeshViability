package com.example.meshviability.data.protocol

import com.example.meshviability.data.transport.FakeTransport
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.ToRadio
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ProtoEncodingTest {

    // Los bytes coinciden con el formato protobuf real.
    // Campo 3, tipo varint: tag = (3 << 3) | 0 = 24 = 0x18. Valor 42 = 0x2A.
    @Test
    fun wantConfigIdGeneraLosBytesExactos() {
        val bytes = ToRadio(want_config_id = 42).encode()
        assertContentEquals(byteArrayOf(0x18, 0x2A), bytes)
    }

    // Los tipos de mensaje tienen los números oficiales.
    @Test
    fun portNumTieneLosValoresOficiales() {
        assertEquals(1, PortNum.TEXT_MESSAGE_APP.value)
        assertEquals(256, PortNum.PRIVATE_APP.value)
    }

    // Un paquete con métricas de señal sobrevive ida y vuelta,
    // incluidos valores negativos y el broadcast.
    @Test
    fun meshPacketConMetricasDeSenalIdaYVuelta() {
        val original = MeshPacket(
            from = 0x5678,
            to = -1,
            id = 123456,
            hop_limit = 3,
            want_ack = true,
            rx_rssi = -112,
            rx_snr = -7.5f,
            decoded = Data(
                portnum = PortNum.TEXT_MESSAGE_APP,
                payload = "hola malla".encodeUtf8(),
            ),
        )

        val copia = MeshPacket.ADAPTER.decode(original.encode())

        assertEquals(original, copia)
        assertEquals(-112, copia.rx_rssi)
        assertEquals(-7.5f, copia.rx_snr)
        assertEquals("hola malla", copia.decoded!!.payload.utf8())
    }

    // Llega un mensaje de otro nodo y el cliente lo entrega a la app.
    @Test
    fun elClienteRecibeUnMensajeDeOtroNodo() = runTest {
        val fake = FakeTransport()
        val client = MeshProtocolClient(fake, backgroundScope)
        val recibidos = mutableListOf<MeshPacket>()

        backgroundScope.launch { client.packets.collect { recibidos += it } }
        client.start()
        client.connect("fake")
        runCurrent()

        fake.push(
            FromRadio(
                packet = MeshPacket(
                    from = 0x5678,
                    to = -1,
                    rx_rssi = -95,
                    rx_snr = 4.25f,
                    decoded = Data(
                        portnum = PortNum.TEXT_MESSAGE_APP,
                        payload = "hola desde B".encodeUtf8(),
                    ),
                ),
            ),
        )
        runCurrent()

        assertEquals(1, recibidos.size)
        val p = recibidos.first()
        assertEquals(0x5678, p.from)
        assertEquals(-95, p.rx_rssi)
        assertEquals("hola desde B", p.decoded!!.payload.utf8())
    }
}