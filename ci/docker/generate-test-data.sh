#!/usr/bin/env bash
# Generates a ~160 MB ENG-employee.json in connector_b_resources before running
# data-transfer test suites. The large file gives the suspend/resume tests
# enough transfer time to issue a suspension before the download completes.
#
# Usage: ./ci/docker/generate-test-data.sh
# Run this once before: docker compose -f ci/docker/docker-compose.yml ... up -d

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="$SCRIPT_DIR/connector_b_resources/ENG-employee.json"
TARGET_SIZE_MB=120

echo "Generating ${TARGET_SIZE_MB} MB test file at: $TARGET"

python3 - "$TARGET" "$TARGET_SIZE_MB" <<'EOF'
import json, random, string, sys

target_path = sys.argv[1]
target_mb   = int(sys.argv[2])
target_bytes = target_mb * 1024 * 1024

BASE_EMPLOYEES = [
    {"name": "John Doe",      "age": 30, "city": "New York"},
    {"name": "Jane Smith",    "age": 25, "city": "London"},
    {"name": "Carlos Rivera", "age": 35, "city": "Madrid"},
    {"name": "Yuki Tanaka",   "age": 28, "city": "Tokyo"},
]

def rand_string(length):
    return ''.join(random.choices(string.ascii_letters + string.digits, k=length))

records = []
current_size = 4  # opening "[\n" + closing "\n]"
idx = 0

while current_size < target_bytes:
    base = BASE_EMPLOYEES[idx % len(BASE_EMPLOYEES)]
    record = {
        "employee": {
            "name":    base["name"],
            "age":     base["age"] + random.randint(0, 10),
            "city":    base["city"],
            "id":      idx,
            "dept":    rand_string(12),
            "email":   f"{rand_string(8)}@example.com",
            "notes":   rand_string(512),
        }
    }
    serialised = json.dumps(record, separators=(',', ':'))
    current_size += len(serialised) + 2  # comma + newline
    records.append(record)
    idx += 1
    if idx % 10000 == 0:
        print(f"  {current_size // (1024*1024)} MB written so far...", flush=True)

with open(target_path, 'w') as f:
    json.dump(records, f, separators=(',', ':'))

actual_mb = len(open(target_path, 'rb').read()) / (1024 * 1024)
print(f"Done. File size: {actual_mb:.1f} MB  ({len(records):,} records)")
EOF
