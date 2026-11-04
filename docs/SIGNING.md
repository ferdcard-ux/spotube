# Firma de los APK Android (fork)

Este fork firma sus APK release con **su propia clave**, distinta de la del Spotube oficial. Un APK solo puede instalarse encima de otro si los dos están firmados con el mismo certificado. Por eso:

- **No crees nunca una segunda clave.** Si el keystore se pierde, ninguna instalación existente podrá actualizarse y habrá que desinstalar la app, con lo que se pierden sus datos.
- No se puede actualizar una instalación del Spotube oficial (firmada por el autor original, `CN=Kingkor Roy Tirtho`) con el fork. Tampoco una instalación debug. Hay que desinstalarla primero.

## Identidad de firma

| Dato | Valor |
|---|---|
| Keystore | `%USERPROFILE%\.keystores\spotube\spotube.keystore` (fuera del repositorio) |
| Tipo | PKCS12 (la contraseña de la clave es la misma que la del keystore) |
| Alias | `spotube` |
| Titular | `CN=Fernando Cardenas, OU=Unknown, O=ferdcard-dev, L=Unknown, ST=Unknown, C=CO` |
| Clave | RSA 4096, SHA384withRSA, válida 10 950 días (desde 2026-11-04) |
| Certificado | SHA-256: `A6:36:90:09:FA:B7:8D:B5:B1:2A:4D:4D:77:FC:A2:52:E1:D6:A9:EB:77:0E:DB:63:04:DB:49:61:AA:FC:34:A2` |

La huella SHA-256 es pública. Sirve para comprobar que un APK está firmado por este fork. No es la clave privada ni la contraseña.

## Cómo se firma

`composeApp/build.gradle.kts` lee la firma release, por este orden, de:

1. **Variables de entorno de un solo proceso:** `SPOTUBE_SIGNING_STORE_FILE`, `SPOTUBE_SIGNING_STORE_PASSWORD`, `SPOTUBE_SIGNING_KEY_ALIAS` y `SPOTUBE_SIGNING_KEY_PASSWORD` (opcional; por defecto, la del keystore). Las define el script de compilación local.
2. **`composeApp/local.properties`** (`release.signing.*`, ignorado por Git). Solo lo usa el CI, que lo escribe a partir de sus secretos.

Si se pide una tarea release (`assemble*Release`, `bundle*Release`, `package*Release`, `install*Release`) y falta el keystore, el alias o la contraseña, el build falla con un mensaje claro. No hay ninguna vuelta silenciosa a la clave debug.

Los builds debug usan siempre la clave debug de Android y no necesitan contraseña. Un APK debug no se instala encima de uno release, ni al revés, sin desinstalar antes.

## Crear el keystore (solo una vez)

En tu propia terminal de PowerShell, desde la raíz del repositorio:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\signing\create-keystore.ps1
```

`-ExecutionPolicy Bypass` solo afecta a esa ejecución. No cambia la política de scripts de Windows, que por defecto bloquea los `.ps1`.

El script:

- se niega a ejecutarse si el keystore ya existe;
- restringe la carpeta a tu usuario de Windows;
- deja que `keytool` pida la contraseña sin mostrarla, junto con los datos del certificado;
- al terminar, muestra la huella SHA-256, que hay que anotar en la tabla de arriba.

Usa una contraseña fuerte y única, de al menos 12 caracteres, y guárdala en tu gestor de contraseñas. No la escribas en archivos, en el chat ni en la línea de comandos.

## Compilar el release firmado

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\signing\build-release.ps1   # stable
powershell -ExecutionPolicy Bypass -File .\scripts\signing\build-release.ps1 -Task :composeApp:assembleNightlyRelease
```

El script:

- pide la contraseña de forma oculta;
- la pasa a Gradle solo mediante variables de entorno de ese proceso y las borra al terminar, también si el build falla;
- desactiva la caché de configuración en esa ejecución;
- verifica el APK con `apksigner` y compara su SHA-256 con la de este documento.

El resultado queda en `composeApp/build/outputs/apk/<stable|nightly>/release/`.

## Verificar la firma de un APK

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\apksigner.bat" verify --print-certs ruta\al.apk
```

`Signer #1 certificate SHA-256 digest` debe coincidir con la huella de la tabla.

## Respaldo y restauración

- **Qué respaldar:** `spotube.keystore` y su contraseña. Guárdalos en dos sitios distintos, fuera del repositorio y del equipo. Por ejemplo, el archivo como adjunto en tu gestor de contraseñas y una copia en una unidad externa cifrada.
- **Cómo respaldar:** copia el archivo y comprueba la copia con `keytool -list -v -keystore <copia> -alias spotube`. Debe pedir la contraseña y mostrar la misma SHA-256.
- **Cómo restaurar:** copia el archivo a `%USERPROFILE%\.keystores\spotube\spotube.keystore` y vuelve a restringir los permisos:

  ```powershell
  icacls "$env:USERPROFILE\.keystores\spotube" /inheritance:r /grant:r "${env:USERDOMAIN}\${env:USERNAME}:(OI)(CI)F"
  ```

  Comprueba la SHA-256 antes de compilar.

## Cambiar la contraseña

Cambiar la contraseña no cambia el certificado ni la huella, así que las actualizaciones siguen funcionando.

1. Haz una copia de seguridad: `Copy-Item spotube.keystore spotube.keystore.bak`.
2. Trabaja sobre otra copia: `Copy-Item spotube.keystore spotube.keystore.new`.
3. Cambia la contraseña de esa copia; `keytool` la pide oculta:

   ```powershell
   keytool -storepasswd -keystore spotube.keystore.new
   ```

   En PKCS12 la contraseña de la clave sigue a la del keystore.
4. Abre la copia con la contraseña nueva (`keytool -list -v -keystore spotube.keystore.new -alias spotube`) y comprueba que la SHA-256 es idéntica.
5. Solo entonces sustituye el original por la copia nueva. Guarda `.bak` hasta haber compilado y verificado un APK con la contraseña nueva, y luego bórralo de forma segura.
6. Actualiza la contraseña en tu gestor de contraseñas y, si el CI firma con esta clave, en el secreto `KEYSTORE_PASSWORD`.

Si algún paso falla, el original queda intacto.

## CI

`.github/workflows/spotube-release-binary.yml` decodifica el secreto `KEYSTORE` (base64) y escribe `release.signing.*` en `composeApp/local.properties` a partir de `KEYSTORE_PASSWORD`, `KEYSTORE_KEY_ALIAS` y `KEYSTORE_KEY_PASSWORD`.

Para que el CI publique con la identidad del fork, esos secretos del repositorio deben contener este keystore. Si contienen otra clave, sus APK no actualizarán los compilados en local. El flujo no se ha modificado.
