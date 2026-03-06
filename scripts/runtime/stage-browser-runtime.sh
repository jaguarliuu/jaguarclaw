#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MANIFEST_PATH="${ROOT_DIR}/runtime/manifest.json"
DOWNLOAD_DIR="${ROOT_DIR}/runtime/_downloads"
STAGING_DIR="${ROOT_DIR}/runtime/staging"
TMP_DIR="${ROOT_DIR}/runtime/_tmp/browser-stage"

AGENT_SOURCE_OVERRIDE=""
CHROMIUM_SOURCE_OVERRIDE=""
NO_CLEAN=0

usage() {
  cat <<'EOF'
Usage:
  bash scripts/runtime/stage-browser-runtime.sh [options]

Options:
  --agent-source <url-or-file>      Override agent-browser source
  --chromium-source <url-or-file>   Override chromium source (.zip or folder)
  --no-clean                         Keep temporary extraction folders
  -h, --help                         Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-source)
      AGENT_SOURCE_OVERRIDE="${2:-}"
      shift 2
      ;;
    --chromium-source)
      CHROMIUM_SOURCE_OVERRIDE="${2:-}"
      shift 2
      ;;
    --no-clean)
      NO_CLEAN=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ ! -f "${MANIFEST_PATH}" ]]; then
  echo "manifest not found: ${MANIFEST_PATH}" >&2
  exit 1
fi

manifest_get_required() {
  local key="$1"
  local value
  value="$(node -e '
    const fs = require("fs");
    const file = process.argv[1];
    const key = process.argv[2];
    const obj = JSON.parse(fs.readFileSync(file, "utf8"));
    let cur = obj;
    for (const part of key.split(".")) {
      if (cur == null || !(part in cur)) process.exit(2);
      cur = cur[part];
    }
    const out = String(cur ?? "").trim();
    if (!out) process.exit(3);
    process.stdout.write(out);
  ' "${MANIFEST_PATH}" "${key}")" || {
    echo "manifest field is required: ${key}" >&2
    exit 1
  }
  echo "${value}"
}

manifest_get_optional() {
  node -e '
    const fs = require("fs");
    const file = process.argv[1];
    const key = process.argv[2];
    const obj = JSON.parse(fs.readFileSync(file, "utf8"));
    let cur = obj;
    for (const part of key.split(".")) {
      if (cur == null || !(part in cur)) {
        process.stdout.write("");
        process.exit(0);
      }
      cur = cur[part];
    }
    process.stdout.write(String(cur ?? "").trim());
  ' "${MANIFEST_PATH}" "$1"
}

is_url() {
  [[ "$1" =~ ^https?:// ]]
}

ensure_dir() {
  mkdir -p "$1"
}

download_file() {
  local url="$1"
  local dest="$2"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 --connect-timeout 20 -o "${dest}" "${url}"
    return
  fi
  if command -v wget >/dev/null 2>&1; then
    wget -O "${dest}" "${url}"
    return
  fi
  echo "Neither curl nor wget is available for download" >&2
  exit 1
}

resolve_source_path() {
  local src="$1"
  if is_url "${src}"; then
    echo "${src}"
    return
  fi
  if [[ "${src}" = /* ]]; then
    echo "${src}"
    return
  fi
  echo "${ROOT_DIR}/${src}"
}

copy_or_download_to_file() {
  local source="$1"
  local dest="$2"
  ensure_dir "$(dirname "${dest}")"
  if is_url "${source}"; then
    echo "Downloading: ${source}"
    download_file "${source}" "${dest}"
    return
  fi
  if [[ ! -f "${source}" ]]; then
    echo "Source file not found: ${source}" >&2
    exit 1
  fi
  cp -f "${source}" "${dest}"
}

copy_dir_contents() {
  local src="$1"
  local dest="$2"
  ensure_dir "${dest}"
  cp -a "${src}/." "${dest}/"
}

AGENT_BIN_REL="$(manifest_get_required "agentBrowser.bin")"
AGENT_LOCAL_PATH="$(manifest_get_optional "agentBrowser.localPath")"
AGENT_DOWNLOAD_URL="$(manifest_get_optional "agentBrowser.downloadUrl")"
AGENT_SOURCE="${AGENT_SOURCE_OVERRIDE:-${AGENT_LOCAL_PATH:-${AGENT_DOWNLOAD_URL}}}"
if [[ -z "${AGENT_SOURCE}" ]]; then
  echo "No agent-browser source configured (agentBrowser.localPath / agentBrowser.downloadUrl)" >&2
  exit 1
fi

CHROMIUM_ROOT_REL="$(manifest_get_required "chromium.root")"
CHROMIUM_BIN_REL="$(manifest_get_required "chromium.bin")"
CHROMIUM_ARCHIVE_ROOT="$(manifest_get_optional "chromium.archiveRoot")"
CHROMIUM_LOCAL_PATH="$(manifest_get_optional "chromium.localPath")"
CHROMIUM_DOWNLOAD_URL="$(manifest_get_optional "chromium.downloadUrl")"
CHROMIUM_SOURCE="${CHROMIUM_SOURCE_OVERRIDE:-${CHROMIUM_LOCAL_PATH:-${CHROMIUM_DOWNLOAD_URL}}}"
if [[ -z "${CHROMIUM_SOURCE}" ]]; then
  echo "No chromium source configured (chromium.localPath / chromium.downloadUrl)" >&2
  exit 1
fi

AGENT_SOURCE="$(resolve_source_path "${AGENT_SOURCE}")"
CHROMIUM_SOURCE="$(resolve_source_path "${CHROMIUM_SOURCE}")"

ensure_dir "${DOWNLOAD_DIR}"
ensure_dir "${STAGING_DIR}"
rm -rf "${TMP_DIR}"
ensure_dir "${TMP_DIR}"

# Stage agent-browser
AGENT_DEST="${STAGING_DIR}/${AGENT_BIN_REL}"
AGENT_DL_PATH="${DOWNLOAD_DIR}/agent-browser$(basename "${AGENT_DEST}" | sed -e 's/.*\(\.[^.]*\)$/\1/' -e t -e d)"
if [[ -z "${AGENT_DL_PATH}" || "${AGENT_DL_PATH}" == "${DOWNLOAD_DIR}/agent-browser" ]]; then
  AGENT_DL_PATH="${DOWNLOAD_DIR}/agent-browser.bin"
fi
copy_or_download_to_file "${AGENT_SOURCE}" "${AGENT_DL_PATH}"
ensure_dir "$(dirname "${AGENT_DEST}")"
cp -f "${AGENT_DL_PATH}" "${AGENT_DEST}"

if [[ "${AGENT_DEST}" == *.exe ]]; then
  AGENT_BASE="$(basename "${AGENT_DEST}" .exe)"
  AGENT_CMD_PATH="$(dirname "${AGENT_DEST}")/${AGENT_BASE}.cmd"
  cat > "${AGENT_CMD_PATH}" <<EOF
@echo off
"%~dp0${AGENT_BASE}.exe" %*
EOF
fi

# Stage chromium
CHROMIUM_ROOT_DEST="${STAGING_DIR}/${CHROMIUM_ROOT_REL}"
rm -rf "${CHROMIUM_ROOT_DEST}"
ensure_dir "${CHROMIUM_ROOT_DEST}"

if [[ -d "${CHROMIUM_SOURCE}" ]]; then
  copy_dir_contents "${CHROMIUM_SOURCE}" "${CHROMIUM_ROOT_DEST}"
else
  CHROMIUM_ZIP_PATH="${DOWNLOAD_DIR}/chromium.zip"
  copy_or_download_to_file "${CHROMIUM_SOURCE}" "${CHROMIUM_ZIP_PATH}"

  EXTRACT_DIR="${TMP_DIR}/extract"
  ensure_dir "${EXTRACT_DIR}"
  unzip -q -o "${CHROMIUM_ZIP_PATH}" -d "${EXTRACT_DIR}"

  SOURCE_DIR="${EXTRACT_DIR}"
  if [[ -n "${CHROMIUM_ARCHIVE_ROOT}" && -d "${EXTRACT_DIR}/${CHROMIUM_ARCHIVE_ROOT}" ]]; then
    SOURCE_DIR="${EXTRACT_DIR}/${CHROMIUM_ARCHIVE_ROOT}"
  else
    mapfile -t TOP_DIRS < <(find "${EXTRACT_DIR}" -mindepth 1 -maxdepth 1 -type d | sort)
    if [[ "${#TOP_DIRS[@]}" -eq 1 ]]; then
      SOURCE_DIR="${TOP_DIRS[0]}"
    fi
  fi
  copy_dir_contents "${SOURCE_DIR}" "${CHROMIUM_ROOT_DEST}"
fi

if [[ ! -f "${AGENT_DEST}" ]]; then
  echo "agent-browser staged binary missing: ${AGENT_DEST}" >&2
  exit 1
fi

CHROMIUM_BIN_DEST="${STAGING_DIR}/${CHROMIUM_BIN_REL}"
if [[ ! -f "${CHROMIUM_BIN_DEST}" ]]; then
  echo "chromium staged binary missing: ${CHROMIUM_BIN_DEST}" >&2
  exit 1
fi

echo "Staging complete:"
echo "  agent-browser: ${AGENT_DEST}"
if [[ -n "${AGENT_CMD_PATH:-}" ]]; then
  echo "  agent-browser shim: ${AGENT_CMD_PATH}"
fi
echo "  chromium root: ${CHROMIUM_ROOT_DEST}"
echo "  chromium bin: ${CHROMIUM_BIN_DEST}"

if [[ "${NO_CLEAN}" -eq 0 ]]; then
  rm -rf "${TMP_DIR}"
fi
