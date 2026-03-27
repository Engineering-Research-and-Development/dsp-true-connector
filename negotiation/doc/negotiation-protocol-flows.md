# Contract Negotiation — How It Works

> **See also:** [Technical Documentation](./negotiation-technical.md) | [Data Models](./model.md) | [Policy Enforcement](./policy-enforcement.md)

---

## What is Contract Negotiation?

Before one connector can access data from another, both parties must agree on the **terms of use**. These terms are
expressed as policies — rules that govern how, when, and how many times the data may be used.

Think of it like signing a contract before accessing a service: the data provider states their conditions, the consumer
agrees (or proposes different conditions), and once both sides formally agree, the contract is stored and data transfer
can begin.

In the Dataspace Protocol (DSP), this process is called **Contract Negotiation**. The TRUE Connector implements the
full negotiation protocol for both sides of this exchange.

---

## Roles: Provider and Consumer

**Provider** — the connector that owns or controls the data:
- Publishes datasets with usage policies attached (via the catalog).
- Reviews incoming contract requests.
- Sends formal agreement messages when terms are acceptable.
- Finalizes the negotiation once the consumer has verified the agreement.

**Consumer** — the connector that wants to access the data:
- Browses the provider's catalog to find a dataset.
- Sends a contract request with the intended usage terms.
- Accepts or negotiates the provider's offer.
- Verifies the agreement once received.

The TRUE Connector can act as **both roles simultaneously** — different negotiations may have different roles.

---

## The Negotiation Process

### Step-by-Step Flow (Consumer Initiates)

```
Consumer                                          Provider
   │                                                 │
   │──── POST /api/v1/negotiations/request ─────►    │
   │     (ContractRequestMessage)                    │
   │                                                 │  validate offer
   │                                                 │  create negotiation
   │     ◄──── 201 Created ────────────────────────  │  state: REQUESTED
   │           (ConsumerNegotiation: REQUESTED)      │
   │                                                 │
   │     (Manual or automatic)                       │
   │                                                 │──── ContractAgreementMessage ────►
   │     ◄──── agreement received ─────────────────  │  consumer state: AGREED
   │                                                 │
   │     (Manual or automatic)                       │
   │──── ContractAgreementVerificationMessage ──►    │
   │                                                 │  state: VERIFIED
   │     (Manual or automatic)                       │
   │     ◄──── ContractNegotiationEvent(FINALIZED) ─ │
   │                                                 │
   │  state: FINALIZED                               │  state: FINALIZED
   │  transfer process can begin                     │
```

### Negotiation States

| State | Who sets it | What it means |
|---|---|---|
| `REQUESTED` | Provider (after receiving request) | Consumer has asked for access; provider is considering |
| `OFFERED` | Consumer (after receiving offer) | Provider has made an offer; consumer is considering |
| `ACCEPTED` | Provider (after receiving acceptance) | Consumer has accepted the current offer terms |
| `AGREED` | Consumer (after receiving agreement) | Both sides formally agree; agreement document stored |
| `VERIFIED` | Provider (after receiving verification) | Consumer has confirmed receipt of the agreement |
| `FINALIZED` | Consumer (after receiving finalize event) | Negotiation complete; data transfer can now start |
| `TERMINATED` | Either side | Negotiation ended without agreement |

### State Flow Diagram

```
   REQUESTED ──────────────────────────────► OFFERED
       │                                       │
       │ (provider agrees directly)            │ (consumer accepts)
       ▼                                       ▼
    AGREED ◄───────────────────────────── ACCEPTED
       │
       │ (consumer verifies)
       ▼
   VERIFIED
       │
       │ (provider finalizes)
       ▼
   FINALIZED ✓  ←── data transfer can begin

   Any active state ──► TERMINATED  (either party)
```

---

## Starting a Negotiation (as Consumer)

### Prerequisites

1. You have the URL of a provider connector.
2. You have browsed the provider's catalog and identified a dataset you want access to.
3. You know the dataset ID (`target`) and the offer conditions you are willing to accept.

### Step 1 — Send a Contract Request

Use the management API to initiate the negotiation. The connector will construct and send the DSP
`ContractRequestMessage` to the provider on your behalf.

```bash
curl -X POST https://my-connector.example.com/api/v1/negotiations/request \
  -H "Content-Type: application/json" \
  -u admin:password \
  -d '{
    "Forward-To": "https://provider.example.com",
    "offer": {
      "target": "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
      "action": "USE",
      "assigner": "provider-connector-id",
      "constraint": []
    }
  }'
```

The `Forward-To` field is the base URL of the provider connector. The `offer` contains the dataset ID (`target`) and
any usage constraints you propose.

### Step 2 — Check the Response

A successful response returns the created `ContractNegotiation` object with state `REQUESTED`:

```json
{
  "success": true,
  "message": "Contract negotiation initiated",
  "data": {
    "id": "abc-123-internal-id",
    "consumerPid": "urn:uuid:consumer-generated-id",
    "providerPid": "urn:uuid:provider-generated-id",
    "state": "REQUESTED",
    "role": "CONSUMER"
  }
}
```

Save the `id` — you will need it for follow-up actions.

### Step 3 — Check Status

Check the current state of your negotiation at any time:

```bash
curl https://my-connector.example.com/api/v1/negotiations/abc-123-internal-id \
  -H "Content-Type: application/json" \
  -u admin:password
```

Or list all negotiations with filtering:

```bash
curl "https://my-connector.example.com/api/v1/negotiations?state=AGREED" \
  -H "Content-Type: application/json" \
  -u admin:password
```

### Step 4 — Accept an Offer (if provider counter-offered)

If the provider sent back a counteroffer, you will see state `OFFERED`. Accept it:

```bash
curl -X PUT https://my-connector.example.com/api/v1/negotiations/abc-123-internal-id/accept \
  -H "Content-Type: application/json" \
  -u admin:password
```

### Step 5 — Verify the Agreement

Once the provider sends an agreement (state becomes `AGREED`), verify it:

```bash
curl -X PUT https://my-connector.example.com/api/v1/negotiations/abc-123-internal-id/verify \
  -H "Content-Type: application/json" \
  -u admin:password
```

After this, the provider will finalize the negotiation and state will become `FINALIZED`.

---

## Reviewing and Responding (as Provider)

When consumers send contract requests, the provider connector stores them and waits for human action (unless automatic
negotiation is enabled).

### View Incoming Requests

```bash
curl "https://my-connector.example.com/api/v1/negotiations?state=REQUESTED&role=PROVIDER" \
  -H "Content-Type: application/json" \
  -u admin:password
```

### Agree to a Request

If the terms are acceptable, send a formal agreement:

```bash
curl -X PUT https://my-connector.example.com/api/v1/negotiations/abc-123-internal-id/agree \
  -H "Content-Type: application/json" \
  -u admin:password
```

### Send a Counter-Offer

If you want to propose different terms, send a counteroffer with a modified policy:

```bash
curl -X PUT https://my-connector.example.com/api/v1/negotiations/abc-123-internal-id/offer \
  -H "Content-Type: application/json" \
  -u admin:password \
  -d '{
    "target": "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
    "action": "USE",
    "constraint": [
      { "leftOperand": "COUNT", "operator": "LTEQ", "rightOperand": "3" }
    ]
  }'
```

### Finalize

Once the consumer has verified the agreement (state `VERIFIED`), finalize to complete the negotiation:

```bash
curl -X PUT https://my-connector.example.com/api/v1/negotiations/abc-123-internal-id/finalize \
  -H "Content-Type: application/json" \
  -u admin:password
```

### Terminate

To reject or abort a negotiation at any active state:

```bash
curl -X PUT https://my-connector.example.com/api/v1/negotiations/abc-123-internal-id/terminate \
  -H "Content-Type: application/json" \
  -u admin:password
```

---

## Automatic Negotiation

### What Is It?

When **automatic negotiation** is enabled, the connector handles the entire negotiation flow without human
intervention. As soon as a message is received (e.g., a contract request arrives at the provider, or an offer arrives
at the consumer), the connector automatically advances to the next state and sends the appropriate response.

### When Is It Useful?

- **Development and testing** — eliminates manual steps when setting up a data exchange.
- **Trusted partners** — when you have an established relationship and want to streamline access.
- **Catalog-validated offers** — the provider already validates the offer against the catalog; automatic negotiation
  simply skips the human review step.

> ⚠️ **Caution:** Enable automatic negotiation only when you trust incoming offers. The connector will agree to
> any valid offer from any consumer automatically.

### How to Enable

In your `application.properties`:

```properties
application.automatic.negotiation=true
application.automatic.negotiation.retry.max=3
application.automatic.negotiation.retry.delay.ms=2000
```

With these settings, if a step fails (e.g., network issue), the connector will retry up to 3 times with a 2-second
delay between attempts. If all retries fail, the negotiation is automatically terminated.

### Automatic Flow (Provider side)

1. Consumer sends `ContractRequestMessage`.
2. Provider validates offer against catalog (always happens, even in automatic mode).
3. Provider automatically sends `ContractAgreementMessage`.
4. After consumer verifies, provider automatically sends `ContractNegotiationEventMessage(FINALIZED)`.

### Automatic Flow (Consumer side)

1. Provider sends `ContractOfferMessage`.
2. Consumer automatically sends `ContractNegotiationEventMessage(ACCEPTED)`.
3. After provider sends agreement, consumer automatically sends `ContractAgreementVerificationMessage`.
4. After provider finalizes, transfer initialization begins.

---

## Policies — Usage Rules

Policies define the conditions under which data can be used. They are embedded in the offer that is negotiated. When
the negotiation finalizes, the agreed policy is stored and enforced on every data access.

### How Policies Work

A policy is a set of **permissions**, each containing an **action** and a list of **constraints**. All constraints
must be satisfied for access to be granted.

```json
{
  "target": "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
  "action": "USE",
  "constraint": [
    { "leftOperand": "...", "operator": "...", "rightOperand": "..." }
  ]
}
```

### Supported Constraint Types

#### Number of Usages (`COUNT`)

Limits how many times the data can be accessed.

```json
{
  "target": "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
  "action": "USE",
  "constraint": [
    { "leftOperand": "COUNT", "operator": "LTEQ", "rightOperand": "5" }
  ]
}
```

Supported operators: `LT` (less than), `LTEQ` (less than or equal).

---

#### Time-Based (`DATE_TIME`)

Restricts access to a specific time window.

```json
{
  "target": "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
  "action": "USE",
  "constraint": [
    { "leftOperand": "DATE_TIME", "operator": "GT", "rightOperand": "2024-02-29T00:00:01+01:00" }
  ]
}
```

Supported operators: `LT`, `LTEQ`, `GT` (after), `GTEQ` (from).

---

#### Purpose-Based (`PURPOSE`)

Restricts data use to a declared purpose.

```json
{
  "odrl:target": "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
  "odrl:action": "odrl:use",
  "odrl:constraint": [
    { "odrl:leftOperand": "odrl:purpose", "odrl:operator": "odrl:EQ", "odrl:rightOperand": "demo" }
  ]
}
```

Supported operators: `IS_ANY_OF`, `EQ`.

---

#### Location-Based (`SPATIAL`)

Restricts access based on the geographic location of the consumer.

```json
{
  "odrl:target": "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
  "odrl:action": "odrl:use",
  "odrl:constraint": [
    { "odrl:leftOperand": "odrl:spatial", "odrl:operator": "odrl:EQ", "odrl:rightOperand": "EU" }
  ]
}
```

Supported operators: `IS_ANY_OF`, `EQ`.

---

Multiple constraints can be combined. All must evaluate to `true` for access to be allowed (AND logic).

> **See also:** [Policy Enforcement Documentation](./policy-enforcement.md) for full details on how policies are
> evaluated at runtime.

---

## Common Questions

**Q: What happens after negotiation completes?**

Once the negotiation reaches `FINALIZED`, the agreement is stored and a data transfer can be initialized. The consumer
can then request data transfer using the agreed agreement ID. Policy constraints will be checked on every data access.

---

**Q: Can a negotiation be cancelled?**

Yes. Either party can terminate a negotiation at any active state (before `FINALIZED`). Use:

```bash
curl -X PUT https://my-connector.example.com/api/v1/negotiations/{id}/terminate \
  -H "Content-Type: application/json" \
  -u admin:password
```

The termination message is sent to the peer connector and both sides set the state to `TERMINATED`. A terminated
negotiation cannot be restarted; a new one must be initiated.

---

**Q: What if the provider rejects my request?**

If the provider rejects an offer (e.g., the offer is not valid per the catalog), you will receive an error response
from the initial `POST /api/v1/negotiations/request` call. The negotiation will not be created.

If the provider terminates an in-progress negotiation, the state transitions to `TERMINATED` and you will see this
when querying the negotiation status.

---

**Q: Can terms be negotiated back and forth?**

Yes. Both parties can send counteroffers:
- **Consumer** can send a counteroffer via `PUT /api/v1/negotiations/{id}/request`.
- **Provider** can send a counteroffer via `PUT /api/v1/negotiations/{id}/offer`.

Each counteroffer must reference the same offer ID and target dataset. This iterative exchange continues until both
sides agree or one terminates.

---

**Q: How can I verify an agreement is still valid?**

Check whether an agreement's constraints are still satisfied (e.g., count not exceeded, date not expired):

```bash
curl -X POST https://my-connector.example.com/api/v1/agreements/{agreementId}/enforce \
  -H "Content-Type: application/json" \
  -u admin:password
```

A `200 OK` with `success: true` means the agreement is valid and access may proceed.

---

## See Also

- [Technical Documentation](./negotiation-technical.md) — REST API reference, service details, architecture
- [Data Models](./model.md) — ContractNegotiation, Agreement, Offer class structures and builder pattern
- [Policy Enforcement](./policy-enforcement.md) — How policies are evaluated at runtime
- [DSP Specification 2025-1](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/) — The upstream protocol specification
