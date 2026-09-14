# Dashboard UI Handoff for Angular Developer

## Purpose

This file is a backend-to-frontend handoff for building statistics pages in the Angular UI project.
It describes the available dashboard API endpoints, suggested UI pages, expected payload shapes, and Angular-friendly mock data for component and service tests.

## Backend API Summary

Base path:

```text
/api/v1/dashboard
```

All responses use this wrapper shape:

```ts
export interface GenericApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  timestamp: string; // ISO-8601, e.g. 2026-05-21T12:00:00+02:00
}
```

Available endpoints:

| Endpoint | Purpose |
| --- | --- |
| `GET /api/v1/dashboard/summary` | Full dashboard payload: negotiations, transfers, events, runtime |
| `GET /api/v1/dashboard/runtime` | Runtime-only JVM/process metrics |
| `GET /api/v1/dashboard/negotiations` | Current negotiation snapshot |
| `GET /api/v1/dashboard/transfers` | Current transfer snapshot |
| `GET /api/v1/dashboard/events` | Historical event aggregates |

Query params for `summary` and `events`:

```text
from=<ISO-8601 instant>
to=<ISO-8601 instant>
bucket=hour|day
```

Defaults on backend:

- `to = now`
- `from = to - 24h`
- `bucket = hour`

## Tenant and Security Notes

- These are admin endpoints.
- Tenant admins see only their own tenant data.
- Super-admin can see cross-tenant data or override tenant scope with `X-Tenant-Id`.
- Runtime metrics are process-wide, not tenant-specific.

## Suggested Angular Pages

### 1. Dashboard overview page

Suggested route:

```text
/dashboard
```

Recommended sections:

1. KPI cards
   - total negotiations
   - total transfers
   - total processed events in selected window
   - process CPU
2. Negotiation state chart
3. Transfer state chart
4. Transfer format chart
5. Historical events chart
6. Runtime panel

Recommended data source:

```text
GET /api/v1/dashboard/summary
```

### 2. Negotiation statistics page

Suggested route:

```text
/dashboard/negotiations
```

Recommended widgets:

- table: counts by state
- stacked bar or grouped bar: counts by role/state

Data source:

```text
GET /api/v1/dashboard/negotiations
```

### 3. Transfer statistics page

Suggested route:

```text
/dashboard/transfers
```

Recommended widgets:

- table or bar chart: counts by state
- table or bar chart: counts by role/state
- pie/donut: counts by format
- status cards: download flags

Data source:

```text
GET /api/v1/dashboard/transfers
```

### 4. Event history page

Suggested route:

```text
/dashboard/events
```

Recommended widgets:

- time-window selector
- bucket selector (`hour` / `day`)
- timeline chart from `countsOverTime`
- side tables for event types and roles

Data source:

```text
GET /api/v1/dashboard/events?from=...&to=...&bucket=hour
```

### 5. Runtime page or expandable panel

Suggested route:

```text
/dashboard/runtime
```

Recommended widgets:

- CPU cards
- heap / non-heap memory cards
- live thread count
- uptime display

Data source:

```text
GET /api/v1/dashboard/runtime
```

## Suggested Angular Models

```ts
export interface KeyCount {
  key: string;
  count: number;
}

export interface TimeBucketCount {
  bucketStart: string; // ISO-8601 instant
  key: string;
  count: number;
}

export interface TenantMetrics<T> {
  tenantId: string;
  tenantName: string;
  metrics: T;
}

export interface NegotiationSnapshotMetrics {
  totalCount: number;
  byState: KeyCount[];
  byRoleAndState: KeyCount[];
  byTenant: TenantMetrics<NegotiationSnapshotMetrics>[] | null;
}

export interface TransferSnapshotMetrics {
  totalCount: number;
  byState: KeyCount[];
  byRoleAndState: KeyCount[];
  byFormat: KeyCount[];
  downloadedCount: number;
  downloadInProgressCount: number;
  byTenant: TenantMetrics<TransferSnapshotMetrics>[] | null;
}

export interface HistoricalEventMetrics {
  totalCount: number;
  byEventType: KeyCount[];
  byRole: KeyCount[];
  overTime: TimeBucketCount[];
  byTenant: TenantMetrics<HistoricalEventMetrics>[] | null;
}

export interface RuntimeMetricsResponse {
  processCpuUsage: number;      // ratio 0..1, or -1 when unavailable
  systemCpuUsage: number;       // ratio 0..1, or -1 when unavailable
  heapUsedBytes: number;
  heapMaxBytes: number;         // can be -1 when undefined
  nonHeapUsedBytes: number;
  liveThreadCount: number;
  uptimeMilliseconds: number;
}

export interface DashboardSummaryResponse {
  negotiations: NegotiationSnapshotMetrics;
  transfers: TransferSnapshotMetrics;
  events: HistoricalEventMetrics;
  runtime: RuntimeMetricsResponse;
}
```

## UI Formatting Notes

### CPU

Backend returns CPU values as ratio, not percent.

Example:

- `0.37` -> `37%`
- `-1` -> show `N/A`

Suggested helper:

```ts
export function formatCpuRatio(value: number): string {
  if (value < 0) {
    return 'N/A';
  }
  return `${Math.round(value * 100)}%`;
}
```

### Bytes

Memory values are raw bytes.

Suggested helper:

```ts
export function formatBytes(value: number): string {
  if (value < 0) {
    return 'N/A';
  }

  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let size = value;
  let unitIndex = 0;

  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex++;
  }

  return `${size.toFixed(size >= 10 ? 0 : 1)} ${units[unitIndex]}`;
}
```

### Role/state combined keys

Backend returns combined keys as strings like:

```text
CONSUMER:REQUESTED
PROVIDER:STARTED
```

Suggested helper:

```ts
export function splitRoleStateKey(key: string): { role: string; state: string } {
  const [role, state] = key.split(':');
  return { role, state };
}
```

## Angular Mock Data

These fixtures are intended for:

- component tests
- service tests
- Storybook/demo states
- local page prototyping before backend integration

```ts
export interface GenericApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  timestamp: string;
}

export interface KeyCount {
  key: string;
  count: number;
}

export interface TimeBucketCount {
  bucketStart: string;
  key: string;
  count: number;
}

export interface TenantMetrics<T> {
  tenantId: string;
  tenantName: string;
  metrics: T;
}

export interface NegotiationSnapshotMetrics {
  totalCount: number;
  byState: KeyCount[];
  byRoleAndState: KeyCount[];
  byTenant: TenantMetrics<NegotiationSnapshotMetrics>[] | null;
}

export interface TransferSnapshotMetrics {
  totalCount: number;
  byState: KeyCount[];
  byRoleAndState: KeyCount[];
  byFormat: KeyCount[];
  downloadedCount: number;
  downloadInProgressCount: number;
  byTenant: TenantMetrics<TransferSnapshotMetrics>[] | null;
}

export interface HistoricalEventMetrics {
  totalCount: number;
  byEventType: KeyCount[];
  byRole: KeyCount[];
  overTime: TimeBucketCount[];
  byTenant: TenantMetrics<HistoricalEventMetrics>[] | null;
}

export interface RuntimeMetricsResponse {
  processCpuUsage: number;
  systemCpuUsage: number;
  heapUsedBytes: number;
  heapMaxBytes: number;
  nonHeapUsedBytes: number;
  liveThreadCount: number;
  uptimeMilliseconds: number;
}

export interface DashboardSummaryResponse {
  negotiations: NegotiationSnapshotMetrics;
  transfers: TransferSnapshotMetrics;
  events: HistoricalEventMetrics;
  runtime: RuntimeMetricsResponse;
}

export const MOCK_NEGOTIATIONS: GenericApiResponse<NegotiationSnapshotMetrics> = {
  success: true,
  message: 'Dashboard negotiation metrics fetched',
  timestamp: '2026-05-21T12:00:00+02:00',
  data: {
    totalCount: 18,
    byState: [
      { key: 'REQUESTED', count: 4 },
      { key: 'OFFERED', count: 3 },
      { key: 'ACCEPTED', count: 2 },
      { key: 'AGREED', count: 2 },
      { key: 'VERIFIED', count: 1 },
      { key: 'FINALIZED', count: 5 },
      { key: 'TERMINATED', count: 1 }
    ],
    byRoleAndState: [
      { key: 'CONSUMER:REQUESTED', count: 2 },
      { key: 'PROVIDER:REQUESTED', count: 2 },
      { key: 'CONSUMER:OFFERED', count: 1 },
      { key: 'PROVIDER:OFFERED', count: 2 },
      { key: 'CONSUMER:ACCEPTED', count: 1 },
      { key: 'PROVIDER:ACCEPTED', count: 1 },
      { key: 'CONSUMER:FINALIZED', count: 3 },
      { key: 'PROVIDER:FINALIZED', count: 2 },
      { key: 'PROVIDER:TERMINATED', count: 1 }
    ],
    byTenant: null
  }
};

export const MOCK_TRANSFERS: GenericApiResponse<TransferSnapshotMetrics> = {
  success: true,
  message: 'Dashboard transfer metrics fetched',
  timestamp: '2026-05-21T12:00:00+02:00',
  data: {
    totalCount: 14,
    byState: [
      { key: 'INITIALIZED', count: 1 },
      { key: 'REQUESTED', count: 3 },
      { key: 'STARTED', count: 4 },
      { key: 'SUSPENDED', count: 1 },
      { key: 'COMPLETED', count: 4 },
      { key: 'TERMINATED', count: 1 }
    ],
    byRoleAndState: [
      { key: 'CONSUMER:REQUESTED', count: 2 },
      { key: 'PROVIDER:REQUESTED', count: 1 },
      { key: 'CONSUMER:STARTED', count: 3 },
      { key: 'PROVIDER:STARTED', count: 1 },
      { key: 'CONSUMER:COMPLETED', count: 2 },
      { key: 'PROVIDER:COMPLETED', count: 2 },
      { key: 'PROVIDER:TERMINATED', count: 1 }
    ],
    byFormat: [
      { key: 'HTTP_PULL', count: 9 },
      { key: 'HTTP_PUSH', count: 4 },
      { key: 'SFTP', count: 1 }
    ],
    downloadedCount: 4,
    downloadInProgressCount: 1,
    byTenant: null
  }
};

export const MOCK_EVENTS: GenericApiResponse<HistoricalEventMetrics> = {
  success: true,
  message: 'Dashboard event metrics fetched',
  timestamp: '2026-05-21T12:00:00+02:00',
  data: {
    totalCount: 26,
    byEventType: [
      { key: 'Protocol negotiation requested', count: 6 },
      { key: 'Protocol negotiation finalized', count: 4 },
      { key: 'Transfer requested', count: 5 },
      { key: 'Transfer started', count: 4 },
      { key: 'Transfer completed', count: 4 },
      { key: 'State transition invalid', count: 3 }
    ],
    byRole: [
      { key: 'ROLE_API', count: 11 },
      { key: 'ROLE_PROVIDER', count: 8 },
      { key: 'ROLE_CONSUMER', count: 5 },
      { key: 'ROLE_PROTOCOL', count: 2 }
    ],
    overTime: [
      { bucketStart: '2026-05-20T13:00:00Z', key: 'Transfer requested', count: 1 },
      { bucketStart: '2026-05-20T14:00:00Z', key: 'Transfer requested', count: 2 },
      { bucketStart: '2026-05-20T15:00:00Z', key: 'Transfer started', count: 2 },
      { bucketStart: '2026-05-20T16:00:00Z', key: 'Transfer completed', count: 1 },
      { bucketStart: '2026-05-20T17:00:00Z', key: 'Protocol negotiation requested', count: 2 },
      { bucketStart: '2026-05-20T18:00:00Z', key: 'Protocol negotiation finalized', count: 1 },
      { bucketStart: '2026-05-20T19:00:00Z', key: 'State transition invalid', count: 1 },
      { bucketStart: '2026-05-20T20:00:00Z', key: 'Transfer completed', count: 3 }
    ],
    byTenant: null
  }
};

export const MOCK_RUNTIME: GenericApiResponse<RuntimeMetricsResponse> = {
  success: true,
  message: 'Dashboard runtime metrics fetched',
  timestamp: '2026-05-21T12:00:00+02:00',
  data: {
    processCpuUsage: 0.37,
    systemCpuUsage: 0.61,
    heapUsedBytes: 402653184,
    heapMaxBytes: 1073741824,
    nonHeapUsedBytes: 125829120,
    liveThreadCount: 48,
    uptimeMilliseconds: 86400123
  }
};

export const MOCK_DASHBOARD_SUMMARY: GenericApiResponse<DashboardSummaryResponse> = {
  success: true,
  message: 'Dashboard summary fetched',
  timestamp: '2026-05-21T12:00:00+02:00',
  data: {
    negotiations: MOCK_NEGOTIATIONS.data!,
    transfers: MOCK_TRANSFERS.data!,
    events: MOCK_EVENTS.data!,
    runtime: MOCK_RUNTIME.data!
  }
};

export const MOCK_EMPTY_DASHBOARD_SUMMARY: GenericApiResponse<DashboardSummaryResponse> = {
  success: true,
  message: 'Dashboard summary fetched',
  timestamp: '2026-05-21T12:00:00+02:00',
  data: {
    negotiations: {
      totalCount: 0,
      byState: [],
      byRoleAndState: [],
      byTenant: null
    },
    transfers: {
      totalCount: 0,
      byState: [],
      byRoleAndState: [],
      byFormat: [],
      downloadedCount: 0,
      downloadInProgressCount: 0,
      byTenant: null
    },
    events: {
      totalCount: 0,
      byEventType: [],
      byRole: [],
      overTime: [],
      byTenant: null
    },
    runtime: {
      processCpuUsage: -1,
      systemCpuUsage: -1,
      heapUsedBytes: 0,
      heapMaxBytes: -1,
      nonHeapUsedBytes: 0,
      liveThreadCount: 0,
      uptimeMilliseconds: 0
    }
  }
};
```

## Suggested Angular Service Surface

```ts
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/dashboard';

  getSummary(params?: { from?: string; to?: string; bucket?: 'hour' | 'day' }) {
    return this.http.get<GenericApiResponse<DashboardSummaryResponse>>(
      `${this.baseUrl}/summary`,
      { params: this.toHttpParams(params) }
    );
  }

  getRuntime(): Observable<GenericApiResponse<RuntimeMetricsResponse>> {
    return this.http.get<GenericApiResponse<RuntimeMetricsResponse>>(`${this.baseUrl}/runtime`);
  }

  getNegotiations(): Observable<GenericApiResponse<NegotiationSnapshotMetrics>> {
    return this.http.get<GenericApiResponse<NegotiationSnapshotMetrics>>(`${this.baseUrl}/negotiations`);
  }

  getTransfers(): Observable<GenericApiResponse<TransferSnapshotMetrics>> {
    return this.http.get<GenericApiResponse<TransferSnapshotMetrics>>(`${this.baseUrl}/transfers`);
  }

  getEvents(params?: { from?: string; to?: string; bucket?: 'hour' | 'day' }) {
    return this.http.get<GenericApiResponse<HistoricalEventMetrics>>(
      `${this.baseUrl}/events`,
      { params: this.toHttpParams(params) }
    );
  }

  private toHttpParams(params?: Record<string, string | undefined>): HttpParams {
    let httpParams = new HttpParams();
    Object.entries(params ?? {}).forEach(([key, value]) => {
      if (value) {
        httpParams = httpParams.set(key, value);
      }
    });
    return httpParams;
  }
}
```

## Recommended UI Test Cases

1. summary page renders KPI cards from `MOCK_DASHBOARD_SUMMARY`
2. empty state renders correctly from `MOCK_EMPTY_DASHBOARD_SUMMARY`
3. CPU formatting shows `37%` for `0.37`
4. CPU formatting shows `N/A` for `-1`
5. bytes formatter converts heap values to MB/GB
6. role/state labels split correctly from `CONSUMER:REQUESTED`
7. hourly event chart maps `countsOverTime` correctly
8. page reacts to `bucket = day` vs `bucket = hour`

## Recommended Implementation Order for UI Developer

1. add Angular interfaces and mock fixtures
2. build API service
3. build dashboard overview page from summary endpoint
4. build detail pages for negotiations, transfers, events
5. add runtime panel last
6. connect charts/tables after cards and basic layout are stable

## Out of Scope for This Handoff

- Angular routing setup details in the UI repo
- actual charting library choice
- export/CSV actions
- websocket/live refresh
- backend changes

