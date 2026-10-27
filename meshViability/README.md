# meshViability

App Kotlin Multiplatform (Android + escritorio JVM; iOS compila la UI común) para mandar mensajes por
Meshtastic y evaluar si un enlace LoRa directo entre dos nodos es viable: compara la predicción teórica
(Unidad 3, Comunicación de Datos, UTN-FRT) con el RSSI y SNR medidos en cada paquete.

```
Teléfono (app) --Bluetooth--> Nodo A --LoRa--> Nodo B --USB--> Notebook (app de escritorio)
```

Hardware: Heltec WiFi LoRa 32 V3 (SX1262), región US, preset Long Fast (SF11, 250 kHz, CR 4/5).

## Módulos

| Módulo | Qué tiene |
|---|---|
| `shared/commonMain` | Protocolo Meshtastic (protobuf con Wire), repositorios en memoria, cálculos del enlace, ViewModels y pantallas Compose. Sin dependencias de plataforma. |
| `shared/androidMain` | Transporte Bluetooth LE (`BleTransport`, `BleDiscovery`). |
| `shared/jvmMain` | Transporte por puerto serie USB (`SerialTransport`, con jSerialComm). |
| `androidApp` | Activity, permisos de Bluetooth. |
| `desktopApp` | Ventana de escritorio. |
| `iosApp` | Entrada iOS (solo nodo simulado, sin transporte real). |

## Compilar y probar

```bash
./gradlew :shared:jvmTest :shared:testAndroidHostTest   # tests (cálculos, protocolo, ViewModels)
./gradlew :androidApp:assembleDebug                      # APK en androidApp/build/outputs/apk/debug
./gradlew :desktopApp:run                                # app de escritorio
```

Los tests verifican los valores de referencia: N = −120 dBm, S = −131,5 dBm, L0 = 91,7 dB,
Prx = −71,7 dBm y M = 59,8 dB (los "≈ 60 dB" del enunciado), además de los límites del semáforo.

## Cálculos

- Ruido N = kTB (T = 290 K), equivalente a −174 + 10·log10(B).
- Sensibilidad S = N + NF + SNRmin(SF). NF = 6 dB por defecto, editable (≈ 9 dB según la hoja de datos de la Heltec V3).
- L0 = 32,45 + 20·log10(f MHz) + 20·log10(d km); Prx = Ptx − cables + antenas − L0; M = Prx − S.
- Alcance máximo: distancia donde M = 0.
- Medición: margen por potencia = RSSI − S, margen por SNR = SNR − SNRmin; manda el peor.
  Semáforo: Sólido ≥ 20 dB, Aceptable 10–20, Frágil 0–10, No confiable < 0. Los 20 dB son un criterio
  propio (variación en exteriores), editable en la pantalla de Medición.
- Comparación: pérdida extra = RSSI esperado − RSSI medido.

Solo cuenta como medición un paquete **directo** (hop_start − hop_limit = 0) que no llegó por MQTT. En uno
retransmitido, RSSI y SNR son del último salto: se muestra como "no válido para medición". Con firmware
anterior a 2.3 los saltos no se conocen (hop_start = 0) y esos paquetes no cuentan, salvo que se active la
opción en Medición.

Lo que el nodo informa (preset → SF y BW, potencia Tx, frecuencia fijada a mano) reemplaza lo ingresado.
`tx_power = 0` (máxima legal) y la frecuencia calculada por el firmware no llegan: se usan los valores de la
pantalla (20 dBm y 915 MHz por defecto). Con US / Long Fast el firmware usa por defecto un canal cercano a
906,875 MHz; si se quiere precisión, ingresar esa frecuencia.

## Cómo probar cada pantalla

### Sin hardware (nodo simulado)

En Android o escritorio: **Mensajería → Buscar nodos → "Nodo simulado (sin hardware)"**. El simulado se
comporta como un nodo A con un nodo B a 1 km: confirma los mensajes (a veces falla, para ver "Fallido") y
contesta con un eco cuyo RSSI/SNR sale del balance teórico más una pérdida extra al azar; ~20 % de las
respuestas llegan retransmitidas (no válidas).

1. **Mensajería**: enviar mensajes a "Nodo B (simulado)"; ver Pendiente → Entregado/Fallido y la señal de los ecos.
2. **Predicción**: con los valores por defecto debe dar S −131,5 dBm, Prx −71,7 dBm, M 59,8 dB. Probar un
   valor inválido (texto, distancia 0) y ver el error; cambiar SF o NF y ver el recálculo.
3. **Medición**: lista de ecos directos con semáforo, márgenes, RSSI esperado y pérdida extra; cambiar el
   umbral de "Sólido" y ver cómo cambian los colores; "Copiar sesión como CSV" y pegar en una planilla.

### Con hardware

Preparación (una vez):

1. Actualizar el firmware de las dos Heltec V3 a la última versión estable desde
   <https://flasher.meshtastic.org> (Chrome o Edge, cable USB de datos; en Windows instalar el driver CP210x).
   Elegir "Heltec V3", la versión estable más reciente y **Flash** (sin "Full erase" para conservar la config).
2. Con la app oficial de Meshtastic, en cada nodo: región **US**, preset **Long Fast**, mismo canal.
3. Linux (escritorio): `sudo usermod -aG dialout $USER` y volver a iniciar sesión para poder abrir `/dev/ttyUSB0`.

Prueba:

1. **Notebook**: conectar el nodo B por USB, `./gradlew :desktopApp:run`, Buscar nodos, elegir el puerto
   (CP2102 / ttyUSB0) y esperar "Conectado".
2. **Teléfono**: instalar el APK, Buscar nodos (acepta los permisos de Bluetooth), elegir el nodo A. La
   primera vez Android pide el PIN de emparejamiento que muestra la pantalla del nodo (o 123456 si se fijó así).
   Un nodo conectado a otro teléfono no aparece: cerrar la app oficial de Meshtastic.
3. Desde el teléfono enviar mensajes al nodo B (unicast): el estado pasa a Entregado cuando B confirma.
4. Desde la notebook enviar al nodo A: en el teléfono aparece cada mensaje con RSSI/SNR y, en Medición, el
   semáforo. Cargar en Predicción la distancia real entre los nodos para que la comparación tenga sentido.

Errores que la app informa: Bluetooth apagado o sin permiso, nodo que no responde o que no es Meshtastic,
puerto USB que no abre (cable, permisos) o que se desconecta, paquetes que no se pueden decodificar
(se cuentan y se descartan sin cortar la recepción).
