# Generic Filtering

Example API calls with various filters:

- All events, without any filters:

```
/api/v1/audit
```

- Single event type filter:

```
/api/v1/audit?eventType=PROTOCOL_NEGOTIATION_AGREED
```

- Multiple event type filters:

```
/api/v1/audit?eventType=PROTOCOL_NEGOTIATION_AGREED&eventType=APPLICATION_START
```

- Filtering by event type and details:

```
/api/v1/audit?eventType=PROTOCOL_NEGOTIATION_AGREED&details.contractNegotiation.state=FINALIZED
```

- Filtering by timestamp range:

```
/api/v1/audit?from=2025-07-16T10:47:29.304&to=2025-07-16T10:47:31.304
```
