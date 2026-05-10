# Project Plan

## Current Direction

- Mobile app release is the target.
- Frontend clients are planned with Kotlin and Swift.
- Backend is planned with Spring Boot.
- Deployment target is AWS.
- Database is MySQL.
- Deployment automation is planned with Docker and GitHub Actions.
- Use zero-downtime deployment practices.

## Recommended Initial Architecture

```text
Kotlin / Swift mobile clients
  -> Spring Boot API
  -> MySQL for relational data
  -> S3-compatible object storage for uploaded images
  -> AWS deployment with Docker-based release pipeline
```

## Initial Backend Shape

- Start with a modular monolith.
- Keep clear package boundaries for auth, user, postcard, delivery, media, reaction, theme, notification, admin, and common.
- Use versioned APIs under `/api/v1`.
- Keep `/docs/ddl.sql` current with the latest desired MySQL schema.

## Initial Product Priorities

- Anonymous emotional postcard creation.
- Photo upload.
- Random postcard delivery.
- Today's postcard receipt.
- Saved postcards.
- Seasonal themes.
- Emotional tags.
- Basic moderation controls such as report, block, hide, and review status.

## Open Questions

- Whether Android and iOS launch together or one platform launches first.
- Whether AWS deployment starts with EC2 Docker or ECS.
- Whether login starts with email/password, Apple, Google, or a combination.
- Whether image upload uses backend proxy upload or presigned object storage upload.

## Next Planning Jobs

- Define MVP domain model and DDL.
- Define API contract for auth, postcard, media, delivery, reaction, and moderation.
- Decide initial AWS deployment topology.
- Decide first mobile platform and UI implementation sequence.
