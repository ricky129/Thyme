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

jdk_version() {
    local home=$1 output version
    output=$("$home/bin/java" -XshowSettings:properties -version 2>&1) || return 1
    version=$(printf '%s\n' "$output" |
        sed -n 's/^[[:space:]]*java.specification.version = //p')
    [[ "$version" =~ ^[0-9]+$ ]] || return 1
    printf '%s\n' "$version"
}

jdk_is_usable() {
    local home=${1%/} tool version
    [[ -n "$home" && -d "$home" ]] || return 1
    for tool in java javac jlink; do
        [[ -x "$home/bin/$tool" ]] || return 1
    done
    [[ -d "$home/jmods" ]] || return 1
    version=$(jdk_version "$home") || return 1
    (( version >= 25 ))
}

if ! jdk_is_usable "${JAVA_HOME:-}"; then
    detected_jdk=''
    while IFS= read -r candidate; do
        if jdk_is_usable "$candidate"; then
            detected_jdk=${candidate%/}
            break
        fi
    done < <(printf '%s\n' /usr/lib/jvm/*/ | sort -rV)
    if [[ -n "$detected_jdk" ]]; then
        printf '==> JAVA_HOME=%s is not a complete JDK 25+; using %s.\n' \
            "${JAVA_HOME:-<unset>}" "$detected_jdk"
        export JAVA_HOME=$detected_jdk
    fi
fi

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
PATH="$JAVA_HOME/bin:$PATH" "$SCRIPT_DIR/mvnw" -f "$SCRIPT_DIR/pom.xml" verify

printf '==> Preparing the remote directory...\n'
REMOTE_SHELL_DIR="${REMOTE_DIR/#\~/\$HOME}"
ssh "${SSH_OPTS[@]}" "$TARGET" "mkdir -p \"$REMOTE_SHELL_DIR\""

printf '==> Creating a remote staging directory...\n'
REMOTE_STAGING_DIR=$(ssh "${SSH_OPTS[@]}" "$TARGET" \
    "mktemp -d \"$REMOTE_SHELL_DIR/.thyme-server-runtime.XXXXXX\"")
REMOTE_RUNTIME_STAGING="$REMOTE_STAGING_DIR/thyme-server-runtime"
REMOTE_RUNTIME="$REMOTE_SHELL_DIR/thyme-server-runtime"
REMOTE_RUNTIME_BACKUP="$REMOTE_SHELL_DIR/.thyme-server-runtime.previous.$RANDOM"

cleanup_remote_staging() {
    if [[ -n "${REMOTE_STAGING_DIR:-}" ]]; then
        ssh "${SSH_OPTS[@]}" "$TARGET" "rm -rf -- \"$REMOTE_STAGING_DIR\"" >/dev/null 2>&1 || true
    fi
}
trap cleanup_remote_staging EXIT

printf '==> Transferring the server runtime to staging...\n'
if ! scp "${SCP_OPTS[@]}" -r \
    "$SCRIPT_DIR/server/target/thyme-server-runtime" \
    "$TARGET:$REMOTE_STAGING_DIR/"; then
    printf 'Error: could not transfer the server runtime to the remote staging directory.\n' >&2
    exit 1
fi

printf '==> Verifying the staged server runtime...\n'
if ! ssh "${SSH_OPTS[@]}" "$TARGET" \
    "test -x \"$REMOTE_RUNTIME_STAGING/bin/java\""; then
    printf 'Error: staged server runtime is incomplete: bin/java is missing or not executable.\n' >&2
    exit 1
fi

printf '==> Activating the new server runtime...\n'
if ! ssh "${SSH_OPTS[@]}" "$TARGET" "sh -c '
    set -eu
    moved_old=0
    rollback() {
        if [ \"\$moved_old\" -eq 1 ] && [ ! -e \"$REMOTE_RUNTIME\" ] && [ ! -L \"$REMOTE_RUNTIME\" ]; then
            mv -- \"$REMOTE_RUNTIME_BACKUP\" \"$REMOTE_RUNTIME\"
        fi
    }
    trap rollback EXIT
    if [ -e \"$REMOTE_RUNTIME_BACKUP\" ] || [ -L \"$REMOTE_RUNTIME_BACKUP\" ]; then
        rm -rf -- \"$REMOTE_RUNTIME_BACKUP\"
    fi
    if [ -e \"$REMOTE_RUNTIME\" ] || [ -L \"$REMOTE_RUNTIME\" ]; then
        mv -- \"$REMOTE_RUNTIME\" \"$REMOTE_RUNTIME_BACKUP\"
        moved_old=1
    fi
    mv -- \"$REMOTE_RUNTIME_STAGING\" \"$REMOTE_RUNTIME\"
    trap - EXIT
    rm -rf -- \"$REMOTE_RUNTIME_BACKUP\" || true
'"; then
    printf 'Error: could not activate the staged server runtime.\n' >&2
    exit 1
fi

REMOTE_STAGING_DIR=''
trap - EXIT

printf '==> Verifying the server runtime...\n'
ssh "${SSH_OPTS[@]}" "$TARGET" \
    "test -x \"$REMOTE_RUNTIME/bin/java\""

printf '==> Installing the local client...\n'
install -d "$HOME/.local/bin" "$HOME/.local/lib/thyme"
install -m 0644 "$SCRIPT_DIR/client/target/thyme-client.jar" "$HOME/.local/lib/thyme/thyme-client.jar"
install -m 0755 "$SCRIPT_DIR/bin/thyme" "$HOME/.local/bin/thyme"

printf 'Deployment completed on %s.\n' "$TARGET"
