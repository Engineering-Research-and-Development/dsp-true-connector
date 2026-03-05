#!/usr/bin/env bash
# ============================================================
# SpotBugs full-project scan — scan ALL modules then report
# ============================================================
#
# Usage (run from project root):
#   ./spotbugs-scan.sh            # scan only, never fails
#   ./spotbugs-scan.sh --gate     # scan all, then fail if findings found
#
# Reports: <module>/target/spotbugsXml.xml   (one per module)
#
# Why two steps?
#   Maven's reactor is sequential — the spotbugs:check goal runs per-module
#   and would abort the reactor on the first module with findings.
#   By running spotbugs:spotbugs first (scan only, never fails) across the
#   whole project, and then spotbugs:check separately with -fae (fail-at-end),
#   we guarantee that every module is scanned before any failure is reported.
# ============================================================

set -euo pipefail

GATE=false
if [[ "${1:-}" == "--gate" ]]; then
  GATE=true
fi

echo "========================================================"
echo " Step 1 — Scanning all modules (never fails)"
echo "========================================================"
mvn verify -Pspotbugs -Dspotbugs.skipCheck=true

echo ""
echo "========================================================"
echo " SpotBugs XML reports written to:"
find . -name "spotbugsXml.xml" -not -path "*/\.*" | sort | sed 's/^/   /'
echo ""
echo " Aggregate report (all modules combined):"
echo "   $(pwd)/target/spotbugs-aggregate.xml"
echo "========================================================"

if [[ "$GATE" == "true" ]]; then
  echo ""
  echo "========================================================"
  echo " Step 2 — Checking all reports (gate mode)"
  echo "========================================================"
  mvn spotbugs:check -Pspotbugs -Dspotbugs.skipCheck=false -fae
  echo "Gate passed — no findings above threshold."
fi

