# Compilar la versión de escritorio (fork)

La versión de escritorio es una imagen nativa de GraalVM. Cada compilación:

- tarda unos 10 minutos;
- usa todos los núcleos;
- llega a unos 15 GB de RAM.

Nucleus descarga GraalVM CE la primera vez. Cada sistema operativo solo compila su propio instalador.

Los instaladores de escritorio del fork **no llevan firma de código** (Authenticode en Windows) por decisión del propietario. Se publican junto con su hash SHA-256 para poder verificarlos.

## Windows (`.exe`)

**Requisitos:**

- JDK 21 o superior para lanzar Gradle.
- Rust (`rustup`).
- Visual Studio Build Tools con «Desarrollo para el escritorio con C++».
- ComposeNativeWebview publicado en `~/.m2`, en el commit fijado en `.github/workflows/spotube-release-binary.yml` (ver el README, «Building from source»).

**Compilar:**

```powershell
$env:GITHUB_TOKEN = (gh auth token)   # opcional: evita el límite de la API al buscar GraalVM
.\gradlew.bat :composeApp:packageGraalvmExe -Pnightly=false
Remove-Item Env:GITHUB_TOKEN
```

El instalador queda en `composeApp/build/compose/binaries/main/graalvm-exe/`.

**Calcular el hash:**

```powershell
Get-FileHash composeApp\build\compose\binaries\main\graalvm-exe\*.exe -Algorithm SHA256
```

## Linux (`.deb`, Debian/Ubuntu)

En el equipo Linux:

```bash
git clone -b feature/android-tv https://github.com/ferdcard-ux/spotube.git
cd spotube
./scripts/linux/build-deb.sh
```

El script pide confirmación antes de instalar nada y se encarga de:

- Paquetes `apt`: herramientas de compilación, cabeceras de GTK y WebKitGTK, `patchelf`, JDK 21 y las librerías para ejecutar la app (VLC, WebKitGTK).
- Rust, si falta.
- El SDK de Android, si falta. Gradle configura el destino Android del proyecto también para las tareas de escritorio. El script muestra las licencias del SDK para que las aceptes.
- ComposeNativeWebview en el commit fijado por el CI: compila su librería nativa de Linux y la publica en `~/.m2`.
- `./gradlew :composeApp:packageGraalvmDeb`. Al final muestra la ruta del `.deb` y su SHA-256.

**Instalar:**

```bash
sudo apt install ./composeApp/build/compose/binaries/main/graalvm-deb/*.deb
```

Usa `--nightly` para el canal nightly, que tiene otro nombre e iconos.
