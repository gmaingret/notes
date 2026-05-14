---
phase: 19-server-foundation
plan: "02"
subsystem: ci-cd
tags: [github-actions, ci, validation]
dependency_graph:
  requires: []
  provides: [server-ci-workflow, client-ci-workflow]
  affects: []
tech_stack:
  added: []
  patterns: [github-actions]
key_files:
  created:
    - .github/workflows/server-ci.yml
    - .github/workflows/client-ci.yml
  modified: []
requirements_completed: [CICD-01, CICD-02]
metrics:
  completed_date: "2026-03-15"
  tasks_completed: 2
  files_modified: 2
---

# Phase 19 Plan 02: CI/CD Workflows

**One-liner:** GitHub Actions workflows for server (typecheck + tests + build) and client (lint + typecheck + tests + build) that fire on PRs to `main` and pushes to `phase-*` branches when the relevant path changes.

## Status

**Shipped previously** as part of `646cd8a feat: CI/CD workflows for server and client (S25) (#55)`. Cataloged here so REQUIREMENTS.md and ROADMAP.md reflect that CICD-01 and CICD-02 are satisfied.

## What Exists

- `.github/workflows/server-ci.yml` — Node 22, runs `npx tsc --noEmit`, `npm test`, `npm run build`. No SSH deploy step. (Note: vitest tests are mocked so no Postgres service container needed; pre-existing CI step `sudo mkdir -p /data/attachments` is now redundant after Plan 19-01 made the upload path configurable, but kept for backwards compatibility.)
- `.github/workflows/client-ci.yml` — Node 22, runs `npm run lint`, `npx tsc -b --noEmit`, `npm test`, `npm run build`. No SSH deploy step.

Both follow the `.github/workflows/android-ci.yml` pattern for triggers (`pull_request: branches: [main]` + `push: branches: ['phase-*']`) and action versions (`actions/checkout@v4`, `actions/setup-node@v4`).

## Acceptance Criteria

- [x] PRs touching `server/**` trigger server-ci
- [x] PRs touching `client/**` trigger client-ci
- [x] Neither workflow contains SSH deploy steps
- [x] Both workflows are valid YAML
