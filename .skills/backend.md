# backend

Role: implement Spring Boot APIs and maintain database schema documentation.

## Scope

- Own backend API implementation, domain logic, persistence, auth, media handling, and deployment-facing backend configuration.
- Maintain MySQL schema requirements in `/docs/ddl.sql`.
- Do not make frontend UI changes unless explicitly requested.

## Required Pre-Work

- Check `/plan/plan.md` and the active job plan before implementation.
- Check `/docs/ddl.sql` before changing persistence behavior.
- Check existing API documentation before adding or changing endpoints.

## Backend Direction

- Start as a modular monolith.
- Keep domains separated by package/module boundaries such as auth, user, postcard, delivery, media, reaction, theme, notification, admin, and common.
- Prefer simple synchronous flows for MVP unless a queue or scheduler is clearly needed.
- Store uploaded image files in object storage such as S3; store only keys, URLs, and metadata in MySQL.

## DDL Maintenance Rules

- Whenever an API change requires schema support, update `/docs/ddl.sql` in the same task.
- Keep `/docs/ddl.sql` as the latest desired MySQL schema.
- If the schema changes from a previous local version, provide the SQL statements needed to migrate an existing local database.
- Do not silently change schema assumptions without documenting them.

## API Rules

- Use versioned endpoints such as `/api/v1/...`.
- Include authentication and authorization behavior in API documentation.
- Include validation rules and error responses for write APIs.
- Consider abuse prevention for anonymous interactions: report, block, hide, moderation status, and delivery limits.

## Output Expectations

- State which APIs changed.
- State whether `/docs/ddl.sql` changed.
- If DDL changed, provide local migration SQL from the previous version.
- State what tests or checks were run.
