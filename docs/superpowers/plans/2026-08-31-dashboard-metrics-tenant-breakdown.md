# Dashboard Metrics Tenant Breakdown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-tenant breakdown (`byTenant`) to the dashboard metrics response DTOs so that when a superadmin requests cross-tenant data (`TenantContextHolder.getTenantId()` blank), the response includes a zero-filled, per-tenant breakdown alongside the existing aggregate totals — enabling client-side tenant filtering on the UI with a single API call.

**Architecture:** Extend the three existing response records (`NegotiationSnapshotMetrics`, `TransferSnapshotMetrics`, `HistoricalEventMetrics`) with a new trailing `byTenant` field holding a list of `TenantMetrics<T>` entries (one per registered tenant, zero-filled if absent). Each of the three metrics services adds `tenantId` to its existing MongoDB aggregation `$group` stage(s) so a single query serves both the aggregate (re-grouped in Java ignoring tenantId) and the per-tenant breakdown (partitioned by tenantId, merged against the full tenant roster from `TenantRepository`). `byTenant` is populated only when the incoming `tenantId` service parameter is blank; otherwise it is left `null`. Backward compatibility for existing callers/tests is preserved via secondary (overloaded) constructors on the three records.

**Tech Stack:** Java 17, Spring Boot 3.5.x, Spring Data MongoDB (`MongoTemplate` aggregation pipelines), JUnit 5 + Mockito, Maven multi-module build (`tools`, `negotiation`, `data-transfer`, `connector`).

**Source spec:** `docs/superpowers/specs/2026-08-31-dashboard-metrics-tenant-breakdown-design.md` (approved, committed `1d8ae6ed`). Read it before starting if anything below is ambiguous.

---

## Current State Reference

These are exact current contents of files you'll modify, captured so you don't need to re-discover them.

**`tools/src/main/java/it/eng/tools/model/dashboard/NegotiationSnapshotMetrics.java`** (current, pre-change):
```java
package it.eng.tools.model.dashboard;

import java.util.List;

public record NegotiationSnapshotMetrics(
        long totalCount,
        List<KeyCount> byState,
        List<KeyCount> byRoleAndState) {
}
```

**`tools/src/main/java/it/eng/tools/model/dashboard/TransferSnapshotMetrics.java`** (current, pre-change):
```java
package it.eng.tools.model.dashboard;

import java.util.List;

public record TransferSnapshotMetrics(
        long totalCount,
        List<KeyCount> byState,
        List<KeyCount> byRoleAndState,
        List<KeyCount> byFormat,
        long downloadedCount,
        long downloadInProgressCount) {
}
```

**`tools/src/main/java/it/eng/tools/model/dashboard/HistoricalEventMetrics.java`** (current, pre-change):
```java
package it.eng.tools.model.dashboard;

import java.util.List;

public record HistoricalEventMetrics(
        long totalCount,
        List<KeyCount> byEventType,
        List<KeyCount> byRole,
        List<TimeBucketCount> overTime) {
}
```

**`tools/src/main/java/it/eng/tools/model/dashboard/KeyCount.java`**:
```java
package it.eng.tools.model.dashboard;

public record KeyCount(String key, long count) {
}
```

**`tools/src/main/java/it/eng/tools/model/dashboard/TimeBucketCount.java`**:
```java
package it.eng.tools.model.dashboard;

import java.time.Instant;

public record TimeBucketCount(Instant bucketStart, long count) {
}
```

**`tools/src/main/java/it/eng/tools/repository/TenantRepository.java`**:
```java
package it.eng.tools.repository;

import it.eng.tools.model.Tenant;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TenantRepository extends MongoRepository<Tenant, String> {
}
```

**`tools/src/main/java/it/eng/tools/model/Tenant.java`** — relevant fields: `id` (String), `name` (String), `enabled` (boolean). Use `Tenant::getId()`/`Tenant::getName()` (Lombok-generated getters — confirm exact accessor names in the file before use; if the class uses record-style or `@Data`, adjust accordingly).

---

## Task 1: Create `TenantMetrics<T>` shared DTO - IMPLEMENTED

**Files:**
- Create: `tools/src/main/java/it/eng/tools/model/dashboard/TenantMetrics.java`
- Test: `tools/src/test/java/it/eng/tools/model/dashboard/TenantMetricsTest.java`

- [x] **Step 1: Write the failing test**

```java
package it.eng.tools.model.dashboard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantMetricsTest {

    @Test
    void shouldExposeTenantIdTenantNameAndMetrics() {
        TenantMetrics<Long> tenantMetrics = new TenantMetrics<>("tenant-1", "Tenant One", 42L);

        assertThat(tenantMetrics.tenantId()).isEqualTo("tenant-1");
        assertThat(tenantMetrics.tenantName()).isEqualTo("Tenant One");
        assertThat(tenantMetrics.metrics()).isEqualTo(42L);
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn -pl tools -am -Dtest=TenantMetricsTest test`
Expected: FAIL — compilation error, `TenantMetrics` does not exist.

- [x] **Step 3: Write minimal implementation**

```java
package it.eng.tools.model.dashboard;

/**
 * Wraps a per-tenant slice of dashboard metrics data.
 *
 * @param tenantId   the tenant's technical identifier
 * @param tenantName the tenant's display name
 * @param metrics    the metrics payload scoped to this tenant
 * @param <T>        the metrics payload type (e.g. {@link NegotiationSnapshotMetrics})
 */
public record TenantMetrics<T>(String tenantId, String tenantName, T metrics) {
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `mvn -pl tools -am -Dtest=TenantMetricsTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add tools/src/main/java/it/eng/tools/model/dashboard/TenantMetrics.java tools/src/test/java/it/eng/tools/model/dashboard/TenantMetricsTest.java
git commit -m "feat: add TenantMetrics wrapper DTO for dashboard per-tenant breakdown"
```

---

## Task 2: Extend the three response records with `byTenant` - IMPLEMENTED

**Files:**
- Modify: `tools/src/main/java/it/eng/tools/model/dashboard/NegotiationSnapshotMetrics.java`
- Modify: `tools/src/main/java/it/eng/tools/model/dashboard/TransferSnapshotMetrics.java`
- Modify: `tools/src/main/java/it/eng/tools/model/dashboard/HistoricalEventMetrics.java`
- Test: `tools/src/test/java/it/eng/tools/model/dashboard/NegotiationSnapshotMetricsTest.java` (new)
- Test: `tools/src/test/java/it/eng/tools/model/dashboard/TransferSnapshotMetricsTest.java` (new)
- Test: `tools/src/test/java/it/eng/tools/model/dashboard/HistoricalEventMetricsTest.java` (new)

Each record gets a new trailing `List<TenantMetrics<Self>> byTenant` canonical component, plus a secondary constructor preserving the OLD arity (delegating with `byTenant = null`) so every existing call site across the codebase keeps compiling unchanged.

- [x] **Step 1: Write the failing tests**

`tools/src/test/java/it/eng/tools/model/dashboard/NegotiationSnapshotMetricsTest.java`:
```java
package it.eng.tools.model.dashboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NegotiationSnapshotMetricsTest {

    @Test
    void legacyConstructorShouldLeaveByTenantNull() {
        NegotiationSnapshotMetrics metrics = new NegotiationSnapshotMetrics(
                5L, List.of(new KeyCount("REQUESTED", 5L)), List.of(new KeyCount("REQUESTED_PROVIDER", 5L)));

        assertThat(metrics.byTenant()).isNull();
        assertThat(metrics.totalCount()).isEqualTo(5L);
    }

    @Test
    void canonicalConstructorShouldAcceptByTenant() {
        NegotiationSnapshotMetrics perTenant = new NegotiationSnapshotMetrics(
                2L, List.of(new KeyCount("REQUESTED", 2L)), List.of(), null);
        NegotiationSnapshotMetrics metrics = new NegotiationSnapshotMetrics(
                5L, List.of(new KeyCount("REQUESTED", 5L)), List.of(),
                List.of(new TenantMetrics<>("tenant-1", "Tenant One", perTenant)));

        assertThat(metrics.byTenant()).hasSize(1);
        assertThat(metrics.byTenant().get(0).tenantId()).isEqualTo("tenant-1");
        assertThat(metrics.byTenant().get(0).metrics().byTenant()).isNull();
    }
}
```

`tools/src/test/java/it/eng/tools/model/dashboard/TransferSnapshotMetricsTest.java`:
```java
package it.eng.tools.model.dashboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransferSnapshotMetricsTest {

    @Test
    void legacyConstructorShouldLeaveByTenantNull() {
        TransferSnapshotMetrics metrics = new TransferSnapshotMetrics(
                3L, List.of(), List.of(), List.of(), 1L, 0L);

        assertThat(metrics.byTenant()).isNull();
        assertThat(metrics.totalCount()).isEqualTo(3L);
    }

    @Test
    void canonicalConstructorShouldAcceptByTenant() {
        TransferSnapshotMetrics perTenant = new TransferSnapshotMetrics(
                1L, List.of(), List.of(), List.of(), 0L, 0L, null);
        TransferSnapshotMetrics metrics = new TransferSnapshotMetrics(
                3L, List.of(), List.of(), List.of(), 1L, 0L,
                List.of(new TenantMetrics<>("tenant-1", "Tenant One", perTenant)));

        assertThat(metrics.byTenant()).hasSize(1);
        assertThat(metrics.byTenant().get(0).metrics().totalCount()).isEqualTo(1L);
    }
}
```

`tools/src/test/java/it/eng/tools/model/dashboard/HistoricalEventMetricsTest.java`:
```java
package it.eng.tools.model.dashboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalEventMetricsTest {

    @Test
    void legacyConstructorShouldLeaveByTenantNull() {
        HistoricalEventMetrics metrics = new HistoricalEventMetrics(
                7L, List.of(), List.of(), List.of());

        assertThat(metrics.byTenant()).isNull();
        assertThat(metrics.totalCount()).isEqualTo(7L);
    }

    @Test
    void canonicalConstructorShouldAcceptByTenant() {
        HistoricalEventMetrics perTenant = new HistoricalEventMetrics(2L, List.of(), List.of(), List.of(), null);
        HistoricalEventMetrics metrics = new HistoricalEventMetrics(
                7L, List.of(), List.of(), List.of(),
                List.of(new TenantMetrics<>("tenant-1", "Tenant One", perTenant)));

        assertThat(metrics.byTenant()).hasSize(1);
        assertThat(metrics.byTenant().get(0).metrics().totalCount()).isEqualTo(2L);
    }
}
```

- [x] **Step 2: Run tests to verify they fail**

Run: `mvn -pl tools -am -Dtest=NegotiationSnapshotMetricsTest,TransferSnapshotMetricsTest,HistoricalEventMetricsTest test`
Expected: FAIL — compilation errors, `byTenant()` accessor / 4th-5th/7th-arg constructors don't exist yet.

- [x] **Step 3: Write minimal implementation**

`tools/src/main/java/it/eng/tools/model/dashboard/NegotiationSnapshotMetrics.java`:
```java
package it.eng.tools.model.dashboard;

import java.util.List;

/**
 * Snapshot of negotiation counts, optionally broken down per tenant.
 *
 * @param totalCount       total negotiation count in scope
 * @param byState          counts grouped by negotiation state
 * @param byRoleAndState   counts grouped by role and state combined
 * @param byTenant         per-tenant breakdown; {@code null} unless the request was made
 *                         by a superadmin without a tenant scope
 */
public record NegotiationSnapshotMetrics(
        long totalCount,
        List<KeyCount> byState,
        List<KeyCount> byRoleAndState,
        List<TenantMetrics<NegotiationSnapshotMetrics>> byTenant) {

    /**
     * Backward-compatible constructor for callers that predate the per-tenant breakdown.
     * {@code byTenant} is left {@code null}.
     *
     * @param totalCount     total negotiation count in scope
     * @param byState        counts grouped by negotiation state
     * @param byRoleAndState counts grouped by role and state combined
     */
    public NegotiationSnapshotMetrics(long totalCount, List<KeyCount> byState, List<KeyCount> byRoleAndState) {
        this(totalCount, byState, byRoleAndState, null);
    }
}
```

`tools/src/main/java/it/eng/tools/model/dashboard/TransferSnapshotMetrics.java`:
```java
package it.eng.tools.model.dashboard;

import java.util.List;

/**
 * Snapshot of transfer process counts, optionally broken down per tenant.
 *
 * @param totalCount                total transfer process count in scope
 * @param byState                   counts grouped by transfer state
 * @param byRoleAndState            counts grouped by role and state combined
 * @param byFormat                  counts grouped by transfer format
 * @param downloadedCount           count of transfers marked as downloaded
 * @param downloadInProgressCount   count of transfers with a download in progress
 * @param byTenant                  per-tenant breakdown; {@code null} unless the request was made
 *                                  by a superadmin without a tenant scope
 */
public record TransferSnapshotMetrics(
        long totalCount,
        List<KeyCount> byState,
        List<KeyCount> byRoleAndState,
        List<KeyCount> byFormat,
        long downloadedCount,
        long downloadInProgressCount,
        List<TenantMetrics<TransferSnapshotMetrics>> byTenant) {

    /**
     * Backward-compatible constructor for callers that predate the per-tenant breakdown.
     * {@code byTenant} is left {@code null}.
     *
     * @param totalCount              total transfer process count in scope
     * @param byState                 counts grouped by transfer state
     * @param byRoleAndState          counts grouped by role and state combined
     * @param byFormat                counts grouped by transfer format
     * @param downloadedCount         count of transfers marked as downloaded
     * @param downloadInProgressCount count of transfers with a download in progress
     */
    public TransferSnapshotMetrics(long totalCount, List<KeyCount> byState, List<KeyCount> byRoleAndState,
            List<KeyCount> byFormat, long downloadedCount, long downloadInProgressCount) {
        this(totalCount, byState, byRoleAndState, byFormat, downloadedCount, downloadInProgressCount, null);
    }
}
```

`tools/src/main/java/it/eng/tools/model/dashboard/HistoricalEventMetrics.java`:
```java
package it.eng.tools.model.dashboard;

import java.util.List;

/**
 * Snapshot of historical audit event counts, optionally broken down per tenant.
 *
 * @param totalCount   total event count in scope
 * @param byEventType  counts grouped by event type
 * @param byRole       counts grouped by role
 * @param overTime     counts grouped by time bucket
 * @param byTenant     per-tenant breakdown; {@code null} unless the request was made
 *                     by a superadmin without a tenant scope
 */
public record HistoricalEventMetrics(
        long totalCount,
        List<KeyCount> byEventType,
        List<KeyCount> byRole,
        List<TimeBucketCount> overTime,
        List<TenantMetrics<HistoricalEventMetrics>> byTenant) {

    /**
     * Backward-compatible constructor for callers that predate the per-tenant breakdown.
     * {@code byTenant} is left {@code null}.
     *
     * @param totalCount  total event count in scope
     * @param byEventType counts grouped by event type
     * @param byRole      counts grouped by role
     * @param overTime    counts grouped by time bucket
     */
    public HistoricalEventMetrics(long totalCount, List<KeyCount> byEventType, List<KeyCount> byRole,
            List<TimeBucketCount> overTime) {
        this(totalCount, byEventType, byRole, overTime, null);
    }
}
```

- [x] **Step 4: Run tests to verify they pass**

Run: `mvn -pl tools -am -Dtest=NegotiationSnapshotMetricsTest,TransferSnapshotMetricsTest,HistoricalEventMetricsTest test`
Expected: PASS

- [ ] **Step 5: Run full `tools` module test suite to confirm no existing call sites broke**

Run: `mvn -pl tools -am test`
Expected: PASS (all existing tests using the old-arity constructors, e.g. `TimeWindowTest`, continue to compile and pass unchanged).

- [ ] **Step 6: Commit**

```bash
git add tools/src/main/java/it/eng/tools/model/dashboard/NegotiationSnapshotMetrics.java \
        tools/src/main/java/it/eng/tools/model/dashboard/TransferSnapshotMetrics.java \
        tools/src/main/java/it/eng/tools/model/dashboard/HistoricalEventMetrics.java \
        tools/src/test/java/it/eng/tools/model/dashboard/NegotiationSnapshotMetricsTest.java \
        tools/src/test/java/it/eng/tools/model/dashboard/TransferSnapshotMetricsTest.java \
        tools/src/test/java/it/eng/tools/model/dashboard/HistoricalEventMetricsTest.java
git commit -m "feat: add nullable byTenant field to dashboard metrics records"
```

---

## Task 3: `NegotiationMetricsService` per-tenant breakdown

**Files:**
- Modify: `negotiation/src/main/java/it/eng/negotiation/service/NegotiationMetricsService.java`
- Modify: `negotiation/src/test/java/it/eng/negotiation/service/NegotiationMetricsServiceTest.java`

> Before editing, view the current full contents of both files — the exact private `record GroupedNegotiationCount` fields, aggregation stages, and existing test stub shapes must match what you edit. Do not guess field names; read the file first.

### 3a. Production code changes

- [ ] **Step 1: Read current file and locate the aggregation pipeline**

Run: `view negotiation/src/main/java/it/eng/negotiation/service/NegotiationMetricsService.java`

Identify: the private `record GroupedNegotiationCount(String state, String key, long count)`, the `$group`/`$project` aggregation stage(s) that produce it, and the constructor (currently takes only a Mongo template dependency — confirm exact existing dependencies before adding `TenantRepository`).

- [ ] **Step 2: Write the failing tests first (byTenant behavior)**

Add to `NegotiationMetricsServiceTest.java` (adjust existing `@Mock`/constructor wiring per the file's actual structure — add a `@Mock TenantRepository tenantRepository;` field and pass it into the service constructor under test):

```java
@Test
void getSnapshotShouldReturnNullByTenantWhenTenantIdProvided() {
    // arrange: stub existing mongoTemplate.aggregate(...) calls as already done in this test class
    // for a non-blank tenantId ("tenant-1")

    NegotiationSnapshotMetrics result = negotiationMetricsService.getSnapshot("tenant-1");

    assertThat(result.byTenant()).isNull();
}

@Test
void getSnapshotShouldReturnZeroFilledByTenantWhenTenantIdBlank() {
    Tenant tenantA = Tenant.Builder.newInstance().id("tenant-a").name("Tenant A").enabled(true).build();
    Tenant tenantB = Tenant.Builder.newInstance().id("tenant-b").name("Tenant B").enabled(true).build();
    when(tenantRepository.findAll()).thenReturn(List.of(tenantA, tenantB));
    // arrange: stub mongoTemplate.aggregate(...) to return rows where tenantId = "tenant-a" only
    // (reuse this test class's existing aggregate-stubbing pattern, adding tenantId to each row)

    NegotiationSnapshotMetrics result = negotiationMetricsService.getSnapshot("");

    assertThat(result.byTenant()).hasSize(2);
    assertThat(result.byTenant())
            .extracting(TenantMetrics::tenantId)
            .containsExactlyInAnyOrder("tenant-a", "tenant-b");
    TenantMetrics<NegotiationSnapshotMetrics> tenantBEntry = result.byTenant().stream()
            .filter(tm -> tm.tenantId().equals("tenant-b"))
            .findFirst().orElseThrow();
    assertThat(tenantBEntry.metrics().totalCount()).isZero();
}
```

Note: this plan cannot show the exact existing Mockito stubbing syntax for `mongoTemplate.aggregate(...)` without first reading the file (the aggregation call shape must match exactly). When implementing this step, copy the exact stubbing style already used by the existing test method that exercises "aggregate across all tenants when tenantId is blank" in this file, and add a `tenantId` field to each stubbed result `Document`/DTO row.

- [ ] **Step 3: Fix the existing "aggregate across all tenants" test to stub the tenant roster**

Locate the existing test (per design doc, e.g. `getSnapshotShouldAggregateAcrossTenantsWhenTenantIdBlank` or similarly named) that calls `getSnapshot("")` or `getSnapshot(null)`. Add this stub at the top of that test body (Mockito returns `null` by default for unstubbed `List`-returning calls, which would NPE in the new `buildByTenant` merge step):

```java
when(tenantRepository.findAll()).thenReturn(List.of());
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `mvn -pl negotiation -am -Dtest=NegotiationMetricsServiceTest test`
Expected: FAIL — compilation error (`TenantRepository` not a constructor param yet, `byTenant()` used but old aggregate logic doesn't populate it).

- [x] **Step 5: Update the constructor to accept `TenantRepository`**

In `NegotiationMetricsService.java`, add the field and constructor parameter (keep existing fields/params, just append):

```java
private final TenantRepository tenantRepository;
```

Add `TenantRepository tenantRepository` as the last parameter of the existing constructor, and assign `this.tenantRepository = tenantRepository;`. Add the import:

```java
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.model.Tenant;
import it.eng.tools.model.dashboard.TenantMetrics;
```

- [ ] **Step 6: Extend `GroupedNegotiationCount` and the aggregation pipeline with `tenantId`**

Change the private record:
```java
private record GroupedNegotiationCount(String tenantId, String state, String key, long count) {
}
```

In the `$group`/`$project` aggregation stage(s) that build this record (locate via the code you read in Step 1), 
add `tenantId` as a group key alongside the existing keys, and add a corresponding `$project` field 
`tenantId: "$_id.tenantId"` (or equivalent, matching the existing style for `state`/`key`). 
When mapping the raw aggregation `Document` results into `GroupedNegotiationCount`, 
read `doc.getString("tenantId")` (may be `null` for legacy documents lacking a `tenantId` field — this is fine, `getString` returns `null` in that case).

- [ ] **Step 7: Confirm existing aggregate methods are unaffected**

The existing methods that consume `List<GroupedNegotiationCount>` (e.g. `getCountsByState`, `getCountsByRoleAndState`, `getTotalCount` — confirm exact names from the file you read) already use `Collectors.groupingBy(GroupedNegotiationCount::state, ...)` or similar, ignoring `tenantId`. **Do not change these methods** — adding an extra dimension to the input records doesn't change their grouping-by-state/key output.

- [ ] **Step 8: Add the `buildByTenant` method**

```java
private List<TenantMetrics<NegotiationSnapshotMetrics>> buildByTenant(
        String tenantId, List<GroupedNegotiationCount> groupedCounts) {
    if (StringUtils.hasText(tenantId)) {
        return null;
    }
    Map<String, List<GroupedNegotiationCount>> byTenantId = groupedCounts.stream()
            .filter(row -> row.tenantId() != null)
            .collect(Collectors.groupingBy(GroupedNegotiationCount::tenantId));

    return tenantRepository.findAll().stream()
            .sorted(Comparator.comparing(Tenant::getId))
            .map(tenant -> {
                List<GroupedNegotiationCount> rows = byTenantId.getOrDefault(tenant.getId(), List.of());
                NegotiationSnapshotMetrics tenantMetrics = new NegotiationSnapshotMetrics(
                        rows.stream().mapToLong(GroupedNegotiationCount::count).sum(),
                        summarizeByKey(rows, GroupedNegotiationCount::state),
                        summarizeByKey(rows, GroupedNegotiationCount::key),
                        null);
                return new TenantMetrics<>(tenant.getId(), tenant.getName(), tenantMetrics);
            })
            .toList();
}
```

Note: `summarizeByKey` is illustrative — reuse whatever existing private helper this file already uses to turn a `List<GroupedNegotiationCount>` into `List<KeyCount>` grouped by a given extractor (the file you read in Step 1 already has this logic inline in `getCountsByState`/`getCountsByRoleAndState`; extract it into a shared private helper if not already one, since it's now called from two places — the existing aggregate path and this new per-tenant path). If such a helper doesn't already exist, create it:

```java
private static List<KeyCount> summarizeByKey(
        List<GroupedNegotiationCount> rows, Function<GroupedNegotiationCount, String> keyExtractor) {
    return rows.stream()
            .collect(Collectors.groupingBy(keyExtractor, Collectors.summingLong(GroupedNegotiationCount::count)))
            .entrySet().stream()
            .map(e -> new KeyCount(e.getKey(), e.getValue()))
            .toList();
}
```

- [x] **Step 9: Wire `buildByTenant` into `getSnapshot`**

At the end of the method that assembles the final `NegotiationSnapshotMetrics` (e.g. `getSnapshot(String tenantId)`), change the final `return new NegotiationSnapshotMetrics(total, byState, byRoleAndState);` to:

```java
return new NegotiationSnapshotMetrics(total, byState, byRoleAndState, buildByTenant(tenantId, groupedCounts));
```

(`groupedCounts` is whatever local variable name the file uses for the full `List<GroupedNegotiationCount>` fetched from Mongo — confirm exact name from Step 1.)

- [ ] **Step 10: Run tests to verify they pass**

Run: `mvn -pl negotiation -am -Dtest=NegotiationMetricsServiceTest test`
Expected: PASS

- [ ] **Step 11: Update any other production callers of the `NegotiationMetricsService` constructor**

Run: `grep -rn "new NegotiationMetricsService(" --include=*.java .`

If found outside test code (e.g. Spring `@Configuration` manual bean wiring — unlikely since `@Service`/`@Autowired` is the norm in this repo, but verify), no change needed if Spring component-scans and autowires `TenantRepository` (it's already an existing bean per `TenantRepository` being used elsewhere in `tools`). If any explicit `new NegotiationMetricsService(...)` call exists outside tests, add the `tenantRepository` argument there too.

- [ ] **Step 12: Run full module verify**

Run: `mvn -pl negotiation -am verify`
Expected: PASS

- [ ] **Step 13: Commit**

```bash
git add negotiation/src/main/java/it/eng/negotiation/service/NegotiationMetricsService.java \
        negotiation/src/test/java/it/eng/negotiation/service/NegotiationMetricsServiceTest.java
git commit -m "feat: add per-tenant breakdown to NegotiationMetricsService"
```

---

## Task 4: `TransferMetricsService` per-tenant breakdown (highest complexity)

**Files:**
- Modify: `data-transfer/src/main/java/it/eng/datatransfer/service/TransferMetricsService.java`
- Modify: `data-transfer/src/test/java/it/eng/datatransfer/service/TransferMetricsServiceTest.java`

This service uses a single `$facet` aggregation stage with (per the design spec) 5 dimensional sub-pipelines (state, role+state, format, downloaded flag, download-in-progress flag) plus a `total` facet. All dimensional facets need `tenantId` added to their `$group`/`$project`. The `total` facet changes from a single count document to a per-tenant-grouped list of count documents.

> Before editing, view the current full contents of both files to get exact facet names, the `getCounts(snapshot, fieldName)` helper signature, and the existing test's stubbed `Document` shapes (in particular the `total` facet stub, documented in the design/summary as `List.of(new Document("count", 3L))`).

- [ ] **Step 1: Read current file and locate the `$facet` pipeline**

Run: `view data-transfer/src/main/java/it/eng/datatransfer/service/TransferMetricsService.java`

Confirm: facet names/keys, the private record used for dimensional rows (call it `KeyCountRow` or similar per the actual file), 
the constructor's current dependencies, and how `getTotalCount` currently reads the `total` facet (single doc vs. list).

- [ ] **Step 2: Write the failing tests (byTenant behavior)**

Add to `TransferMetricsServiceTest.java` (mirror the existing test class's Mockito/aggregation-stubbing style exactly — add `@Mock TenantRepository tenantRepository;` and pass into the constructor under test):

```java
@Test
void getSnapshotShouldReturnNullByTenantWhenTenantIdProvided() {
    // arrange: stub mongoTemplate.aggregate(...) exactly as the existing
    // "non-blank tenantId" test in this class already does

    TransferSnapshotMetrics result = transferMetricsService.getSnapshot("tenant-1");

    assertThat(result.byTenant()).isNull();
}

@Test
void getSnapshotShouldReturnZeroFilledByTenantWhenTenantIdBlank() {
    Tenant tenantA = Tenant.Builder.newInstance().id("tenant-a").name("Tenant A").enabled(true).build();
    Tenant tenantB = Tenant.Builder.newInstance().id("tenant-b").name("Tenant B").enabled(true).build();
    when(tenantRepository.findAll()).thenReturn(List.of(tenantA, tenantB));
    // arrange: stub mongoTemplate.aggregate(...) to return facet documents where all
    // dimensional rows and the total facet's per-tenant rows have tenantId = "tenant-a" only
    // (reuse this test class's existing $facet document-building helper, adding a
    // "tenantId" field to each nested row document)

    TransferSnapshotMetrics result = transferMetricsService.getSnapshot("");

    assertThat(result.byTenant()).hasSize(2);
    assertThat(result.byTenant())
            .extracting(TenantMetrics::tenantId)
            .containsExactlyInAnyOrder("tenant-a", "tenant-b");
    TenantMetrics<TransferSnapshotMetrics> tenantBEntry = result.byTenant().stream()
            .filter(tm -> tm.tenantId().equals("tenant-b"))
            .findFirst().orElseThrow();
    assertThat(tenantBEntry.metrics().totalCount()).isZero();
    assertThat(tenantBEntry.metrics().downloadedCount()).isZero();
}
```

As with Task 3, the exact Mockito stub construction for the `$facet` result document must mirror the existing test file's pattern precisely — read it first and adapt in place; do not invent a different mocking style.

- [ ] **Step 3: Fix the existing "aggregate across all tenants" test to stub the tenant roster**

In the existing test that calls `getSnapshot("")` or `getSnapshot(null)` expecting an all-tenant aggregate, add:

```java
when(tenantRepository.findAll()).thenReturn(List.of());
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `mvn -pl data-transfer -am -Dtest=TransferMetricsServiceTest test`
Expected: FAIL — compilation error (`TenantRepository` not wired, `byTenant()` unpopulated).

- [ ] **Step 5: Update the constructor to accept `TenantRepository`**

Add the field, constructor parameter (appended last), and assignment, plus imports:

```java
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.model.Tenant;
import it.eng.tools.model.dashboard.TenantMetrics;
```

```java
private final TenantRepository tenantRepository;
```

- [ ] **Step 6: Add `tenantId` to the dimensional row record and each facet's group/project stages**

Rename/extend the existing per-row record (confirm the actual current name from Step 1; the design doc calls it 
`TenantKeyCount` conceptually) to include `tenantId` as its first component:

```java
private record TenantKeyCount(String tenantId, String key, long count) {
}
```

For each of the dimensional facets (state, role+state, format, downloaded flag, download-in-progress flag), add `tenantId` to the `$group` id document and a corresponding `$project` field, matching the existing style used for the dimension's own key. When parsing each facet's output `Document` list into `TenantKeyCount`, read `tenantId` via `doc.getString("tenantId")` (nullable for legacy shape, which is fine).

- [ ] **Step 7: Change the `total` facet to a per-tenant grouped count list**

Change the `total` facet's pipeline from a single ungrouped `$count`-style stage to a `$group` by `tenantId` producing one document per tenant:

```
{ $group: { _id: "$tenantId", count: { $sum: 1 } } },
{ $project: { _id: 0, tenantId: "$_id", count: 1 } }
```

Add a new private record:
```java
private record TenantCount(String tenantId, long count) {
}
```

Update the parsing of the `total` facet's output from a single `Document` into a `List<TenantCount>` 
(parse each returned document the same way as the other facets — `doc.getString("tenantId")`, `doc.getLong("count")` 
or equivalent per this file's existing numeric-extraction style).

- [ ] **Step 8: Rewrite `getTotalCount` to sum across the per-tenant list**

Locate the current `getTotalCount`-equivalent logic (reads the single `total` facet document). Change it to sum the `count` field 
across the new `List<TenantCount>`:

```java
long totalCount = totalRows.stream().mapToLong(TenantCount::count).sum();
```

This remains backward compatible: even a legacy-shaped single row with `tenantId = null` still contributes its `count` to the sum, producing an identical total.

- [ ] **Step 9: Rewrite `getCounts(snapshot, fieldName)` (or equivalent helper) to regroup ignoring tenantId**

Wherever this file currently turns one facet's raw rows into a `List<KeyCount>` for the aggregate response, ensure it regroups by the dimensional key only (ignoring `tenantId`), reusing a shared helper:

```java
private static List<KeyCount> summarizeByKey(List<TenantKeyCount> rows) {
    return rows.stream()
            .collect(Collectors.groupingBy(TenantKeyCount::key, Collectors.summingLong(TenantKeyCount::count)))
            .entrySet().stream()
            .map(e -> new KeyCount(e.getKey(), e.getValue()))
            .toList();
}
```

Apply this to all 5 dimensional facets when building the existing aggregate `byState`/`byRoleAndState`/`byFormat`/`downloadedCount`/`downloadInProgressCount` fields (the two flag-based facets, `downloadedCount`/`downloadInProgressCount`, likely reduce a `List<TenantKeyCount>` down to a single count where `key = "true"` or similar boolean-flag encoding — confirm exact existing logic from Step 1 and preserve its semantics, just re-derived from the tenant-aware rows).

- [ ] **Step 10: Add the `buildByTenant` method**

```java
private List<TenantMetrics<TransferSnapshotMetrics>> buildByTenant(
        String tenantId,
        List<TenantKeyCount> byStateRows,
        List<TenantKeyCount> byRoleAndStateRows,
        List<TenantKeyCount> byFormatRows,
        List<TenantKeyCount> downloadedRows,
        List<TenantKeyCount> downloadInProgressRows,
        List<TenantCount> totalRows) {
    if (StringUtils.hasText(tenantId)) {
        return null;
    }

    return tenantRepository.findAll().stream()
            .sorted(Comparator.comparing(Tenant::getId))
            .map(tenant -> {
                String id = tenant.getId();
                TransferSnapshotMetrics tenantMetrics = new TransferSnapshotMetrics(
                        sumForTenant(totalRows, id),
                        filterAndSummarize(byStateRows, id),
                        filterAndSummarize(byRoleAndStateRows, id),
                        filterAndSummarize(byFormatRows, id),
                        sumFlagForTenant(downloadedRows, id),
                        sumFlagForTenant(downloadInProgressRows, id),
                        null);
                return new TenantMetrics<>(id, tenant.getName(), tenantMetrics);
            })
            .toList();
}

private static long sumForTenant(List<TenantCount> rows, String tenantId) {
    return rows.stream()
            .filter(row -> tenantId.equals(row.tenantId()))
            .mapToLong(TenantCount::count)
            .sum();
}

private static List<KeyCount> filterAndSummarize(List<TenantKeyCount> rows, String tenantId) {
    return summarizeByKey(rows.stream().filter(row -> tenantId.equals(row.tenantId())).toList());
}

private static long sumFlagForTenant(List<TenantKeyCount> rows, String tenantId) {
    // Mirror whatever existing logic in this file currently reduces a flag-dimension's
    // KeyCount list down to a single long (e.g. summing the row where key equals "true").
    // Read Step 1's file contents and replicate that exact reduction here, filtered by tenantId.
    return rows.stream()
            .filter(row -> tenantId.equals(row.tenantId()))
            .mapToLong(TenantKeyCount::count)
            .sum();
}
```

Note: `sumFlagForTenant`'s body above is a placeholder for the *shape* of the reduction only — before finalizing this step, re-read the current file's existing downloaded/downloadInProgress-count logic (Step 1) and make `sumFlagForTenant` reproduce that exact same filtering/summing semantics (e.g. it may filter rows by `key.equals("true")` rather than summing all rows). Do not commit this step until that existing logic has been located and matched exactly.

- [ ] **Step 11: Wire `buildByTenant` into `getSnapshot`**

At the end of the snapshot-assembling method, change the final `return new TransferSnapshotMetrics(total, byState, byRoleAndState, byFormat, downloadedCount, downloadInProgressCount);` to:

```java
return new TransferSnapshotMetrics(totalCount, byState, byRoleAndState, byFormat, downloadedCount, downloadInProgressCount,
        buildByTenant(tenantId, byStateRows, byRoleAndStateRows, byFormatRows, downloadedRows, downloadInProgressRows, totalRows));
```

(Variable names must match whatever the file actually uses for each raw row list — confirm from Step 1/6-9 edits.)

- [ ] **Step 12: Run tests to verify they pass**

Run: `mvn -pl data-transfer -am -Dtest=TransferMetricsServiceTest test`
Expected: PASS

- [ ] **Step 13: Update any other production callers of the constructor**

Run: `grep -rn "new TransferMetricsService(" --include=*.java .`

Add `tenantRepository` argument to any explicit non-Spring instantiation found outside tests.

- [ ] **Step 14: Run full module verify**

Run: `mvn -pl data-transfer -am verify`
Expected: PASS (requires Docker for integration tests, per repo convention).

- [ ] **Step 15: Commit**

```bash
git add data-transfer/src/main/java/it/eng/datatransfer/service/TransferMetricsService.java \
        data-transfer/src/test/java/it/eng/datatransfer/service/TransferMetricsServiceTest.java
git commit -m "feat: add per-tenant breakdown to TransferMetricsService"
```

---

## Task 5: `AuditEventMetricsService` per-tenant breakdown

**Files:**
- Modify: `tools/src/main/java/it/eng/tools/service/AuditEventMetricsService.java`
- Modify: `tools/src/test/java/it/eng/tools/service/AuditEventMetricsServiceTest.java`

This service runs 4 separate `mongoTemplate.aggregate()` calls (event type, role, time-bucket, total). Each needs `tenantId` added to its `$group`/`$project` stage.

> Before editing, view the current full contents of both files to confirm exact aggregation method names, private record shapes, and existing test stub patterns.

- [ ] **Step 1: Read current file and locate the 4 aggregation methods**

Run: `view tools/src/main/java/it/eng/tools/service/AuditEventMetricsService.java`

Confirm: method names for event-type counts, role counts, time-bucket counts, and total count; the constructor's current dependencies; how `getTotalCount`(or equivalent) currently reads its result (design doc notes existing test stub shape `{total: 3L}`).

- [ ] **Step 2: Write the failing tests (byTenant behavior)**

Add to `AuditEventMetricsServiceTest.java` (mirror existing Mockito/aggregation-stubbing style; add `@Mock TenantRepository tenantRepository;` and wire into the constructor under test):

```java
@Test
void getSnapshotShouldReturnNullByTenantWhenTenantIdProvided() {
    // arrange: stub mongoTemplate.aggregate(...) exactly as the existing
    // "non-blank tenantId" test in this class already does, for all 4 aggregation calls

    HistoricalEventMetrics result = auditEventMetricsService.getSnapshot("tenant-1", timeWindow);

    assertThat(result.byTenant()).isNull();
}

@Test
void getSnapshotShouldReturnZeroFilledByTenantWhenTenantIdBlank() {
    Tenant tenantA = Tenant.Builder.newInstance().id("tenant-a").name("Tenant A").enabled(true).build();
    Tenant tenantB = Tenant.Builder.newInstance().id("tenant-b").name("Tenant B").enabled(true).build();
    when(tenantRepository.findAll()).thenReturn(List.of(tenantA, tenantB));
    // arrange: stub all 4 mongoTemplate.aggregate(...) calls to return rows with
    // tenantId = "tenant-a" only (reuse this test class's existing stubbing helpers,
    // adding a "tenantId" field to each row document)

    HistoricalEventMetrics result = auditEventMetricsService.getSnapshot("", timeWindow);

    assertThat(result.byTenant()).hasSize(2);
    assertThat(result.byTenant())
            .extracting(TenantMetrics::tenantId)
            .containsExactlyInAnyOrder("tenant-a", "tenant-b");
    TenantMetrics<HistoricalEventMetrics> tenantBEntry = result.byTenant().stream()
            .filter(tm -> tm.tenantId().equals("tenant-b"))
            .findFirst().orElseThrow();
    assertThat(tenantBEntry.metrics().totalCount()).isZero();
}
```

(`timeWindow` — use whatever fixture/variable the existing test class already sets up for its `TimeWindow` parameter.)

- [ ] **Step 3: Fix the existing "aggregate across all tenants" test to stub the tenant roster**

In the existing test calling `getSnapshot("", ...)` or `getSnapshot(null, ...)`, add:

```java
when(tenantRepository.findAll()).thenReturn(List.of());
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `mvn -pl tools -am -Dtest=AuditEventMetricsServiceTest test`
Expected: FAIL — compilation error (`TenantRepository` not wired, `byTenant()` unpopulated).

- [ ] **Step 5: Update the constructor to accept `TenantRepository`**

Add field, constructor parameter (appended last), assignment, and import (`TenantRepository` is already in the same `tools` module — no cross-module import needed here, unlike Tasks 3/4):

```java
private final TenantRepository tenantRepository;
```

- [ ] **Step 6: Add `tenantId` to each of the 4 aggregation pipelines and their row records**

Add new private records (or extend existing ones if this file already has per-dimension records — confirm from Step 1):

```java
private record TenantKeyCount(String tenantId, String key, long count) {
}

private record TenantTimeBucketCount(String tenantId, Instant bucketStart, long count) {
}

private record TenantCount(String tenantId, long count) {
}
```

For each of the 4 aggregation pipelines (event type, role, time-bucket, total), add `tenantId` to the `$group` id and a corresponding `$project` field. Parse `tenantId` from each result `Document` via `doc.getString("tenantId")` (nullable for legacy documents).

- [ ] **Step 7: Add `summarize`/`summarizeOverTime` helpers to reproduce old aggregate behavior**

```java
private static List<KeyCount> summarizeByKey(List<TenantKeyCount> rows) {
    return rows.stream()
            .collect(Collectors.groupingBy(TenantKeyCount::key, Collectors.summingLong(TenantKeyCount::count)))
            .entrySet().stream()
            .map(e -> new KeyCount(e.getKey(), e.getValue()))
            .toList();
}

private static List<TimeBucketCount> summarizeOverTime(List<TenantTimeBucketCount> rows) {
    return rows.stream()
            .collect(Collectors.groupingBy(TenantTimeBucketCount::bucketStart, Collectors.summingLong(TenantTimeBucketCount::count)))
            .entrySet().stream()
            .map(e -> new TimeBucketCount(e.getKey(), e.getValue()))
            .sorted(Comparator.comparing(TimeBucketCount::bucketStart))
            .toList();
}
```

Use these in place of whatever inline grouping logic the existing event-type/role/time-bucket aggregate-assembly code currently has (Step 1).

- [ ] **Step 8: Rewrite total count to sum across the per-tenant list**

```java
long totalCount = totalRows.stream().mapToLong(TenantCount::count).sum();
```

Verified backward compatible: a legacy single-row shape `{total: 3L}` parses as `tenantId = null, count = 3L`; summing still yields `3L`.

- [ ] **Step 9: Add the `buildByTenant` method**

```java
private List<TenantMetrics<HistoricalEventMetrics>> buildByTenant(
        String tenantId,
        List<TenantKeyCount> byEventTypeRows,
        List<TenantKeyCount> byRoleRows,
        List<TenantTimeBucketCount> overTimeRows,
        List<TenantCount> totalRows) {
    if (StringUtils.hasText(tenantId)) {
        return null;
    }

    return tenantRepository.findAll().stream()
            .sorted(Comparator.comparing(Tenant::getId))
            .map(tenant -> {
                String id = tenant.getId();
                HistoricalEventMetrics tenantMetrics = new HistoricalEventMetrics(
                        totalRows.stream().filter(r -> id.equals(r.tenantId())).mapToLong(TenantCount::count).sum(),
                        summarizeByKey(byEventTypeRows.stream().filter(r -> id.equals(r.tenantId())).toList()),
                        summarizeByKey(byRoleRows.stream().filter(r -> id.equals(r.tenantId())).toList()),
                        summarizeOverTime(overTimeRows.stream().filter(r -> id.equals(r.tenantId())).toList()),
                        null);
                return new TenantMetrics<>(id, tenant.getName(), tenantMetrics);
            })
            .toList();
}
```

- [ ] **Step 10: Wire `buildByTenant` into `getSnapshot`**

Change the final `return new HistoricalEventMetrics(total, byEventType, byRole, overTime);` to:

```java
return new HistoricalEventMetrics(totalCount, byEventType, byRole, overTime,
        buildByTenant(tenantId, byEventTypeRows, byRoleRows, overTimeRows, totalRows));
```

(Match actual local variable names from Steps 6-8.)

- [ ] **Step 11: Run tests to verify they pass**

Run: `mvn -pl tools -am -Dtest=AuditEventMetricsServiceTest test`
Expected: PASS

- [ ] **Step 12: Update any other production callers of the constructor**

Run: `grep -rn "new AuditEventMetricsService(" --include=*.java .`

Add `tenantRepository` argument to any explicit non-Spring instantiation found outside tests.

- [ ] **Step 13: Run full module verify**

Run: `mvn -pl tools -am verify`
Expected: PASS

- [ ] **Step 14: Commit**

```bash
git add tools/src/main/java/it/eng/tools/service/AuditEventMetricsService.java \
        tools/src/test/java/it/eng/tools/service/AuditEventMetricsServiceTest.java
git commit -m "feat: add per-tenant breakdown to AuditEventMetricsService"
```

---

## Task 6: End-to-end controller/service test updates

**Files:**
- Modify: `connector/src/test/java/it/eng/connector/rest/api/DashboardMetricsControllerTest.java`
- Modify: `connector/src/test/java/it/eng/connector/service/DashboardMetricsServiceTest.java`

`DashboardMetricsController` and `DashboardMetricsService` (the connector-level orchestration layer, distinct from the three per-domain metrics services) require **no production code changes** — they already pass `tenantId` straight through. Only their tests need new assertions confirming `byTenant` presence/absence end-to-end.

- [ ] **Step 1: Read both current test files**

Run: `view connector/src/test/java/it/eng/connector/rest/api/DashboardMetricsControllerTest.java` and `view connector/src/test/java/it/eng/connector/service/DashboardMetricsServiceTest.java`

Confirm how `TenantContextHolder` is set/cleared in these tests (per design doc, likely via `TenantContextHolder.setTenantId(...)`/`clear()` in `@BeforeEach`/`@AfterEach` or per-test), and how the three per-domain services are mocked (`@MockBean`/`@Mock`) to return `NegotiationSnapshotMetrics`/`TransferSnapshotMetrics`/`HistoricalEventMetrics` instances.

- [ ] **Step 2: Write the failing tests**

Add a superadmin-scenario test to `DashboardMetricsControllerTest.java` (adapt to the file's actual MockMvc/setup conventions found in Step 1):

```java
@Test
void getSummaryShouldIncludeByTenantWhenSuperAdminRequestsAllTenants() throws Exception {
    TenantContextHolder.setTenantId(null); // or however this test file simulates superadmin scope

    NegotiationSnapshotMetrics negotiationMetrics = new NegotiationSnapshotMetrics(
            1L, List.of(), List.of(),
            List.of(new TenantMetrics<>("tenant-1", "Tenant One",
                    new NegotiationSnapshotMetrics(1L, List.of(), List.of(), null))));
    when(negotiationMetricsService.getSnapshot(any())).thenReturn(negotiationMetrics);
    // stub transferMetricsService/auditEventMetricsService similarly, matching this file's existing pattern

    mockMvc.perform(get(ApiEndpoints.DASHBOARD_SUMMARY_V1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.negotiations.byTenant").isArray())
            .andExpect(jsonPath("$.negotiations.byTenant[0].tenantId").value("tenant-1"));
}

@Test
void getSummaryShouldOmitByTenantWhenAdminRequestsOwnTenant() throws Exception {
    TenantContextHolder.setTenantId("tenant-1"); // or however this test file simulates admin scope

    NegotiationSnapshotMetrics negotiationMetrics = new NegotiationSnapshotMetrics(1L, List.of(), List.of());
    when(negotiationMetricsService.getSnapshot(any())).thenReturn(negotiationMetrics);
    // stub transferMetricsService/auditEventMetricsService similarly

    mockMvc.perform(get(ApiEndpoints.DASHBOARD_SUMMARY_V1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.negotiations.byTenant").doesNotExist());
}
```

Add analogous assertions (`byTenant` present/absent per scope) to `DashboardMetricsServiceTest.java` for whatever methods it tests directly (e.g. `getSummary`), following that file's existing structure exactly.

- [ ] **Step 3: Run tests to verify they fail (or trivially pass if wiring is already correct)**

Run: `mvn -pl connector -am -Dtest=DashboardMetricsControllerTest,DashboardMetricsServiceTest test`

Expected: since no production change is needed in these two orchestration classes, these tests should PASS immediately once Tasks 2-5 are complete (they exercise already-correct pass-through logic). If they fail, the failure indicates a wiring gap in `DashboardMetricsController`/`DashboardMetricsService` that must be fixed — read the failure message and the two production files before making any change here, since the design explicitly expects zero production changes at this layer.

- [ ] **Step 4: Run full module verify**

Run: `mvn -pl connector -am verify`
Expected: PASS (Docker required).

- [ ] **Step 5: Commit**

```bash
git add connector/src/test/java/it/eng/connector/rest/api/DashboardMetricsControllerTest.java \
        connector/src/test/java/it/eng/connector/service/DashboardMetricsServiceTest.java
git commit -m "test: verify byTenant flows end-to-end through DashboardMetricsController"
```

---

## Task 7: Update documentation

**Files:**
- Modify: `doc/dashboard-metrics.md`
- Modify: `doc/dashboard-ui-handoff.md`

- [ ] **Step 1: Read both current files**

Run: `view doc/dashboard-metrics.md` and `view doc/dashboard-ui-handoff.md`

Locate: `doc/dashboard-metrics.md`'s "Tenant behavior" section (or equivalent), and `doc/dashboard-ui-handoff.md`'s TypeScript interface definitions and mock JSON payloads for `NegotiationSnapshotMetrics`/`TransferSnapshotMetrics`/`HistoricalEventMetrics`.

- [ ] **Step 2: Update `doc/dashboard-metrics.md`**

Under the existing "Tenant behavior" section, add:

```markdown
### Per-tenant breakdown (`byTenant`)

When a superadmin requests dashboard data without a tenant scope (blank `tenantId`, no `X-Tenant-Id` header), each of `NegotiationSnapshotMetrics`, `TransferSnapshotMetrics`, and `HistoricalEventMetrics` includes an additional `byTenant` field: a list of `{ tenantId, tenantName, metrics }` entries, one per registered tenant (including disabled tenants), zero-filled for tenants with no matching data. The nested `metrics` object has the same shape as its parent but with its own `byTenant` always `null` (no further recursion).

When an admin requests data (tenant-scoped JWT) or a superadmin scopes the request via `X-Tenant-Id`, `byTenant` is `null` on every affected object.

This applies to `/api/v1/dashboard/summary`, `/api/v1/dashboard/negotiations`, `/api/v1/dashboard/transfers`, and `/api/v1/dashboard/events`. It does **not** apply to `/api/v1/dashboard/runtime`.
```

(Adjust exact endpoint paths/constant names to match what's actually documented in the file — confirm from Step 1 / `ApiEndpoints`.)

- [ ] **Step 3: Update `doc/dashboard-ui-handoff.md`**

Add `byTenant` to each relevant TypeScript interface (mirroring the file's existing style):

```typescript
interface NegotiationSnapshotMetrics {
  totalCount: number;
  byState: KeyCount[];
  byRoleAndState: KeyCount[];
  byTenant: TenantMetrics<NegotiationSnapshotMetrics>[] | null;
}

interface TenantMetrics<T> {
  tenantId: string;
  tenantName: string;
  metrics: T;
}
```

Apply the analogous `byTenant: TenantMetrics<...>[] | null;` addition to the `TransferSnapshotMetrics` and `HistoricalEventMetrics` TypeScript interfaces already present in this file. Add a short prose note (matching the file's existing tone) explaining: `byTenant` is populated only for superadmin, all-tenant requests; UI should treat it as the source for a tenant filter dropdown, filtering client-side with no extra API calls; `null`/absent means the requester is already tenant-scoped. Update any existing mock JSON response example in the file to include a sample `byTenant` array with 1-2 entries.

- [ ] **Step 4: Commit**

```bash
git add doc/dashboard-metrics.md doc/dashboard-ui-handoff.md
git commit -m "docs: document byTenant per-tenant breakdown for dashboard metrics"
```

---

## Task 8: Update CHANGELOG

**Files:**
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Read the current `## [Unreleased] — Dashboard Metrics API` section**

Run: `view CHANGELOG.md` (first ~20 lines)

- [ ] **Step 2: Add a new bullet**

Add under the existing "Dashboard Metrics API" Unreleased section (matching its existing bullet style/tense):

```markdown
- Added per-tenant breakdown (`byTenant`) to negotiation, transfer, and audit-event dashboard metrics responses, populated only for superadmin cross-tenant requests, enabling UI-side tenant filtering without additional API calls.
```

- [ ] **Step 3: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs: add CHANGELOG entry for dashboard metrics byTenant breakdown"
```

---

## Task 9: Full verification

- [ ] **Step 1: Run full multi-module verify**

Run: `mvn clean verify`
Expected: PASS. Requires Docker running (integration tests use Testcontainers for MongoDB/MinIO).

- [ ] **Step 2: Run Checkstyle explicitly**

Run: `mvn checkstyle:check`
Expected: PASS — confirm every new/modified public/protected method (including the new record canonical/secondary constructors, `TenantMetrics`, and all new private-but-still-Javadoc'd-if-required helpers) satisfies `JavadocMethod`/`JavadocStyle`. Note: private methods are NOT subject to `JavadocMethod` by default Checkstyle config (module scope defaults to `public`); only the public record constructors and public accessors need Javadoc, which Task 1/2 already added at the record level.

- [ ] **Step 3: If any TCK-relevant code paths were touched, run the TCK profile**

This feature only touches management/admin-facing dashboard DTOs and services, not protocol-facing (`/catalog`, `/negotiations`, `/transfers` DSP) message shapes, so the TCK profile is not expected to be affected. Skip running `-Ptck` unless `mvn clean verify` surfaces unexpected protocol-layer test failures.

- [ ] **Step 4: Final review of all touched files for leftover TODOs or placeholder code**

Run: `git diff --stat` and `git log --oneline -20` to confirm the full commit sequence from Tasks 1-8 is present and clean, then `grep -rn "TODO\|FIXME" tools/src/main/java/it/eng/tools/model/dashboard tools/src/main/java/it/eng/tools/service/AuditEventMetricsService.java negotiation/src/main/java/it/eng/negotiation/service/NegotiationMetricsService.java data-transfer/src/main/java/it/eng/datatransfer/service/TransferMetricsService.java` to confirm none were left behind.

---

## Self-Review Notes (completed during plan authoring)

**Spec coverage:** All spec sections are covered — trigger rule (Tasks 3-5, `StringUtils.hasText` check in `buildByTenant`), response shape (Task 1-2), computation strategy (Tasks 3-5, single aggregation + tenantId in group key + in-app merge), per-service complexity (Task 4 flagged as hardest, matching spec's note about `$facet`), testing plan (every task has a test step, plus Task 6 for controller/service-level end-to-end), documentation requirement (Tasks 7-8).

**Placeholder scan:** Two intentional exceptions are flagged, not left silently vague: (a) Task 3/4/5 Step 2 test bodies explicitly say "read the existing test file's stubbing pattern first" rather than fabricating fake Mongo `Document` stub syntax that might not match this codebase's actual aggregation-mocking style — this is a deliberate, explained deferral to the exact current file contents (which cannot be guessed without reading them at execution time), not a "TBD"; (b) Task 4 Step 10's `sumFlagForTenant` explicitly flags itself as needing verification against the real existing flag-reduction logic before being finalized — again a deliberate call-out, not a shortcut, since the actual current file must be read first to get this exactly right.

**Type consistency:** `TenantMetrics<T>` (Task 1) is used consistently across Tasks 2-6 with the same 3-arg shape (`tenantId`, `tenantName`, `metrics`). `buildByTenant` method name and `StringUtils.hasText(tenantId)` null/blank check is used consistently across Tasks 3, 4, 5. `TenantRepository`/`Tenant.getId()`/`Tenant.getName()` accessor usage is consistent across all three service tasks. Record component ordering (`byTenant` always last) is consistent across Task 2's three records and every constructor call throughout Tasks 3-6.
