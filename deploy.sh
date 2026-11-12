#!/usr/bin/env bash
set -euo pipefail

usage() {
    printf 'Uso: %s [utente@host]\n' "${0##*/}" >&2
    printf 'Senza argomenti usa deploy.user/deploy.host dal file config.properties.\n' >&2
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
    printf 'Errore: file config.properties non trovato.\n' >&2
    printf 'Copia config.properties.example in config.properties o ~/.config/thyme/config.properties.\n' >&2
    exit 1
fi

if [[ ! -r "$CONFIG_PATH" ]]; then
    printf 'Errore: file di configurazione non leggibile: %s\n' "$CONFIG_PATH" >&2
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
    printf 'Errore: destinazione SSH non valida: %s\n' "$TARGET" >&2
    exit 2
fi

if [[ -n "$DEPLOY_PORT" ]] \
    && { [[ ! "$DEPLOY_PORT" =~ ^[0-9]+$ ]] || (( DEPLOY_PORT < 1 || DEPLOY_PORT > 65535 )); }; then
    printf 'Errore: deploy.port deve essere compreso tra 1 e 65535.\n' >&2
    exit 2
fi

if [[ ! "$REMOTE_DIR" =~ ^(~/|/)[A-Za-z0-9_./~-]*$ ]] \
    || [[ "$REMOTE_DIR" == *"/../"* ]] \
    || [[ "$REMOTE_DIR" == */.. ]] \
    || [[ "$REMOTE_DIR" == .. ]]; then
    printf 'Errore: deploy.remote_dir contiene caratteri o segmenti non validi.\n' >&2
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
printf '==> Build locale del jar Thyme...\n'
"$SCRIPT_DIR/mvnw" -q -f "$SCRIPT_DIR/pom.xml" package

printf '==> Preparazione directory sul server remoto...\n'
REMOTE_SHELL_DIR="${REMOTE_DIR/#\~/\$HOME}"
ssh "${SSH_OPTS[@]}" "$TARGET" "mkdir -p \"$REMOTE_SHELL_DIR\""

printf '==> Trasferimento jar remoto...\n'
scp "${SCP_OPTS[@]}" \
    "$SCRIPT_DIR/target/thyme.jar" \
    "$TARGET:$REMOTE_DIR/"

printf '==> Verifica runtime Java sul server...\n'
ssh "${SSH_OPTS[@]}" "$TARGET" 'java -version'

printf '==> Installazione client locale...\n'
install -d "$HOME/.local/bin" "$HOME/.local/lib/thyme"
install -m 0644 "$SCRIPT_DIR/target/thyme.jar" "$HOME/.local/lib/thyme/thyme.jar"
install -m 0755 "$SCRIPT_DIR/bin/thyme" "$HOME/.local/bin/thyme"

printf 'Deployment completato su %s.\n' "$TARGET"
