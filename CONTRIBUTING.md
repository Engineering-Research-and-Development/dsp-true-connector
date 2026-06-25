# Contributing to DSP TRUE Connector

This guide is the contributor entry point. The full process definition lives in [doc/development_procedure.md](doc/development_procedure.md); AI-agent-specific rules live in [AGENTS.md](AGENTS.md).

## Prerequisites

- Java 17
- Maven 3.9.4 (compatible with Java 17)
- Docker (required — integration tests use Testcontainers)
- MongoDB 7.0.12 (local or Docker) for running the connector
- 16 GB RAM, 5 GB disk, 4-core processor recommended

## Build and Test

```bash
# Full build with unit + integration tests (Docker must be running)
mvn clean verify

# Code quality
mvn checkstyle:check        # Checkstyle incl. Javadoc rules (config: scripts/ci/checkstyle.xml)
./spotbugs-scan.sh          # SpotBugs + Find Security Bugs (spotbugs-scan.cmd on Windows)
```

Run two local instances (provider on port 8090 + consumer) by passing the `provider` / `consumer` Spring profile — see [doc/profiles.md](doc/profiles.md).

## Workflow

Work is tracked on the [GitHub Project dashboard](https://github.com/users/Engineering-Research-and-Development/projects/2) (private — request access) using the columns **Backlog | To Do | Ready | In Progress | In Review | Done**. The AI-assisted slicing/decomposition/implementation pipeline that feeds this board is described in [AGENTS.md](AGENTS.md) (GitHub-Integrated Task Workflow).

1. Pick a task that satisfies the **Definition of Ready**: clear breakdown, estimation of 8–16 hours max, minimal uncertainty. Larger tasks must be split.
2. Move it to **In Progress**, assign yourself, and convert it to a GitHub issue.
3. Synchronize your local `develop`, then create a feature branch from it with a name that identifies the task (`feature/{issue-number}-{short-name}`).
4. Implement within the task scope. Do **not** extend scope — if something else needs attention, create a new Backlog task instead.
5. Open a pull request against `develop` and link it to the board item. Dedicate time daily to reviewing pending PRs.
6. After approval, the PR is **squash merged** to `develop` (one commit) and the feature branch is deleted.

## Definition of Done

A task is done only when all of these hold:

- Code is implemented and pushed to the feature branch
- New features/bug fixes are covered by junit/integration/GitHub Action tests
- All tests pass via `mvn clean verify` (Docker running — Testcontainers)
- Documentation is updated
- `CHANGELOG.md` is updated (if applicable)
- PR review is complete (all conversations resolved), squash merged to `develop`
- Feature branch deleted, board item closed

## Code Standards

- Java conventions: [.github/instructions/java.instructions.md](.github/instructions/java.instructions.md) — Records for DTOs, `Optional` over null, immutability, naming
- Javadoc required on public/protected methods (enforced by Checkstyle)
- Tests: [.github/instructions/junittest.instructions.md](.github/instructions/junittest.instructions.md); integration tests extend the Testcontainers base class ([doc/test_containers_starting_guide.md](doc/test_containers_starting_guide.md))
- Protocol model classes: builder + validation pattern ([negotiation/doc/model.md](negotiation/doc/model.md))
- Module boundaries: shared logic goes in `tools`; no cross-module reach-ins ([doc/architecture.md](doc/architecture.md))
- Protocol-facing changes must not regress DSP TCK compliance ([doc/tck/tck_compliancy.md](doc/tck/tck_compliancy.md))
- Architecturally significant decisions need an ADR ([doc/decisions/](doc/decisions/README.md))

## CI

GitHub Actions run on every push: **Build** (feature/hotfix branches — `mvn clean verify` + dockerized e2e), **Develop** (version bump + full verify), **Release** (manual — tag, Docker image, changelog propagation), **TCK compliance**. Project-board automation moves issues to **Done** when their PR merges to `develop`.
