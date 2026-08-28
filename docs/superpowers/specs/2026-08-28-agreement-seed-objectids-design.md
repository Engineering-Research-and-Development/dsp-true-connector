# Agreement Seed ObjectId Design

## Goal

Update every agreement document in the repository's 12 `initial_data*.json` seed files so it has a MongoDB-shaped `_id` field while preserving its existing agreement `id` and all other data.

## Design

Only objects whose `_class` is `it.eng.negotiation.model.Agreement` will be changed. Each will receive a unique 24-character lowercase hexadecimal `_id` value, inserted as the MongoDB technical identifier. Existing agreement fields, including the business `id`, will remain unchanged.

The change is limited to the seed JSON files listed in the repository instructions. JSON validity and complete agreement coverage will be checked after editing; no application code or unrelated seed documents will be modified.

## Validation

Validation will parse all 12 files, verify that every agreement has exactly one ObjectId-shaped `_id`, verify that agreement `id` values remain present, and inspect the resulting diff for unrelated changes.
