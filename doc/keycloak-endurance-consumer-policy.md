# Consumer-identity policy (per-tenant offer access) — deferred design notes

**Status:** OUT OF SCOPE for the ENDURANCE Keycloak integration. Kept for a future backlog slice.
**Depends on:** ENDURANCE integration (multiple trusted realms, `Tenant.realm`, outbound tokens from the acting tenant's realm).

## Problem
Provider tenant wants to let a dataset/offer be negotiated only by specific consumer tenants
(e.g. Tenant-1 yes, Tenant-2 no).

## Current code state
- Policy engine (`negotiation/.../policy/service/PolicyEnforcementPoint`, `PolicyInformationPoint`) evaluates
  agreement constraints only at access/transfer time (`AgreementAPIService.enforceAgreement`).
- `LeftOperand` supports only `count`, `dateTime`, `spatial`, `purpose`.
- No consumer-identity check at catalog fetch or contract request.

## Decisions
| # | Question | Decision |
|---|---|---|
| 1 | Representation | New ODRL **constraint** on the offer: `leftOperand: "participant"`, `operator: eq / isAnyOf`, `rightOperand: [participantId...]` (not ODRL `assignee`). |
| 2 | Consumer identifier | **`Tenant.participantId`**. |
| 3 | Catalog visibility | **Hide** restricted offers/datasets from `/{tenantId}/catalog` for non-matching consumers. |

## Design notes (agreed direction)
- Identity source is the **authenticated token only**: `iss` → `Tenant.realm` → `Tenant.participantId`.
  Never trust identity values from the DSP message body.
- Enforcement points:
  1. Catalog: filter out restricted offers/datasets.
  2. ContractRequestMessage: reject with a DSP error if the consumer isn't allowed.
  3. Transfer/access: re-check through the existing policy engine (defence in depth).
- TCK: unaffected as long as TCK offers don't use this constraint; the TCK run is still mandatory.

## Open questions for when the slice is picked up
- Exact operator set (`eq`, `isAnyOf`, `neq`/`isNoneOf`?).
- Catalog filtering when an offer has several permissions, only some restricted.
- Admin API/UI for authoring the constraint.
