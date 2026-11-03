# Control Remoto Universal

App Android para controlar televisiones desde el celular.

| TV | Conexión | Primer uso |
| --- | --- | --- |
| Android TV / Google TV | Wi-Fi (Android TV Remote v2) | Escribir el código que muestra la TV |
| Samsung Tizen (2016 en adelante) | Wi-Fi | Aceptar el aviso en la TV |
| LG webOS | Wi-Fi | Aceptar el aviso en la TV |
| TV sin Wi-Fi | Infrarrojo | Requiere un celular con emisor IR |

Marcas por infrarrojo: LG, Samsung, Sony, Panasonic, Philips, TCL, RCA, Toshiba,
Sanyo, Hitachi e Insignia.

## Compilar

Abre esta carpeta en Android Studio y ejecuta `app`. Requiere JDK 17 y Android SDK 35.
Pruebas: `./gradlew testDebugUnitTest`.

Las versiones hechas en Android Studio usan otra firma, por lo que no se instalan
encima de las publicadas en Releases.

## Actualizaciones

Cada cambio en `main` ejecuta `.github/workflows/release.yml`, que prueba la app,
firma un APK y lo publica en Releases. La app busca versiones nuevas al abrirse y
cada 12 horas, y Android verifica que la firma coincida antes de instalar.

Android puede pedir una confirmación en la primera actualización. Después, en
Android 12 o superior, la app normalmente se actualiza sin preguntar.

Secretos necesarios en GitHub: `RELEASE_KEYSTORE_BASE64` y `RELEASE_KEYSTORE_PASSWORD`.
`UPDATE_TOKEN` solo hace falta si el repositorio sigue privado: debe ser un token
de solo lectura limitado a este repositorio, y queda incluido dentro del APK.

Consulta [NOTICE.md](NOTICE.md) para los avisos de terceros.
