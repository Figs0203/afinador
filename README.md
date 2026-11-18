# Afinador · Cancionero

Afinador de instrumentos para Android con cancionero. Escucha el micrófono, detecta la altura del
sonido y muestra **la nota más cercana** con su desviación exacta en cents, tanto si
es una de las cuerdas de la guitarra como si no.

Interfaz oscura, sin anuncios, sin cuentas, sin telemetría y funciona sin conexión.
Solo pide dos permisos: el micrófono y, únicamente para importar un enlace, internet.

## Características

- **Modo cromático** — detecta cualquiera de las 12 notas en un rango de 55 Hz a
  1600 Hz (A1 a G6). Muestra nota, octava, frecuencia detectada y desviación en cents,
  aunque la nota tocada no corresponda a ninguna cuerda.
- **Modo guitarra** — nueve afinaciones seleccionables. Las seis cuerdas aparecen como
  objetivos concretos y se resalta la cuerda más cercana al sonido que estás tocando,
  con su nota y su frecuencia objetivo.
- **Medidor de cents** de ±50 con aguja, marcas cada 5 cents y zona verde de ±5 cents
  para saber de un vistazo si está afinada.
- **Detección estable** — mediana móvil y suavizado para que la aguja no salte, con
  puerta de ruido para ignorar el silencio y el ruido de fondo.
- **Pantalla siempre encendida** mientras el afinador está abierto.
- **Cancionero** — guarda tablaturas y páginas de acordes de Ultimate Guitar y
  CifraClub y las muestra como texto limpio, sin anuncios, con el tono desplazable
  de medio en medio tono.

## Capturas

<p align="center">
  <img src="docs/tuner-cromatico.png" width="300" alt="Modo cromático afinando un C4" />
  <img src="docs/tuner-guitarra.png" width="300" alt="Modo guitarra con la cuerda E4 resaltada" />
</p>

A la izquierda, el modo cromático afinando un **C4**: una nota que no es ninguna de las
seis cuerdas. A la derecha, el modo guitarra en afinación estándar, con la cuerda **E4**
detectada y su frecuencia objetivo.

## Instalar en el teléfono

1. Descarga el APK desde [Releases](../../releases).
2. Ábrelo en el teléfono y acepta la instalación de aplicaciones de origen desconocido
   cuando Android lo pida.
3. Al abrir la app, concede el permiso de micrófono.

Requiere Android 6.0 (API 23) o superior. Tamaño del APK: ~126 KB.

## Uso

- **Cromático**: toca cualquier nota y la app muestra la nota más cercana. La aguja a la
  izquierda significa que estás grave; a la derecha, agudo. Verde = afinado.
- **Guitarra**: elige la afinación arriba y toca las cuerdas una por una. La cuerda
  objetivo se resalta y la nota grande es la que debes afinar.
- **Cancionero**: toca el botón *Cancionero* de la cabecera. Dentro puedes importar
  con el botón *Importar*, y desde el visor mover el tono con **−** y **+** (o tocar
  el centro para volver al original). La transposición se recuerda por canción.

## Cancionero

El cancionero lee el texto de la tablatura, nunca la página web, así que no hay
anuncios, scripts ni rastreadores. Hay cuatro formas de añadir una canción:

1. **Compartir desde el navegador o la app**: elige *Compartir* en la página y luego
   *Afinador*. La app descarga la página, extrae el texto y lo abre.
2. **Enlace directo**: pega la URL en *Importar enlace*. También funciona si abres un
   enlace de esos dos sitios con Afinador (el sistema pregunta con qué app abrirlo).
3. **Pegar el texto**: si la página no se puede leer (muro de pago, captcha,
   protección anti-bots o un cambio de diseño), copia el texto de la tablatura y
   pégalo en el mismo formulario. Esta vía funciona sin conexión y es la más fiable a
   largo plazo.
4. **Lista de Ultimate Guitar**: pega (o abre) el enlace de una lista compartida,
   `www.ultimate-guitar.com/playlist/shared/…`. La app lee el nombre de la lista y
   sus canciones, pide confirmación y descarga cada tablatura una detrás de otra.
   Las que ya estaban guardadas se saltan, así que volver a importar la lista no
   duplica nada, y las que no se pueden leer se cuentan al final.

La descarga directa funciona con Ultimate Guitar. CifraClub publica las cifras en HTML
renderizado en servidor (un `<pre>` con las líneas en `<div>` y los acordes en `<b>`),
pero su CDN responde **403 Access Denied** a cualquier cliente que no sea un navegador
real — también con cabeceras de Chrome y `--http2` —, así que en ese sitio la app avisa
y propone pegar el texto. Ver *Verificar la importación* más abajo.

Las listas se leen del JSON que la propia página de Ultimate Guitar lleva en
`data-content`: ahí vienen el nombre de la lista y el título, el artista y el enlace de
cada canción, sin necesidad de cuenta ni de navegador. Solo se admiten listas
compartidas públicamente; las privadas piden sesión y no se descargan. Las tablaturas
personales que un autor incluya en su lista tampoco se pueden leer sin su cuenta, y se
cuentan como fallo en el resumen.

Se guarda **solo el texto** en el almacenamiento privado de la app, para uso personal.
Las canciones se pueden borrar desde la lista con el aspa **✕**.

La transposición solo toca los acordes: detecta las líneas de acordes por contenido
(nunca por posición), conserva las columnas para que cada acorde siga sobre su sílaba
y deja intactas las líneas de tablatura ASCII. Los acordes escritos con bemol siguen
con bemol, los escritos con sostenido con sostenido, y los naturales usan sostenidos
al subir y bemoles al bajar.

## Afinaciones incluidas

| Afinación | Cuerdas (grave → agudo) |
| --- | --- |
| Estándar | E2 A2 D3 G3 B3 E4 |
| Drop D | D2 A2 D3 G3 B3 E4 |
| ½ tono abajo | D#2 G#2 C#3 F#3 A#3 D#4 |
| D estándar | D2 G2 C3 F3 A3 D4 |
| Drop C | C2 G2 C3 F3 A3 D4 |
| Open G | D2 G2 D3 G3 B3 D4 |
| Open D | D2 A2 D3 F#3 A3 D4 |
| Open E | E2 B2 E3 G#3 B3 E4 |
| DADGAD | D2 A2 D3 G3 A3 D4 |

## Compilar

Requisitos: JDK 17 o superior y el SDK de Android (API 36 y build-tools 36.1.0).

```bash
./gradlew :app:assembleDebug        # APK de depuración
./gradlew :app:testDebugUnitTest    # tests del detector y de la teoría musical
./gradlew :app:assembleRelease      # APK de release (firmado si hay keystore.properties)
```

La firma de release se lee de `keystore.properties` (no se versiona):

```properties
storeFile=afinador-release.jks
storePassword=…
keyAlias=afinador
keyPassword=…
```

Si ese archivo no existe, el build de release usa la clave de depuración para que el
proyecto compile igualmente.

## Verificar la importación

La extracción se prueba de dos formas: con tests de JVM sobre HTML de ejemplo (rápidos,
sin red) y contra páginas reales descargadas, ejecutando el mismo `SongImporter`:

```bash
# 1. Descargar una página real (Ultimate Guitar responde 200 a curl)
curl -sL --compressed -A "Mozilla/5.0 (Linux; Android 13) Chrome/122 Mobile" \
  -o build/live-samples/ug-chords.html \
  "https://tabs.ultimate-guitar.com/tab/oasis/wonderwall-chords-27596"

# y una lista compartida
curl -sL --compressed -A "Mozilla/5.0 (Linux; Android 13) Chrome/122 Mobile" \
  -o build/live-samples/ug-playlist.html \
  "https://www.ultimate-guitar.com/playlist/shared/vy6zwyg6w3zx"

# 2. Pasar el HTML por el extractor real y comprobar líneas de acordes
./gradlew :app:testDebugUnitTest --tests "*LiveExtractionCheck"
```

Comprobado así con `wonderwall-chords-27596`: 131 líneas, 48 de acordes, título y
artista correctos, tablatura intacta y transposición alineada; con el `<pre>` eliminado
del HTML, el respaldo JSON (`data-content`) da exactamente el mismo texto. La lista de
prueba (`vy6zwyg6w3zx`) devuelve 62 canciones en orden, sin enlaces repetidos y con
título y artista en todas. En CifraClub
se validó el texto real de la página (90 líneas, 10 de acordes, 44 de tablatura sin
cambios) porque la descarga HTTP está bloqueada; si añades canciones de otros sitios,
repite esta comprobación antes de darla por buena.

## Cómo funciona

El pipeline es deliberadamente corto y sin dependencias externas:

1. `AudioEngine` captura PCM 16 bits mono a 44,1 kHz en un hilo propio con prioridad de
   audio, calcula el nivel RMS y descarta los frames por debajo del umbral de ruido.
2. `PitchDetector` estima el período con **YIN** (función de diferencia normalizada
   acumulada, umbral 0,12) e interpola parabólicamente la posición del mínimo. YIN es
   mucho más fiable que un pico de FFT en cuerdas pulsadas: evita los saltos de octava
   por el fundamental débil de las cuerdas graves y da precisión sub-muestra, que es lo
   que hace que los cents sean creíbles.
3. `Music` convierte frecuencia a nota MIDI, nombre, octava y cents con temperamento
   igual y A4 = 440 Hz.
4. `MainActivity` estabiliza la lectura (mediana de 5 frames + media exponencial) y
   elige qué mostrar según el modo.
5. `TunerGaugeView` dibuja todo el medidor en un solo `Canvas` para que nota, aguja y
   escala queden siempre alineadas en cualquier tamaño de pantalla.

Rango de detección: 55 Hz – 1600 Hz. Ventana de análisis: 4096 muestras (~93 ms).

## Estructura

```
app/src/main/java/io/github/figs0203/afinador/
├── MainActivity.kt             interfaz, modos, permisos y estabilización
├── audio/AudioEngine.kt        captura de micrófono y bucle de análisis
├── dsp/PitchDetector.kt        YIN (Kotlin puro, testeable en JVM)
├── music/Music.kt              frecuencia ↔ nota, cents
├── music/Chords.kt             acordes: detección, transposición y ortografía
├── music/Tunings.kt            afinaciones y cuerda más cercana
├── songbook/Song.kt            modelo de canción y almacenamiento JSON local
├── songbook/ChordSheet.kt      líneas de acordes y alineación de columnas
├── songbook/SongImporter.kt    descarga y extracción del texto de la página
├── songbook/PlaylistImporter.kt     listas compartidas de Ultimate Guitar
├── songbook/SongLibraryActivity.kt  lista de canciones guardadas
├── songbook/SongViewActivity.kt     visor con desplazamiento de tono
├── songbook/ImportActivity.kt       compartir, enlace o texto pegado
├── ui/TunerGaugeView.kt        medidor dibujado con Canvas
└── ui/EdgeToEdge.kt            insets de sistema compartidos
app/src/test/java/…             tests de detección, afinaciones, acordes e importación
```

## Licencia

MIT. Ver [LICENSE](LICENSE).
