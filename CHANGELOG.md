# Changelog

## Unreleased

## 0.2.0 - 2026-08-12

- Add the Ktor backend adapter for `GET/POST /api/v1/messages`.
- Add durable synchronization with stable idempotency keys, paginated pulls,
  cursor persistence, and duplicate-safe reconciliation.
- Classify retryable and permanent delivery failures and preserve retryable
  status while durable outbox work is deferred until its next-attempt time.
- Add synchronization, retry-recovery, remote-adapter, and public API coverage.

## 0.1.0 - 2026-08-09

- Bootstrap the KMP messages package.
- Add message validation and the durable message/outbox SQLDelight schema.
- Implement transactional offline creation, reactive observation, deterministic
  timeline ordering, and process-restart persistence.
- Add the initial schema snapshot, numbered migration, migration verification,
  persistence/outbox tests, release packaging, and complete quality/security
  gates.
