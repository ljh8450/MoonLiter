# AGENTS.md

Instructions for Codex agents working in this repository. Merge these with any
task-specific user instructions.

These guidelines favor careful, minimal changes over speed. For trivial tasks,
use judgment.

## Project Harness

Before task execution, check whether the task maps to a project role under
`.skills/`:

- Documentation-only work: read `.skills/docs.md`.
- Document briefing only, with no file changes: read `.skills/brief.md`.
- Planning work: read `.skills/planner.md`.
- Implementation review work: read `.skills/reviewer.md`.
- Frontend UI or API integration work: read `.skills/frontend.md` and
  `DESIGN.md`.
- Backend API, persistence, or deployment-facing backend work: read
  `.skills/backend.md`.

Follow the matching role file in addition to these general instructions.

## Documentation Language

Always write project documentation in Korean. This applies to documents under
`/docs`, `/plan`, `.skills/`, `README.md`, `DESIGN.md`, and other project-facing
markdown or text documentation unless the user explicitly requests another
language.

## Git Ignore Hygiene

Always keep `.gitignore` aligned with the work being done. When adding tools,
build outputs, generated files, local environment files, IDE metadata, logs, or
deployment artifacts, check whether `.gitignore` needs to be created or updated.
Do not ignore source files, documentation, plans, schema files, or other project
artifacts that should be versioned.

## 1. Think Before Coding

Do not assume, and do not hide uncertainty. Surface tradeoffs early.

Before implementing:

- State assumptions explicitly when they affect the solution.
- If multiple interpretations are plausible, present them instead of choosing
  silently.
- If a simpler approach exists, mention it. Push back when the requested
  approach seems unnecessarily complex or risky.
- If something is unclear and a reasonable assumption would be risky, stop,
  name the uncertainty, and ask.

## 2. Simplicity First

Write the minimum code that solves the requested problem. Avoid speculative
work.

- Do not add features beyond what was asked.
- Do not create abstractions for single-use code.
- Do not add flexibility or configurability that was not requested.
- Do not add error handling for scenarios that cannot occur in this codebase.
- If a solution is much longer than it needs to be, simplify it before finishing.

Use this check: would a senior engineer call this overcomplicated? If yes,
simplify.

## 3. Surgical Changes

Touch only what is needed. Clean up only issues introduced by your own work.

When editing existing code:

- Do not improve adjacent code, comments, or formatting unless required.
- Do not refactor unrelated code.
- Match the existing style, even if another style would be preferable.
- If you notice unrelated dead code or technical debt, mention it instead of
  deleting it.

When your changes create unused code:

- Remove imports, variables, functions, or files that your changes made unused.
- Do not remove pre-existing dead code unless explicitly asked.

Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

Turn tasks into verifiable goals and keep working until the result is checked.

Examples:

- "Add validation" means write or update checks for invalid inputs, then make
  them pass.
- "Fix the bug" means reproduce the bug when practical, fix it, and verify the
  fix.
- "Refactor X" means preserve behavior and run the relevant tests before
  finishing.

For multi-step tasks, state a brief plan:

```text
1. [Step] - verify: [check]
2. [Step] - verify: [check]
3. [Step] - verify: [check]
```

Strong success criteria let the agent proceed independently. Weak criteria such
as "make it work" should be clarified when they create meaningful risk.

## Success Signal

These instructions are working when diffs stay focused, unnecessary rewrites are
rare, and clarifying questions happen before implementation mistakes.
