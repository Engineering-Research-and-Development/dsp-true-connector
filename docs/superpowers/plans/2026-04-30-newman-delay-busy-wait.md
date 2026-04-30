# Newman Delay: Replace postman-echo with Busy-Wait Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the dependency on `https://postman-echo.com/delay/2` in the two data-transfer Newman collections by replacing the external HTTP delay call with a synchronous busy-wait in the pre-request script.

**Architecture:** Each collection uses a dedicated `[C] Poll delay` step to hit postman-echo.com and pause for 2 seconds between poll iterations. That step is deleted; the wait logic moves into the pre-request script of the polling step itself, looping back to itself via `setNextRequest`. A separate `setTimeout` no-op in `[C] View data` is also replaced with the busy-wait.

**Tech Stack:** Newman/Postman collection JSON (v2.1), `python3` for JSON manipulation.

---

### Task 1: Fix `datatransfer-api-http-pull-tests.json`

**Files:**
- Modify: `ci/docker/test-cases/datatransfer-api-http-pull-tests/datatransfer-api-http-pull-tests.json`

The collection currently has 14 items (indices 0–13). Item 7 is the polling step, item 8 is the delay step, item 9 is `[C] View data`.

- [ ] **Step 1: Apply changes with Python**

Run this script from the repo root:

```bash
python3 - <<'EOF'
import json, copy

PATH = "ci/docker/test-cases/datatransfer-api-http-pull-tests/datatransfer-api-http-pull-tests.json"

with open(PATH) as f:
    data = json.load(f)

items = data["item"]

# 1. Add busy-wait to pre-request of item 7 "[C] Wait for download to complete"
wait_item = items[7]
assert wait_item["name"] == "[C] Wait for download to complete", f"Unexpected name: {wait_item['name']}"
for ev in wait_item["event"]:
    if ev["listen"] == "prerequest":
        ev["script"]["exec"] = [
            "var start = new Date().getTime();",
            "while (new Date().getTime() - start < 2000) {}"
        ]
    if ev["listen"] == "test":
        ev["script"]["exec"] = [
            line.replace(
                "pm.execution.setNextRequest('[C] Poll delay');",
                "pm.execution.setNextRequest('[C] Wait for download to complete');"
            )
            for line in ev["script"]["exec"]
        ]

# 2. Remove item 8 "[C] Poll delay"
poll_item = items[8]
assert poll_item["name"] == "[C] Poll delay", f"Unexpected name: {poll_item['name']}"
items.pop(8)

# 3. Replace setTimeout no-op in item 8 (now shifted) "[C] View data"
view_item = items[8]
assert view_item["name"] == "[C] View data", f"Unexpected name: {view_item['name']}"
for ev in view_item["event"]:
    if ev["listen"] == "prerequest":
        ev["script"]["exec"] = [
            "var start = new Date().getTime();",
            "while (new Date().getTime() - start < 2000) {}"
        ]

with open(PATH, "w") as f:
    json.dump(data, f, indent=2)
    f.write("\n")

print("Done. Items remaining:", len(data["item"]))
EOF
```

Expected output: `Done. Items remaining: 13`

- [ ] **Step 2: Verify no postman-echo references remain**

```bash
grep -c "postman-echo" "ci/docker/test-cases/datatransfer-api-http-pull-tests/datatransfer-api-http-pull-tests.json"
```

Expected output: `0`

- [ ] **Step 3: Verify JSON is valid**

```bash
python3 -c "import json; json.load(open('ci/docker/test-cases/datatransfer-api-http-pull-tests/datatransfer-api-http-pull-tests.json')); print('valid')"
```

Expected output: `valid`

- [ ] **Step 4: Spot-check the key changes**

```bash
python3 - <<'EOF'
import json
PATH = "ci/docker/test-cases/datatransfer-api-http-pull-tests/datatransfer-api-http-pull-tests.json"
with open(PATH) as f:
    data = json.load(f)
items = data["item"]

# Item 7 pre-request should have busy-wait
wait_item = items[7]
print("=== Item 7 pre-request ===")
for ev in wait_item["event"]:
    if ev["listen"] == "prerequest":
        print("\n".join(ev["script"]["exec"]))

# Item 7 test setNextRequest should point to itself
print("\n=== Item 7 setNextRequest ===")
for ev in wait_item["event"]:
    if ev["listen"] == "test":
        for line in ev["script"]["exec"]:
            if "setNextRequest" in line:
                print(line)

# Item 8 should now be [C] View data
print("\n=== Item 8 name ===", items[8]["name"])

# Item 8 pre-request should have busy-wait
print("\n=== Item 8 pre-request ===")
for ev in items[8]["event"]:
    if ev["listen"] == "prerequest":
        print("\n".join(ev["script"]["exec"]))
EOF
```

Expected output:
```
=== Item 7 pre-request ===
var start = new Date().getTime();
while (new Date().getTime() - start < 2000) {}

=== Item 7 setNextRequest ===
        pm.execution.setNextRequest('[C] Wait for download to complete');

=== Item 8 name === [C] View data

=== Item 8 pre-request ===
var start = new Date().getTime();
while (new Date().getTime() - start < 2000) {}
```

- [ ] **Step 5: Commit**

```bash
git add ci/docker/test-cases/datatransfer-api-http-pull-tests/datatransfer-api-http-pull-tests.json
git commit -m "fix: replace postman-echo delay with busy-wait in http-pull tests

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 2: Fix `datatransfer-api-http-push-tests.json`

**Files:**
- Modify: `ci/docker/test-cases/datatransfer-api-http-push-tests/datatransfer-api-http-push-tests.json`

This collection has the same structure: item 7 is the polling step, item 8 is the delay step, item 9 is `[C] View data`.

- [ ] **Step 1: Apply changes with Python**

```bash
python3 - <<'EOF'
import json

PATH = "ci/docker/test-cases/datatransfer-api-http-push-tests/datatransfer-api-http-push-tests.json"

with open(PATH) as f:
    data = json.load(f)

items = data["item"]

# 1. Add busy-wait to pre-request of item 7 "[C] Wait for download to complete"
wait_item = items[7]
assert wait_item["name"] == "[C] Wait for download to complete", f"Unexpected name: {wait_item['name']}"
for ev in wait_item["event"]:
    if ev["listen"] == "prerequest":
        ev["script"]["exec"] = [
            "var start = new Date().getTime();",
            "while (new Date().getTime() - start < 2000) {}"
        ]
    if ev["listen"] == "test":
        ev["script"]["exec"] = [
            line.replace(
                "pm.execution.setNextRequest('[C] Poll delay');",
                "pm.execution.setNextRequest('[C] Wait for download to complete');"
            )
            for line in ev["script"]["exec"]
        ]

# 2. Remove item 8 "[C] Poll delay"
poll_item = items[8]
assert poll_item["name"] == "[C] Poll delay", f"Unexpected name: {poll_item['name']}"
items.pop(8)

# 3. Replace setTimeout no-op in item 8 (now shifted) "[C] View data"
view_item = items[8]
assert view_item["name"] == "[C] View data", f"Unexpected name: {view_item['name']}"
for ev in view_item["event"]:
    if ev["listen"] == "prerequest":
        ev["script"]["exec"] = [
            "var start = new Date().getTime();",
            "while (new Date().getTime() - start < 2000) {}"
        ]

with open(PATH, "w") as f:
    json.dump(data, f, indent=2)
    f.write("\n")

print("Done. Items remaining:", len(data["item"]))
EOF
```

Expected output: `Done. Items remaining: 13`

- [ ] **Step 2: Verify no postman-echo references remain**

```bash
grep -c "postman-echo" "ci/docker/test-cases/datatransfer-api-http-push-tests/datatransfer-api-http-push-tests.json"
```

Expected output: `0`

- [ ] **Step 3: Verify JSON is valid**

```bash
python3 -c "import json; json.load(open('ci/docker/test-cases/datatransfer-api-http-push-tests/datatransfer-api-http-push-tests.json')); print('valid')"
```

Expected output: `valid`

- [ ] **Step 4: Spot-check the key changes**

```bash
python3 - <<'EOF'
import json
PATH = "ci/docker/test-cases/datatransfer-api-http-push-tests/datatransfer-api-http-push-tests.json"
with open(PATH) as f:
    data = json.load(f)
items = data["item"]

wait_item = items[7]
print("=== Item 7 pre-request ===")
for ev in wait_item["event"]:
    if ev["listen"] == "prerequest":
        print("\n".join(ev["script"]["exec"]))

print("\n=== Item 7 setNextRequest ===")
for ev in wait_item["event"]:
    if ev["listen"] == "test":
        for line in ev["script"]["exec"]:
            if "setNextRequest" in line:
                print(line)

print("\n=== Item 8 name ===", items[8]["name"])

print("\n=== Item 8 pre-request ===")
for ev in items[8]["event"]:
    if ev["listen"] == "prerequest":
        print("\n".join(ev["script"]["exec"]))
EOF
```

Expected output:
```
=== Item 7 pre-request ===
var start = new Date().getTime();
while (new Date().getTime() - start < 2000) {}

=== Item 7 setNextRequest ===
        pm.execution.setNextRequest('[C] Wait for download to complete');

=== Item 8 name === [C] View data

=== Item 8 pre-request ===
var start = new Date().getTime();
while (new Date().getTime() - start < 2000) {}
```

- [ ] **Step 5: Commit**

```bash
git add ci/docker/test-cases/datatransfer-api-http-push-tests/datatransfer-api-http-push-tests.json
git commit -m "fix: replace postman-echo delay with busy-wait in http-push tests

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 3: Final verification — no postman-echo anywhere in test-cases

**Files:** (read-only check)

- [ ] **Step 1: Confirm no postman-echo references remain across all test collections**

```bash
grep -r "postman-echo" ci/docker/test-cases/
```

Expected output: *(empty — no output)*

- [ ] **Step 2: Confirm both collections are valid JSON**

```bash
python3 - <<'EOF'
import json, pathlib
for p in sorted(pathlib.Path("ci/docker/test-cases").rglob("*.json")):
    try:
        json.load(open(p))
        print(f"OK  {p}")
    except Exception as e:
        print(f"ERR {p}: {e}")
EOF
```

Expected output: all lines start with `OK`.
