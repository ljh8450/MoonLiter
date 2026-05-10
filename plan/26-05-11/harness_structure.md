# harness_structure

## Goal

Create a lightweight working harness for role-specific project execution.

## Context

The project is currently documentation-first. The intended stack is Kotlin/Swift
mobile clients, Spring Boot backend, AWS deployment, MySQL database, Docker, and
GitHub Actions based zero-downtime deployment.

## Scope

- Create role instructions under `.skills/`.
- Create `/docs/ddl.sql` as the canonical MySQL schema document.
- Create `/plan/plan.md` as the latest high-level plan.
- Create this dated job plan.

## Out of Scope

- Application code.
- Backend API implementation.
- Frontend UI implementation.
- Actual AWS, Docker, or GitHub Actions setup.

## Tasks

- [x] Define docs role - verify: `.skills/docs.md` exists.
- [x] Define planner role - verify: `.skills/planner.md` exists.
- [x] Define frontend role - verify: `.skills/frontend.md` exists.
- [x] Define backend role - verify: `.skills/backend.md` exists.
- [x] Create DDL placeholder - verify: `docs/ddl.sql` exists.
- [x] Create high-level plan - verify: `plan/plan.md` exists.

## Dependencies

- `README.md`
- `DESIGN.md`
- `AGENTS.md`

## Open Questions

- Whether `.skills/` should remain project-local markdown only or later become
  Codex-discoverable skills with `SKILL.md` folders.
- Whether deployment should start with EC2 Docker or ECS.

## Completion Criteria

- The repository has role-specific markdown instructions.
- Documentation, planning, frontend, and backend responsibilities are explicit.
- No application implementation work is performed.
