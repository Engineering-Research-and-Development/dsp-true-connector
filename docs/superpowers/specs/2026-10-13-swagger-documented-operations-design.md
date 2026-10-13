# Swagger Documented Operations — Design

Date: 2026-10-13
Status: Approved design

## Goal

Show only operations with YAML OpenAPI documentation in both Swagger groups. Each
documented operation should appear once, under its YAML-configured tag, without a
duplicate springdoc-generated controller tag.

## Root cause

The live `/v3/api-docs/management-api` document shows documented user operations carrying
both `user-api-controller` and `Users` tags. `OpenApiDocsCustomizer` currently appends
the YAML tag to springdoc's generated controller tag. Operations missing YAML documentation
are also left in the generated document.

## Design

Use the existing YAML operation registry and method/signature matching as the inclusion
source for both OpenAPI groups. Exclude handlers with no matching YAML operation entry.
For included operations, replace the generated tag list with the single YAML-configured
tag. Preserve the existing summaries, descriptions, responses, examples, security
requirements, and group path matching.

This keeps the scope in the existing OpenAPI configuration/customizer and avoids changing
controllers or YAML. A separate duplicate-tag cleanup plus independent undocumented
operation filtering would add more moving parts without improving behavior.

## Verification

- Unit tests prove undocumented methods are excluded and documented methods are included
  with only their YAML tag.
- The Swagger integration test verifies that each published group contains only documented
  operations and no duplicate generated controller tags.
