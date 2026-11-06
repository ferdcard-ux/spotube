#!/usr/bin/env bash
# Builds the Linux desktop .deb (GraalVM native image) on a Debian/Ubuntu machine.
#
#   ./scripts/linux/build-deb.sh            # stable channel
#   ./scripts/linux/build-deb.sh --nightly  # nightly channel (separate name and icons)
#
# What it does, asking before installing anything:
#   1. apt packages: build tools, GTK/WebKitGTK headers (for the WebView native library),
#      patchelf, JDK 21 and the runtime libraries (VLC, WebKitGTK) to run the app.
#   2. Rust (rustup) if cargo is missing.
#   3. Android SDK command-line tools if no SDK is found: Gradle configures the Android target of
#      the project even for desktop tasks.
#   4. ComposeNativeWebview at the commit pinned in .github/workflows/spotube-release-binary.yml:
#      builds its Linux library and publishes it to ~/.m2 (it is not on Maven Central).
#   5. ./gradlew :composeApp:packageGraalvmDeb, then prints the .deb path and its SHA-256.
#
# The native build takes ~10 minutes, uses every core and peaks at ~15 GB of RAM.
set -euo pipefail

CHANNEL_FLAG="-Pnightly=false"
[[ "${1:-}" == "--nightly" ]] && CHANNEL_FLAG="-Pnightly=true"

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK_DIR="${SPOTUBE_BUILD_CACHE:-$HOME/.cache/spotube-build}"
WORKFLOW="$REPO_DIR/.github/workflows/spotube-release-binary.yml"

SUDO_BIN="${SUDO_CMD:-sudo}"
if [[ "$SUDO_BIN" == "sudo" ]] && command -v pkexec >/dev/null 2>&1 && ! sudo -n true >/dev/null 2>&1; then
    SUDO_BIN="pkexec"
fi

confirm() {
    [[ "${CI:-false}" == "true" ]] || [[ "${ASSUME_YES:-false}" == "true" ]] && return 0
    read -r -p "$1 [s/N] " answer
    [[ "$answer" =~ ^[sSyY]$ ]]
}

step() { printf '\n==> %s\n' "$*"; }

if ! command -v apt-get >/dev/null 2>&1; then
    echo "This script targets Debian/Ubuntu (apt). See README.md, 'Building from source'." >&2
    exit 1
fi

# --- 1. System packages ------------------------------------------------------------------------
PACKAGES=(
    git curl unzip ca-certificates
    build-essential pkg-config zlib1g-dev patchelf zstd
    libgtk-3-dev libcairo2-dev libsoup-3.0-dev libwebkit2gtk-4.1-dev
    openjdk-21-jdk
    libvlc5 vlc-plugin-base libwebkit2gtk-4.1-0 libgl1 libxtst6 libxi6 libxrender1
)
MISSING=()
for p in "${PACKAGES[@]}"; do
    dpkg -s "$p" >/dev/null 2>&1 || MISSING+=("$p")
done
# Support both libasound2 and libasound2t64 (Ubuntu 24.04+)
if ! dpkg -s libasound2 >/dev/null 2>&1 && ! dpkg -s libasound2t64 >/dev/null 2>&1; then
    if apt-cache show libasound2t64 >/dev/null 2>&1; then
        MISSING+=("libasound2t64")
    else
        MISSING+=("libasound2")
    fi
fi
if ((${#MISSING[@]})); then
    step "Missing packages: ${MISSING[*]}"
    if confirm "Install them with $SUDO_BIN apt-get?"; then
        $SUDO_BIN apt-get update
        $SUDO_BIN apt-get install -y "${MISSING[@]}"
    else
        echo "Cannot continue without them." >&2
        exit 1
    fi
fi

# Gradle needs JDK 21 (the daemon JDK and GraalVM are provisioned by Gradle/Nucleus).
if [[ -z "${JAVA_HOME:-}" ]]; then
    for candidate in /usr/lib/jvm/java-21-openjdk-*; do
        [[ -d "$candidate" ]] && export JAVA_HOME="$candidate" && break
    done
fi
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"

# --- 2. Rust -------------------------------------------------------------------------------------
[[ -f "$HOME/.cargo/env" ]] && source "$HOME/.cargo/env"
if ! command -v cargo >/dev/null 2>&1; then
    step "Rust (cargo) is not installed."
    if confirm "Install it with rustup (https://rustup.rs)?"; then
        curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --profile minimal
        source "$HOME/.cargo/env"
    else
        echo "Cannot continue without Rust." >&2
        exit 1
    fi
fi

# --- 3. Android SDK ------------------------------------------------------------------------------
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
if [[ ! -d "$SDK_DIR/platforms" ]]; then
    step "No Android SDK found at $SDK_DIR (Gradle configures the Android target even for desktop builds)."
    if confirm "Download the Android command-line tools and install the SDK platform there?"; then
        mkdir -p "$SDK_DIR/cmdline-tools"
        TOOLS_ZIP="$WORK_DIR/cmdline-tools.zip"
        mkdir -p "$WORK_DIR"
        curl -fL -o "$TOOLS_ZIP" \
            "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
        rm -rf "$SDK_DIR/cmdline-tools/latest"
        unzip -q "$TOOLS_ZIP" -d "$SDK_DIR/cmdline-tools"
        mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
        echo "The Android SDK licenses are shown next; accept them to continue."
        "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK_DIR" --licenses
        "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK_DIR" \
            "platform-tools" "platforms;android-37" "build-tools;37.0.0"
    else
        echo "Cannot continue without an Android SDK." >&2
        exit 1
    fi
fi
export ANDROID_HOME="$SDK_DIR"

# --- 4. ComposeNativeWebview ---------------------------------------------------------------------
WEBVIEW_REPO="$(grep -E '^\s*COMPOSE_WEBVIEW_REPO:' "$WORKFLOW" | awk '{print $2}')"
WEBVIEW_COMMIT="$(grep -E '^\s*COMPOSE_WEBVIEW_COMMIT:' "$WORKFLOW" | awk '{print $2}')"
WEBVIEW_DIR="$WORK_DIR/compose-webview"
step "ComposeNativeWebview $WEBVIEW_REPO @ $WEBVIEW_COMMIT"
mkdir -p "$WORK_DIR"
if [[ ! -d "$WEBVIEW_DIR/.git" ]]; then
    git clone "https://github.com/$WEBVIEW_REPO.git" "$WEBVIEW_DIR"
fi
git -C "$WEBVIEW_DIR" fetch --quiet origin
git -C "$WEBVIEW_DIR" checkout --quiet "$WEBVIEW_COMMIT"

ARCH="$(uname -m)"
case "$ARCH" in
    x86_64) NATIVE_DIR="linux-x64" ;;
    aarch64) NATIVE_DIR="linux-aarch64" ;;
    *) echo "Unsupported CPU: $ARCH" >&2; exit 1 ;;
esac
(
    cd "$WEBVIEW_DIR"
    bash webview-compose/src/jvmMain/native/linux/build.sh
    test -f "webview-compose/src/jvmMain/resources/nucleus/native/$NATIVE_DIR/libcompose_webview_linux.so"
    printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
    chmod +x gradlew
    ./gradlew publishToMavenLocal -x verifyPublicationCoordinates
)

# --- 5. Spotube .deb -----------------------------------------------------------------------------
step "Building the .deb (about 10 minutes)"
cd "$REPO_DIR"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
chmod +x gradlew
# Nucleus lists GraalVM releases through the GitHub API; a token avoids its 60 requests/hour limit.
if [[ -z "${GITHUB_TOKEN:-}" ]] && command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
    GITHUB_TOKEN="$(gh auth token)"
    export GITHUB_TOKEN
fi
./gradlew :composeApp:packageGraalvmDeb "$CHANNEL_FLAG"
unset GITHUB_TOKEN

DEB="$(find composeApp/build/compose/binaries/main/graalvm-deb -maxdepth 1 -name '*.deb' -type f | head -1)"
if [[ -z "$DEB" ]]; then
    echo "No .deb was produced." >&2
    exit 1
fi
step "Done"
echo "Package: $REPO_DIR/$DEB"
echo "SHA-256: $(sha256sum "$DEB" | awk '{print $1}')"
echo "Install it with: sudo apt install ./$DEB"
