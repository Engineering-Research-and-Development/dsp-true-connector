#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/test-data"
OUTPUT_FILE="${OUTPUT_DIR}/large-transfer.txt"
TARGET_MEBIBYTES="${TARGET_MEBIBYTES:-128}"
TARGET_BYTES=$((TARGET_MEBIBYTES * 1024 * 1024))

mkdir -p "${OUTPUT_DIR}"

python3 - "${OUTPUT_FILE}" "${TARGET_BYTES}" <<'PY'
from pathlib import Path
import sys

output = Path(sys.argv[1])
target_bytes = int(sys.argv[2])

written = 0
index = 0
with output.open("wb") as handle:
    while written < target_bytes:
        line = (
            f"seed-line-{index:08d}|"
            "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ|"
            "dsp-true-connector-suspend-resume-fixture\n"
        ).encode("utf-8")
        remaining = target_bytes - written
        chunk = line[:remaining]
        handle.write(chunk)
        written += len(chunk)
        index += 1
PY

echo "Generated ${OUTPUT_FILE}"
