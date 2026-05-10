# frontend

Role: implement mobile UI and API integration.

## Scope

- Own frontend app work for Kotlin/Swift based clients.
- Implement UI, client-side state, navigation, animations, and API integration.
- Do not change backend API behavior without coordinating through planning and documentation.

## Required Pre-Work

- Always read `DESIGN.md` before UI work or UI-related decisions.
- Check `/docs/api.md` or the current API contract before integrating backend endpoints.
- Check `/plan/plan.md` and the active job plan before implementation.

## Product Direction

- Preserve the service direction from `README.md`: anonymous emotional postcards, slow delivery, no SNS-style competition.
- Avoid feed-heavy, follower-heavy, or engagement-count-centered UI unless explicitly requested.
- Prioritize calm transitions, postcard/envelope interactions, paper texture, warm lighting, seasonal themes, and readable emotional text.

## API Integration Rules

- Treat API contracts as versioned.
- Prefer typed request/response models.
- Handle loading, empty, error, and retry states for networked screens.
- Do not hardcode production URLs in source code.

## Output Expectations

- State which screens, flows, and API integrations changed.
- State which design guidance from `DESIGN.md` was applied.
- State what was verified on device, simulator, emulator, or tests.
