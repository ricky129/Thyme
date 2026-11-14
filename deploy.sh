#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

usage() {
    printf 'Usage: %s [user@host]\n' "${0##*/}" >&2
    printf 'Without arguments, deploy.user/deploy.host from config.properties are used.\n' >&2
}

if (( $# > 1 )); then
    usage
    exit 2
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

if [[ -n "${THYME_CONFIG:-}" ]]; then
    CONFIG_PATH=$THYME_CONFIG
elif [[ -f "$HOME/.config/thyme/config.properties" ]]; then
    CONFIG_PATH="$HOME/.config/thyme/config.properties"
elif [[ -f "$SCRIPT_DIR/config.properties" ]]; then
    CONFIG_PATH="$SCRIPT_DIR/config.properties"
else
    printf 'Error: config.properties file not found.\n' >&2
    printf 'Copy config.properties.example to config.properties or ~/.config/thyme/config.properties.\n' >&2
    exit 1
fi

if [[ ! -r "$CONFIG_PATH" ]]; then
    printf 'Error: configuration file is not readable: %s\n' "$CONFIG_PATH" >&2
    exit 1
fi

get_prop() {
    local key=$1
    awk -F= -v wanted="$key" '
        /^[[:space:]]*[#!]/ || index($0, "=") == 0 { next }
        {
            name = $1
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", name)
            if (name == wanted) {
                sub(/^[^=]*=/, "")
                gsub(/^[[:space:]]+|[[:space:]]+$/, "")
                sub(/\r$/, "")
                print
                exit
            }
        }
    ' "$CONFIG_PATH"
}

DEPLOY_USER="$(get_prop deploy.user)"
DEPLOY_HOST="$(get_prop deploy.host)"
if [[ -z "$DEPLOY_HOST" ]]; then
    SERVER_URL="$(get_prop server.tailscale)"
    [[ -n "$SERVER_URL" ]] || SERVER_URL="$(get_prop server.local)"
    DEPLOY_HOST="${SERVER_URL#http://}"
    DEPLOY_HOST="${DEPLOY_HOST#https://}"
    DEPLOY_HOST="${DEPLOY_HOST%%/*}"
    DEPLOY_HOST="${DEPLOY_HOST%%:*}"
fi
DEPLOY_PORT="$(get_prop deploy.port)"
DEPLOY_KEY="$(get_prop deploy.key)"
REMOTE_DIR="$(get_prop deploy.remote_dir)"
REMOTE_DIR="${REMOTE_DIR:-~/docker/thyme}"

if (( $# == 1 )); then
    TARGET=$1
else
    TARGET="${DEPLOY_USER}@${DEPLOY_HOST}"
fi

if [[ ! "$TARGET" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,31}@[A-Za-z0-9][A-Za-z0-9.-]{0,252}$ ]] \
    || [[ "$TARGET" == *..* ]] \
    || [[ "$TARGET" == *.-* ]] \
    || [[ "$TARGET" == *-. ]]; then
    printf 'Error: invalid SSH target: %s\n' "$TARGET" >&2
    exit 2
fi

if [[ -n "$DEPLOY_PORT" ]] \
    && { [[ ! "$DEPLOY_PORT" =~ ^[0-9]+$ ]] || (( DEPLOY_PORT < 1 || DEPLOY_PORT > 65535 )); }; then
    printf 'Error: deploy.port must be between 1 and 65535.\n' >&2
    exit 2
fi

if [[ ! "$REMOTE_DIR" =~ ^(~/|/)[A-Za-z0-9_./~-]*$ ]] \
    || [[ "$REMOTE_DIR" == *"/../"* ]] \
    || [[ "$REMOTE_DIR" == */.. ]] \
    || [[ "$REMOTE_DIR" == .. ]]; then
    printf 'Error: deploy.remote_dir contains invalid characters or path segments.\n' >&2
    exit 2
fi

SSH_OPTS=()
SCP_OPTS=()
if [[ -n "$DEPLOY_PORT" ]]; then
    SSH_OPTS+=(-p "$DEPLOY_PORT")
    SCP_OPTS+=(-P "$DEPLOY_PORT")
fi
if [[ -n "$DEPLOY_KEY" ]]; then
    DEPLOY_KEY="${DEPLOY_KEY/#\~/$HOME}"
    SSH_OPTS+=(-i "$DEPLOY_KEY")
    SCP_OPTS+=(-i "$DEPLOY_KEY")
fi

printf '==> Target deploy: %s:%s\n' "$TARGET" "$REMOTE_DIR"
if [[ "$(uname -s)" != Linux || "$(uname -m)" != x86_64 ]]; then
    printf 'Error: the server runtime is built for Linux x86_64; run deployment from Linux x86_64.\n' >&2
    exit 1
fi

fail_jdk_check() {
    printf 'Error: %s\n' "$1" >&2
    printf 'JAVA_HOME must point to a complete local JDK 25+ with bin/java, bin/javac, bin/jlink, and jmods.\n' >&2
    printf 'The build runs on this computer; the remote server JAVA_HOME is not used.\n' >&2
    exit 1
}

if [[ -z "${JAVA_HOME:-}" ]]; then
    fail_jdk_check 'JAVA_HOME is not set.'
fi
if [[ ! -d "$JAVA_HOME" ]]; then
    fail_jdk_check "JAVA_HOME points to a directory that does not exist: $JAVA_HOME"
fi
for tool in java javac jlink; do
    if [[ ! -x "$JAVA_HOME/bin/$tool" ]]; then
        fail_jdk_check "missing $JAVA_HOME/bin/$tool."
    fi
done
if [[ ! -d "$JAVA_HOME/jmods" ]]; then
    fail_jdk_check "missing $JAVA_HOME/jmods: jlink requires it to create the Thyme runtime."
fi

if ! JAVA_VERSION_OUTPUT=$("$JAVA_HOME/bin/java" -XshowSettings:properties -version 2>&1); then
    fail_jdk_check "cannot run Java from $JAVA_HOME/bin/java."
fi
JAVA_VERSION=$(printf '%s\n' "$JAVA_VERSION_OUTPUT" |
    sed -n 's/^[[:space:]]*java.specification.version = //p')
if [[ ! "$JAVA_VERSION" =~ ^[0-9]+$ ]]; then
    fail_jdk_check "cannot determine the Java version in $JAVA_HOME."
fi
if (( JAVA_VERSION < 25 )); then
    fail_jdk_check "JAVA_HOME contains Java $JAVA_VERSION; this project requires Java 25 or later."
fi

printf '==> Checking server architecture...\n'
if ! ssh "${SSH_OPTS[@]}" "$TARGET" \
    'test "$(uname -s)" = Linux && test "$(uname -m)" = x86_64'; then
    printf 'Error: the server must be Linux x86_64.\n' >&2
    exit 1
fi

printf '==> Building the client and server runtime...\n'
PATH="$JAVA_HOME/bin:$PATH" "$SCRIPT_DIR/mvnw" -q -f "$SCRIPT_DIR/pom.xml" verify

printf '==> Preparing the remote directory...\n'
REMOTE_SHELL_DIR="${REMOTE_DIR/#\~/\$HOME}"
ssh "${SSH_OPTS[@]}" "$TARGET" "mkdir -p \"$REMOTE_SHELL_DIR\""

printf '==> Transferring the server runtime...\n'
scp "${SCP_OPTS[@]}" -r \
    "$SCRIPT_DIR/server/target/thyme-server-runtime" \
    "$TARGET:$REMOTE_DIR/"

printf '==> Verifying the server runtime...\n'
ssh "${SSH_OPTS[@]}" "$TARGET" \
    "test -x \"$REMOTE_SHELL_DIR/thyme-server-runtime/bin/java\""

printf '==> Installing the local client...\n'
install -d "$HOME/.local/bin" "$HOME/.local/lib/thyme"
install -m 0644 "$SCRIPT_DIR/client/target/thyme-client.jar" "$HOME/.local/lib/thyme/thyme-client.jar"
install -m 0755 "$SCRIPT_DIR/bin/thyme" "$HOME/.local/bin/thyme"

printf 'Deployment completed on %s.\n' "$TARGET"
