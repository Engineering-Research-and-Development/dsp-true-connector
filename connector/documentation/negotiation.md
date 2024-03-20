# Contract Negotiation

## Scheduling

When Agreement is made, scheduler should be created, or any other time based mechanism, that will notify (send event) then agreement is over, or if agreement is canceled, either from consumer or from provider side. Information about schedulers should be persisted, or when connector is started, they need to be recreated, from still valid contract agreements.

When agreement is no longer valid, event should be broadcasted, and listeners (data plane) should update status of the artifact availability.